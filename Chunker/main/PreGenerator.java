package main;

import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * PreGenerator is responsible for pre-generating chunks asynchronously to improve server performance.
 */
public class PreGenerator implements Listener {

	private final JavaPlugin plugin;
	private final ConcurrentHashMap<Integer, PreGenerationTask> tasks = new ConcurrentHashMap<>();

	private static final String
	ENABLED_WARNING_MESSAGE = "pre-generator is already enabled.",
	DISABLED_WARNING_MESSAGE = "pre-generator is already disabled.",
	RADIUS_EXCEEDED_MESSAGE = "radius reached. To process more chunks, please increase the radius.";

	private static final boolean IS_PAPER = detectPaper();
	private static final int TICK_MILLISECOND = 50;
	private long task_queue_timer;

	public PreGenerator(JavaPlugin plugin) {
		this.plugin = plugin;
		cC.logSB("Available Processors: " + PluginSettings.THREADS());
	}

	/**
	 * Enables the pre-generator for a specific world.
	 *
	 * @param parallelTasksMultiplier Multiplier for parallel tasks.
	 * @param timeUnit               Unit of time for printing.
	 * @param timeValue              Value of time for printing.
	 * @param printTime              Interval for printing progress.
	 * @param world                  The world to pre-generate.
	 * @param radius                 The radius up to which to pre-generate chunks.
	 */
	public synchronized void enable(int parallelTasksMultiplier, char timeUnit, int timeValue, int printTime, World world, long radius) {
		int worldId = WorldIdManager.getWorldId(world);
		synchronized (tasks) {
			if (tasks.containsKey(worldId)) {
				cC.logS(cC.YELLOW, world.getName() + " " + ENABLED_WARNING_MESSAGE);
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

		loadTaskState(task);
		if (task.totalChunksProcessed.sum() >= radius) {
			cC.logS(cC.YELLOW, world.getName() + " " + RADIUS_EXCEEDED_MESSAGE);
			task.enabled = false;
			return;
		}
		initializeSchedulers(task);
		startGeneration(task);
		startPrintInfoTimer(task);
	}

	/**
	 * Initializes the schedulers for printing and task execution.
	 *
	 * @param task The pre-generation task to initialize schedulers for.
	 */
	private void initializeSchedulers(PreGenerationTask task) {
		task.printScheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("PreGen-PrintScheduler", true));
		task.taskSubmit = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("PreGen-TaskSubmit", false));
		task.taskDo = Executors.newFixedThreadPool(PluginSettings.THREADS(), new NamedThreadFactory("PreGen-TaskDo", false));
	}

	/**
	 * Disables the pre-generator for a specific world.
	 *
	 * @param world The world to disable pre-generation for.
	 */
	public synchronized void disable(World world) {
		int worldId = WorldIdManager.getWorldId(world);
		PreGenerationTask task;
		synchronized (tasks) {
			task = tasks.get(worldId);
			if (task == null || !task.enabled) {
				cC.logS(cC.YELLOW, world.getName() + " " + DISABLED_WARNING_MESSAGE);
				return;
			}
			tasks.remove(worldId);
		}
		terminate(task);
		HandlerList.unregisterAll(this);
	}

	/**
	 * Terminates the pre-generation task, shutting down schedulers and saving state.
	 *
	 * @param task The task to terminate.
	 */
	private synchronized void terminate(PreGenerationTask task) {
		task.timerEnd = System.currentTimeMillis();
		saveTaskState(task);
		printInfo(task);
		stopPrintInfoTimer(task);
		shutdownSchedulers(task);
		task.enabled = false;
	}

	/**
	 * Shuts down both schedulers associated with the task.
	 *
	 * @param task The task whose schedulers are to be shut down.
	 */
	private void shutdownSchedulers(PreGenerationTask task) {
		if (!task.enabled) {
			return;
		}
		if (task.printScheduler != null && !task.printScheduler.isShutdown()) {
			task.printScheduler.shutdown();
		}
		if (task.taskSubmit != null && !task.taskSubmit.isShutdown()) {
			task.taskSubmit.shutdown();
		}
		if (task.taskDo != null && !task.taskDo.isShutdown()) {
			task.taskDo.shutdown();
		}
	}

	/**
	 * Starts the timer to periodically print information about the pre-generation progress.
	 *
	 * @param task The task to start the print timer for.
	 */
	private void startPrintInfoTimer(PreGenerationTask task) {
		task.printScheduler.scheduleAtFixedRate(() -> {
			if (!task.enabled) {
				return;
			}
			printInfo(task);
		}, 0, task.printTime * TICK_MILLISECOND, TimeUnit.MILLISECONDS);
	}

	/**
	 * Stops the print info timer and logs the total elapsed time.
	 *
	 * @param task The task to stop the print timer for.
	 */
	private void stopPrintInfoTimer(PreGenerationTask task) {
		if (!task.enabled) {
			return;
		}
		if (task.printScheduler != null && !task.printScheduler.isShutdown()) {
			task.printScheduler.shutdown();
		}
		long elapsedTime = (task.timerEnd - task.timerStart) / 1000;
		cC.logSB("Total time: " + formatElapsedTime(elapsedTime));
		task.timerStart = 0;
		task.timerEnd = 0;
	}

	/**
	 * Formats elapsed time into a human-readable string.
	 *
	 * @param seconds The total elapsed time in seconds.
	 * @return A formatted string representing the elapsed time.
	 */
	private String formatElapsedTime(long seconds) {
		long hours = seconds / 3600;
		long minutes = (seconds % 3600) / 60;
		long remainingSeconds = seconds % 60;
		StringBuilder formattedTime = new StringBuilder();
		if (hours > 0) {
			formattedTime.append(hours).append(" Hour").append(hours > 1 ? "s" : "").append(" ");
		}
		if (minutes > 0) {
			formattedTime.append(minutes).append(" Minute").append(minutes > 1 ? "s" : "").append(" ");
		}
		if (remainingSeconds > 0 || formattedTime.length() == 0) {
			formattedTime.append(remainingSeconds).append(" Second").append(remainingSeconds != 1 ? "s" : "").append(" ");
		}
		return formattedTime.toString().trim();
	}

	/**
	 * Starts the chunk generation process based on whether the server is running PaperMC.
	 *
	 * @param task The task to start generating chunks for.
	 */
	private void startGeneration(PreGenerationTask task) {
		task.timerStart = System.currentTimeMillis();
		if (IS_PAPER) {
			for (int i = 0; i < task.parallelTasksMultiplier; i++) {
				asyncProcess(task);
			}
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
	 * Asynchronously processes chunk loading tasks using batch processing.
	 *
	 * @param task The task to process chunks for.
	 */
	private void asyncProcess(PreGenerationTask task) {
		if (!task.enabled) {
			return;
		}

		task.taskSubmit.scheduleAtFixedRate(() -> {
			if (!task.enabled || task.totalChunksProcessed.sum() >= task.radius) {
				saveTaskState(task);
				return;
			}
			long nextChunkKey = task.chunkIterator.getNextChunkCoordinates();
			if (nextChunkKey == -1) {
				saveTaskState(task);
				return;
			}
			task.taskDo.execute(() -> processChunks(task, nextChunkKey));
		}, 0, task_queue_timer, TimeUnit.MILLISECONDS);
	}

	/**
	 * Processes a batch of chunks asynchronously.
	 *
	 * @param task        The task to process chunks for.
	 * @param initialChunkKey The initial chunk key to start processing from.
	 */
	private void processChunks(PreGenerationTask task, long initialChunkKey) {
		Queue<Long> chunkKeys = new ConcurrentLinkedQueue<>();
		chunkKeys.add(initialChunkKey);
		while (!chunkKeys.isEmpty() && task.enabled) {
			Long currentChunkKey = chunkKeys.poll();
			if (currentChunkKey == null) {
				break;
			}
			ChunkData chunkData = new ChunkData(currentChunkKey);
			processChunk(task, chunkData);
		}
		completionCheck(task);
	}

	/**
	 * Synchronously processes chunk loading tasks.
	 *
	 * @param task The task to process chunks for.
	 */
	private void syncProcess(PreGenerationTask task) {
		if (!task.enabled) {
			return;
		}
		if (task.totalChunksProcessed.sum() >= task.radius) {
			return;
		}
		long chunkKey = task.chunkIterator.getNextChunkCoordinates();
		if (chunkKey == -1) {
			saveTaskState(task);
			return;
		}
		ChunkData cData = new ChunkData(chunkKey);
		handleChunk(task, cData);
		completionCheck(task);
	}

	/**
	 * Processes a single chunk by loading it and managing its state.
	 *
	 * @param task   The task handling the chunk.
	 * @param chunk  The ChunkData instance representing the chunk.
	 */
	private void processChunk(PreGenerationTask task, ChunkData cData) {
		if (!task.enabled) {
			return;
		}
		getChunkAsync(task, cData, true);
		task.totalChunksProcessed.increment();
		task.chunksThisCycle++;
	}

	/**
	 * Asynchronously retrieves a chunk using the world's async method.
	 *
	 * @param task  The task to retrieve the chunk for.
	 * @param chunk The ChunkData instance representing the chunk.
	 * @param gen   Whether to generate the chunk if it doesn't exist.
	 */
	private void getChunkAsync(PreGenerationTask task, ChunkData cData, boolean gen) {
		if (!task.enabled) {
			return;
		}
		task.world.getChunkAtAsync(cData.getX(), cData.getZ(), gen, true);
	}

	/**
	 * Handles chunk loading and unloading synchronously.
	 *
	 * @param task  The task handling the chunk.
	 * @param chunk The ChunkData instance representing the chunk.
	 */
	private void handleChunk(PreGenerationTask task, ChunkData cData) {
		if (!task.enabled) {
			return;
		}
		Chunk bukkitChunk = task.world.getChunkAt(cData.getX(), cData.getZ());
		bukkitChunk.load(true);
		while (bukkitChunk.getLoadLevel() == Chunk.LoadLevel.ENTITY_TICKING) {
			boolean unloaded = task.world.unloadChunk(bukkitChunk.getX(), bukkitChunk.getZ(), true);
			if (!unloaded) {
				break;
			}
		}
		task.totalChunksProcessed.increment();
		task.chunksThisCycle++;
	}

	/**
	 * Checks if the chunk processing has completed based on the radius.
	 *
	 * @param task The task to check completion for.
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
	 *
	 * @param event The chunk load event.
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	private void onChunkLoad(ChunkLoadEvent event) {
		int worldId = WorldIdManager.getWorldId(event.getWorld());
		PreGenerationTask task;
		synchronized (tasks) {
			task = tasks.get(worldId);
		}
		if (task == null || !task.enabled) {
			return;
		}
		handleChunkLoad(task, event);
	}

	/**
	 * Processes a chunk load event, unloading chunks that shouldn't remain loaded.
	 *
	 * @param task  The task handling the chunk.
	 * @param event The chunk load event.
	 */
	private void handleChunkLoad(PreGenerationTask task, ChunkLoadEvent event) {
		if (!task.enabled) {
			return;
		}
		Chunk chunk = event.getChunk();
		if (chunk == null) {
			return;
		}
		long chunkKey = MortonCode.encode(chunk.getX(), chunk.getZ());
		if (task.playerLoadedChunks.contains(chunkKey)) {
			return;
		}
		if (!event.isNewChunk()) {
			task.world.unloadChunk(chunk.getX(), chunk.getZ(), true);
		} else {
			task.playerLoadedChunks.add(chunkKey);
		}
		if (IS_PAPER) {
			asyncUnloadCheck(task, chunk, chunkKey);
		}
	}

	/**
	 * Asynchronously checks and unloads chunks to prevent them from staying loaded unnecessarily.
	 *
	 * @param task     The task handling the chunk.
	 * @param chunk    The chunk to check.
	 * @param chunkKey The encoded key of the chunk.
	 */
	private void asyncUnloadCheck(PreGenerationTask task, Chunk chunk, long chunkKey) {
		if (!task.enabled) {
			return;
		}
		task.taskDo.execute(() -> {
			try {
				while (task.enabled) {
					if (chunk.getLoadLevel() != Chunk.LoadLevel.TICKING || task.playerLoadedChunks.contains(chunkKey) || chunk.unload(true)) {
						break;
					}
					try {
						Thread.sleep(TICK_MILLISECOND);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						break;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	/**
	 * Handles player movement events to manage loaded chunks.
	 *
	 * @param event The player move event.
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	private void onPlayerMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();
		Chunk fromChunk = event.getFrom().getChunk();
		Chunk toChunk = event.getTo().getChunk();
		if (fromChunk.equals(toChunk)) {
			return;
		}
		long fromChunkKey = MortonCode.encode(fromChunk.getX(), fromChunk.getZ());
		long toChunkKey = MortonCode.encode(toChunk.getX(), toChunk.getZ());
		UUID playerId = player.getUniqueId();
		synchronized (tasks) {
			for (PreGenerationTask task : tasks.values()) {
				task.playerChunkMap.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());
				Set<Long> playerChunks = task.playerChunkMap.get(playerId);
				boolean removed = playerChunks.remove(fromChunkKey);
				if (removed) {
					boolean stillLoaded = task.playerChunkMap.values().stream()
							.anyMatch(set -> set.contains(fromChunkKey));
					if (!stillLoaded) {
						task.playerLoadedChunks.remove(fromChunkKey);
					}
				}
				boolean added = playerChunks.add(toChunkKey);
				if (added) {
					task.playerLoadedChunks.add(toChunkKey);
				}
			}
		}
	}

	/**
	 * Handles player disconnect events to clean up loaded chunks.
	 *
	 * @param event The player quit event.
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	private void onPlayerQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		UUID playerId = player.getUniqueId();

		synchronized (tasks) {
			for (PreGenerationTask task : tasks.values()) {
				Set<Long> playerChunks = task.playerChunkMap.remove(playerId);
				if (playerChunks != null) {
					for (Long chunkKey : playerChunks) {
						boolean stillLoaded = task.playerChunkMap.values().stream()
								.anyMatch(set -> set.contains(chunkKey));
						if (!stillLoaded) {
							task.playerLoadedChunks.remove(chunkKey);
						}
					}
				}
			}
		}
	}

	/**
	 * Handles player world change events to manage loaded chunks.
	 *
	 * @param event The player changed world event.
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	private void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
		Player player = event.getPlayer();
		World newWorld = player.getWorld();
		Chunk toChunk = player.getLocation().getChunk();
		UUID playerId = player.getUniqueId();
		synchronized (tasks) {
			for (PreGenerationTask task : tasks.values()) {
				Set<Long> playerChunks = task.playerChunkMap.remove(playerId);
				if (playerChunks != null) {
					for (Long chunkKey : playerChunks) {
						boolean stillLoaded = task.playerChunkMap.values().stream()
								.anyMatch(set -> set.contains(chunkKey));
						if (!stillLoaded) {
							task.playerLoadedChunks.remove(chunkKey);
						}
					}
				}
				if (task.world.equals(newWorld)) {
					task.playerChunkMap.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());
					Set<Long> newPlayerChunks = task.playerChunkMap.get(playerId);
					long toChunkKey = MortonCode.encode(toChunk.getX(), toChunk.getZ());
					if (newPlayerChunks.add(toChunkKey)) {
						task.playerLoadedChunks.add(toChunkKey);
					}
				}
			}
		}
	}

	/**
	 * Prints information about the current state of chunk pre-generation.
	 *
	 * @param task The task to print information for.
	 */
	private void printInfo(PreGenerationTask task) {
		task.localChunksThisCycle = task.chunksThisCycle;
		task.chunksPerSec = task.localChunksThisCycle / task.timeValue;
		logProgress(task);
		resetCycleCounts(task);
	}

	/**
	 * Logs the progress of chunk pre-generation to the console.
	 *
	 * @param task The task whose progress is to be logged.
	 */
	private void logProgress(PreGenerationTask task) {
		int radiusWidth = String.valueOf(task.radius).length();
		String worldStr = cC.logO(cC.GOLD, cC.padWorldName(task.world.getName(), 13));
		String processed = cC.fA(cC.GOLD, task.localChunksThisCycle, 4);
		String perSecond = cC.fA(cC.GOLD, task.chunksPerSec, 4);
		String completion = cC.fA(cC.GOLD, task.totalChunksProcessed.sum(), radiusWidth);
		String radiusStr = cC.fA(cC.GOLD, task.radius, radiusWidth);
		String logFormat = "%s Processed: %s Chunks/%s: %s Completed: %s out of %s Chunks";

		if (task.enabled && !task.complete) {
			cC.logSB(String.format(logFormat, worldStr, processed, task.timeUnit, perSecond, completion, radiusStr));
		} else if (task.complete && task.localChunksThisCycle != 0 && task.chunksPerSec != 0) {
			cC.logSB(String.format(logFormat, worldStr, processed, task.timeUnit, perSecond, radiusStr, radiusStr));
		}
	}

	/**
	 * Resets cycle-specific counters after logging progress.
	 *
	 * @param task The task whose counters are to be reset.
	 */
	private void resetCycleCounts(PreGenerationTask task) {
		task.chunksPerSec = 0;
		task.localChunksThisCycle = 0;
		task.chunksThisCycle = 0;
	}

	/**
	 * Saves the state of the task to a plain-text file with underscore-separated values.
	 *
	 * @param task The task whose state is to be saved.
	 */
	private void saveTaskState(PreGenerationTask task) {
		synchronized (tasks) {
			if (!task.enabled) {
				return;
			}
			File dataFolder = plugin.getDataFolder();
			if (!dataFolder.exists()) {
				if (!dataFolder.mkdirs()) {
					cC.logS(cC.RED, "Failed to create data folder for " + task.world.getName());
					return;
				}
			}
			File dataFile = new File(dataFolder, task.world.getName() + "_pregenerator.txt");
			String data;
			data = String.format("%d_%d_%d_%d_%d_%d_%d",
					task.chunkIterator.getCurrentRegionX(),
					task.chunkIterator.getCurrentRegionZ(),
					task.chunkIterator.getDirectionIndex(),
					task.chunkIterator.getStepsRemaining(),
					task.chunkIterator.getStepsToChange(),
					task.chunkIterator.getChunkIndex(),
					task.totalChunksProcessed.sum());
			try {
				Files.writeString(dataFile.toPath(), data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			} catch (IOException e) {
				e.printStackTrace();
				cC.logS(cC.RED, "Failed to save processed chunks for " + task.world.getName() + ": " + e.getMessage());
			}
		}
	}

	/**
	 * Loads the state of the task from a plain-text file with underscore-separated values.
	 *
	 * @param task The task whose state is to be loaded.
	 */
	private void loadTaskState(PreGenerationTask task) {
		synchronized (tasks) {
			File dataFile = new File(plugin.getDataFolder(), task.world.getName() + "_pregenerator.txt");

			if (dataFile.exists()) {
				try {
					List<String> lines = Files.readAllLines(dataFile.toPath());
					if (!lines.isEmpty()) {
						String[] parts = lines.get(0).split("_");
						if (parts.length == 7) {
							int x = Integer.parseInt(parts[0]);
							int z = Integer.parseInt(parts[1]);
							int directionIndex = Integer.parseInt(parts[2]);
							int stepsRemaining = Integer.parseInt(parts[3]);
							int stepsToChange = Integer.parseInt(parts[4]);
							int chunkIndex = Integer.parseInt(parts[5]);
							long processedChunks = Long.parseLong(parts[6]);
							task.chunkIterator.setState(x, z, directionIndex, stepsRemaining, stepsToChange, chunkIndex);
							task.totalChunksProcessed.add(processedChunks);
							cC.logSB("Successfully loaded " + processedChunks + " processed chunks for " + task.world.getName());
						} else {
							throw new IOException("Invalid task state format. Expected 7 parts but found " + parts.length);
						}
					} else {
						throw new IOException("Empty task state file.");
					}
				} catch (IOException | NumberFormatException e) {
					e.printStackTrace();
					cC.logS(cC.RED, "Failed to load processed chunks for " + task.world.getName() + ": " + e.getMessage());
					task.chunkIterator.reset();
					task.totalChunksProcessed.reset();
				}
			} else {
				task.chunkIterator.reset();
				task.totalChunksProcessed.reset();
				cC.logSB("No pre-generator data found for " + task.world.getName() + ". Starting fresh.");
			}
		}
	}

	/**
	 * Detects if the server is running PaperMC.
	 *
	 * @return true if PaperMC is detected, false otherwise.
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