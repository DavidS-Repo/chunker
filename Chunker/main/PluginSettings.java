package main;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;

/**
 * Manages settings.yml (world configurations) and optimizations.yml (game rules)
 */
public class PluginSettings {
	private final JavaPlugin plugin;
	private final CustomConfig settingsLoader;
	private final CustomConfig optimizationsLoader;
	private static YamlConfiguration settingsConfig;
	private static YamlConfiguration optimizationsConfig;
	private static boolean initialized = false;

	/**
	 * Represents all configurable game rules with their default values.
	 */
	public enum GameRule {
		SPAWN_CHUNK_RADIUS("spawn_chunk_radius", 0, 2, true),
		RANDOM_TICK_SPEED("random_tick_speed", 0, 3, true),
		DO_MOB_SPAWNING("do_mob_spawning", false, true, true),
		DO_FIRE_TICK("do_fire_tick", false, true, true),
		DO_PATROL_SPAWNING("do_patrol_spawning", false, true, true),
		DO_WARDEN_SPAWNING("do_warden_spawning", false, true, true),
		DO_TRADER_SPAWNING("do_trader_spawning", false, true, true),
		MAX_ENTITY_CRAMMING("max_entity_cramming", 8, 24, true),
		MOB_GRIEFING("mob_griefing", false, true, true),
		DO_INSOMNIA("do_insomnia", false, true, true),
		DO_WEATHER_CYCLE("do_weather_cycle", false, true, true),
		DO_DAYLIGHT_CYCLE("do_daylight_cycle", false, true, true),
		DO_ENTITY_DROPS("do_entity_drops", false, true, true),
		DO_TILE_DROPS("do_tile_drops", false, true, true);

		private final String key;
		private final Object optimizedDefault;
		private final Object normalDefault;
		private final boolean managedByDefault;

		GameRule(String key, Object optimizedDefault, Object normalDefault, boolean managedByDefault) {
			this.key = key;
			this.optimizedDefault = optimizedDefault;
			this.normalDefault = normalDefault;
			this.managedByDefault = managedByDefault;
		}

		public String getManagementKey() { return "game_rules.manage_" + key; }
		public String getOptimizedKey() { return "game_rules.optimized." + key; }
		public String getNormalKey() { return "game_rules.normal." + key; }

		public boolean isManaged() {
			return optimizationsConfig.getBoolean(getManagementKey(), managedByDefault);
		}

		@SuppressWarnings("unchecked")
		public <T> T getOptimizedValue() {
			return (T) optimizationsConfig.get(getOptimizedKey(), optimizedDefault);
		}

		@SuppressWarnings("unchecked")
		public <T> T getNormalValue() {
			return (T) optimizationsConfig.get(getNormalKey(), normalDefault);
		}
	}

	/**
	 * Represents world configuration settings.
	 */
	public record WorldSettings(
			boolean autoRun,
			int taskQueueTimer,
			String parallelTasksMultiplier,
			String printUpdateDelay,
			String radius
			) {
		public static WorldSettings getDefaults() {
			return new WorldSettings(false, 60, "auto", "5s", "default");
		}

		public static WorldSettings forWorld(String worldName) {
			if (!isInitialized()) return getDefaults();

			return new WorldSettings(
					settingsConfig.getBoolean(worldName + ".auto_run", false),
					(int) settingsConfig.getLong(worldName + ".task_queue_timer", 60),
					settingsConfig.getString(worldName + ".parallel_tasks_multiplier", "auto"),
					settingsConfig.getString(worldName + ".print_update_delay", "5s"),
					settingsConfig.getString(worldName + ".radius", "default")
					);
		}
	}

	/**
	 * Creates a new PluginSettings handler.
	 *
	 * @param plugin the main plugin instance
	 */
	public PluginSettings(JavaPlugin plugin) {
		this.plugin = plugin;
		this.settingsLoader = new CustomConfig(plugin, "settings.yml");
		this.optimizationsLoader = new CustomConfig(plugin, "optimizations.yml");
		initConfigs();
		initialized = true;
	}

	/**
	 * Checks if settings have been fully initialized.
	 *
	 * @return true if settings are loaded and ready
	 */
	public static boolean isInitialized() {
		return initialized && settingsConfig != null && optimizationsConfig != null;
	}

	private void initConfigs() {
		if (!plugin.getDataFolder().exists()) {
			plugin.getDataFolder().mkdirs();
		}

		initConfigFile("optimizations.yml", this::createEmptyOptimizationsConfig, this::loadOptimizationsConfig);
		initConfigFile("settings.yml", this::createEmptySettingsConfig, this::loadSettingsConfig);
	}

	private void initConfigFile(String filename, Runnable createEmpty, Runnable loadConfig) {
		File configFile = new File(plugin.getDataFolder(), filename);
		if (!configFile.exists()) {
			extractDefaultFile(filename, configFile, createEmpty);
		}
		loadConfig.run();
	}

	private void extractDefaultFile(String resourceName, File destFile, Runnable fallback) {
		try (InputStream in = plugin.getResource(resourceName);
				OutputStream out = new FileOutputStream(destFile)) {
			if (in == null) {
				plugin.getLogger().severe("Default " + resourceName + " not found in JAR");
				fallback.run();
				return;
			}
			in.transferTo(out);
		} catch (IOException e) {
			plugin.getLogger().severe("Failed to extract default " + resourceName);
			e.printStackTrace();
			fallback.run();
		}
	}

	private void createEmptyOptimizationsConfig() {
		optimizationsConfig = new YamlConfiguration();
		populateGameRuleDefaults();
		saveConfig(optimizationsConfig, "optimizations.yml");
	}

	private void createEmptySettingsConfig() {
		settingsConfig = new YamlConfiguration();
		populateWorldDefaults();
		saveConfig(settingsConfig, "settings.yml");
	}

	private void loadOptimizationsConfig() {
		optimizationsConfig = loadYamlConfig(optimizationsLoader);
		populateGameRuleDefaults();
		optimizationsConfig.options().copyDefaults(true);
		saveConfig(optimizationsConfig, "optimizations.yml");
	}

	private void loadSettingsConfig() {
		settingsConfig = loadYamlConfig(settingsLoader);
		populateWorldDefaults();
		settingsConfig.options().copyDefaults(true);
		saveConfig(settingsConfig, "settings.yml");
	}

	private YamlConfiguration loadYamlConfig(CustomConfig loader) {
		String raw = loader.loadConfig();
		return (raw == null || raw.trim().isEmpty()) 
				? new YamlConfiguration() 
						: YamlConfiguration.loadConfiguration(new StringReader(raw));
	}

	private void populateGameRuleDefaults() {
		for (GameRule rule : GameRule.values()) {
			optimizationsConfig.addDefault(rule.getManagementKey(), rule.managedByDefault);
			optimizationsConfig.addDefault(rule.getOptimizedKey(), rule.optimizedDefault);
			optimizationsConfig.addDefault(rule.getNormalKey(), rule.normalDefault);
		}
	}

	private void populateWorldDefaults() {
		File worldContainer = plugin.getServer().getWorldContainer();
		File[] worldFolders = worldContainer.listFiles();
		if (worldFolders == null) return;

		WorldSettings defaults = WorldSettings.getDefaults();

		for (File worldFolder : worldFolders) {
			if (!worldFolder.isDirectory()) continue;
			if (!new File(worldFolder, "level.dat").exists()) continue;

			String name = worldFolder.getName();
			settingsConfig.addDefault(name + ".auto_run", defaults.autoRun());
			settingsConfig.addDefault(name + ".task_queue_timer", defaults.taskQueueTimer());
			settingsConfig.addDefault(name + ".parallel_tasks_multiplier", defaults.parallelTasksMultiplier());
			settingsConfig.addDefault(name + ".print_update_delay", defaults.printUpdateDelay());
			settingsConfig.addDefault(name + ".radius", defaults.radius());
		}
	}

	private void saveConfig(YamlConfiguration config, String filename) {
		try {
			config.save(new File(plugin.getDataFolder(), filename));
		} catch (IOException e) {
			plugin.getLogger().severe("Could not save " + filename);
			e.printStackTrace();
		}
	}

	// Public API

	/**
	 * @return number of CPU cores available to the JVM
	 */
	public static int getAvailableProcessors() {
		return Runtime.getRuntime().availableProcessors();
	}

	/**
	 * Gets world settings for the specified world.
	 *
	 * @param worldName the name of the world folder
	 * @return world settings record with all configuration values
	 */
	public static WorldSettings getWorldSettings(String worldName) {
		return WorldSettings.forWorld(worldName);
	}

	// Convenience methods for backward compatibility
	public static boolean getAutoRun(String worldName) { return getWorldSettings(worldName).autoRun(); }
	public static int getTaskQueueTimer(String worldName) { return getWorldSettings(worldName).taskQueueTimer(); }
	public static String getParallelTasksMultiplier(String worldName) { return getWorldSettings(worldName).parallelTasksMultiplier(); }
	public static String getPrintUpdateDelay(String worldName) { return getWorldSettings(worldName).printUpdateDelay(); }
	public static String getRadius(String worldName) { return getWorldSettings(worldName).radius(); }

	// Game rule convenience methods
	public static boolean shouldManageSpawnChunkRadius() { return GameRule.SPAWN_CHUNK_RADIUS.isManaged(); }
	public static boolean shouldManageRandomTickSpeed() { return GameRule.RANDOM_TICK_SPEED.isManaged(); }
	public static boolean shouldManageDoMobSpawning() { return GameRule.DO_MOB_SPAWNING.isManaged(); }
	public static boolean shouldManageDoFireTick() { return GameRule.DO_FIRE_TICK.isManaged(); }
	public static boolean shouldManageDoPatrolSpawning() { return GameRule.DO_PATROL_SPAWNING.isManaged(); }
	public static boolean shouldManageDoWardenSpawning() { return GameRule.DO_WARDEN_SPAWNING.isManaged(); }
	public static boolean shouldManageDoTraderSpawning() { return GameRule.DO_TRADER_SPAWNING.isManaged(); }
	public static boolean shouldManageMaxEntityCramming() { return GameRule.MAX_ENTITY_CRAMMING.isManaged(); }
	public static boolean shouldManageMobGriefing() { return GameRule.MOB_GRIEFING.isManaged(); }
	public static boolean shouldManageDoInsomnia() { return GameRule.DO_INSOMNIA.isManaged(); }
	public static boolean shouldManageDoWeatherCycle() { return GameRule.DO_WEATHER_CYCLE.isManaged(); }
	public static boolean shouldManageDoDaylightCycle() { return GameRule.DO_DAYLIGHT_CYCLE.isManaged(); }
	public static boolean shouldManageDoEntityDrops() { return GameRule.DO_ENTITY_DROPS.isManaged(); }
	public static boolean shouldManageDoTileDrops() { return GameRule.DO_TILE_DROPS.isManaged(); }

	public static int getOptimizedSpawnChunkRadius() { return GameRule.SPAWN_CHUNK_RADIUS.getOptimizedValue(); }
	public static int getOptimizedRandomTickSpeed() { return GameRule.RANDOM_TICK_SPEED.getOptimizedValue(); }
	public static boolean getOptimizedDoMobSpawning() { return GameRule.DO_MOB_SPAWNING.getOptimizedValue(); }
	public static boolean getOptimizedDoFireTick() { return GameRule.DO_FIRE_TICK.getOptimizedValue(); }
	public static boolean getOptimizedDoPatrolSpawning() { return GameRule.DO_PATROL_SPAWNING.getOptimizedValue(); }
	public static boolean getOptimizedDoWardenSpawning() { return GameRule.DO_WARDEN_SPAWNING.getOptimizedValue(); }
	public static boolean getOptimizedDoTraderSpawning() { return GameRule.DO_TRADER_SPAWNING.getOptimizedValue(); }
	public static int getOptimizedMaxEntityCramming() { return GameRule.MAX_ENTITY_CRAMMING.getOptimizedValue(); }
	public static boolean getOptimizedMobGriefing() { return GameRule.MOB_GRIEFING.getOptimizedValue(); }
	public static boolean getOptimizedDoInsomnia() { return GameRule.DO_INSOMNIA.getOptimizedValue(); }
	public static boolean getOptimizedDoWeatherCycle() { return GameRule.DO_WEATHER_CYCLE.getOptimizedValue(); }
	public static boolean getOptimizedDoDaylightCycle() { return GameRule.DO_DAYLIGHT_CYCLE.getOptimizedValue(); }
	public static boolean getOptimizedDoEntityDrops() { return GameRule.DO_ENTITY_DROPS.getOptimizedValue(); }
	public static boolean getOptimizedDoTileDrops() { return GameRule.DO_TILE_DROPS.getOptimizedValue(); }

	public static int getNormalSpawnChunkRadius() { return GameRule.SPAWN_CHUNK_RADIUS.getNormalValue(); }
	public static int getNormalRandomTickSpeed() { return GameRule.RANDOM_TICK_SPEED.getNormalValue(); }
	public static boolean getNormalDoMobSpawning() { return GameRule.DO_MOB_SPAWNING.getNormalValue(); }
	public static boolean getNormalDoFireTick() { return GameRule.DO_FIRE_TICK.getNormalValue(); }
	public static boolean getNormalDoPatrolSpawning() { return GameRule.DO_PATROL_SPAWNING.getNormalValue(); }
	public static boolean getNormalDoWardenSpawning() { return GameRule.DO_WARDEN_SPAWNING.getNormalValue(); }
	public static boolean getNormalDoTraderSpawning() { return GameRule.DO_TRADER_SPAWNING.getNormalValue(); }
	public static int getNormalMaxEntityCramming() { return GameRule.MAX_ENTITY_CRAMMING.getNormalValue(); }
	public static boolean getNormalMobGriefing() { return GameRule.MOB_GRIEFING.getNormalValue(); }
	public static boolean getNormalDoInsomnia() { return GameRule.DO_INSOMNIA.getNormalValue(); }
	public static boolean getNormalDoWeatherCycle() { return GameRule.DO_WEATHER_CYCLE.getNormalValue(); }
	public static boolean getNormalDoDaylightCycle() { return GameRule.DO_DAYLIGHT_CYCLE.getNormalValue(); }
	public static boolean getNormalDoEntityDrops() { return GameRule.DO_ENTITY_DROPS.getNormalValue(); }
	public static boolean getNormalDoTileDrops() { return GameRule.DO_TILE_DROPS.getNormalValue(); }
}