package main;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;

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
			.runDelayed(plugin, new ScheduledRunnable(task), delayTicks);
		}

		@Override
		public void scheduleImmediate(Runnable task) {
			plugin.getServer().getGlobalRegionScheduler().execute(plugin, task);
		}
	}

	private record ScheduledRunnable(Runnable task) implements Consumer<ScheduledTask> {
		@Override
		public void accept(ScheduledTask scheduledTask) {
			task.run();
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
		RANDOM_TICK_SPEED(PluginSettings.GameRule.RANDOM_TICK_SPEED, new IntRuleHandler(GameRules.RANDOM_TICK_SPEED)),
		DO_MOB_SPAWNING(PluginSettings.GameRule.DO_MOB_SPAWNING, new BooleanRuleHandler(GameRules.SPAWN_MOBS)),
		DO_FIRE_TICK(PluginSettings.GameRule.DO_FIRE_TICK, new FireTickRuleHandler(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER)),
		DO_PATROL_SPAWNING(PluginSettings.GameRule.DO_PATROL_SPAWNING, new BooleanRuleHandler(GameRules.SPAWN_PATROLS)),
		DO_WARDEN_SPAWNING(PluginSettings.GameRule.DO_WARDEN_SPAWNING, new BooleanRuleHandler(GameRules.SPAWN_WARDENS)),
		DO_TRADER_SPAWNING(PluginSettings.GameRule.DO_TRADER_SPAWNING, new BooleanRuleHandler(GameRules.SPAWN_WANDERING_TRADERS)),
		MAX_ENTITY_CRAMMING(PluginSettings.GameRule.MAX_ENTITY_CRAMMING, new IntRuleHandler(GameRules.MAX_ENTITY_CRAMMING)),
		MOB_GRIEFING(PluginSettings.GameRule.MOB_GRIEFING, new BooleanRuleHandler(GameRules.MOB_GRIEFING)),
		DO_INSOMNIA(PluginSettings.GameRule.DO_INSOMNIA, new BooleanRuleHandler(GameRules.SPAWN_PHANTOMS)),
		DO_WEATHER_CYCLE(PluginSettings.GameRule.DO_WEATHER_CYCLE, new BooleanRuleHandler(GameRules.ADVANCE_WEATHER)),
		DO_DAYLIGHT_CYCLE(PluginSettings.GameRule.DO_DAYLIGHT_CYCLE, new BooleanRuleHandler(GameRules.ADVANCE_TIME)),
		DO_ENTITY_DROPS(PluginSettings.GameRule.DO_ENTITY_DROPS, new BooleanRuleHandler(GameRules.ENTITY_DROPS)),
		DO_TILE_DROPS(PluginSettings.GameRule.DO_TILE_DROPS, new BooleanRuleHandler(GameRules.BLOCK_DROPS));

		private final PluginSettings.GameRule configRule;
		private final RuleHandler handler;

		GameRuleMapping(PluginSettings.GameRule configRule, RuleHandler handler) {
			this.configRule = configRule;
			this.handler = handler;
		}

		/**
		 * Applies optimized or normal values to all worlds based on rule configuration.
		 */
		public void applyToAllWorlds(boolean useOptimized) {
			if (!configRule.isManaged()) return;

			Object rawValue = useOptimized ? configRule.getOptimizedValue() : configRule.getNormalValue();

			for (World world : Bukkit.getWorlds()) {
				handler.apply(world, rawValue);
			}
		}
	}

	private interface RuleHandler {
		void apply(World world, Object rawValue);
	}

	private static final class BooleanRuleHandler implements RuleHandler {
		private final GameRule<Boolean> rule;

		private BooleanRuleHandler(GameRule<Boolean> rule) {
			this.rule = rule;
		}

		@Override
		public void apply(World world, Object rawValue) {
			Boolean b = toBoolean(rawValue);
			if (b == null) return;
			world.setGameRule(rule, b);
		}
	}

	private static class IntRuleHandler implements RuleHandler {
		protected final GameRule<Integer> rule;

		private IntRuleHandler(GameRule<Integer> rule) {
			this.rule = rule;
		}

		@Override
		public void apply(World world, Object rawValue) {
			Integer i = toInteger(rawValue);
			if (i == null) return;
			world.setGameRule(rule, i);
		}
	}

	private static final class FireTickRuleHandler extends IntRuleHandler {
		private FireTickRuleHandler(GameRule<Integer> rule) {
			super(rule);
		}

		@Override
		public void apply(World world, Object rawValue) {
			if (rawValue instanceof Boolean b) {
				if (!b) {
					world.setGameRule(rule, 0);
					return;
				}

				Integer def = rule.getDefaultValue();
				if (def != null) {
					world.setGameRule(rule, def);
					return;
				}

				Integer cur = world.getGameRuleValue(rule);
				if (cur != null) {
					world.setGameRule(rule, cur);
					return;
				}

				world.setGameRule(rule, 0);
				return;
			}

			super.apply(world, rawValue);
		}
	}

	private static Boolean toBoolean(Object v) {
		if (v instanceof Boolean b) return b;
		if (v instanceof Number n) return n.intValue() != 0;

		if (v instanceof String s) {
			String t = s.trim();
			if (t.equalsIgnoreCase("true")) return true;
			if (t.equalsIgnoreCase("false")) return false;
			if (t.equals("1")) return true;
			if (t.equals("0")) return false;
		}
		return null;
	}

	private static Integer toInteger(Object v) {
		if (v instanceof Integer i) return i;
		if (v instanceof Number n) return n.intValue();

		if (v instanceof String s) {
			try {
				return Integer.parseInt(s.trim());
			} catch (NumberFormatException ignored) {
				return null;
			}
		}
		return null;
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
			World world = WorldRegistry.resolveWorld(worldName, false);
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