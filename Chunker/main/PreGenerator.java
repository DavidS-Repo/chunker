package main;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PreGenerator implements Listener {

	private final JavaPlugin plugin;
	private final Map<String, PreGenerationTask> tasks = new ConcurrentHashMap<>();

	private static final String 
	ENABLED_WARNING_MESSAGE = "pre-generator is already enabled.",
	ENABLED_MESSAGE = "pre-generation has been enabled.",
	COMPLETE = "pre-generation complete.",
	DISABLED_WARNING_MESSAGE = "pre-generator is already disabled.",
	DISABLED_MESSAGE = "pre-generation disabled.",
	RADIUS_EXCEEDED_MESSAGE = "radius reached, To process more chunks please increase the radius.";

	private static final boolean IS_PAPER = detectPaper();
	private static final int TICK_MILLISECOND = 50;
	private int SERVERMILLISECOND;

	public PreGenerator(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	public synchronized void enable(int parallelTasksMultiplier, char timeUnit, int timeValue, int printTime, World world, long radius) {
		String worldName = world.getName();
		if (tasks.containsKey(worldName)) {
			cC.logS(cC.YELLOW, worldName + " " + ENABLED_WARNING_MESSAGE);
			return;
		}

		PreGenerationTask task = new PreGenerationTask();
		task.parallelTasksMultiplier = parallelTasksMultiplier;
		task.timeUnit = timeUnit;
		task.timeValue = timeValue;
		task.printTime = printTime;
		task.world = world;
		task.radius = radius;
		task.enabled = true;
		task.firstPrint = true;
		task.currentWorldName = worldName;

		if (world.getEnvironment() == Environment.NORMAL) {
			SERVERMILLISECOND = PluginSettings.world_SERVERMILLISECOND();
		} else if (world.getEnvironment() == Environment.NETHER) {
			SERVERMILLISECOND = PluginSettings.world_nether_SERVERMILLISECOND();
		} else if (world.getEnvironment() == Environment.THE_END) {
			SERVERMILLISECOND = PluginSettings.world_the_end_SERVERMILLISECOND();
		}

		tasks.put(worldName, task);

		loadProcessedChunks(task);
		if (task.totalChunksProcessed >= radius) {
			cC.logS(cC.YELLOW, worldName + " " + RADIUS_EXCEEDED_MESSAGE);
			task.enabled = false;
			return;
		}

		cC.logS(cC.GREEN, ENABLED_MESSAGE);
		initializeSchedulers(task);
		startGeneration(task);
		startPrintInfoTimer(task);
	}

	public synchronized void disable(World world) {
		String worldName = world.getName();
		PreGenerationTask task = tasks.get(worldName);
		if (task == null || !task.enabled) {
			cC.logS(cC.YELLOW, worldName + " " + DISABLED_WARNING_MESSAGE);
			return;
		}

		terminate(task);
		tasks.remove(worldName);
		cC.logS(cC.RED, worldName + " " + DISABLED_MESSAGE);
	}

	private void initializeSchedulers(PreGenerationTask task) {
		task.scheduler = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
		task.taskScheduler = Executors.newScheduledThreadPool(PluginSettings.THREADS(), Thread.ofVirtual().factory());
	}

	private synchronized void terminate(PreGenerationTask task) {
		task.TimerEnd = System.currentTimeMillis();
		saveProcessedChunks(task);
		printInfo(task);
		stopPrintInfoTimer(task);
		shutdownSchedulers(task);
		task.enabled = false;
	}

	private void shutdownSchedulers(PreGenerationTask task) {
		if (!task.enabled) {
			return;
		}
		if (task.scheduler != null && !task.scheduler.isShutdown()) {
			task.scheduler.shutdownNow();
		}
		if (task.taskScheduler != null && !task.taskScheduler.isShutdown()) {
			task.taskScheduler.shutdownNow();
		}
	}

	private void startPrintInfoTimer(PreGenerationTask task) {
		if (task.firstPrint) {
			task.firstPrint = false;
			cC.logSB("Available Processors: " + PluginSettings.THREADS());
		}
		task.scheduler.scheduleAtFixedRate(() -> printInfo(task), 0, task.printTime * TICK_MILLISECOND, TimeUnit.MILLISECONDS);
	}

	private void stopPrintInfoTimer(PreGenerationTask task) {
		if (!task.enabled) {
			return;
		}
		task.scheduler.shutdownNow();
		long elapsedTime = (task.TimerEnd - task.TimerStart) / 1000;
		cC.logSB("Total time: " + cC.GOLD + formatElapsedTime(elapsedTime) + cC.RESET);
		task.TimerStart = 0;
		task.TimerEnd = 0;
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

	private void startGeneration(PreGenerationTask task) {
		task.TimerStart = System.currentTimeMillis();
		if (IS_PAPER) {
			for (int i = 0; i < task.parallelTasksMultiplier; i++) {
				asyncProcess(task);
			}
		} else {
			new BukkitRunnable() {
				@Override
				public void run() {
					task.tasks = Math.max(1, (int)(task.parallelTasksMultiplier / 2.5));
					for (int i = 0; i < task.tasks; i++) {
						syncProcess(task);
					}
				}
			}.runTaskTimer(plugin, 0L, 0L);
		}
	}

	private void asyncProcess(PreGenerationTask task) {
		if (!task.enabled) {
			return;
		}
		task.taskScheduler.scheduleAtFixedRate(() -> {
			if (task.totalChunksProcessed >= task.radius) {
				return;
			}
			synchronized (task) {
				task.currentX = task.x; task.currentZ = task.z;
				updateCoordinates(task);
			}
			getChunkAsync(task, task.currentX, task.currentZ, true);
			completionCheck(task);
		}, 0, SERVERMILLISECOND, TimeUnit.MILLISECONDS);
	}

	private void syncProcess(PreGenerationTask task) {
		if (!task.enabled) {
			return;
		}
		if (task.totalChunksProcessed >= task.radius) {
			return;
		}
		task.currentX = task.x; task.currentZ = task.z;
		updateCoordinates(task);
		handleChunk(task, task.currentX, task.currentZ);
		completionCheck(task);
		saveProcessedChunks(task);
	}

	private void getChunkAsync(PreGenerationTask task, int x, int z, boolean gen) {
		task.taskScheduler.execute(() -> {
			task.world.getChunkAtAsync(x, z, gen);
			saveProcessedChunks(task);
			synchronized (task) {
				task.totalChunksProcessed++;
				task.chunksThisCycle++;
				completionCheck(task);
			}
		});
	}

	private void updateCoordinates(PreGenerationTask task) {
		if (task.x == task.z || (task.x < 0 && task.x == -task.z) || (task.x > 0 && task.x == 1 - task.z)) {
			int temp = task.dx;
			task.dx = -task.dz;
			task.dz = temp;
		}
		task.x += task.dx;
		task.z += task.dz;
	}

	private void handleChunk(PreGenerationTask task, int x, int z) {
		Chunk chunk = task.world.getChunkAt(x, z);
		chunk.load(true);
		while (chunk.getLoadLevel() == Chunk.LoadLevel.ENTITY_TICKING) {
			boolean unloaded = task.world.unloadChunk(x, z, true);
			if (!unloaded) {
				break;
			}
		}
		task.totalChunksProcessed++;
		task.chunksThisCycle++;
	}

	private void completionCheck(PreGenerationTask task) {
		if (task.totalChunksProcessed >= task.radius) {
			task.complete = true;
			terminate(task);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	private void onChunkLoad(ChunkLoadEvent event) {
		PreGenerationTask task = tasks.get(event.getWorld().getName());
		if (task == null || !task.enabled) {
			return;
		}
		handleChunkLoad(task, event);
	}

	private void handleChunkLoad(PreGenerationTask task, ChunkLoadEvent event) {
		Chunk chunk = event.getChunk();
		World chunkWorld = chunk.getWorld();
		String chunkId = getChunkId(chunk);
		if (chunk == null || task.playerLoadedChunks.contains(chunkId)) {
			return;
		}
		chunkId = getChunkId(chunk);
		if (!event.isNewChunk() && !task.playerLoadedChunks.contains(chunkId)) {
			chunkWorld.unloadChunk(task.x, task.z, true);
		} else {
			task.playerLoadedChunks.add(chunkId);
		}
		if (IS_PAPER) {
			asyncUnloadCheck(task, chunk, chunkId);
		}
	}

	private String getChunkId(Chunk chunk) {
		return chunk.getWorld().getName() + "_" + chunk.getX() + "_" + chunk.getZ();
	}

	private void asyncUnloadCheck(PreGenerationTask task, Chunk chunk, String chunkId) {
		if (task.scheduledChunks.add(chunkId)) {
			task.taskScheduler.execute(() -> {
				try {
					while (chunk.getLoadLevel() == Chunk.LoadLevel.TICKING && !task.playerLoadedChunks.contains(chunkId)) {
						boolean unloaded = chunk.unload(true);
						if (!unloaded) {
							break;
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					task.scheduledChunks.remove(chunkId);
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
		tasks.values().forEach(task -> task.playerLoadedChunks.add(getChunkId(chunk)));
	}

	private void printInfo(PreGenerationTask task) {
		task.localChunksThisCycle = task.chunksThisCycle;
		task.chunksPerSec = task.localChunksThisCycle / task.timeValue;
		logProgress(task);
		resetCycleCounts(task);
	}

	private void logProgress(PreGenerationTask task) {
		World thisTaskWorld = task.world;
		int radiusWidth = String.valueOf(task.radius).length();
		String worldStr = cC.logO(cC.GOLD, cC.padWorldName(task.currentWorldName, 13));
		String processed = cC.fA(cC.GOLD, task.localChunksThisCycle, 4);
		String perSecond = cC.fA(cC.GOLD, task.chunksPerSec, 4);
		String completion = cC.fA(cC.GOLD, task.totalChunksProcessed, radiusWidth);
		String radiusStr = cC.fA(cC.GOLD, task.radius, radiusWidth);
		String logFormat = "%s Processed: %s Chunks/%s: %s Completed: %s out of %s Chunks";

		if (task.enabled && !task.complete) {
			cC.logSB(String.format(logFormat, worldStr, processed, task.timeUnit, perSecond, completion, radiusStr));
		} else if (task.complete && task.localChunksThisCycle != 0 && task.chunksPerSec != 0) {
			cC.logSB(String.format(logFormat, worldStr, processed, task.timeUnit, perSecond, radiusStr, radiusStr));
			cC.logS(cC.GREEN, thisTaskWorld + " " + COMPLETE);
		}
	}

	private void resetCycleCounts(PreGenerationTask task) {
		task.chunksPerSec = 0;
		task.localChunksThisCycle = 0;
		task.chunksThisCycle = 0;
	}

	private void saveProcessedChunks(PreGenerationTask task) {
		if (!task.enabled) {
			return;
		}
		File dataFolder = plugin.getDataFolder();
		if (!dataFolder.exists()) {
			dataFolder.mkdirs();
		}
		File dataFile = new File(dataFolder, task.currentWorldName + "_pregenerator.txt");
		String data;
		synchronized (task) {
			data = String.format("%d_%d_%d_%d%n%d", task.x, task.z, task.dx, task.dz, task.totalChunksProcessed);
		}
		try {
			Files.writeString(dataFile.toPath(), data);
		} catch (IOException e) {
			e.printStackTrace();
			cC.logS(cC.RED, "Failed to save processed chunks for " + task.currentWorldName);
		}
	}

	private void loadProcessedChunks(PreGenerationTask task) {
		File dataFile = new File(plugin.getDataFolder(), task.currentWorldName + "_pregenerator.txt");
		if (dataFile.exists()) {
			try {
				var lines = Files.readAllLines(dataFile.toPath());
				if (lines.size() > 0) {
					var coords = lines.get(0).split("_");
					if (coords.length == 4) {
						task.x = Integer.parseInt(coords[0]);
						task.z = Integer.parseInt(coords[1]);
						task.dx = Integer.parseInt(coords[2]);
						task.dz = Integer.parseInt(coords[3]);
					}
				}
				if (lines.size() > 1) {
					task.totalChunksProcessed = Long.parseLong(lines.get(1));
				}
				cC.logSB("Loaded " + task.totalChunksProcessed + " processed chunks from: " + cC.GOLD + task.currentWorldName + cC.RESET);
			} catch (IOException | NumberFormatException e) {
				e.printStackTrace();
				cC.logS(cC.RED, "Failed to load processed chunks for " + task.currentWorldName);
			}
		} else {
			task.x = 0; task.z = 0; task.dx = 0; task.dz = -1;
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