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
		File dataFolder = plugin.getDataFolder();
		if (!dataFolder.exists() && !dataFolder.mkdirs()) {
			logColor(RED, "Failed to create data folder for " + WorldRegistry.id(task.world));
			return;
		}
		File dataFile = WorldRegistry.stateFile(plugin, task.world);
		long processedChunks = Math.max(task.totalChunksProcessed.sum(), task.submittedChunks.get());
		String data = String.format("%d_%d_%d_%d_%d_%d_%d_%d_%d",
				task.chunkIterator.getCurrentRegionX(),
				task.chunkIterator.getCurrentRegionZ(),
				task.chunkIterator.getDirectionIndex(),
				task.chunkIterator.getStepsRemaining(),
				task.chunkIterator.getStepsToChange(),
				task.chunkIterator.getChunkIndex(),
				processedChunks,
				task.centerBlockX,
				task.centerBlockZ);
		try {
			Files.writeString(dataFile.toPath(), data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		} catch (IOException e) {
			e.printStackTrace();
			exceptionMsg("Failed to save processed chunks for " + WorldRegistry.id(task.world) + ": " + e.getMessage());
		}
	}
}