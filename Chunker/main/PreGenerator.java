package main;

import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.Chunk;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
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

	private static final boolean IS_PAPER = detectPaper();
	private long task_queue_timer;

	public PreGenerator(JavaPlugin plugin) {
		this.plugin = plugin;
		logPlain("Available Processors: " + PluginSettings.THREADS());
		this.playerEvents = new PlayerEvents(tasks);
		this.load = new Load();
		this.save = new Save();
		this.print = new Print();
		plugin.getServer().getPluginManager().registerEvents(playerEvents, plugin);
	}

	/**
	 * Enables the pre-generator for a specific world.
	 */
	public synchronized void enable(int parallelTasksMultiplier, char timeUnit, int timeValue, int printTime, World world, long radius) {
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

		if (world.getEnvironment() == Environment.NORMAL) {
			task_queue_timer = PluginSettings.world_task_queue_timer();
		} else if (world.getEnvironment() == Environment.NETHER) {
			task_queue_timer = PluginSettings.world_nether_task_queue_timer();
		} else if (world.getEnvironment() == Environment.THE_END) {
			task_queue_timer = PluginSettings.world_the_end_task_queue_timer();
		}

		synchronized (tasks) {
			tasks.put(worldId, task);
		}
		task.timerStart = System.currentTimeMillis();
		load.state(plugin, task);
		initializeSchedulers(task);

		if (task.totalChunksProcessed.sum() >= radius) {
			logColor(YELLOW, world.getName() + " " + RADIUS_EXCEEDED_MESSAGE);
			terminate(task);
			return;
		}

		startGeneration(task);
		print.start(task);
	}

	/**
	 * Initializes the schedulers for printing and task execution.
	 */
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
		HandlerList.unregisterAll(playerEvents);
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
		task.enabled = false;
		synchronized (tasks) {
			tasks.remove(task.worldId);
		}
	}

	/**
	 * Disables both schedulers associated with the task.
	 */
	private void shutdownSchedulers(PreGenerationTask task) {
		if (!task.enabled) {
			return;
		}
		if (task.printScheduler != null) {
			task.printScheduler.setEnabled(false);
		}
		if (task.taskSubmitScheduler != null) {
			task.taskSubmitScheduler.setEnabled(false);
		}
	}

	/**
	 * Starts the chunk generation process based on whether the server is running PaperMC.
	 */
	private void startGeneration(PreGenerationTask task) {
		if (IS_PAPER) {
			task.taskSubmitScheduler.scheduleAtFixedRate(
					() -> {
						try {
							if (!task.enabled) {
								return;
							}
							for (int i = 0; i < task.parallelTasksMultiplier; i++) {
								if (task.totalChunksProcessed.sum() >= task.radius) {
									saveTaskState(task);
									return;
								}
								RegionChunkIterator.NextChunkResult nextChunkResult = task.chunkIterator.getNextChunkCoordinates();
								if (nextChunkResult == null) {
									saveTaskState(task);
									return;
								}
								if (nextChunkResult.regionCompleted) {
									saveTaskState(task);
								}
								ChunkPos nextChunkPos = nextChunkResult.chunkPos;
								CompletableFuture.runAsync(() -> {
									try {
										processChunk(task, nextChunkPos);
									} catch (Exception e) {
										exceptionMsg("Exception in processChunk: " + e.getMessage());
										e.printStackTrace();
									}
								}).exceptionally(ex -> {
									exceptionMsg("Exception in CompletableFuture in chunk generation: " + ex.getMessage());
									ex.printStackTrace();
									return null;
								});
							}
						} catch (Exception e) {
							exceptionMsg("Exception in repeated chunk generation: " + e.getMessage());
							e.printStackTrace();
						}
					},
					0, task_queue_timer, TimeUnit.MILLISECONDS, task.taskSubmitScheduler.isEnabledSupplier());
		} else {
			new BukkitRunnable() {
				@Override
				public void run() {
					task.tasks = Math.max(1, (int) (task.parallelTasksMultiplier / 2.5));
					for (int i = 0; i < task.tasks; i++) {
						syncProcess(task);
					}
				}
			}.runTaskTimer(plugin, 0L, 0L);
		}
	}

	/**
	 * Synchronously processes chunk loading tasks.
	 */
	private void syncProcess(PreGenerationTask task) {
		try {
			if (!task.enabled) {
				return;
			}
			if (task.totalChunksProcessed.sum() >= task.radius) {
				return;
			}
			RegionChunkIterator.NextChunkResult nextChunkResult = task.chunkIterator.getNextChunkCoordinates();
			if (nextChunkResult == null) {
				saveTaskState(task);
				return;
			}
			if (nextChunkResult.regionCompleted) {
				saveTaskState(task);
			}
			ChunkPos chunkPos = nextChunkResult.chunkPos;
			handleChunk(task, chunkPos);
			completionCheck(task);
		} catch (Exception e) {
			exceptionMsg("Exception in syncProcess: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Saves the task state.
	 */
	private void saveTaskState(PreGenerationTask task) {
		save.state(plugin, task);
	}

	/**
	 * Asynchronously retrieves a chunk using the world's async method and unloads it after loading.
	 */
	private void processChunk(PreGenerationTask task, ChunkPos chunkPos) {
		try {
			if (!task.enabled) {
				return;
			}
			getChunkAsync(task, chunkPos, true);
			task.totalChunksProcessed.increment();
			task.chunksThisCycle++;
			completionCheck(task);
		} catch (Exception e) {
			exceptionMsg("Exception in processChunk: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Asynchronously retrieves a chunk using the world's async method and unloads it after loading.
	 */
	private void getChunkAsync(PreGenerationTask task, ChunkPos chunkPos, boolean gen) {
		if (!task.enabled) {
			return;
		}
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
	 * Handles chunk loading and unloading synchronously.
	 */
	private void handleChunk(PreGenerationTask task, ChunkPos chunkPos) {
		try {
			if (!task.enabled) {
				return;
			}
			Chunk chunk = task.world.getChunkAt(chunkPos.getX(), chunkPos.getZ());
			chunk.load(true);
			while (chunk.isLoaded() && !chunk.isEntitiesLoaded()) {
				boolean unloaded = task.world.unloadChunk(chunk.getX(), chunk.getZ(), true);
				if (!unloaded) {
					break;
				}
			}
			task.totalChunksProcessed.increment();
			task.chunksThisCycle++;
		} catch (Exception e) {
			exceptionMsg("Exception in handleChunk: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Checks if the chunk processing has completed based on the radius.
	 */
	private void completionCheck(PreGenerationTask task) {
		if (!task.enabled) {
			return;
		}
		if (task.totalChunksProcessed.sum() >= task.radius) {
			task.complete = true;
			terminate(task);
		}
	}

	/**
	 * Handles chunk load events to manage chunk unloading during pre-generation.
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	private void onChunkLoad(ChunkLoadEvent event) {
		try {
			int worldId = WorldIdManager.getWorldId(event.getWorld());
			PreGenerationTask task;
			synchronized (tasks) {
				task = tasks.get(worldId);
			}
			if (task == null || !task.enabled) {
				return;
			}
			handleChunkLoad(task, event);
		} catch (Exception e) {
			exceptionMsg("Exception in onChunkLoad: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Processes a chunk load event, unloading chunks that shouldn't remain loaded.
	 */
	private void handleChunkLoad(PreGenerationTask task, ChunkLoadEvent event) {
		try {
			if (!task.enabled) {
				return;
			}
			Chunk chunk = event.getChunk();
			if (chunk == null) {
				return;
			}
			ChunkPos chunkPos = new ChunkPos(chunk.getX(), chunk.getZ());
			if (task.playerLoadedChunks.contains(chunkPos)) {
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
	 * Detects if the server is running PaperMC.
	 */
	private static boolean detectPaper() {
		try {
			Class.forName("com.destroystokyo.paper.PaperConfig");
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}
}
