package main;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import org.bukkit.plugin.java.JavaPlugin;

import static main.ConsoleColorUtils.*;

/**
 * Loads the state of the task from a plain-text file with underscore-separated values.
 */
public class Load {

	/**
	 * Loads the saved state of the given PreGenerationTask from a file in the plugin's data folder.
	 *
	 * @param plugin the JavaPlugin instance
	 * @param task   the PreGenerationTask whose state is to be loaded
	 * @return true if a saved state was loaded, false if starting fresh
	 */
	public boolean state(JavaPlugin plugin, PreGenerationTask task) {
		String worldName = task.worldName != null ? task.worldName : WorldRegistry.id(task.world);
		File dataFile = null;
		for (File candidate : WorldRegistry.stateFiles(plugin, worldName)) {
			if (candidate.isFile()) {
				dataFile = candidate;
				break;
			}
		}

		if (dataFile == null) {
			resetTaskState(task);
			logPlain("No pre-generator data found for " + worldName + ". Starting fresh.");
			return false;
		}

		try {
			String state = Files.readString(dataFile.toPath()).strip();
			if (state.isEmpty()) {
				throw new IOException("Empty task state file.");
			}

			ParsedState parsed = parseState(state);

			int x = parsed.regionX();
			int z = parsed.regionZ();
			int directionIndex = parsed.directionIndex();
			int stepsRemaining = parsed.stepsRemaining();
			int stepsToChange = parsed.stepsToChange();
			int chunkIndex = parsed.chunkIndex();
			long processedChunks = parsed.processedChunks();

			task.chunkIterator.setState(x, z, directionIndex, stepsRemaining, stepsToChange, chunkIndex);
			task.totalChunksProcessed.add(processedChunks);
			task.submittedChunks.set(processedChunks);

			if (parsed.hasCenter()) {
				task.centerBlockX = parsed.centerBlockX();
				task.centerBlockZ = parsed.centerBlockZ();
				task.stateHasCenter = true;
			} else {
				task.centerBlockX = 0;
				task.centerBlockZ = 0;
				task.stateHasCenter = true;

				String upgradedData = new StringBuilder(96)
						.append(x).append('_')
						.append(z).append('_')
						.append(directionIndex).append('_')
						.append(stepsRemaining).append('_')
						.append(stepsToChange).append('_')
						.append(task.chunkIterator.getChunkIndex()).append('_')
						.append(processedChunks).append('_')
						.append(task.centerBlockX).append('_')
						.append(task.centerBlockZ)
						.toString();
				Files.writeString(dataFile.toPath(), upgradedData, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
				logPlain("Upgraded pregenerator state file for " + worldName + " to include center 0,0");
			}

			logPlain("Successfully loaded " + processedChunks + " processed chunks for " + worldName);
			return true;
		} catch (IOException | NumberFormatException e) {
			e.printStackTrace();
			exceptionMsg("Failed to load processed chunks for " + worldName + ": " + e.getMessage());
			resetTaskState(task);
			return false;
		}
	}


	private static ParsedState parseState(String state) throws IOException {
		long[] values = new long[9];
		int count = 0;
		int start = 0;
		int len = state.length();

		for (int i = 0; i <= len; i++) {
			if (i != len && state.charAt(i) != '_') continue;

			if (count == values.length) {
				throw new IOException("Invalid task state format. Expected 7 or 9 parts but found more than 9");
			}
			values[count++] = Long.parseLong(state.substring(start, i));
			start = i + 1;
		}

		if (count != 7 && count != 9) {
			throw new IOException("Invalid task state format. Expected 7 or 9 parts but found " + count);
		}

		return new ParsedState(
				toInt(values[0], "region x"),
				toInt(values[1], "region z"),
				toInt(values[2], "direction index"),
				toInt(values[3], "steps remaining"),
				toInt(values[4], "steps to change"),
				toInt(values[5], "chunk index"),
				values[6],
				count == 9,
				count == 9 ? toInt(values[7], "center block x") : 0,
				count == 9 ? toInt(values[8], "center block z") : 0
				);
	}

	private static int toInt(long value, String fieldName) throws IOException {
		if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
			throw new IOException("Invalid " + fieldName + " value: " + value);
		}
		return (int) value;
	}

	private record ParsedState(
			int regionX,
			int regionZ,
			int directionIndex,
			int stepsRemaining,
			int stepsToChange,
			int chunkIndex,
			long processedChunks,
			boolean hasCenter,
			int centerBlockX,
			int centerBlockZ
			) {
	}

	private static void resetTaskState(PreGenerationTask task) {
		task.chunkIterator.reset();
		task.totalChunksProcessed.reset();
		task.submittedChunks.set(0L);
		task.stateHasCenter = false;
	}
}
