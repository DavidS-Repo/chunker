package main;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Supplier;

import static main.ConsoleColorUtils.*;

/**
 * Monitors player activity and tweaks server state
 */
public class ServerStateManager implements Listener {
	private final JavaPlugin plugin;
	private final PreGeneratorCommands commands;
	private final TaskScheduler scheduler;
	private boolean optimizationDone;

	/**
	 * Abstraction for task scheduling that handles both Folia and standard Bukkit.
	 */
	private sealed interface TaskScheduler permits FoliaScheduler, BukkitScheduler {
		void scheduleDelayed(Runnable task, long delayTicks);
		void scheduleImmediate(Runnable task);

		static TaskScheduler create(JavaPlugin plugin) {
			return detectFolia() ? new FoliaScheduler(plugin) : new BukkitScheduler(plugin);
		}

		private static boolean detectFolia() {
			try {
				Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
				logColor(GREEN, "Folia detected, enabling support");
				return true;
			} catch (ClassNotFoundException e) {
				logColor(YELLOW, "Folia not detected, running standard mode");
				return false;
			}
		}
	}

	private record FoliaScheduler(JavaPlugin plugin) implements TaskScheduler {
		@Override
		public void scheduleDelayed(Runnable task, long delayTicks) {
			plugin.getServer()
			.getGlobalRegionScheduler()
			.runDelayed(plugin, t -> task.run(), delayTicks);
		}

		@Override
		public void scheduleImmediate(Runnable task) {
			plugin.getServer().getGlobalRegionScheduler().execute(plugin, task);
		}
	}

	private record BukkitScheduler(JavaPlugin plugin) implements TaskScheduler {
		@Override
		public void scheduleDelayed(Runnable task, long delayTicks) {
			Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
		}

		@Override
		public void scheduleImmediate(Runnable task) {
			Bukkit.getScheduler().runTask(plugin, task);
		}
	}

	/**
	 * Maps plugin GameRule enums to Bukkit GameRule objects with their value getters.
	 */
	private enum GameRuleMapping {
		SPAWN_CHUNK_RADIUS(PluginSettings.GameRule.SPAWN_CHUNK_RADIUS, 
				() -> GameRule.SPAWN_CHUNK_RADIUS),
		RANDOM_TICK_SPEED(PluginSettings.GameRule.RANDOM_TICK_SPEED, 
				() -> GameRule.RANDOM_TICK_SPEED),
		DO_MOB_SPAWNING(PluginSettings.GameRule.DO_MOB_SPAWNING, 
				() -> GameRule.DO_MOB_SPAWNING),
		DO_FIRE_TICK(PluginSettings.GameRule.DO_FIRE_TICK, 
				() -> GameRule.DO_FIRE_TICK),
		DO_PATROL_SPAWNING(PluginSettings.GameRule.DO_PATROL_SPAWNING, 
				() -> GameRule.DO_PATROL_SPAWNING),
		DO_WARDEN_SPAWNING(PluginSettings.GameRule.DO_WARDEN_SPAWNING, 
				() -> GameRule.DO_WARDEN_SPAWNING),
		DO_TRADER_SPAWNING(PluginSettings.GameRule.DO_TRADER_SPAWNING, 
				() -> GameRule.DO_TRADER_SPAWNING),
		MAX_ENTITY_CRAMMING(PluginSettings.GameRule.MAX_ENTITY_CRAMMING, 
				() -> GameRule.MAX_ENTITY_CRAMMING),
		MOB_GRIEFING(PluginSettings.GameRule.MOB_GRIEFING, 
				() -> GameRule.MOB_GRIEFING),
		DO_INSOMNIA(PluginSettings.GameRule.DO_INSOMNIA, 
				() -> GameRule.DO_INSOMNIA),
		DO_WEATHER_CYCLE(PluginSettings.GameRule.DO_WEATHER_CYCLE, 
				() -> GameRule.DO_WEATHER_CYCLE),
		DO_DAYLIGHT_CYCLE(PluginSettings.GameRule.DO_DAYLIGHT_CYCLE, 
				() -> GameRule.DO_DAYLIGHT_CYCLE),
		DO_ENTITY_DROPS(PluginSettings.GameRule.DO_ENTITY_DROPS, 
				() -> GameRule.DO_ENTITY_DROPS),
		DO_TILE_DROPS(PluginSettings.GameRule.DO_TILE_DROPS, 
				() -> GameRule.DO_TILE_DROPS);

		private final PluginSettings.GameRule configRule;
		private final Supplier<GameRule<?>> bukkitRule;

		GameRuleMapping(PluginSettings.GameRule configRule, Supplier<GameRule<?>> bukkitRule) {
			this.configRule = configRule;
			this.bukkitRule = bukkitRule;
		}

		/**
		 * Applies optimized or normal values to all worlds based on rule configuration.
		 */
		public void applyToAllWorlds(boolean useOptimized) {
			if (!configRule.isManaged()) return;

			Object value = useOptimized ? configRule.getOptimizedValue() : configRule.getNormalValue();

			for (World world : Bukkit.getWorlds()) {
				setGameRuleValue(world, bukkitRule.get(), value);
			}
		}

		@SuppressWarnings("unchecked")
		private static <T> void setGameRuleValue(World world, GameRule<T> rule, Object value) {
			world.setGameRule(rule, (T) value);
		}
	}

	/**
	 * Sets up server state manager with automatic Folia detection.
	 *
	 * @param plugin   your main plugin instance
	 * @param commands the pre-gen command handler
	 */
	public ServerStateManager(JavaPlugin plugin, PreGeneratorCommands commands) {
		this.plugin = plugin;
		this.commands = commands;
		this.scheduler = TaskScheduler.create(plugin);
		this.optimizationDone = false;

		Bukkit.getPluginManager().registerEvents(this, plugin);

		if (noPlayersOnline()) {
			scheduler.scheduleDelayed(this::optimizeServer, 40L);
		}
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		scheduler.scheduleImmediate(() -> {
			applyGameRules(false); // Apply normal rules
			stopAllPreGeneration();
			optimizationDone = false;
		});
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		scheduler.scheduleDelayed(() -> {
			if (noPlayersOnline() && !optimizationDone) {
				optimizeServer();
			}
		}, 20L);
	}

	private void optimizeServer() {
		if (!PluginSettings.isInitialized()) {
			logColor(YELLOW, "Settings not yet initialized, delaying optimization");
			scheduler.scheduleDelayed(this::optimizeServer, 20L);
			return;
		}

		scheduler.scheduleImmediate(() -> {
			logColor(WHITE, "No players online, optimizing server");
			applyGameRules(true); // Apply optimized rules
			unloadAllChunks();
			commands.checkAndRunAutoPreGenerators(Bukkit.getConsoleSender());
			optimizationDone = true;
		});
	}

	/**
	 * Applies either optimized or normal game rule values to all worlds.
	 *
	 * @param useOptimized true for optimized values, false for normal values
	 */
	private void applyGameRules(boolean useOptimized) {
		for (GameRuleMapping mapping : GameRuleMapping.values()) {
			mapping.applyToAllWorlds(useOptimized);
		}
	}

	private void stopAllPreGeneration() {
		for (String worldName : commands.getActivePreGenWorlds()) {
			World world = Bukkit.getWorld(worldName);
			if (world != null) {
				commands.getPreGenerator().disable(Bukkit.getConsoleSender(), world, false);
			}
		}
		commands.clearActivePreGenWorlds();
	}

	private void unloadAllChunks() {
		for (World world : Bukkit.getWorlds()) {
			for (Chunk chunk : world.getLoadedChunks()) {
				if (scheduler instanceof FoliaScheduler) {
					Bukkit.getRegionScheduler()
					.execute(plugin, world, chunk.getX(), chunk.getZ(), () -> {
						if (chunk.isLoaded()) {
							world.unloadChunk(chunk);
						}
					});
				} else {
					world.unloadChunk(chunk);
				}
			}
		}
	}

	private boolean noPlayersOnline() {
		return Bukkit.getOnlinePlayers().isEmpty();
	}

	/**
	 * Set a GameRule value in a world (kept for backward compatibility).
	 *
	 * @param world the target world
	 * @param rule  the GameRule to change
	 * @param value the new value for that rule
	 * @param <T>   the type of the rule value
	 */
	public static <T> void setGameRule(World world, GameRule<T> rule, T value) {
		world.setGameRule(rule, value);
	}
}