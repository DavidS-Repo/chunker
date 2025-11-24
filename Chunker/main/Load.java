package main;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;

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
		File dataFile = new File(plugin.getDataFolder(), task.world.getName() + "_pregenerator.txt");
		if (dataFile.exists()) {
			try {
				List<String> lines = Files.readAllLines(dataFile.toPath());
				if (!lines.isEmpty()) {
					String[] parts = lines.get(0).split("_");
					if (parts.length == 7 || parts.length == 9) {
						int x = Integer.parseInt(parts[0]);
						int z = Integer.parseInt(parts[1]);
						int directionIndex = Integer.parseInt(parts[2]);
						int stepsRemaining = Integer.parseInt(parts[3]);
						int stepsToChange = Integer.parseInt(parts[4]);
						int chunkIndex = Integer.parseInt(parts[5]);
						long processedChunks = Long.parseLong(parts[6]);

						int maxIndex = (32 * 32) - 1;
						if (chunkIndex < 0) {
							chunkIndex = 0;
						} else if (chunkIndex > maxIndex) {
							chunkIndex = maxIndex;
						}

						task.chunkIterator.setState(x, z, directionIndex, stepsRemaining, stepsToChange, chunkIndex);
						task.totalChunksProcessed.add(processedChunks);

						if (parts.length == 9) {
							task.centerBlockX = Integer.parseInt(parts[7]);
							task.centerBlockZ = Integer.parseInt(parts[8]);
							task.stateHasCenter = true;
						} else {
							task.centerBlockX = 0;
							task.centerBlockZ = 0;
							task.stateHasCenter = true;

							String upgradedData = String.format(
									"%d_%d_%d_%d_%d_%d_%d_%d_%d",
									x,
									z,
									directionIndex,
									stepsRemaining,
									stepsToChange,
									chunkIndex,
									processedChunks,
									task.centerBlockX,
									task.centerBlockZ
									);
							Files.writeString(
									dataFile.toPath(),
									upgradedData,
									StandardOpenOption.CREATE,
									StandardOpenOption.TRUNCATE_EXISTING
									);
							logPlain("Upgraded pregenerator state file for " + task.world.getName() + " to include center 0,0");
						}

						logPlain("Successfully loaded " + processedChunks + " processed chunks for " + task.world.getName());
						return true;
					} else {
						throw new IOException("Invalid task state format. Expected 7 or 9 parts but found " + parts.length);
					}
				} else {
					throw new IOException("Empty task state file.");
				}
			} catch (IOException | NumberFormatException e) {
				e.printStackTrace();
				exceptionMsg("Failed to load processed chunks for " + task.world.getName() + ": " + e.getMessage());
				task.chunkIterator.reset();
				task.totalChunksProcessed.reset();
				task.stateHasCenter = false;
				return false;
			}
		} else {
			task.chunkIterator.reset();
			task.totalChunksProcessed.reset();
			task.stateHasCenter = false;
			logPlain("No pre-generator data found for " + task.world.getName() + ". Starting fresh.");
			return false;
		}
	}
}
