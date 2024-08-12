package main;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;

public class PluginSettings {
	private final JavaPlugin plugin;
	private final CustomConfig customConfig;
	private static YamlConfiguration config;

	public PluginSettings(JavaPlugin plugin) {
		this.plugin = plugin;
		this.customConfig = new CustomConfig(plugin, "settings.yml");
		initializeConfig();
	}

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

	private void loadConfig() {
		String configContent = customConfig.loadConfig();
		config = YamlConfiguration.loadConfiguration(new StringReader(configContent));
		loadDefaults();
	}

	private void loadDefaults() {
		// Overworld
		config.addDefault("world.auto_run", false);
		config.addDefault("world.task_queue_timer", 60);
		config.addDefault("world.parallel_tasks_multiplier", "auto");
		config.addDefault("world.print_update_delay", "5s");
		config.addDefault("world.radius", "default");

		// Nether
		config.addDefault("world_nether.auto_run", false);
		config.addDefault("world_nether.task_queue_timer", 30);
		config.addDefault("world_nether.parallel_tasks_multiplier", "auto");
		config.addDefault("world_nether.print_update_delay", "5s");
		config.addDefault("world_nether.radius", "default");

		// End
		config.addDefault("world_the_end.auto_run", false);
		config.addDefault("world_the_end.task_queue_timer", 15);
		config.addDefault("world_the_end.parallel_tasks_multiplier", "auto");
		config.addDefault("world_the_end.print_update_delay", "5s");
		config.addDefault("world_the_end.radius", "default");

		config.options().copyDefaults(true);
		saveConfig();
	}

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

	// Overworld
	public static boolean world_auto_run() {
		return config.getBoolean("world.auto_run");
	}

	public static int world_task_queue_timer() {
		return config.getInt("world.task_queue_timer");
	}

	public static String world_parallel_tasks_multiplier() {
		return config.getString("world.parallel_tasks_multiplier");
	}

	public static String world_print_update_delay() {
		return config.getString("world.print_update_delay");
	}

	public static String world_radius() {
		return config.getString("world.radius", "default");
	}

	// Nether
	public static boolean world_nether_auto_run() {
		return config.getBoolean("world_nether.auto_run");
	}

	public static int world_nether_task_queue_timer() {
		return config.getInt("world_nether.task_queue_timer");
	}

	public static String world_nether_parallel_tasks_multiplier() {
		return config.getString("world_nether.parallel_tasks_multiplier");
	}

	public static String world_nether_print_update_delay() {
		return config.getString("world_nether.print_update_delay");
	}

	public static String world_nether_radius() {
		return config.getString("world_nether.radius", "default"); 
	}

	// End
	public static boolean world_the_end_auto_run() {
		return config.getBoolean("world_the_end.auto_run");
	}

	public static int world_the_end_task_queue_timer() {
		return config.getInt("world_the_end.task_queue_timer");
	}

	public static String world_the_end_parallel_tasks_multiplier() {
		return config.getString("world_the_end.parallel_tasks_multiplier");
	}

	public static String world_the_end_print_update_delay() {
		return config.getString("world_the_end.print_update_delay");
	}

	public static String world_the_end_radius() {
		return config.getString("world_the_end.radius", "default");
	}

	public final static int THREADS() {
		return Runtime.getRuntime().availableProcessors();
	}
}
