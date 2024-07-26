package main;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public class PreGenerator implements Listener {

	private boolean enabled = false, complete = false, lastPrint = false;
	private final JavaPlugin plugin;

	private String currentWorldName;
	private World world;
	private boolean firstPrint;

	private ScheduledExecutorService scheduler;
	private ScheduledExecutorService taskScheduler;
	private final static int tickMillisecond = 50;

	private int parallelTasksMultiplier, timeValue, printTime, chunksPerSec;
	private char timeUnit;
	private long radius;

	private int x, z, dx, dz, currentX, currentZ;
	private LongAdder totalChunksProcessed = new LongAdder();
	private int chunksThisCycle = 0, localChunksThisCycle = 0;

	private static final String ENABLED_WARNING_MESSAGE = cC.YELLOW + "Pre-Generator is already enabled." + cC.RESET;
	private static final String ENABLED_MESSAGE = cC.GREEN + "Pre-generation has been enabled." + cC.RESET;
	private static final String COMPLETE = cC.GREEN + "Pre-generation Complete." + cC.RESET;
	private static final String DISABLED_WARNING_MESSAGE = cC.YELLOW + "Pre-Generator is already disabled." + cC.RESET;
	private static final String DISABLED_MESSAGE = cC.RED + "Pre-generation disabled." + cC.RESET;
	private static final String RADIUS_EXCEEDED_MESSAGE = cC.YELLOW + "To process more chunks please increase the radius." + cC.RESET;

	private final Set<String> scheduledChunks = ConcurrentHashMap.newKeySet();
	private final Set<String> playerLoadedChunks = ConcurrentHashMap.newKeySet();

	private static final boolean IS_PAPER = detectPaper();

	private long TimerStart = 0;
	private long TimerEnd = 0;

	public PreGenerator(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public synchronized void enable(int parallelTasksMultiplier, char timeUnit, int timeValue, int printTime, World world, long radius) {
		if (enabled) {
			Bukkit.getLogger().info(ENABLED_WARNING_MESSAGE);
			return;
		}
		if (this.world != null && !this.world.equals(world)) {
			totalChunksProcessed.reset();
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
		if (totalChunksProcessed.sum() >= radius) {
			Bukkit.getLogger().info(RADIUS_EXCEEDED_MESSAGE);
			enabled = false;
			return;
		}
		Bukkit.getLogger().info(ENABLED_MESSAGE);
		scheduler = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
		taskScheduler = Executors.newScheduledThreadPool(parallelTasksMultiplier, Thread.ofVirtual().factory());
		startGeneration();
		startPrintInfoTimer();
	}

	public synchronized void disable() {
		if (!enabled) {
			Bukkit.getLogger().info(DISABLED_WARNING_MESSAGE);
			return;
		}
		terminate();
		Bukkit.getLogger().info(DISABLED_MESSAGE);
	}

	private synchronized void terminate() {
		TimerEnd = System.currentTimeMillis();
		enabled = false;
		saveProcessedChunks();
		printInfo();
		stopPrintInfoTimer();
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
			Bukkit.getLogger().info("Available Processors: " + Runtime.getRuntime().availableProcessors());
		}
		scheduler.scheduleAtFixedRate(this::printInfo, 0, printTime * tickMillisecond, TimeUnit.MILLISECONDS);
	}

	private void stopPrintInfoTimer() {
		scheduler.shutdownNow();
		long elapsedTime = (TimerEnd - TimerStart) / 1000;
		Bukkit.getLogger().info("Total time: " + cC.GOLD + formatElapsedTime(elapsedTime) + cC.RESET);
		TimerStart = 0;
		TimerEnd = 0;
	}

	private String formatElapsedTime(long seconds) {
		long hours = seconds / 3600, minutes = (seconds % 3600) / 60, remainingSeconds = seconds % 60;
		StringBuilder formattedTime = new StringBuilder();
		if (hours > 0) {formattedTime.append(hours).append(" Hour").append(hours > 1 ? "s" : "").append(" ");}
		if (minutes > 0) {formattedTime.append(minutes).append(" Minute").append(minutes > 1 ? "s" : "").append(" ");}
		if (remainingSeconds > 0 || formattedTime.length() == 0) {formattedTime.append(remainingSeconds).append(" Second").append(remainingSeconds != 1 ? "s" : "").append(" ");}
		return formattedTime.toString().trim();
	}

	private void startGeneration() {
		TimerStart = System.currentTimeMillis();
		for (int i = 0; i < parallelTasksMultiplier; i++) {
			generateChunkBatch();
		}
	}

	private void generateChunkBatch() {
		if (!enabled) {
			return;
		}

		taskScheduler.scheduleAtFixedRate(() -> {
			if (!enabled || totalChunksProcessed.sum() >= radius) {
				return;
			}
			synchronized (this) {
				currentX = x;
				currentZ = z;
				if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
					int temp = dx;
					dx = -dz;
					dz = temp;
				}
				x += dx;
				z += dz;
			}
			getChunkAtAsync(world, currentX, currentZ, true, false);
		}, 0, tickMillisecond, TimeUnit.MILLISECONDS);
	}

	private void getChunkAtAsync(World world, int x, int z, boolean gen, boolean urgent) {
		taskScheduler.execute(() -> {
			Chunk chunk;
			if (IS_PAPER) {
				world.getChunkAtAsync(x, z, gen, false);
			} else {
				chunk = world.getChunkAt(x, z);
				if (gen && !chunk.isLoaded()) {
					chunk.load(true);
				}
			}
			synchronized (this) {
				totalChunksProcessed.increment();
				chunksThisCycle++;
				if (totalChunksProcessed.sum() >= radius) {
					if (!complete) {
						complete = true;
						lastPrint = true;
						terminate();
					}
				}
			}
		});
		saveProcessedChunks();
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	private void onChunkLoad(ChunkLoadEvent event) {
		if (!enabled) {
			return;
		}
		Chunk chunk = event.getChunk();
		if (chunk == null) {
			return;
		}
		String chunkId = getChunkId(chunk);
		World chunkWorld = chunk.getWorld();
		if (chunkWorld == null) {
			return;
		}
		if (!event.isNewChunk() && !playerLoadedChunks.contains(chunkId)) {
			chunkWorld.unloadChunk(chunk.getX(), chunk.getZ(), true);
		} else {
			playerLoadedChunks.add(chunkId);
		}
		scheduleChunkCheck(chunk, chunkId);
	}

	private String getChunkId(Chunk chunk) {
		return chunk.getWorld().getName() + "_" + chunk.getX() + "_" + chunk.getZ();
	}

	private void scheduleChunkCheck(Chunk chunk, String chunkId) {
		if (scheduledChunks.add(chunkId)) {
			taskScheduler.execute(() -> {
				try {
					while (chunk.getLoadLevel() == Chunk.LoadLevel.TICKING && !playerLoadedChunks.contains(chunkId)) {
						boolean unloaded = chunk.getWorld().unloadChunk(chunk.getX(), chunk.getZ(), true);
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
		String PROCESSED = cC.GOLD + String.valueOf(localChunksThisCycle) + cC.RESET;
		String PERSEC = cC.GOLD + String.valueOf(chunksPerSec) + cC.RESET;
		String WORLD = cC.GOLD + currentWorldName + cC.RESET;
		String COMPLETION = cC.GOLD + totalChunksProcessed.sum() + cC.RESET;
		String RADIUS = cC.GOLD + radius + cC.RESET;
		if (enabled && !complete) {
			Bukkit.getLogger().info("[" + WORLD + "] Processed: " + PROCESSED + " Chunks/" + timeUnit + ": " + PERSEC + " Completed: " + COMPLETION + " out of " + RADIUS + " Chunks");
		}
		else if (!enabled && complete && lastPrint) {
			Bukkit.getLogger().info("[" + WORLD + "] Processed: " + PROCESSED + " Chunks/" + timeUnit + ": " + PERSEC + " Completed: " + RADIUS + " out of " + RADIUS + " Chunks");
			Bukkit.getLogger().info(COMPLETE);
			lastPrint = false;
		}
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
			data = String.format("%d_%d_%d_%d%n%d", x, z, dx, dz, totalChunksProcessed.sum());
		}
		try {
			Files.writeString(dataFile.toPath(), data);
		} catch (IOException e) {
			e.printStackTrace();
			Bukkit.getLogger().info("Failed to save processed chunks for " + currentWorldName);
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
					long processedChunks = Long.parseLong(lines.get(1));
					totalChunksProcessed.add(processedChunks - totalChunksProcessed.sum());
				}
				String WORLD = cC.GOLD + currentWorldName + cC.RESET;
				Bukkit.getLogger().info("Loaded " + totalChunksProcessed.sum() + " processed chunks from: " + WORLD);
			} catch (IOException | NumberFormatException e) {
				e.printStackTrace();
				Bukkit.getLogger().info("Failed to load processed chunks for " + currentWorldName);
			}
		} else {
			x = 0; z = 0; dx = 0; dz = -1;
		}
	}

	private static final boolean detectPaper() {
		try {
			Class.forName("com.destroystokyo.paper.PaperConfig");
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}
}
