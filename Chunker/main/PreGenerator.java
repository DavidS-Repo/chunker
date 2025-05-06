package main;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static main.ConsoleColorUtils.*;

/**
 * PreGenerator is responsible for pre-generating chunks asynchronously to improve server performance.
 */
public class PreGenerator implements Listener {

	private final PlayerEvents playerEvents;
	private final JavaPlugin plugin;
	private final Print print;
	private final Load load;
	private final Save save;
	private final ConcurrentHashMap<Integer, PreGenerationTask> tasks = new ConcurrentHashMap<>();

	private static final String ENABLED_WARNING_MESSAGE = "pre-generator is already enabled.";
	private static final String DISABLED_WARNING_MESSAGE = "pre-generator is already disabled.";
	private static final String RADIUS_EXCEEDED_MESSAGE = "radius reached. To process more chunks, please increase the radius.";

	// Detect Paper and Folia at runtime
	private static final boolean IS_PAPER = detectPaper();
	private static final boolean IS_FOLIA = detectFolia();

	private long task_queue_timer;

	public PreGenerator(JavaPlugin plugin) {
		this.plugin = plugin;
		logPlain("Available Processors: " + PluginSettings.getAvailableProcessors());
		this.playerEvents = new PlayerEvents(tasks);
		this.load = new Load();
		this.save = new Save();
		this.print = new Print();

		plugin.getServer().getPluginManager().registerEvents(playerEvents, plugin);
	}

	/**
	 * Enables the pre-generator for a specific world.
	 */
	public synchronized void enable(int parallelTasksMultiplier,
			char timeUnit,
			int timeValue,
			int printTime,
			World world,
			long radius) {
		int worldId = WorldIdManager.getWorldId(world);

		synchronized (tasks) {
			if (tasks.containsKey(worldId)) {
				logColor(YELLOW, world.getName() + " " + ENABLED_WARNING_MESSAGE);
				return;
			}
		}

		PreGenerationTask task = new PreGenerationTask();
		task.parallelTasksMultiplier = parallelTasksMultiplier;
		task.timeUnit = timeUnit;
		task.timeValue = timeValue;
		task.printTime = printTime;
		task.world = world;
		task.radius = radius;
		task.enabled = true;
		task.worldId = worldId;

		// Determine task queue timer based on environment
		task_queue_timer = PluginSettings.getTaskQueueTimer(world.getName());

		synchronized (tasks) {
			tasks.put(worldId, task);
		}

		task.timerStart = System.currentTimeMillis();
		load.state(plugin, task);
		initializeSchedulers(task);

		// Initialize and schedule the cleanup scheduler for playerLoadedChunks.
		task.cleanupScheduler = new AsyncDelayedScheduler();
		task.cleanupScheduler.scheduleAtFixedRate(
				() -> {
					// If no players are in the task's world, clear the set.
					if (task.world.getPlayers().isEmpty()) {
						task.playerLoadedChunks.clear();
					}
				},
				60000,  // initial delay: 60 seconds
				60000,  // period: 60 seconds
				TimeUnit.MILLISECONDS,
				task.cleanupScheduler.isEnabledSupplier()
				);

		if (task.totalChunksProcessed.sum() >= radius) {
			logColor(YELLOW, world.getName() + " " + RADIUS_EXCEEDED_MESSAGE);
			terminate(task);
			return;
		}

		startGeneration(task);
		print.start(task);
	}

	private void initializeSchedulers(PreGenerationTask task) {
		task.printScheduler = new AsyncDelayedScheduler();
		task.taskSubmitScheduler = new AsyncDelayedScheduler();
	}

	/**
	 * Disables the pre-generator for a specific world.
	 */
	public synchronized void disable(World world) {
		int worldId = WorldIdManager.getWorldId(world);
		PreGenerationTask task;

		synchronized (tasks) {
			task = tasks.get(worldId);
			if (task == null) {
				logColor(YELLOW, world.getName() + " " + DISABLED_WARNING_MESSAGE);
				return;
			}
			if (!task.enabled) {
				logColor(YELLOW, world.getName() + " " + DISABLED_WARNING_MESSAGE);
				tasks.remove(worldId);
				return;
			}
			tasks.remove(worldId);
		}

		terminate(task);

		// Only unregister the global player event listener if there are no tasks remaining.
		if (tasks.isEmpty()) {
			HandlerList.unregisterAll(playerEvents);
		}
	}

	/**
	 * Terminates the pre-generation task, shutting down schedulers and saving state.
	 */
	private synchronized void terminate(PreGenerationTask task) {
		task.timerEnd = System.currentTimeMillis();
		try {
			save.state(plugin, task);
			print.info(task);
		} catch (Exception e) {
			exceptionMsg("Exception during saveTaskState or printInfo: " + e.getMessage());
			e.printStackTrace();
		}
		print.stop(task);
		shutdownSchedulers(task);
		task.playerLoadedChunks.clear();
		task.enabled = false;
		synchronized (tasks) {
			tasks.remove(task.worldId);
		}
	}

	private void shutdownSchedulers(PreGenerationTask task) {
		if (!task.enabled) return;
		if (task.printScheduler != null) {
			task.printScheduler.setEnabled(false);
		}
		if (task.taskSubmitScheduler != null) {
			task.taskSubmitScheduler.setEnabled(false);
		}
		if (task.cleanupScheduler != null) {
			task.cleanupScheduler.setEnabled(false);
		}
	}

	/**
	 * Starts the chunk generation process using your custom AsyncDelayedScheduler for Folia, or existing paths for Paper/Spigot.
	 */
	private void startGeneration(PreGenerationTask task) {
		if (IS_FOLIA) {
			// FOLIA PATH with custom scheduler
			task.taskSubmitScheduler.scheduleAtFixedRate(
					() -> {
						if (!task.enabled) return;

						for (int i = 0; i < task.parallelTasksMultiplier; i++) {
							if (task.totalChunksProcessed.sum() >= task.radius) {
								saveTaskState(task);
								task.taskSubmitScheduler.setEnabled(false);
								return;
							}
							RegionChunkIterator.NextChunkResult nextChunkResult =
									task.chunkIterator.getNextChunkCoordinates();
							if (nextChunkResult == null) {
								saveTaskState(task);
								task.taskSubmitScheduler.setEnabled(false);
								return;
							}
							// Restore saving after region completion
							if (nextChunkResult.regionCompleted) {
								saveTaskState(task);
							}
							ChunkPos nextChunkPos = nextChunkResult.chunkPos;
							processChunkFolia(task, nextChunkPos);
						}
					},
					1,
					task_queue_timer,
					TimeUnit.MILLISECONDS,
					task.taskSubmitScheduler.isEnabledSupplier()
					);
		} else if (IS_PAPER) {
			// PAPER PATH
			task.taskSubmitScheduler.scheduleAtFixedRate(
					() -> {
						if (!task.enabled) return;
						for (int i = 0; i < task.parallelTasksMultiplier; i++) {
							if (task.totalChunksProcessed.sum() >= task.radius) {
								saveTaskState(task);
								return;
							}
							RegionChunkIterator.NextChunkResult nextChunkResult =
									task.chunkIterator.getNextChunkCoordinates();
							if (nextChunkResult == null) {
								saveTaskState(task);
								return;
							}
							// Restore saving after region completion
							if (nextChunkResult.regionCompleted) {
								saveTaskState(task);
							}
							ChunkPos nextChunkPos = nextChunkResult.chunkPos;
							CompletableFuture.runAsync(() -> {
								try {
									processChunkPaper(task, nextChunkPos);
								} catch (Exception e) {
									exceptionMsg("Exception in processChunkPaper: " + e.getMessage());
									e.printStackTrace();
								}
							}).exceptionally(ex -> {
								exceptionMsg("Exception in CompletableFuture: " + ex.getMessage());
								ex.printStackTrace();
								return null;
							});
						}
					},
					0,
					task_queue_timer,
					TimeUnit.MILLISECONDS,
					task.taskSubmitScheduler.isEnabledSupplier()
					);
		} else {
			// SPIGOT / BUKKIT PATH (unchanged)
			new org.bukkit.scheduler.BukkitRunnable() {
				@Override
				public void run() {
					if (!task.enabled) {
						cancel();
						return;
					}
					task.tasks = Math.max(1, (int) (task.parallelTasksMultiplier / 2.5));
					for (int i = 0; i < task.tasks; i++) {
						syncProcess(task);
					}
				}
			}.runTaskTimer(plugin, 0L, 1L);
		}
	}

	/**
	 * Folia chunk processing.
	 */
	private void processChunkFolia(PreGenerationTask task, ChunkPos chunkPos) {
		if (!task.enabled) return;
		Bukkit.getRegionScheduler().execute(plugin, task.world, chunkPos.getX(), chunkPos.getZ(), () -> {
			if (!task.enabled) return;
			task.world.getChunkAtAsync(chunkPos.getX(), chunkPos.getZ(), true).thenAccept(chunk -> {
				if (chunk != null && chunk.isLoaded()) {
					Bukkit.getRegionScheduler().execute(plugin, task.world, chunkPos.getX(), chunkPos.getZ(), () -> {
						chunk.unload(true);
						task.totalChunksProcessed.increment();
						task.chunksThisCycle++;
						completionCheck(task);
					});
				} else {
					task.totalChunksProcessed.increment();
					completionCheck(task);
				}
			}).exceptionally(ex -> {
				exceptionMsg("Async chunk load exception in processChunkFolia: " + ex.getMessage());
				ex.printStackTrace();
				task.totalChunksProcessed.increment();
				completionCheck(task);
				return null;
			});
		});
	}

	/**
	 * Paper: async chunk load then immediate unload.
	 */
	private void processChunkPaper(PreGenerationTask task, ChunkPos chunkPos) {
		if (!task.enabled) return;
		getChunkAsync(task, chunkPos, true);
		task.totalChunksProcessed.increment();
		task.chunksThisCycle++;
		completionCheck(task);
	}

	/**
	 * Spigot/Bukkit synchronous chunk processing.
	 */
	private void syncProcess(PreGenerationTask task) {
		try {
			if (!task.enabled) return;
			if (task.totalChunksProcessed.sum() >= task.radius) return;
			RegionChunkIterator.NextChunkResult nextChunkResult = task.chunkIterator.getNextChunkCoordinates();
			if (nextChunkResult == null) {
				saveTaskState(task);
				return;
			}
			if (nextChunkResult.regionCompleted) {
				saveTaskState(task);
			}
			ChunkPos chunkPos = nextChunkResult.chunkPos;
			handleChunkBukkit(task, chunkPos);
			completionCheck(task);
		} catch (Exception e) {
			exceptionMsg("Exception in syncProcess: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void handleChunkBukkit(PreGenerationTask task, ChunkPos chunkPos) {
		try {
			if (!task.enabled) return;
			Chunk chunk = task.world.getChunkAt(chunkPos.getX(), chunkPos.getZ());
			chunk.load(true);
			while (chunk.isLoaded() && !chunk.isEntitiesLoaded()) {
				boolean unloaded = task.world.unloadChunk(chunk.getX(), chunk.getZ(), true);
				if (!unloaded) break;
			}
			task.totalChunksProcessed.increment();
			task.chunksThisCycle++;
		} catch (Exception e) {
			exceptionMsg("Exception in handleChunkBukkit: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Paper's getChunkAtAsync + immediate unload.
	 */
	private void getChunkAsync(PreGenerationTask task, ChunkPos chunkPos, boolean gen) {
		if (!task.enabled) return;
		CompletableFuture<Chunk> future = task.world.getChunkAtAsync(chunkPos.getX(), chunkPos.getZ(), gen);
		future.thenAccept(chunk -> {
			if (chunk != null && chunk.isLoaded()) {
				chunk.unload(true);
			}
		}).exceptionally(ex -> {
			exceptionMsg("Exception in getChunkAsync: " + ex.getMessage());
			ex.printStackTrace();
			return null;
		});
	}

	/**
	 * Saves current progress to disk.
	 */
	private void saveTaskState(PreGenerationTask task) {
		save.state(plugin, task);
	}

	/**
	 * Checks if the radius limit is reached and terminates the task.
	 */
	private void completionCheck(PreGenerationTask task) {
		if (!task.enabled) return;
		if (task.totalChunksProcessed.sum() >= task.radius) {
			task.complete = true;
			terminate(task);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	private void onChunkLoad(ChunkLoadEvent event) {
		try {
			int worldId = WorldIdManager.getWorldId(event.getWorld());
			PreGenerationTask task;
			synchronized (tasks) {
				task = tasks.get(worldId);
			}
			if (task == null || !task.enabled) return;
			handleChunkLoad(task, event);
		} catch (Exception e) {
			exceptionMsg("Exception in onChunkLoad: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void handleChunkLoad(PreGenerationTask task, ChunkLoadEvent event) {
		try {
			if (!task.enabled) return;
			Chunk chunk = event.getChunk();
			if (chunk == null) return;
			// Use the new factory method instead of the constructor.
			ChunkPos chunkPos = ChunkPos.get(chunk.getX(), chunk.getZ());
			if (task.playerLoadedChunks.contains(chunkPos)) {
				// If a player explicitly loaded it, don't unload.
				return;
			}
			if (!event.isNewChunk()) {
				task.world.unloadChunk(chunk);
			} else {
				task.playerLoadedChunks.add(chunkPos);
			}
		} catch (Exception e) {
			exceptionMsg("Exception in handleChunkLoad: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Detect if running Paper.
	 */
	private static boolean detectPaper() {
		try {
			Class.forName("com.destroystokyo.paper.PaperConfig");
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	/**
	 * Detect if running Folia.
	 */
	private static boolean detectFolia() {
		try {
			Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}
}
