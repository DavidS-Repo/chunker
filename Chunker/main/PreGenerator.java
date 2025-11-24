package main;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.CommandSender;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

import static main.ConsoleColorUtils.*;

/**
 * Handles async chunk pre-generation for one or more worlds.
 */
public class PreGenerator implements Listener {

	private final PlayerEvents playerEvents;
	private final JavaPlugin plugin;
	private final Print print;
	private final Load load;
	private final Save save;
	private final ConcurrentHashMap<Integer, PreGenerationTask> tasks = new ConcurrentHashMap<>();

	private static final String ENABLED_WARNING_MESSAGE   = "pre-generator is already enabled.";
	private static final String DISABLED_WARNING_MESSAGE  = "pre-generator is already disabled.";
	private static final String RADIUS_EXCEEDED_MESSAGE   = "radius reached. To process more chunks, please increase the radius.";

	private static final boolean IS_PAPER = detectPaper();
	private static final boolean IS_FOLIA = detectFolia();
	private static final boolean REQUIRES_CHUNK_SAFETY = ServerVersion.getInstance().requiresChunkSafety();

	private long task_queue_timer;

	/**
	 * Creates a new pre-generator instance and registers player listeners.
	 *
	 * @param plugin the owning plugin
	 */
	public PreGenerator(JavaPlugin plugin) {
		this.plugin = plugin;
		logPlain("Available Processors: " + PluginSettings.getAvailableProcessors());
		if (IS_PAPER && REQUIRES_CHUNK_SAFETY) {
			logPlain("Server version " + ServerVersion.getInstance().getVersionString() + " detected - using chunk safety measures");
		}
		this.playerEvents = new PlayerEvents(tasks);
		this.load  = new Load();
		this.save  = new Save();
		this.print = new Print();
		plugin.getServer().getPluginManager().registerEvents(playerEvents, plugin);
	}

	/**
	 * Starts a pre-generation task for the given world.
	 *
	 * @return true if the task was created, false if it was already running
	 */
	public synchronized boolean enable(CommandSender sender,
			int parallelTasksMultiplier,
			char timeUnit,
			int timeValue,
			int printTime,
			World world,
			long radius) {
		int worldId = WorldIdManager.getWorldId(world);

		synchronized (tasks) {
			if (tasks.containsKey(worldId)) {
				colorMessage(sender, YELLOW, world.getName() + " " + ENABLED_WARNING_MESSAGE);
				return false;
			}
		}

		PreGenerationTask task = new PreGenerationTask();
		task.parallelTasksMultiplier = parallelTasksMultiplier;
		task.timeUnit  = timeUnit;
		task.timeValue = timeValue;
		task.printTime = printTime;
		task.world     = world;
		task.radius    = radius;
		task.enabled   = true;
		task.worldId   = worldId;
		task.stopAfterCurrentRegion = false;

		task_queue_timer = PluginSettings.getTaskQueueTimer(world.getName());

		synchronized (tasks) {
			tasks.put(worldId, task);
		}

		Location currentCenter = resolveCenterLocation(world);
		int currentCenterBlockX = currentCenter.getBlockX();
		int currentCenterBlockZ = currentCenter.getBlockZ();

		task.timerStart = System.currentTimeMillis();
		boolean loaded = load.state(plugin, task);

		if (!loaded || task.totalChunksProcessed.sum() == 0L) {
			applyCenter(task, currentCenter);
		} else {
			if (task.stateHasCenter &&
					(task.centerBlockX != currentCenterBlockX || task.centerBlockZ != currentCenterBlockZ)) {
				ResetPreGenState.reset(plugin, world.getName());
				task.chunkIterator.reset();
				task.totalChunksProcessed.reset();
				task.stopAfterCurrentRegion = false;
				applyCenter(task, currentCenter);
			} else if (!task.stateHasCenter) {
				task.centerBlockX = currentCenterBlockX;
				task.centerBlockZ = currentCenterBlockZ;
				task.stateHasCenter = true;
			}
		}

		initializeSchedulers(task);

		task.cleanupScheduler = new AsyncDelayedScheduler();
		task.cleanupScheduler.scheduleAtFixedRate(
				() -> {
					if (task.world.getPlayers().isEmpty()) {
						task.playerLoadedChunks.clear();
					}
				},
				60_000,
				60_000,
				TimeUnit.MILLISECONDS,
				task.cleanupScheduler.isEnabledSupplier()
				);

		if (task.totalChunksProcessed.sum() >= radius) {
			colorMessage(sender, YELLOW, world.getName() + " " + RADIUS_EXCEEDED_MESSAGE);
			terminate(task);
			return false;
		}

		startGeneration(task);
		print.start(task);
		return true;
	}

	/**
	 * Finds the center point for generation for a world.
	 * Uses settings center, world border center, or spawn as fallback.
	 */
	private Location resolveCenterLocation(World world) {
		String worldName = world.getName();
		PluginSettings.WorldSettings worldSettings = PluginSettings.getWorldSettings(worldName);
		String centerSetting = worldSettings.center();
		Location centerLocation;

		if (centerSetting == null || centerSetting.trim().isEmpty() || centerSetting.equalsIgnoreCase("default")) {
			centerLocation = world.getWorldBorder().getCenter();
			if (centerLocation == null) {
				centerLocation = world.getSpawnLocation();
			}
		} else {
			String trimmed = centerSetting.trim();
			if (trimmed.equals("~ ~")) {
				centerLocation = world.getSpawnLocation();
			} else {
				String[] parts = trimmed.split("\\s+");
				if (parts.length >= 2) {
					try {
						double x = Double.parseDouble(parts[0]);
						double z = Double.parseDouble(parts[1]);
						centerLocation = new Location(world, x, world.getSpawnLocation().getY(), z);
					} catch (NumberFormatException e) {
						centerLocation = world.getWorldBorder().getCenter();
						if (centerLocation == null) {
							centerLocation = world.getSpawnLocation();
						}
					}
				} else {
					centerLocation = world.getWorldBorder().getCenter();
					if (centerLocation == null) {
						centerLocation = world.getSpawnLocation();
					}
				}
			}
		}

		return centerLocation;
	}

	/**
	 * Applies the center position to the task and centers the iterator.
	 */
	private void applyCenter(PreGenerationTask task, Location centerLocation) {
		task.centerBlockX = centerLocation.getBlockX();
		task.centerBlockZ = centerLocation.getBlockZ();
		task.stateHasCenter = true;

		int centerChunkX = task.centerBlockX >> 4;
		int centerChunkZ = task.centerBlockZ >> 4;
		int centerRegionX = Math.floorDiv(centerChunkX, 32);
		int centerRegionZ = Math.floorDiv(centerChunkZ, 32);
		task.chunkIterator.setCenterRegion(centerRegionX, centerRegionZ);
	}

	/**
	 * Creates scheduler instances for a task.
	 */
	private void initializeSchedulers(PreGenerationTask task) {
		task.printScheduler      = new AsyncDelayedScheduler();
		task.taskSubmitScheduler = new AsyncDelayedScheduler();
	}

	/**
	 * Stops pre-generation for a world when called by a command.
	 */
	public synchronized void disable(CommandSender sender, World world, boolean showMessages) {
		int worldId = WorldIdManager.getWorldId(world);
		PreGenerationTask task;

		synchronized (tasks) {
			task = tasks.get(worldId);
			if (task == null || !task.enabled) {
				if (showMessages) {
					colorMessage(sender, YELLOW, world.getName() + " " + DISABLED_WARNING_MESSAGE);
				}
				tasks.remove(worldId);
				return;
			}
			tasks.remove(worldId);
		}

		terminate(task);

		if (tasks.isEmpty()) {
			HandlerList.unregisterAll(playerEvents);
		}
	}

	/**
	 * Shuts down a pre-generation task and prints final stats.
	 */
	private synchronized void terminate(PreGenerationTask task) {
		if (!task.complete && task.totalChunksProcessed.sum() >= task.radius) {
			task.complete = true;
		}
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

	/**
	 * Disables all schedulers for a task.
	 */
	private void shutdownSchedulers(PreGenerationTask task) {
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
	 * Starts the main generation loop for a task.
	 * Uses different paths for Folia, Paper, and Spigot.
	 */
	private void startGeneration(PreGenerationTask task) {
		if (IS_FOLIA) {
			task.taskSubmitScheduler.scheduleAtFixedRate(
					() -> {
						if (!task.enabled) return;

						for (int i = 0; i < task.parallelTasksMultiplier; i++) {
							RegionChunkIterator.NextChunkResult nextChunkResult =
									task.chunkIterator.getNextChunkCoordinates();
							if (nextChunkResult == null) {
								saveTaskState(task);
								task.taskSubmitScheduler.setEnabled(false);
								task.complete = true;
								terminate(task);
								return;
							}
							if (nextChunkResult.regionCompleted) {
								saveTaskState(task);
								if (task.stopAfterCurrentRegion) {
									task.taskSubmitScheduler.setEnabled(false);
									task.complete = true;
									terminate(task);
									return;
								}
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
			task.taskSubmitScheduler.scheduleAtFixedRate(
					() -> {
						if (!task.enabled) return;
						for (int i = 0; i < task.parallelTasksMultiplier; i++) {
							RegionChunkIterator.NextChunkResult nextChunkResult =
									task.chunkIterator.getNextChunkCoordinates();
							if (nextChunkResult == null) {
								saveTaskState(task);
								task.taskSubmitScheduler.setEnabled(false);
								task.complete = true;
								terminate(task);
								return;
							}
							if (nextChunkResult.regionCompleted) {
								saveTaskState(task);
								if (task.stopAfterCurrentRegion) {
									task.taskSubmitScheduler.setEnabled(false);
									task.complete = true;
									terminate(task);
									return;
								}
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
	 * Loads and unloads a single chunk on Folia.
	 */
	private void processChunkFolia(PreGenerationTask task, ChunkPos chunkPos) {
		if (!task.enabled) return;
		Bukkit.getRegionScheduler().execute(plugin, task.world, chunkPos.getX(), chunkPos.getZ(), () -> {
			if (!task.enabled) return;
			task.world.getChunkAtAsync(chunkPos.getX(), chunkPos.getZ(), true).thenAccept(chunk -> {
				if (!task.enabled) {
					return;
				}
				if (chunk != null && chunk.isLoaded()) {
					Bukkit.getRegionScheduler().execute(plugin, task.world, chunkPos.getX(), chunkPos.getZ(), () -> {
						if (!task.enabled) {
							return;
						}
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
	 * Loads a chunk on Paper and unloads it again.
	 */
	private void processChunkPaper(PreGenerationTask task, ChunkPos chunkPos) {
		if (!task.enabled) return;
		if (REQUIRES_CHUNK_SAFETY) {
			getChunkAsyncWithSafety(task, chunkPos, true);
		} else {
			getChunkAsync(task, chunkPos, true);
		}
		task.totalChunksProcessed.increment();
		task.chunksThisCycle++;
		completionCheck(task);
	}

	/**
	 * Processes chunks on the main thread for Spigot or Bukkit.
	 */
	private void syncProcess(PreGenerationTask task) {
		try {
			if (!task.enabled) return;
			RegionChunkIterator.NextChunkResult nextChunkResult = task.chunkIterator.getNextChunkCoordinates();
			if (nextChunkResult == null) {
				saveTaskState(task);
				task.complete = true;
				terminate(task);
				return;
			}
			if (nextChunkResult.regionCompleted) {
				saveTaskState(task);
				if (task.stopAfterCurrentRegion) {
					task.complete = true;
					terminate(task);
					return;
				}
			}
			ChunkPos chunkPos = nextChunkResult.chunkPos;
			handleChunkBukkit(task, chunkPos);
			completionCheck(task);
		} catch (Exception e) {
			exceptionMsg("Exception in syncProcess: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Loads and unloads a chunk on the main thread.
	 */
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
	 * Loads a chunk on Paper using a dummy player for safe versions.
	 */
	public void getChunkAsyncWithSafety(PreGenerationTask task, ChunkPos chunkPos, boolean gen) {
		if (!task.enabled) return;
		World world = task.world;
		Location home = world.getSpawnLocation();
		Bukkit.getScheduler().runTask(plugin, () -> {
			ChunkSafety.spawnDummyAndProcess(
					plugin,
					world,
					home,
					chunkPos
					);
		});
	}

	/**
	 * Loads a chunk on Paper using the async API directly.
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
	 * Writes current iterator and progress state to disk.
	 */
	private void saveTaskState(PreGenerationTask task) {
		save.state(plugin, task);
	}

	/**
	 * Marks that generation should stop after finishing the current region.
	 */
	private void completionCheck(PreGenerationTask task) {
		if (!task.enabled) return;
		if (!task.stopAfterCurrentRegion && task.totalChunksProcessed.sum() >= task.radius) {
			task.stopAfterCurrentRegion = true;
		}
	}

	/**
	 * Handles chunk load events while generation is active.
	 * Used to avoid keeping old chunks loaded by players.
	 */
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

	/**
	 * Unloads non-new chunks that load while pre-generation is running.
	 */
	private void handleChunkLoad(PreGenerationTask task, ChunkLoadEvent event) {
		try {
			if (!task.enabled) return;
			Chunk chunk = event.getChunk();
			if (chunk == null) return;
			ChunkPos chunkPos = ChunkPos.get(chunk.getX(), chunk.getZ());
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
	 * Checks if the server is running on Paper.
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
	 * Checks if the server is running on Folia.
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
