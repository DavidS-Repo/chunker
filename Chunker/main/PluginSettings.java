package main;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;

/**
 * Manages plugin settings by loading and saving configurations from a YAML file.
 */
public class PluginSettings {
	private final JavaPlugin plugin;
	private final CustomConfig customConfig;
	private static YamlConfiguration config;

	/**
	 * Initializes the PluginSettings with the given plugin instance.
	 *
	 * @param plugin the JavaPlugin instance
	 */
	public PluginSettings(JavaPlugin plugin) {
		this.plugin = plugin;
		this.customConfig = new CustomConfig(plugin, "settings.yml");
		initializeConfig();
	}

	/**
	 * Initializes the configuration by loading or creating the settings file.
	 */
	private void initializeConfig() {
		if (!plugin.getDataFolder().exists()) {
			plugin.getDataFolder().mkdirs();
		}
		File configFile = new File(plugin.getDataFolder(), "settings.yml");
		if (!configFile.exists()) {
			extractDefaultConfig(configFile);
		}
		loadConfig();
	}

	/**
	 * Extracts the default settings.yml from the plugin JAR to the data folder.
	 *
	 * @param configFile the target configuration file
	 */
	private void extractDefaultConfig(File configFile) {
		try (InputStream inputStream = plugin.getResource("settings.yml")) {
			if (inputStream == null) {
				throw new FileNotFoundException("Default settings.yml not found in JAR.");
			}
			try (OutputStream outputStream = new FileOutputStream(configFile)) {
				byte[] buffer = new byte[1024];
				int length;
				while ((length = inputStream.read(buffer)) != -1) {
					outputStream.write(buffer, 0, length);
				}
			}
		} catch (IOException e) {
			plugin.getLogger().severe("Could not extract default settings.yml!");
			e.printStackTrace();
		}
	}

	/**
	 * Loads the configuration from the settings file.
	 */
	private void loadConfig() {
		String configContent = customConfig.loadConfig();
		config = YamlConfiguration.loadConfiguration(new StringReader(configContent));
		loadDefaults();
	}

	/**
	 * Loads default values into the configuration if they are not set.
	 */
	private void loadDefaults() {
		// Overworld settings
		config.addDefault("world.auto_run", false);
		config.addDefault("world.task_queue_timer", 60);
		config.addDefault("world.parallel_tasks_multiplier", "auto");
		config.addDefault("world.print_update_delay", "5s");
		config.addDefault("world.radius", "default");

		// Nether settings
		config.addDefault("world_nether.auto_run", false);
		config.addDefault("world_nether.task_queue_timer", 60);
		config.addDefault("world_nether.parallel_tasks_multiplier", "auto");
		config.addDefault("world_nether.print_update_delay", "5s");
		config.addDefault("world_nether.radius", "default");

		// End settings
		config.addDefault("world_the_end.auto_run", false);
		config.addDefault("world_the_end.task_queue_timer", 60);
		config.addDefault("world_the_end.parallel_tasks_multiplier", "auto");
		config.addDefault("world_the_end.print_update_delay", "5s");
		config.addDefault("world_the_end.radius", "default");

		config.options().copyDefaults(true);
		saveConfig();
	}

	/**
	 * Saves the configuration to the settings file.
	 */
	public void saveConfig() {
		try {
			File tempFile = File.createTempFile("temp", ".yml");
			config.save(tempFile);
			StringWriter writer = new StringWriter();
			BufferedReader reader = new BufferedReader(new FileReader(tempFile));
			String line;
			while ((line = reader.readLine()) != null) {
				writer.write(line);
				writer.write(System.lineSeparator());
			}
			reader.close();
			customConfig.saveConfig(writer.toString());
			tempFile.delete();
		} catch (Exception e) {
			plugin.getLogger().severe("Could not save the settings.yml file!");
			e.printStackTrace();
		}
	}

	/**
	 * Returns the number of available processor threads.
	 *
	 * @return the number of processor threads
	 */
	public static int THREADS() {
		return Runtime.getRuntime().availableProcessors();
	}

	// Overworld getters

	/**
	 * Checks if auto-run is enabled for the overworld.
	 *
	 * @return true if auto-run is enabled
	 */
	public static boolean world_auto_run() {
		return config.getBoolean("world.auto_run");
	}

	/**
	 * Gets the task queue timer for the overworld.
	 *
	 * @return the task queue timer value
	 */
	public static long world_task_queue_timer() {
		return config.getLong("world.task_queue_timer");
	}

	/**
	 * Gets the parallel tasks multiplier for the overworld.
	 *
	 * @return the parallel tasks multiplier
	 */
	public static String world_parallel_tasks_multiplier() {
		return config.getString("world.parallel_tasks_multiplier");
	}

	/**
	 * Gets the print update delay for the overworld.
	 *
	 * @return the print update delay
	 */
	public static String world_print_update_delay() {
		return config.getString("world.print_update_delay");
	}

	/**
	 * Gets the radius setting for the overworld.
	 *
	 * @return the radius setting
	 */
	public static String world_radius() {
		return config.getString("world.radius", "default");
	}

	// Nether getters

	/**
	 * Checks if auto-run is enabled for the nether.
	 *
	 * @return true if auto-run is enabled
	 */
	public static boolean world_nether_auto_run() {
		return config.getBoolean("world_nether.auto_run");
	}

	/**
	 * Gets the task queue timer for the nether.
	 *
	 * @return the task queue timer value
	 */
	public static long world_nether_task_queue_timer() {
		return config.getLong("world_nether.task_queue_timer");
	}

	/**
	 * Gets the parallel tasks multiplier for the nether.
	 *
	 * @return the parallel tasks multiplier
	 */
	public static String world_nether_parallel_tasks_multiplier() {
		return config.getString("world_nether.parallel_tasks_multiplier");
	}

	/**
	 * Gets the print update delay for the nether.
	 *
	 * @return the print update delay
	 */
	public static String world_nether_print_update_delay() {
		return config.getString("world_nether.print_update_delay");
	}

	/**
	 * Gets the radius setting for the nether.
	 *
	 * @return the radius setting
	 */
	public static String world_nether_radius() {
		return config.getString("world_nether.radius", "default");
	}

	// End getters

	/**
	 * Checks if auto-run is enabled for the end.
	 *
	 * @return true if auto-run is enabled
	 */
	public static boolean world_the_end_auto_run() {
		return config.getBoolean("world_the_end.auto_run");
	}

	/**
	 * Gets the task queue timer for the end.
	 *
	 * @return the task queue timer value
	 */
	public static long world_the_end_task_queue_timer() {
		return config.getLong("world_the_end.task_queue_timer");
	}

	/**
	 * Gets the parallel tasks multiplier for the end.
	 *
	 * @return the parallel tasks multiplier
	 */
	public static String world_the_end_parallel_tasks_multiplier() {
		return config.getString("world_the_end.parallel_tasks_multiplier");
	}

	/**
	 * Gets the print update delay for the end.
	 *
	 * @return the print update delay
	 */
	public static String world_the_end_print_update_delay() {
		return config.getString("world_the_end.print_update_delay");
	}

	/**
	 * Gets the radius setting for the end.
	 *
	 * @return the radius setting
	 */
	public static String world_the_end_radius() {
		return config.getString("world_the_end.radius", "default");
	}
}