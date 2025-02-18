package main;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.*;
import java.util.logging.Level;

/**
 * Handles saving and loading of custom configuration files for the plugin.
 */
public class CustomConfig {
	private final JavaPlugin plugin;
	private final Path configFilePath;

	/**
	 * Constructs a CustomConfig for the specified plugin and file name.
	 *
	 * @param plugin   the JavaPlugin instance
	 * @param fileName the name of the configuration file
	 */
	public CustomConfig(JavaPlugin plugin, String fileName) {
		this.plugin = plugin;
		this.configFilePath = Paths.get(plugin.getDataFolder().getPath(), fileName);
	}

	/**
	 * Saves the given content to the configuration file.
	 *
	 * @param content the content to save
	 */
	public void saveConfig(String content) {
		try (BufferedWriter writer = Files.newBufferedWriter(configFilePath)) {
			writer.write(content);
		} catch (IOException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not save config", e);
		}
	}

	/**
	 * Loads and returns the content of the configuration file.
	 *
	 * @return the content of the config file as a String
	 */
	public String loadConfig() {
		StringBuilder content = new StringBuilder();
		try (BufferedReader reader = Files.newBufferedReader(configFilePath)) {
			String line;
			while ((line = reader.readLine()) != null) {
				content.append(line).append("\n");
			}
		} catch (IOException e) {
			plugin.getLogger().log(Level.SEVERE, "Could not load config", e);
		}
		return content.toString();
	}
}