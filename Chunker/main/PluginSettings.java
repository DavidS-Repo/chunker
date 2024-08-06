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
	    // Create the plugin data folder if it doesn't exist
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
		config.addDefault("world.autoRun", false);
		config.addDefault("world.SERVERMILLISECOND", 60);
		config.addDefault("world.parallelTasksMultiplier", "default");
		config.addDefault("world.PrintUpdateDelayin", "5s");
		config.addDefault("world.radius", "default");

		// Nether
		config.addDefault("world_nether.autoRun", false);
		config.addDefault("world_nether.SERVERMILLISECOND", 30);
		config.addDefault("world_nether.parallelTasksMultiplier", "default");
		config.addDefault("world_nether.PrintUpdateDelayin", "5s");
		config.addDefault("world_nether.radius", "default");

		// End
		config.addDefault("world_the_end.autoRun", false);
		config.addDefault("world_the_end.SERVERMILLISECOND", 8);
		config.addDefault("world_the_end.parallelTasksMultiplier", "default");
		config.addDefault("world_the_end.PrintUpdateDelayin", "5s");
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
	public static boolean world_autoRun() {
		return config.getBoolean("world.autoRun");
	}

	public static int world_SERVERMILLISECOND() {
		return config.getInt("world.SERVERMILLISECOND");
	}

	public static String world_parallelTasksMultiplier() {
		return config.getString("world.parallelTasksMultiplier");
	}

	public static String world_PrintUpdateDelayin() {
		return config.getString("world.PrintUpdateDelayin");
	}

	public static String world_radius() {
	    return config.getString("world.radius", "default");
	}

	// Nether
	public static boolean world_nether_autoRun() {
		return config.getBoolean("world_nether.autoRun");
	}

	public static int world_nether_SERVERMILLISECOND() {
		return config.getInt("world_nether.SERVERMILLISECOND");
	}

	public static String world_nether_parallelTasksMultiplier() {
		return config.getString("world_nether.parallelTasksMultiplier");
	}

	public static String world_nether_PrintUpdateDelayin() {
		return config.getString("world_nether.PrintUpdateDelayin");
	}

	public static String world_nether_radius() {
	    return config.getString("world_nether.radius", "default"); 
	}

	// End
	public static boolean world_the_end_autoRun() {
		return config.getBoolean("world_the_end.autoRun");
	}

	public static int world_the_end_SERVERMILLISECOND() {
		return config.getInt("world_the_end.SERVERMILLISECOND");
	}

	public static String world_the_end_parallelTasksMultiplier() {
		return config.getString("world_the_end.parallelTasksMultiplier");
	}

	public static String world_the_end_PrintUpdateDelayin() {
		return config.getString("world_the_end.PrintUpdateDelayin");
	}

	public static String world_the_end_radius() {
	    return config.getString("world_the_end.radius", "default");
	}

	public final static int THREADS() {
		return Runtime.getRuntime().availableProcessors();
	}
}