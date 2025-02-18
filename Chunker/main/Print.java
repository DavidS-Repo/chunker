package main;

import java.util.concurrent.TimeUnit;
import static main.ConsoleColorUtils.*;

/**
 * Handles printing of pre-generation progress information at scheduled intervals.
 */
public class Print {
	private static final int TICK_MILLISECOND = 50;
	private static final ColorPair color = GOLD;

	/**
	 * Starts the timer to periodically print information about the pre-generation progress.
	 *
	 * @param task the PreGenerationTask whose progress is being tracked
	 */
	public void start(PreGenerationTask task) {
		task.printScheduler.scheduleAtFixedRate(() -> {
			try {
				if (!task.enabled) {
					return;
				}
				info(task);
			} catch (Exception e) {
				exceptionMsg("Exception in printScheduler scheduled task: " + e.getMessage());
				e.printStackTrace();
			}
		}, 0, task.printTime * TICK_MILLISECOND, TimeUnit.MILLISECONDS, task.printScheduler.isEnabledSupplier());
	}

	/**
	 * Stops the print info timer and logs the total elapsed time.
	 *
	 * @param task the PreGenerationTask whose progress is being tracked
	 */
	public void stop(PreGenerationTask task) {
		if (!task.enabled) {
			return;
		}
		task.printScheduler.setEnabled(false);
		long elapsedTime = (task.timerEnd - task.timerStart) / 1000;
		logPlain("Total time: " + format(elapsedTime));
		task.timerStart = 0;
		task.timerEnd = 0;
	}

	/**
	 * Formats elapsed time into a human-readable string.
	 *
	 * @param seconds the total elapsed time in seconds
	 * @return a formatted time string
	 */
	private String format(long seconds) {
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
	 * Prints information about the current state of chunk pre-generation.
	 *
	 * @param task the PreGenerationTask whose progress is being tracked
	 */
	public void info(PreGenerationTask task) {
		try {
			task.localChunksThisCycle = task.chunksThisCycle;
			task.chunksPerSec = task.localChunksThisCycle / task.timeValue;
			log(task);
			reset(task);
		} catch (Exception e) {
			exceptionMsg("Exception in printInfo: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Logs the progress of chunk pre-generation to the console.
	 *
	 * @param task the PreGenerationTask whose progress is being tracked
	 */
	private void log(PreGenerationTask task) {
		try {
			String worldStr = formatColorObject(color, formatWorldName(task.world.getName()));
			String processed = formatAligned(color, task.localChunksThisCycle, 5);
			String perSecond = formatAligned(color, task.chunksPerSec, 5);
			String radiusStr = formatAligned(color, task.radius, 14);
			StringBuilder sb = new StringBuilder();
			sb.append(worldStr)
			.append(" Processed: ").append(processed).append(" Chunks/").append(task.timeUnit).append(": ").append(perSecond).append(" Completed: ");
			if (task.enabled && !task.complete) {
				String completion = formatAligned(color, task.totalChunksProcessed.sum(), 14);
				sb.append(completion).append(" out of ").append(radiusStr).append(" Chunks");
				logPlain(sb.toString());
			} else if (task.complete && task.localChunksThisCycle != 0 && task.chunksPerSec != 0) {
				sb.append(radiusStr).append(" out of ").append(radiusStr).append(" Chunks");
				logPlain(sb.toString());
			}
		} catch (Exception e) {
			exceptionMsg("Exception in logProgress: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Resets cycle-specific counters after logging progress.
	 *
	 * @param task the PreGenerationTask whose progress is being tracked
	 */
	private void reset(PreGenerationTask task) {
		task.chunksPerSec = 0;
		task.localChunksThisCycle = 0;
		task.chunksThisCycle = 0;
	}
}