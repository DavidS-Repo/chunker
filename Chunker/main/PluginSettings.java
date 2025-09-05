package main;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.io.StringReader;

/**
 * Manages settings.yml by loading, saving, and creating default entries
 * for every world folder detected in the server root.
 */
public class PluginSettings {
	private final JavaPlugin plugin;
	private final CustomConfig configLoader;
	private static YamlConfiguration config;
	private static boolean initialized = false;

	/**
	 * Creates a new PluginSettings handler.
	 *
	 * @param plugin the main plugin instance
	 */
	public PluginSettings(JavaPlugin plugin) {
		this.plugin = plugin;
		this.configLoader = new CustomConfig(plugin, "settings.yml");
		initConfig();
		initialized = true;
	}

	/**
	 * Checks if settings have been fully initialized.
	 *
	 * @return true if settings are loaded and ready
	 */
	public static boolean isInitialized() {
		return initialized && config != null;
	}

	// ensure data folder and settings.yml exist, then load
	private void initConfig() {
		if (!plugin.getDataFolder().exists()) {
			plugin.getDataFolder().mkdirs();
		}
		File settingsFile = new File(plugin.getDataFolder(), "settings.yml");
		if (!settingsFile.exists()) {
			extractDefaultSettings(settingsFile);
		}
		loadSettings();
	}

	// copy bundled settings.yml from JAR into plugin folder
	private void extractDefaultSettings(File destFile) {
		try (InputStream in = plugin.getResource("settings.yml");
				OutputStream out = new FileOutputStream(destFile)) {
			if (in == null) {
				plugin.getLogger().severe("Default settings.yml not found in JAR");
				createEmptyConfig();
				return;
			}
			byte[] buffer = new byte[1024];
			int read;
			while ((read = in.read(buffer)) > 0) {
				out.write(buffer, 0, read);
			}
		} catch (IOException e) {
			plugin.getLogger().severe("Failed to extract default settings.yml");
			e.printStackTrace();
			createEmptyConfig();
		}
	}

	// create empty config if extraction fails
	private void createEmptyConfig() {
		config = new YamlConfiguration();
		populateDefaultsForAllWorlds();
		saveSettings();
	}

	// load YAML into memory, apply defaults, then save back
	private void loadSettings() {
		String raw = configLoader.loadConfig();
		if (raw == null || raw.trim().isEmpty()) {
			config = new YamlConfiguration();
		} else {
			config = YamlConfiguration.loadConfiguration(new StringReader(raw));
		}
		populateDefaultsForAllWorlds();
		config.options().copyDefaults(true);
		saveSettings();
	}

	// for each folder with a level.dat, add default settings if missing
	private void populateDefaultsForAllWorlds() {
		File worldContainer = plugin.getServer().getWorldContainer();
		File[] worldFolders = worldContainer.listFiles();
		if (worldFolders == null) return;

		for (File worldFolder : worldFolders) {
			if (!worldFolder.isDirectory()) continue;
			File levelDat = new File(worldFolder, "level.dat");
			if (!levelDat.exists()) continue;

			String name = worldFolder.getName();
			String basePath = name + ".";
			config.addDefault(basePath + "auto_run", false);
			config.addDefault(basePath + "task_queue_timer", 60);
			config.addDefault(basePath + "parallel_tasks_multiplier", "auto");
			config.addDefault(basePath + "print_update_delay", "5s");
			config.addDefault(basePath + "radius", "default");
		}
	}

	/**
	 * Writes any changes back to settings.yml.
	 */
	public void saveSettings() {
		try {
			File outFile = new File(plugin.getDataFolder(), "settings.yml");
			config.save(outFile);
		} catch (IOException e) {
			plugin.getLogger().severe("Could not save settings.yml");
			e.printStackTrace();
		}
	}

	/**
	 * @return number of CPU cores available to the JVM
	 */
	public static int getAvailableProcessors() {
		return Runtime.getRuntime().availableProcessors();
	}

	/**
	 * @param worldName the name of the world folder
	 * @return whether auto-run is enabled for that world
	 */
	public static boolean getAutoRun(String worldName) {
		if (!isInitialized()) return false;
		return config.getBoolean(worldName + ".auto_run", false);
	}

	/**
	 * @param worldName the name of the world folder
	 * @return configured task queue timer (in ticks)
	 */
	public static int getTaskQueueTimer(String worldName) {
		if (!isInitialized()) return 60;
		return (int) config.getLong(worldName + ".task_queue_timer", 60);
	}

	/**
	 * @param worldName the name of the world folder
	 * @return configured parallel tasks multiplier ("auto" or number)
	 */
	public static String getParallelTasksMultiplier(String worldName) {
		if (!isInitialized()) return "auto";
		return config.getString(worldName + ".parallel_tasks_multiplier", "auto");
	}

	/**
	 * @param worldName the name of the world folder
	 * @return configured print update delay (e.g. "5s")
	 */
	public static String getPrintUpdateDelay(String worldName) {
		if (!isInitialized()) return "5s";
		return config.getString(worldName + ".print_update_delay", "5s");
	}

	/**
	 * @param worldName the name of the world folder
	 * @return configured radius ("default" or value like "10c")
	 */
	public static String getRadius(String worldName) {
		if (!isInitialized()) return "default";
		return config.getString(worldName + ".radius", "default");
	}
}
