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
			logColor(RED, "Failed to create data folder for " + task.world.getName());
			return;
		}
		File dataFile = new File(dataFolder, task.world.getName() + "_pregenerator.txt");
		String data = String.format("%d_%d_%d_%d_%d_%d_%d_%d_%d",
				task.chunkIterator.getCurrentRegionX(),
				task.chunkIterator.getCurrentRegionZ(),
				task.chunkIterator.getDirectionIndex(),
				task.chunkIterator.getStepsRemaining(),
				task.chunkIterator.getStepsToChange(),
				task.chunkIterator.getChunkIndex(),
				task.totalChunksProcessed.sum(),
				task.centerBlockX,
				task.centerBlockZ);
		try {
			Files.writeString(dataFile.toPath(), data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		} catch (IOException e) {
			e.printStackTrace();
			exceptionMsg("Failed to save processed chunks for " + task.world.getName() + ": " + e.getMessage());
		}
	}
}
