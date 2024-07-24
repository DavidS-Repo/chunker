package main;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public class PreGenerator implements Listener {

	private boolean enabled = false, complete = false, lastPrint = false;
	private final JavaPlugin plugin;
	private static ExecutorService AsyncExecutor;

	private String currentWorldName;
	private World world;
	private boolean firstPrint;

	private ScheduledExecutorService scheduler;
	private ScheduledFuture<?> printInfoTask;
	private final static int tickMillisecond = 50;

	private int parallelTasksMultiplier, timeValue, printTime, chunksPerSec;
	private char timeUnit;
	private long radius;

	private static final int threads = Runtime.getRuntime().availableProcessors();
	private static final int cores = threads / 2;

	private Semaphore semaphore;

	private int x, z, dx, dz, currentX, currentZ;;
	private LongAdder totalChunksProcessed = new LongAdder();
	private int chunksThisCycle = 0, localChunksThisCycle = 0;

	private static final String ENABLED_WARNING_MESSAGE = ChatColor.YELLOW + "Pre-Generator is already enabled.";
	private static final String ENABLED_MESSAGE = ChatColor.GREEN + "Pre-generation has been enabled.";
	private static final String COMPLETE = ChatColor.GREEN + "Pre-generation Complete.";
	private static final String DISABLED_WARNING_MESSAGE = ChatColor.YELLOW + "Pre-Generator is already disabled.";
	private static final String DISABLED_MESSAGE = ChatColor.RED + "Pre-generation disabled.";
	private static final String RADIUS_EXCEEDED_MESSAGE = ChatColor.YELLOW + "To process more chunks please increase the radius.";

	private final Set<String> scheduledChunks = ConcurrentHashMap.newKeySet();
	private final Set<String> playerLoadedChunks = ConcurrentHashMap.newKeySet();

	private static final boolean IS_PAPER = detectPaper();

	public PreGenerator(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public synchronized void enable(int parallelTasksMultiplier, char timeUnit, int timeValue, int printTime, World world, long radius) {
		if (enabled) {
			Bukkit.broadcastMessage(ENABLED_WARNING_MESSAGE);
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
			Bukkit.broadcastMessage(RADIUS_EXCEEDED_MESSAGE);
			enabled = false;
			return;
		}
		Bukkit.broadcastMessage(ENABLED_MESSAGE);
		createParallel();
		startGeneration();
		startPrintInfoTimer();
	}

	public synchronized void disable() {
		if (!enabled) {
			Bukkit.broadcastMessage(DISABLED_WARNING_MESSAGE);
			return;
		}
		terminate();
		Bukkit.broadcastMessage(DISABLED_MESSAGE);
	}

	private synchronized void terminate() {
		enabled = false;
		saveProcessedChunks();
		printInfo();
		stopPrintInfoTimer();
		if (scheduler != null && !scheduler.isShutdown()) {
			scheduler.shutdownNow();
		}
		AsyncExecutor.shutdown();
	}

	private void startPrintInfoTimer() {
		if (printInfoTask == null) {
			if (firstPrint) {
				firstPrint = false;
				Bukkit.broadcastMessage("Available Processors: " + threads);
			}
			printInfoTask = scheduler.scheduleAtFixedRate(() -> {
				printInfo();
			}, 0, (printTime * tickMillisecond), TimeUnit.MILLISECONDS);
		}
	}

	private void stopPrintInfoTimer() {
		if (printInfoTask != null) {
			printInfoTask.cancel(true);
			printInfoTask = null;
		}
	}

	private void startGeneration() {
		for (int i = 0; i < cores; i++) {
			generateChunkBatch();
		}
	}

	private void createParallel() {
		scheduler = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
		AsyncExecutor = Executors.newVirtualThreadPerTaskExecutor();
		semaphore = new Semaphore(cores * parallelTasksMultiplier);
	}

	private void generateChunkBatch() {
		if (!enabled) {
			return;
		}

		CompletableFuture.runAsync(() -> {
			while (enabled && totalChunksProcessed.sum() < radius) {
				try {
					semaphore.acquire();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
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
				getChunkAtAsync(world, currentX, currentZ, true, false).thenAccept(chunk -> {
				}).exceptionally(ex -> {
					ex.printStackTrace();
					return null;
				}).whenComplete((result, throwable) -> {
					semaphore.release();
					totalChunksProcessed.increment();
					synchronized (this) {
						chunksThisCycle++;
						if (totalChunksProcessed.sum() >= radius) {
							if (!complete) {
								complete = true;
								lastPrint = true;
								terminate();
								Bukkit.broadcastMessage(RADIUS_EXCEEDED_MESSAGE);
							}
						}
					}
				});
			}
			saveProcessedChunks();
		}, AsyncExecutor);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	private void onChunkPopulate(ChunkPopulateEvent event) {
		if (!enabled) {
			return;
		}
		Chunk chunk = event.getChunk();
		if (chunk == null) {
			return;
		}
		String chunkId = getChunkId(chunk);
		if (scheduledChunks.contains(chunkId)) {
			return;
		}
		scheduledChunks.add(chunkId);
		CompletableFuture.runAsync(() -> {
			try {
				while (chunk.getLoadLevel() == Chunk.LoadLevel.TICKING && !playerLoadedChunks.contains(chunkId)) {
					chunk.getWorld().unloadChunk(chunk.getX(), chunk.getZ(), true);
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				scheduledChunks.remove(chunkId);
			}
		}, AsyncExecutor);
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
	}

	private String getChunkId(Chunk chunk) {
		return chunk.getWorld().getName() + "_" + chunk.getX() + "_" + chunk.getZ();
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
		String PROCESSED = ChatColor.GOLD + String.valueOf(localChunksThisCycle) + ChatColor.RESET;
		String PERSEC = ChatColor.GOLD + String.valueOf(chunksPerSec) + ChatColor.RESET;
		String WORLD = ChatColor.GOLD + currentWorldName + ChatColor.RESET;
		String COMPLETION = ChatColor.GOLD + "" + totalChunksProcessed.sum() + ChatColor.RESET;
		String RADIUS = ChatColor.GOLD + "" + radius + ChatColor.RESET;
		if (enabled && !complete) {
			Bukkit.broadcastMessage("[" + WORLD + "] Processed: " + PROCESSED + " Chunks/" + timeUnit + ": " + PERSEC + " Completed: " + COMPLETION + " out of " + RADIUS + " Chunks");
		}
		else if (!enabled && complete && lastPrint) {
			Bukkit.broadcastMessage("[" + WORLD + "] Processed: " + PROCESSED + " Chunks/" + timeUnit + ": " + PERSEC + " Completed: " + RADIUS + " out of " + RADIUS + " Chunks");
			Bukkit.broadcastMessage(COMPLETE);
			lastPrint = false;
		}
		chunksPerSec = 0;
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
			Bukkit.broadcastMessage("Failed to save processed chunks for " + currentWorldName);
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
				String WORLD = ChatColor.WHITE + currentWorldName + ChatColor.RESET;
				Bukkit.broadcastMessage("Loaded " + totalChunksProcessed.sum() + " processed chunks from: " + WORLD);
			} catch (IOException | NumberFormatException e) {
				e.printStackTrace();
				Bukkit.broadcastMessage("Failed to load processed chunks for " + currentWorldName);
			}
		} else {
			x = 0; z = 0; dx = 0; dz = -1;
		}
	}

	// PaperLib Replacement
	private static final boolean detectPaper() {
		try {
			Class.forName("com.destroystokyo.paper.PaperConfig");
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	private static final CompletableFuture<Chunk> getChunkAtAsync(World world, int x, int z, boolean gen, boolean urgent) {
		return CompletableFuture.supplyAsync(() -> {
			if (IS_PAPER) {
				return (urgent ? getChunkAtSyncUrgently(world, x, z, gen) : getChunkAtSync(world, x, z, gen)).join();
			} else {
				// Fallback for non-Paper servers
				Chunk chunk = world.getChunkAt(x, z);
				if (gen && !chunk.isLoaded()) {
					chunk.load(true);
				}
				return chunk;
			}
		}, AsyncExecutor);
	}

	private static final CompletableFuture<Chunk> getChunkAtSyncUrgently(World world, int x, int z, boolean gen) {
		return CompletableFuture.supplyAsync(() -> world.getChunkAt(x, z, gen), AsyncExecutor);
	}

	private static final CompletableFuture<Chunk> getChunkAtSync(World world, int x, int z, boolean gen) {
		return CompletableFuture.supplyAsync(() -> world.getChunkAt(x, z, gen), AsyncExecutor);
	}
}
