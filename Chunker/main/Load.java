package main;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
	 */
	public void state(JavaPlugin plugin, PreGenerationTask task) {
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
						logPlain("Successfully loaded " + processedChunks + " processed chunks for " + task.world.getName());
					} else {
						throw new IOException("Invalid task state format. Expected 7 parts but found " + parts.length);
					}
				} else {
					throw new IOException("Empty task state file.");
				}
			} catch (IOException | NumberFormatException e) {
				e.printStackTrace();
				exceptionMsg("Failed to load processed chunks for " + task.world.getName() + ": " + e.getMessage());
				task.chunkIterator.reset();
				task.totalChunksProcessed.reset();
			}
		} else {
			task.chunkIterator.reset();
			task.totalChunksProcessed.reset();
			logPlain("No pre-generator data found for " + task.world.getName() + ". Starting fresh.");
		}
	}
}