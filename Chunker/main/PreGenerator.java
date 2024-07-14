package main;

import io.papermc.lib.PaperLib;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.LongAdder;

public class PreGenerator implements Listener {
	private boolean enabled = false, complete = false, lastPrint = false;
	private final JavaPlugin plugin;
	private ExecutorService oddExecutorService;
	private ExecutorService evenExecutorService;

	private String currentWorldName;
	private World world;
	private BukkitTask printInfoTask;
	private boolean firstPrint;
	private int ParallelTasksMultiplier, timeValue, printTime, chunksPerSec;
	private char timeUnit;
	private long radius;
	private static final int threads = Runtime.getRuntime().availableProcessors();
	private static final int cores = threads / 2;
	private Semaphore evenSemaphore;
	private Semaphore oddSemaphore;

	// Separate coordinate and direction variables for even and odd instances
	private int xEven, zEven, dxEven, dzEven, xOdd, zOdd, dxOdd, dzOdd;

	// Instance variables for even chunks
	private LongAdder totalChunksProcessedEven = new LongAdder();
	private int chunksThisCycleEven = 0;
	private int currentXEven, currentZEven;
	private boolean needsUpdateEven;

	// Instance variables for odd chunks
	private LongAdder totalChunksProcessedOdd = new LongAdder();
	private int chunksThisCycleOdd = 0;
	private int currentXOdd, currentZOdd;
	private boolean needsUpdateOdd;

	private static final String ENABLED_WARNING_MESSAGE = ChatColor.YELLOW + "Pre-Generator is already enabled.";
	private static final String ENABLED_MESSAGE = ChatColor.GREEN + "Pre-generation has been enabled.";
	private static final String COMPLETE = ChatColor.GREEN + "Pre-generation Complete.";
	private static final String DISABLED_WARNING_MESSAGE = ChatColor.YELLOW + "Pre-Generator is already disabled.";
	private static final String DISABLED_MESSAGE = ChatColor.RED + "Pre-generation disabled.";
	private static final String RADIUS_EXCEEDED_MESSAGE = ChatColor.YELLOW + "To process more chunks please increase the radius.";

	private final Set<String> scheduledChunks = ConcurrentHashMap.newKeySet();
	private final Set<String> playerLoadedChunks = ConcurrentHashMap.newKeySet();

	public PreGenerator(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public synchronized void enable(int ParallelTasksMultiplier, char timeUnit, int timeValue, int printTime, World world, long radius) {
		if (enabled) {
			Bukkit.broadcastMessage(ENABLED_WARNING_MESSAGE);
			return;
		}
		if (this.world != null && !this.world.equals(world)) {
			totalChunksProcessedEven.reset();
			totalChunksProcessedOdd.reset();
		}
		this.world = world;
		this.ParallelTasksMultiplier = ParallelTasksMultiplier;
		this.timeUnit = timeUnit;
		this.printTime = printTime;
		this.timeValue = timeValue;
		this.radius = radius;
		enabled = true;
		firstPrint = true;
		complete = false;
		currentWorldName = world.getName();
		loadProcessedChunks();
		if ((totalChunksProcessedEven.sum() + totalChunksProcessedOdd.sum()) >= radius) {
			Bukkit.broadcastMessage(RADIUS_EXCEEDED_MESSAGE);
			enabled = false;
			return;
		}
		Bukkit.broadcastMessage(ENABLED_MESSAGE);
		createParallel();
		startGenerationEven();
		startGenerationOdd();
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

	public synchronized void terminate() {
		enabled = false;
		saveProcessedChunksEven();
		saveProcessedChunksOdd();
		printInfo();
		stopPrintInfoTimer();
		evenExecutorService.shutdown();
		oddExecutorService.shutdown();
	}

	private void createParallel() {
		evenExecutorService = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
		oddExecutorService = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
		evenSemaphore = new Semaphore((cores / 2) * ParallelTasksMultiplier);
		oddSemaphore = new Semaphore((cores / 2) * ParallelTasksMultiplier);
	}

	private void startPrintInfoTimer() {
		if (printInfoTask == null) {
			printInfoTask = new BukkitRunnable() {
				@Override
				public void run() {
					if (firstPrint) {
						firstPrint = false;
						Bukkit.broadcastMessage("Available Processors: " + threads);
						return;
					}
					printInfo();
				}
			}.runTaskTimer(plugin, 0, printTime);
		}
	}

	private void stopPrintInfoTimer() {
		if (printInfoTask != null) {
			printInfoTask.cancel();
			printInfoTask = null;
		}
	}

	private void startGenerationEven() {
		for (int i = 0; i < (cores / 2); i++) {
			generateChunkBatchEven();
		}
	}

	private void startGenerationOdd() {
		for (int i = 0; i < (cores / 2); i++) {
			generateChunkBatchOdd();
		}
	}

	// Even batch generation
	private void generateChunkBatchEven() {
		if (!enabled) {
			return;
		}
		CompletableFuture.runAsync(() -> {
			while (enabled && (totalChunksProcessedEven.sum() + totalChunksProcessedOdd.sum()) < radius) {
				try {
					evenSemaphore.acquire();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
				synchronized (this) {
					currentXEven = xEven;
					currentZEven = zEven;
					do {
						needsUpdateEven = false;
						if (xEven == zEven || (xEven < 0 && xEven == -zEven) || (xEven > 0 && xEven == 1 - zEven)) {
							int temp = dxEven;
							dxEven = -dzEven;
							dzEven = temp;
						}
						xEven += dxEven;
						zEven += dzEven;
						if (xEven % 2 != 0) {
							needsUpdateEven = true;
						}
					} while (needsUpdateEven);
				}
				PaperLib.getChunkAtAsync(world, currentXEven, currentZEven, true).thenAccept(chunk -> {
				}).exceptionally(ex -> {
					ex.printStackTrace();
					return null;
				}).whenComplete((result, throwable) -> {
					evenSemaphore.release();
					totalChunksProcessedEven.increment();
					synchronized (this) {
						chunksThisCycleEven++;
						if ((totalChunksProcessedEven.sum() + totalChunksProcessedOdd.sum()) >= radius) {
							if (!complete) {
								disable();
								Bukkit.broadcastMessage(RADIUS_EXCEEDED_MESSAGE);
								complete = true;
							}
						}
					}
				});
			}
			saveProcessedChunksEven();
		}, evenExecutorService);
	}

	// Odd batch generation
	private void generateChunkBatchOdd() {
		if (!enabled) {
			return;
		}
		CompletableFuture.runAsync(() -> {
			while (enabled && (totalChunksProcessedEven.sum() + totalChunksProcessedOdd.sum()) < radius) {
				try {
					oddSemaphore.acquire();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
				synchronized (this) {
					currentXOdd = xOdd;
					currentZOdd = zOdd;
					do {
						needsUpdateOdd = false;
						if (xOdd == zOdd || (xOdd < 0 && xOdd == -zOdd) || (xOdd > 0 && xOdd == 1 - zOdd)) {
							int temp = dxOdd;
							dxOdd = -dzOdd;
							dzOdd = temp;
						}
						xOdd += dxOdd;
						zOdd += dzOdd;
						if (xOdd % 2 == 0) {
							needsUpdateOdd = true;
						}
					} while (needsUpdateOdd);
				}
				PaperLib.getChunkAtAsync(world, currentXOdd, currentZOdd, true).thenAccept(chunk -> {
				}).exceptionally(ex -> {
					ex.printStackTrace();
					return null;
				}).whenComplete((result, throwable) -> {
					oddSemaphore.release();
					totalChunksProcessedOdd.increment();
					synchronized (this) {
						chunksThisCycleOdd++;
						if ((totalChunksProcessedEven.sum() + totalChunksProcessedOdd.sum()) >= radius) {
							if (!complete) {
								disable();
								Bukkit.broadcastMessage(RADIUS_EXCEEDED_MESSAGE);
								complete = true;
							}
						}
					}
				});
			}
			saveProcessedChunksOdd();
		}, oddExecutorService);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onChunkPopulate(ChunkPopulateEvent event) {
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
				while (chunk.getLoadLevel() == Chunk.LoadLevel.ENTITY_TICKING && !playerLoadedChunks.contains(chunkId)) {
					chunk.getWorld().unloadChunk(chunk.getX(), chunk.getZ(), true);
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				scheduledChunks.remove(chunkId);
			}
		}, Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory()));
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onChunkLoad(ChunkLoadEvent event) {
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
	public void onPlayerMove(PlayerMoveEvent event) {
		Chunk chunk = event.getTo().getChunk();
		if (chunk == null) {
			return;
		}
		playerLoadedChunks.add(getChunkId(chunk));
	}

	private void printInfo() {
		int localChunksThisCycleEven, localChunksThisCycleOdd;
		synchronized (this) {
			localChunksThisCycleEven = chunksThisCycleEven;
			chunksThisCycleEven = 0;
			localChunksThisCycleOdd = chunksThisCycleOdd;
			chunksThisCycleOdd = 0;
		}
		chunksPerSec = (int) ((localChunksThisCycleEven + localChunksThisCycleOdd) / timeValue);
		String PROCESSED = ChatColor.GOLD + String.valueOf(localChunksThisCycleEven + localChunksThisCycleOdd) + ChatColor.RESET;
		String PERSEC = ChatColor.GOLD + String.valueOf(chunksPerSec) + ChatColor.RESET;
		String WORLD = ChatColor.GOLD + currentWorldName + ChatColor.RESET;
		String COMPLETION = ChatColor.GOLD + "" + (totalChunksProcessedEven.sum() + totalChunksProcessedOdd.sum()) + ChatColor.RESET;
		String RADIUS = ChatColor.GOLD + "" + radius + ChatColor.RESET;
		chunksPerSec = 0;

		if (!enabled && complete && !lastPrint) {
			Bukkit.broadcastMessage("[" + WORLD + "] Processed: " + PROCESSED + " Chunks/" + timeUnit + ": " + PERSEC + " Completed: " + COMPLETION + " out of " + RADIUS + " Chunks");
			lastPrint = true;
			Bukkit.broadcastMessage(COMPLETE);
		}
		if (enabled && !complete && ((totalChunksProcessedEven.sum() + totalChunksProcessedOdd.sum()) < radius)) {
			Bukkit.broadcastMessage("[" + WORLD + "] Processed: " + PROCESSED + " Chunks/" + timeUnit + ": " + PERSEC + " Completed: " + COMPLETION + " out of " + RADIUS + " Chunks");
		}
		if (!enabled && !complete && ((totalChunksProcessedEven.sum() + totalChunksProcessedOdd.sum()) < radius)) {
			Bukkit.broadcastMessage("[" + WORLD + "] Processed: " + PROCESSED + " Chunks/" + timeUnit + ": " + PERSEC + " Completed: " + COMPLETION + " out of " + RADIUS + " Chunks");
		}
	}

	private void saveProcessedChunksEven() {
		File dataFolder = plugin.getDataFolder();
		if (!dataFolder.exists()) {
			dataFolder.mkdirs();
		}
		File dataFileEven = new File(dataFolder, currentWorldName + "_pregenerator_even.txt");
		String dataEven;
		synchronized (this) {
			dataEven = String.format("%d_%d_%d_%d%n%d", xEven, zEven, dxEven, dzEven, totalChunksProcessedEven.sum());
		}
		try {
			Files.writeString(dataFileEven.toPath(), dataEven);
		} catch (IOException e) {
			e.printStackTrace();
			Bukkit.broadcastMessage("Failed to save processed chunks for " + currentWorldName);
		}
	}

	private void saveProcessedChunksOdd() {
		File dataFolder = plugin.getDataFolder();
		if (!dataFolder.exists()) {
			dataFolder.mkdirs();
		}
		File dataFileOdd = new File(dataFolder, currentWorldName + "_pregenerator_odd.txt");
		String dataOdd;
		synchronized (this) {
			dataOdd = String.format("%d_%d_%d_%d%n%d", xOdd, zOdd, dxOdd, dzOdd, totalChunksProcessedOdd.sum());
		}
		try {
			Files.writeString(dataFileOdd.toPath(), dataOdd);
		} catch (IOException e) {
			e.printStackTrace();
			Bukkit.broadcastMessage("Failed to save processed chunks for " + currentWorldName);
		}
	}

	private void loadProcessedChunks() {
		File dataFileEven = new File(plugin.getDataFolder(), currentWorldName + "_pregenerator_even.txt");
		File dataFileOdd = new File(plugin.getDataFolder(), currentWorldName + "_pregenerator_odd.txt");
		if (dataFileEven.exists() && dataFileOdd.exists()) {
			try {
				var linesEven = Files.readAllLines(dataFileEven.toPath());
				var linesOdd = Files.readAllLines(dataFileOdd.toPath());
				if (linesEven.size() > 0) {
					var coordsEven = linesEven.get(0).split("_");
					if (coordsEven.length == 4) {
						synchronized (this) {
							xEven = Integer.parseInt(coordsEven[0]);
							zEven = Integer.parseInt(coordsEven[1]);
							dxEven = Integer.parseInt(coordsEven[2]);
							dzEven = Integer.parseInt(coordsEven[3]);
						}
					}
				}
				if (linesEven.size() > 1) {
					long processedChunksEven = Long.parseLong(linesEven.get(1));
					totalChunksProcessedEven.add(processedChunksEven - totalChunksProcessedEven.sum());
				}
				if (linesOdd.size() > 0) {
					var coordsOdd = linesOdd.get(0).split("_");
					if (coordsOdd.length == 4) {
						synchronized (this) {
							xOdd = Integer.parseInt(coordsOdd[0]);
							zOdd = Integer.parseInt(coordsOdd[1]);
							dxOdd = Integer.parseInt(coordsOdd[2]);
							dzOdd = Integer.parseInt(coordsOdd[3]);
						}
					}
				}
				if (linesOdd.size() > 1) {
					long processedChunksOdd = Long.parseLong(linesOdd.get(1));
					totalChunksProcessedOdd.add(processedChunksOdd - totalChunksProcessedOdd.sum());
				}
				String WORLD = ChatColor.WHITE + currentWorldName + ChatColor.RESET;
				Bukkit.broadcastMessage("Loaded " + (totalChunksProcessedEven.sum() + totalChunksProcessedOdd.sum()) + " processed chunks from: " + WORLD);
			} catch (IOException | NumberFormatException e) {
				e.printStackTrace();
				Bukkit.broadcastMessage("Failed to load processed chunks for " + currentWorldName);
			}
		} else {
			xEven = 0; zEven = 0; dxEven = 0; dzEven = -1;
			xOdd = 0; zOdd = 0; dxOdd = 0; dzOdd = -1;
		}
	}
}
