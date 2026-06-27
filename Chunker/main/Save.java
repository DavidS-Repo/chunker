package main;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import org.bukkit.plugin.java.JavaPlugin;

import static main.ConsoleColorUtils.*;

/**
 * Saves the state of the task to a plain-text file with underscore-separated values.
 */
public class Save {

	/**
	 * Saves the current state of the given PreGenerationTask to a file in the plugin's data folder.
	 *
	 * @param plugin the JavaPlugin instance
	 * @param task   the PreGenerationTask whose state is to be saved
	 */
	public void state(JavaPlugin plugin, PreGenerationTask task) {
		if (!task.enabled) {
			return;
		}
		String worldName = task.worldName != null ? task.worldName : WorldRegistry.id(task.world);
		File dataFolder = plugin.getDataFolder();
		if (!dataFolder.exists() && !dataFolder.mkdirs()) {
			logColor(RED, "Failed to create data folder for " + worldName);
			return;
		}
		File dataFile = WorldRegistry.stateFile(plugin, worldName);
		long processedChunks = Math.max(task.totalChunksProcessed.sum(), task.submittedChunks.get());
		String data = new StringBuilder(96)
				.append(task.chunkIterator.getCurrentRegionX()).append('_')
				.append(task.chunkIterator.getCurrentRegionZ()).append('_')
				.append(task.chunkIterator.getDirectionIndex()).append('_')
				.append(task.chunkIterator.getStepsRemaining()).append('_')
				.append(task.chunkIterator.getStepsToChange()).append('_')
				.append(task.chunkIterator.getChunkIndex()).append('_')
				.append(processedChunks).append('_')
				.append(task.centerBlockX).append('_')
				.append(task.centerBlockZ)
				.toString();
		try {
			Files.writeString(dataFile.toPath(), data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		} catch (IOException e) {
			e.printStackTrace();
			exceptionMsg("Failed to save processed chunks for " + worldName + ": " + e.getMessage());
		}
	}
}
