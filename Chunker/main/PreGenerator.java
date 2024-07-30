package main;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PreGenerator implements Listener {

	private boolean enabled = false, complete = false, lastPrint = false;
	private final JavaPlugin plugin;
	private String currentWorldName;
	private World world;
	private boolean firstPrint;

	private ScheduledExecutorService scheduler;
	private ScheduledExecutorService taskScheduler;
	private static final int TICK_MILLISECOND = 50;
	private static final int THREADS = Runtime.getRuntime().availableProcessors();

	private int parallelTasksMultiplier, timeValue, printTime, chunksPerSec, tasks;
	private char timeUnit;
	private long radius;
	private int x, z, dx, dz, currentX, currentZ;
	private long totalChunksProcessed = 0;
	private int chunksThisCycle = 0, localChunksThisCycle = 0;

	private static final String ENABLED_WARNING_MESSAGE = "Pre-Generator is already enabled.";
	private static final String ENABLED_MESSAGE = "Pre-generation has been enabled.";
	private static final String COMPLETE = "Pre-generation Complete.";
	private static final String DISABLED_WARNING_MESSAGE = "Pre-Generator is already disabled.";
	private static final String DISABLED_MESSAGE = "Pre-generation disabled.";
	private static final String RADIUS_EXCEEDED_MESSAGE = "To process more chunks please increase the radius.";

	private final Set<String> scheduledChunks = ConcurrentHashMap.newKeySet();
	private final Set<String> playerLoadedChunks = ConcurrentHashMap.newKeySet();

	private long TimerStart = 0;
	private long TimerEnd = 0;

	private static final boolean IS_PAPER = detectPaper();
	private static final int serverMillisecond = (int) (THREADS * 4.834);

	public PreGenerator(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public synchronized void enable(int parallelTasksMultiplier, char timeUnit, int timeValue, int printTime, World world, long radius) {
		if (enabled) {
			cC.logS(cC.YELLOW, ENABLED_WARNING_MESSAGE);
			return;
		}
		if (this.world != null && !this.world.equals(world)) {
			totalChunksProcessed = 0;
		}
		this.world = world;
		this.parallelTasksMultiplier = parallelTasksMultiplier;
		this.timeUnit = timeUnit;
		this.printTime = printTime;
		this.timeValue = timeValue;
		this.radius = radius;
		enabled = true;
		firstPrint = true;
		complete = false;
		currentWorldName = world.getName();
		loadProcessedChunks();
		if (totalChunksProcessed >= radius) {
			cC.logS(cC.YELLOW, RADIUS_EXCEEDED_MESSAGE);
			enabled = false;
			return;
		}
		cC.logS(cC.GREEN, ENABLED_MESSAGE);
		initializeSchedulers();
		startGeneration();
		startPrintInfoTimer();
	}

	public synchronized void disable() {
		if (!enabled) {
			cC.logS(cC.YELLOW, DISABLED_WARNING_MESSAGE);
			return;
		}
		terminate();
		cC.logS(cC.RED, DISABLED_MESSAGE);
	}

	private void initializeSchedulers() {
		scheduler = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
		taskScheduler = Executors.newScheduledThreadPool(parallelTasksMultiplier, Thread.ofVirtual().factory());
	}

	private synchronized void terminate() {
		TimerEnd = System.currentTimeMillis();
		enabled = false;
		saveProcessedChunks();
		printInfo();
		stopPrintInfoTimer();
		shutdownSchedulers();
	}

	private void shutdownSchedulers() {
		if (scheduler != null && !scheduler.isShutdown()) {
			scheduler.shutdownNow();
		}
		if (taskScheduler != null && !taskScheduler.isShutdown()) {
			taskScheduler.shutdownNow();
		}
	}

	private void startPrintInfoTimer() {
		if (firstPrint) {
			firstPrint = false;
			cC.logSB("Available Processors: " + THREADS);
		}
		scheduler.scheduleAtFixedRate(this::printInfo, 0, printTime * TICK_MILLISECOND, TimeUnit.MILLISECONDS);
	}

	private void stopPrintInfoTimer() {
		scheduler.shutdownNow();
		long elapsedTime = (TimerEnd - TimerStart) / 1000;
		cC.logSB("Total time: " + cC.GOLD + formatElapsedTime(elapsedTime) + cC.RESET);
		TimerStart = 0;
		TimerEnd = 0;
	}

	private String formatElapsedTime(long seconds) {
		long hours = seconds / 3600, minutes = (seconds % 3600) / 60, remainingSeconds = seconds % 60;
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

	private void startGeneration() {
		TimerStart = System.currentTimeMillis();
		if (IS_PAPER) {
			for (int i = 0; i < parallelTasksMultiplier; i++) {
				asyncProcess();
			}
		} else {
			new BukkitRunnable() {
				@Override
				public void run() {
					tasks = Math.max(1, (int)(parallelTasksMultiplier / 2));
					for (int i = 0; i < tasks; i++) {
						syncProcess();
					}
				}
			}.runTaskTimer(plugin, 0L, 0L);
		}
	}

	private void asyncProcess() {
		if (!enabled) {
			return;
		}
		taskScheduler.scheduleAtFixedRate(() -> {
			if (!enabled || totalChunksProcessed >= radius) {
				return;
			}
			synchronized (this) {
				currentX = x;
				currentZ = z;
				updateCoordinates();
			}
			getChunkAsync(currentX, currentZ, true);

		}, 0, serverMillisecond, TimeUnit.MILLISECONDS);
	}

	private void syncProcess() {
		if (!enabled) {
			return;
		}
		if (totalChunksProcessed >= radius) {
			return;
		}
		synchronized (this) {
			currentX = x;
			currentZ = z;
			updateCoordinates();
		}
		World currentWorld = world;
		Chunk chunk = currentWorld.getChunkAt(currentX, currentZ);
		currentWorld.loadChunk(currentX, currentZ, true);
		syncUnload(chunk, currentWorld);
		totalChunksProcessed++;
		completionCheck();
		saveProcessedChunks();
	}

	private void getChunkAsync(int x, int z, boolean gen) {
		taskScheduler.execute(() -> {
			world.getChunkAtAsync(x, z, gen);
			saveProcessedChunks();
			synchronized (this) {
				totalChunksProcessed++;
				completionCheck();
			}
		});
	}

	private void updateCoordinates() {
		if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
			int temp = dx;
			dx = -dz;
			dz = temp;
		}
		x += dx;
		z += dz;
	}

	private void syncUnload(Chunk chunk, World currentWorld) {
		while (chunk.getLoadLevel() == Chunk.LoadLevel.TICKING) {
			boolean unloaded = currentWorld.unloadChunk(currentX, currentZ, true);
			if (!unloaded) {
				break;
			}
		}
	}

	private void completionCheck() {
		if (totalChunksProcessed >= radius) {
			if (!complete) {
				complete = true;
				lastPrint = true;
				terminate();
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	private void onChunkLoad(ChunkLoadEvent event) {
		if (!enabled) {
			return;
		}
		handleChunkLoad(event);
	}

	private void handleChunkLoad(ChunkLoadEvent event) {
		Chunk chunk = event.getChunk();
		World chunkWorld = chunk.getWorld();
		String chunkId = getChunkId(chunk);
		if (chunk == null || playerLoadedChunks.contains(chunkId)) {
			return;
		}
		chunkId = getChunkId(chunk);
		if (!event.isNewChunk() && !playerLoadedChunks.contains(chunkId)) {
			chunkWorld.unloadChunk(x, z, true);
		} else {
			playerLoadedChunks.add(chunkId);
		}
		if (IS_PAPER) {
			chunksThisCycle++;
			asyncUnload(chunk, chunkId);
		} else {
			chunksThisCycle++;
		}
	}

	private String getChunkId(Chunk chunk) {
		return chunk.getWorld().getName() + "_" + chunk.getX() + "_" + chunk.getZ();
	}

	private void asyncUnload(Chunk chunk, String chunkId) {
		if (scheduledChunks.add(chunkId)) {
			taskScheduler.execute(() -> {
				try {
					while (chunk.getLoadLevel() == Chunk.LoadLevel.TICKING && !playerLoadedChunks.contains(chunkId)) {
						boolean unloaded = chunk.unload(true);
						if (!unloaded) {
							break;
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					scheduledChunks.remove(chunkId);
				}
			});
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	private void onPlayerMove(PlayerMoveEvent event) {
		Chunk chunk = event.getTo().getChunk();
		if (chunk == null) {
			return;
		}
		playerLoadedChunks.add(getChunkId(chunk));
	}

	private void printInfo() {
		localChunksThisCycle = chunksThisCycle;
		chunksPerSec = localChunksThisCycle / timeValue;
		logProgress();
		resetCycleCounts();
	}

	private void logProgress() {
		String processedChunks = cC.logO(cC.GOLD, localChunksThisCycle);
		String chunksPerSecStr = cC.logO(cC.GOLD, chunksPerSec);
		String worldStr = cC.logO(cC.GOLD, currentWorldName);
		String completionStr = cC.logO(cC.GOLD, totalChunksProcessed);
		String radiusStr = cC.logO(cC.GOLD, radius);

		if (enabled && !complete) {
			cC.logSB(String.format("[%s] Processed: %s Chunks/%s: %s Completed: %s out of %s Chunks", worldStr, processedChunks, timeUnit, chunksPerSecStr, completionStr, radiusStr));
		} else if (!enabled && complete && lastPrint) {
			cC.logSB(String.format("[%s] Processed: %s Chunks/%s: %s Completed: %s out of %s Chunks", worldStr, processedChunks, timeUnit, chunksPerSecStr, radiusStr, radiusStr));
			cC.logS(cC.GREEN, COMPLETE);
			lastPrint = false;
		}
	}

	private void resetCycleCounts() {
		chunksPerSec = 0;
		localChunksThisCycle = 0;
		chunksThisCycle = 0;
	}

	private void saveProcessedChunks() {
		File dataFolder = plugin.getDataFolder();
		if (!dataFolder.exists()) {
			dataFolder.mkdirs();
		}
		File dataFile = new File(dataFolder, currentWorldName + "_pregenerator.txt");
		String data;
		synchronized (this) {
			data = String.format("%d_%d_%d_%d%n%d", x, z, dx, dz, totalChunksProcessed);
		}
		try {
			Files.writeString(dataFile.toPath(), data);
		} catch (IOException e) {
			e.printStackTrace();
			cC.logS(cC.RED, "Failed to save processed chunks for " + currentWorldName);
		}
	}

	private void loadProcessedChunks() {
		File dataFile = new File(plugin.getDataFolder(), currentWorldName + "_pregenerator.txt");
		if (dataFile.exists()) {
			try {
				var lines = Files.readAllLines(dataFile.toPath());
				if (lines.size() > 0) {
					var coords = lines.get(0).split("_");
					if (coords.length == 4) {
						x = Integer.parseInt(coords[0]);
						z = Integer.parseInt(coords[1]);
						dx = Integer.parseInt(coords[2]);
						dz = Integer.parseInt(coords[3]);
					}
				}
				if (lines.size() > 1) {
					totalChunksProcessed = Long.parseLong(lines.get(1));
				}
				cC.logSB("Loaded " + totalChunksProcessed + " processed chunks from: " + cC.GOLD + currentWorldName + cC.RESET);
			} catch (IOException | NumberFormatException e) {
				e.printStackTrace();
				cC.logS(cC.RED, "Failed to load processed chunks for " + currentWorldName);
			}
		} else {
			x = 0; z = 0; dx = 0; dz = -1;
		}
	}

	private static boolean detectPaper() {
		try {
			Class.forName("com.destroystokyo.paper.PaperConfig");
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}
}
