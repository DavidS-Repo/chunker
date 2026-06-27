package main;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
	private final Int2ObjectOpenHashMap<PreGenerationTask> tasks = new Int2ObjectOpenHashMap<>();
	private final Object tasksLock = new Object();

	private static final String ENABLED_WARNING_MESSAGE = "pre-generator is already enabled.";
	private static final String DISABLED_WARNING_MESSAGE = "pre-generator is already disabled.";
	private static final String RADIUS_EXCEEDED_MESSAGE = "radius reached. To process more chunks, please increase the radius.";

	private static final boolean IS_FOLIA = detectFolia();
	private static final boolean IS_PAPER = detectPaper();
	private static final boolean REQUIRES_CHUNK_SAFETY = ServerVersion.getInstance().requiresChunkSafety();
	private static final int SAFETY_IN_FLIGHT_WINDOW = 16;

	/**
	 * Creates a new pre-generator instance and registers player listeners.
	 *
	 * @param plugin the owning plugin
	 */
	public PreGenerator(JavaPlugin plugin) {
		this.plugin = plugin;
		logPlain("Available Processors: " + PluginSettings.getAvailableProcessors());
		if (IS_PAPER && REQUIRES_CHUNK_SAFETY) {
			logPlain("Server version " + ServerVersion.getInstance().getVersionString() + " detected, using chunk safety measures");
		}
		this.playerEvents = new PlayerEvents(tasks, tasksLock);
		this.load = new Load();
		this.save = new Save();
		this.print = new Print();
		plugin.getServer().getPluginManager().registerEvents(playerEvents, plugin);
	}

	/**
	 * Starts a pre-generation task for the given world.
	 *
	 * @return true if the task was created, false if it was already running
	 */
	public boolean enable(CommandSender sender,
			int parallelTasksMultiplier,
			char timeUnit,
			int timeValue,
			int printTime,
			World world,
			long radius,
			long targetSideChunks,
			boolean forceChunkSafety) {
		int worldId = WorldIdManager.getWorldId(world);
		String worldName = WorldRegistry.id(world);

		PreGenerationTask task = new PreGenerationTask();
		task.parallelTasksMultiplier = parallelTasksMultiplier;
		task.timeUnit = timeUnit;
		task.timeValue = timeValue;
		task.printTime = printTime;
		task.world = world;
		task.worldName = worldName;
		task.radius = radius;
		task.targetSideChunks = targetSideChunks;
		task.forceChunkSafety = forceChunkSafety;
		task.enabled = true;
		task.worldId = worldId;
		task.stopAfterCurrentRegion = false;
		task.taskQueueTimer = PluginSettings.getTaskQueueTimer(world);

		synchronized (tasksLock) {
			if (tasks.containsKey(worldId)) {
				colorMessage(sender, YELLOW, worldName + " " + ENABLED_WARNING_MESSAGE);
				return false;
			}
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
			if (task.stateHasCenter && (task.centerBlockX != currentCenterBlockX || task.centerBlockZ != currentCenterBlockZ)) {
				ResetPreGenState.reset(plugin, worldName);
				task.chunkIterator.reset();
				task.totalChunksProcessed.reset();
				task.submittedChunks.set(0L);
				task.stopAfterCurrentRegion = false;
				applyCenter(task, currentCenter);
			} else if (!task.stateHasCenter) {
				task.centerBlockX = currentCenterBlockX;
				task.centerBlockZ = currentCenterBlockZ;
				task.stateHasCenter = true;
			}
		}
		if (loaded && task.stateHasCenter) {
			applyTargetBounds(task, Math.floorDiv(task.centerBlockX, 16), Math.floorDiv(task.centerBlockZ, 16));
		}

		initializeSchedulers(task);
		startCleanupScheduler(task);

		if (task.totalChunksProcessed.sum() >= radius) {
			colorMessage(sender, YELLOW, worldName + " " + RADIUS_EXCEEDED_MESSAGE);
			terminate(task);
			return false;
		}
		if (task.forceChunkSafety && IS_FOLIA) {
			colorMessage(sender, YELLOW, "chunk safety flag is only used on Paper; Folia will use its region scheduler path for " + worldName + ".");
		} else if (task.forceChunkSafety && IS_PAPER && !REQUIRES_CHUNK_SAFETY) {
			colorMessage(sender, GOLD, "chunk safety forced for " + worldName + "; generation will be more thorough but slower.");
		}

		startGeneration(task);
		print.start(task);
		return true;
	}

	private void startCleanupScheduler(PreGenerationTask task) {
		task.cleanupScheduler = new AsyncDelayedScheduler();
		task.cleanupScheduler.scheduleAtFixedRate(
				() -> {
					if (!task.world.getPlayers().isEmpty()) return;
					synchronized (task.playerChunkLock) {
						task.playerLoadedChunks.clear();
						task.playerChunkMap.clear();
						task.playerChunkRefCount.clear();
						task.pinnedNewChunks.clear();
					}
				},
				60_000,
				60_000,
				TimeUnit.MILLISECONDS,
				task.cleanupScheduler.isEnabledSupplier()
				);
	}

	/**
	 * Finds the center point for generation for a world.
	 * Uses settings center, world border center, or spawn as fallback.
	 */
	private Location resolveCenterLocation(World world) {
		PluginSettings.WorldSettings worldSettings = PluginSettings.getWorldSettings(world);
		String centerSetting = worldSettings.center();

		if (centerSetting == null) return defaultCenter(world);

		String trimmed = centerSetting.trim();
		if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("default")) return defaultCenter(world);
		if (trimmed.equals("~ ~")) return world.getSpawnLocation();

		int split = firstWhitespace(trimmed);
		if (split < 0) return defaultCenter(world);

		try {
			double x = Double.parseDouble(trimmed.substring(0, split));
			double z = Double.parseDouble(trimmed.substring(skipWhitespace(trimmed, split)));
			return new Location(world, x, world.getSpawnLocation().getY(), z);
		} catch (NumberFormatException e) {
			return defaultCenter(world);
		}
	}

	private static Location defaultCenter(World world) {
		Location centerLocation = world.getWorldBorder().getCenter();
		return centerLocation != null ? centerLocation : world.getSpawnLocation();
	}

	private static int firstWhitespace(String value) {
		for (int i = 0, len = value.length(); i < len; i++) {
			if (Character.isWhitespace(value.charAt(i))) return i;
		}
		return -1;
	}

	private static int skipWhitespace(String value, int index) {
		int i = index;
		int len = value.length();
		while (i < len && Character.isWhitespace(value.charAt(i))) i++;
		return i;
	}

	/**
	 * Applies the center position to the task and centers the iterator.
	 */
	private void applyCenter(PreGenerationTask task, Location centerLocation) {
		task.centerBlockX = centerLocation.getBlockX();
		task.centerBlockZ = centerLocation.getBlockZ();
		task.stateHasCenter = true;

		int centerChunkX = Math.floorDiv(task.centerBlockX, 16);
		int centerChunkZ = Math.floorDiv(task.centerBlockZ, 16);
		int centerRegionX = Math.floorDiv(centerChunkX, 32);
		int centerRegionZ = Math.floorDiv(centerChunkZ, 32);
		task.chunkIterator.setCenterRegion(centerRegionX, centerRegionZ);
		applyTargetBounds(task, centerChunkX, centerChunkZ);
	}

	private void applyTargetBounds(PreGenerationTask task, int centerChunkX, int centerChunkZ) {
		if (task.targetSideChunks <= 0L) {
			task.chunkIterator.clearChunkBounds();
			return;
		}

		long halfSide = task.targetSideChunks / 2L;
		long minChunkX = (long) centerChunkX - halfSide;
		long minChunkZ = (long) centerChunkZ - halfSide;
		long maxChunkX = minChunkX + task.targetSideChunks - 1L;
		long maxChunkZ = minChunkZ + task.targetSideChunks - 1L;
		task.chunkIterator.setChunkBounds(
				toChunkCoordinate(minChunkX),
				toChunkCoordinate(maxChunkX),
				toChunkCoordinate(minChunkZ),
				toChunkCoordinate(maxChunkZ)
				);
	}

	private static int toChunkCoordinate(long value) {
		if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Chunk coordinate is outside the supported integer range: " + value);
		}
		return (int) value;
	}

	/**
	 * Creates scheduler instances for a task.
	 */
	private void initializeSchedulers(PreGenerationTask task) {
		task.printScheduler = new AsyncDelayedScheduler();
		task.taskSubmitScheduler = new AsyncDelayedScheduler();
	}

	/**
	 * Stops pre-generation for a world when called by a command.
	 */
	public void disable(CommandSender sender, World world, boolean showMessages) {
		int worldId = WorldIdManager.getWorldId(world);
		String worldName = WorldRegistry.id(world);
		PreGenerationTask task;

		synchronized (tasksLock) {
			task = tasks.remove(worldId);
		}

		if (task == null || !task.enabled) {
			if (showMessages) {
				colorMessage(sender, YELLOW, worldName + " " + DISABLED_WARNING_MESSAGE);
			}
			return;
		}

		terminate(task);

	}

	/**
	 * Shuts down a pre-generation task and prints final stats.
	 */
	private void terminate(PreGenerationTask task) {
		if (!task.terminationStarted.compareAndSet(false, true)) {
			return;
		}
		if (!task.complete && task.totalChunksProcessed.sum() >= task.radius) {
			task.complete = true;
		}
		if (task.taskSubmitScheduler != null) {
			task.taskSubmitScheduler.setEnabled(false);
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

		synchronized (task.playerChunkLock) {
			task.playerLoadedChunks.clear();
			task.playerChunkMap.clear();
			task.playerChunkRefCount.clear();
			task.pinnedNewChunks.clear();
		}

		task.enabled = false;
		synchronized (tasksLock) {
			tasks.remove(task.worldId);
		}
	}

	/**
	 * Disables all schedulers for a task.
	 */
	private void shutdownSchedulers(PreGenerationTask task) {
		if (task.printScheduler != null) task.printScheduler.setEnabled(false);
		if (task.taskSubmitScheduler != null) task.taskSubmitScheduler.setEnabled(false);
		if (task.cleanupScheduler != null) task.cleanupScheduler.setEnabled(false);
	}

	/**
	 * Starts the main generation loop for a task.
	 * Uses different paths for Folia, Paper, and Bukkit fallback.
	 */
	private void startGeneration(PreGenerationTask task) {
		if (IS_FOLIA) {
			task.taskSubmitScheduler.scheduleAtFixedRate(
					() -> submitFoliaBatch(task),
					1,
					task.taskQueueTimer,
					TimeUnit.MILLISECONDS,
					task.taskSubmitScheduler.isEnabledSupplier()
					);
		} else if (IS_PAPER) {
			task.taskSubmitScheduler.scheduleAtFixedRate(
					() -> {
						if (!task.enabled) return;
						if (usesPaperChunkSafety(task)) {
							processPaperSafetyBatch(task);
							return;
						}
						submitPaperBatch(task);
					},
					0,
					task.taskQueueTimer,
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
					for (int i = 0, limit = task.tasks; i < limit; i++) {
						syncProcess(task);
					}
				}
			}.runTaskTimer(plugin, 0L, 1L);
		}
	}

	private void submitFoliaBatch(PreGenerationTask task) {
		if (!task.enabled) return;
		for (int i = 0, limit = task.parallelTasksMultiplier; i < limit; i++) {
			RegionChunkIterator.NextChunkResult next = nextChunkOrFinish(task);
			if (next == null) return;
			processChunkFolia(task, next.chunkX, next.chunkZ);
		}
	}

	private void submitPaperBatch(PreGenerationTask task) {
		if (!task.enabled) return;
		for (int i = 0, limit = task.parallelTasksMultiplier; i < limit; i++) {
			RegionChunkIterator.NextChunkResult next = nextChunkOrFinish(task);
			if (next == null) return;
			processChunkPaper(task, next.chunkX, next.chunkZ);
		}
	}

	private RegionChunkIterator.NextChunkResult nextChunkOrFinish(PreGenerationTask task) {
		if (!task.enabled) return null;
		if (task.submittedChunks.get() >= task.radius) {
			completeTaskIfReady(task);
			return null;
		}

		RegionChunkIterator.NextChunkResult next = task.chunkIterator.getNextChunkCoordinates();
		if (next == null) {
			completeTask(task);
			return null;
		}

		if (next.regionCompleted) {
			saveTaskState(task);
		}
		task.submittedChunks.incrementAndGet();
		return next;
	}

	private void completeTask(PreGenerationTask task) {
		if (!task.enabled) return;
		saveTaskState(task);
		if (task.taskSubmitScheduler != null) {
			task.taskSubmitScheduler.setEnabled(false);
		}
		task.complete = true;
		terminate(task);
	}

	/**
	 * Submits safety-mode chunks without blocking the scheduler.
	 */
	private void processPaperSafetyBatch(PreGenerationTask task) {
		if (!task.enabled) return;

		if (task.submittedChunks.get() >= task.radius) {
			completeSafetyTaskIfReady(task);
			return;
		}

		int available = maxSafetyInFlight(task) - task.activeSafetyTasks.get();
		int batchSize = Math.min(task.parallelTasksMultiplier, Math.max(0, available));
		if (batchSize <= 0) return;

		for (int i = 0; i < batchSize && task.enabled && task.submittedChunks.get() < task.radius; i++) {
			RegionChunkIterator.NextChunkResult next = nextChunkOrFinish(task);
			if (next == null) return;
			processChunkPaperWithSafety(task, next.chunkX, next.chunkZ);
		}

		completeSafetyTaskIfReady(task);
	}

	private int maxSafetyInFlight(PreGenerationTask task) {
		long limit = (long) task.parallelTasksMultiplier * SAFETY_IN_FLIGHT_WINDOW;
		return (int) Math.min(Integer.MAX_VALUE, Math.max(task.parallelTasksMultiplier, limit));
	}

	/**
	 * Loads and unloads a single chunk on Folia.
	 */
	private void processChunkFolia(PreGenerationTask task, int chunkX, int chunkZ) {
		if (!task.enabled) return;
		Bukkit.getRegionScheduler().execute(plugin, task.world, chunkX, chunkZ, () -> {
			if (!task.enabled) return;
			task.world.getChunkAtAsync(chunkX, chunkZ, true).thenAccept(chunk -> {
				if (!task.enabled) return;
				Bukkit.getRegionScheduler().execute(plugin, task.world, chunkX, chunkZ, () -> {
					if (!task.enabled) return;
					if (chunk != null && chunk.isLoaded()) {
						task.world.unloadChunkRequest(chunkX, chunkZ);
					}
					markChunkProcessed(task);
				});
			}).exceptionally(ex -> {
				exceptionMsg("Async chunk load exception in processChunkFolia: " + ex.getMessage());
				ex.printStackTrace();
				markChunkProcessed(task);
				return null;
			});
		});
	}

	/**
	 * Loads a chunk on Paper and queues it for unload again.
	 */
	private void processChunkPaper(PreGenerationTask task, int chunkX, int chunkZ) {
		if (!task.enabled) return;
		if (usesPaperChunkSafety(task)) {
			processChunkPaperWithSafety(task, chunkX, chunkZ);
			return;
		}
		getChunkAsync(task, chunkX, chunkZ, true);
		markChunkProcessed(task);
	}

	/**
	 * Runs safety-mode chunk generation and counts progress only after the safety future completes.
	 */
	private CompletableFuture<Void> processChunkPaperWithSafety(PreGenerationTask task, int chunkX, int chunkZ) {
		task.activeSafetyTasks.incrementAndGet();
		return getChunkAsyncWithSafety(task, chunkX, chunkZ, true).whenComplete((_, ex) -> {
			if (ex != null) {
				Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
				exceptionMsg("Chunk safety generation failed for " + task.worldName + " at " + chunkX + "," + chunkZ + ": " + cause.getMessage());
			}
			markChunkProcessed(task);
			finishSafetyChunk(task);
		});
	}

	private boolean usesPaperChunkSafety(PreGenerationTask task) {
		return REQUIRES_CHUNK_SAFETY || task.forceChunkSafety;
	}

	private void finishSafetyChunk(PreGenerationTask task) {
		task.activeSafetyTasks.decrementAndGet();
		completeSafetyTaskIfReady(task);
	}

	private void completeSafetyTaskIfReady(PreGenerationTask task) {
		completeTaskIfReady(task);
	}

	private void completeTaskIfReady(PreGenerationTask task) {
		if (!task.enabled || task.submittedChunks.get() < task.radius || task.totalChunksProcessed.sum() < task.radius || task.activeSafetyTasks.get() > 0) {
			return;
		}
		completeTask(task);
	}

	/**
	 * Processes chunks on the main thread for Bukkit fallback.
	 */
	private void syncProcess(PreGenerationTask task) {
		try {
			if (!task.enabled) return;
			RegionChunkIterator.NextChunkResult next = nextChunkOrFinish(task);
			if (next == null) return;
			handleChunkBukkit(task, next.chunkX, next.chunkZ);
			completionCheck(task);
		} catch (Exception e) {
			exceptionMsg("Exception in syncProcess: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Loads and unloads a chunk on the main thread.
	 */
	private void handleChunkBukkit(PreGenerationTask task, int chunkX, int chunkZ) {
		try {
			if (!task.enabled) return;
			Chunk chunk = task.world.getChunkAt(chunkX, chunkZ);
			chunk.load(true);
			if (chunk.isLoaded()) {
				task.world.unloadChunk(chunkX, chunkZ, true);
			}
			task.totalChunksProcessed.increment();
			task.chunksThisCycle.increment();
		} catch (Exception e) {
			exceptionMsg("Exception in handleChunkBukkit: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Loads a chunk on Paper through the urgent async safety path.
	 */
	private CompletableFuture<Void> getChunkAsyncWithSafety(PreGenerationTask task, int chunkX, int chunkZ, boolean gen) {
		if (!task.enabled) return CompletableFuture.completedFuture(null);
		return ChunkSafety.generateAndUnload(task.world, chunkX, chunkZ, gen);
	}

	/**
	 * Loads a chunk on Paper using the async API directly.
	 */
	private void getChunkAsync(PreGenerationTask task, int chunkX, int chunkZ, boolean gen) {
		if (!task.enabled) return;
		try {
			task.world.getChunkAtAsync(chunkX, chunkZ, gen, chunk -> {
				if (chunk != null && chunk.isLoaded()) {
					task.world.unloadChunkRequest(chunkX, chunkZ);
				}
			});
		} catch (Exception e) {
			exceptionMsg("Exception in getChunkAsync: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void markChunkProcessed(PreGenerationTask task) {
		if (!task.enabled) return;
		task.totalChunksProcessed.increment();
		task.chunksThisCycle.increment();
		completionCheck(task);
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
		completeTaskIfReady(task);
	}

	/**
	 * Handles chunk load events while generation is active.
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	private void onChunkLoad(ChunkLoadEvent event) {
		try {
			PreGenerationTask task;
			int worldId = WorldIdManager.getWorldId(event.getWorld());
			synchronized (tasksLock) {
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

			int chunkX = chunk.getX();
			int chunkZ = chunk.getZ();
			long key = MortonCode.encode(chunkX, chunkZ);

			synchronized (task.playerChunkLock) {
				if (task.playerLoadedChunks.contains(key) || task.pinnedNewChunks.contains(key)) {
					return;
				}

				if (!event.isNewChunk()) {
					task.world.unloadChunkRequest(chunkX, chunkZ);
				} else {
					task.pinnedNewChunks.add(key);
				}
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
		return IS_FOLIA
				|| classExists("io.papermc.paper.configuration.GlobalConfiguration")
				|| classExists("com.destroystokyo.paper.PaperConfig");
	}

	/**
	 * Checks if the server is running on Folia.
	 */
	private static boolean detectFolia() {
		return classExists("io.papermc.paper.threadedregions.RegionizedServer");
	}

	private static boolean classExists(String className) {
		try {
			Class.forName(className);
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}
}
