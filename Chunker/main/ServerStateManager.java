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

import static main.ConsoleColorUtils.*;

/**
 * Monitors player activity and tweaks server state.
 * When no one’s online it unloads chunks, adjusts game rules,
 * and kicks off any auto pre-generation tasks.
 */
public class ServerStateManager implements Listener {
	private final JavaPlugin plugin;
	private final PreGeneratorCommands commands;
	private final boolean foliaDetected;
	private boolean optimizationDone;

	private static final int DEFAULT_SPAWN_CHUNK_RADIUS = 0;
	private static final int DEFAULT_RANDOM_TICK_SPEED = 3;
	private static final boolean DEFAULT_MOB_SPAWNING = true;
	private static final boolean DEFAULT_FIRE_TICK = true;

	/**
	 * Sets up server state manager.
	 *
	 * @param plugin   your main plugin instance
	 * @param commands the pre-gen command handler
	 */
	public ServerStateManager(JavaPlugin plugin,
			PreGeneratorCommands commands) {
		this.plugin = plugin;
		this.commands = commands;
		this.foliaDetected = detectFolia();
		this.optimizationDone = false;

		Bukkit.getPluginManager().registerEvents(this, plugin);

		// if server starts empty, run optimization immediately
		if (noPlayersOnline()) {
			optimizeServer();
		}
	}

	// check if we're on a Folia server (threaded regions)
	private boolean detectFolia() {
		try {
			Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
			logColor(GREEN, "Folia detected, enabling support");
			return true;
		} catch (ClassNotFoundException e) {
			logColor(YELLOW, "Folia not detected, running standard mode");
			return false;
		}
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		// reset rules and stop pregeneration when someone joins
		scheduleImmediate(() -> {
			resetGameRules();
			commands.handlePreGenOffCommand(Bukkit.getConsoleSender(), new String[0]);
			optimizationDone = false;
		});
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		// after a short delay, if server is empty, re-optimize
		scheduleDelayed(() -> {
			if (noPlayersOnline() && !optimizationDone) {
				optimizeServer();
			}
		}, 20L);
	}

	// core optimization steps when no players are online
	private void optimizeServer() {
		scheduleImmediate(() -> {
			logColor(WHITE, "No players online, optimizing server");
			setPerformanceGameRules();
			unloadAllChunks();
			commands.checkAndRunAutoPreGenerators();
			optimizationDone = true;
		});
	}

	// true when no one is connected
	private boolean noPlayersOnline() {
		return Bukkit.getOnlinePlayers().isEmpty();
	}

	// turn off random ticks, mob spawning, fire tick, spawn chunks
	private void setPerformanceGameRules() {
		for (World world : Bukkit.getWorlds()) {
			setGameRule(world, GameRule.SPAWN_CHUNK_RADIUS, 0);
			setGameRule(world, GameRule.RANDOM_TICK_SPEED, 0);
			setGameRule(world, GameRule.DO_MOB_SPAWNING, false);
			setGameRule(world, GameRule.DO_FIRE_TICK, false);
		}
	}

	// restore defaults for spawn chunks, ticks, mob spawning, fire tick
	private void resetGameRules() {
		for (World world : Bukkit.getWorlds()) {
			setGameRule(world, GameRule.SPAWN_CHUNK_RADIUS, DEFAULT_SPAWN_CHUNK_RADIUS);
			setGameRule(world, GameRule.RANDOM_TICK_SPEED, DEFAULT_RANDOM_TICK_SPEED);
			setGameRule(world, GameRule.DO_MOB_SPAWNING, DEFAULT_MOB_SPAWNING);
			setGameRule(world, GameRule.DO_FIRE_TICK, DEFAULT_FIRE_TICK);
		}
	}

	// unload all currently loaded chunks in every world
	private void unloadAllChunks() {
		for (World world : Bukkit.getWorlds()) {
			for (Chunk chunk : world.getLoadedChunks()) {
				if (foliaDetected) {
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

	/**
	 * Schedule a task after a delay (in server ticks).
	 *
	 * @param task      what to run
	 * @param delayTicks how many ticks to wait
	 */
	private void scheduleDelayed(Runnable task, long delayTicks) {
		if (foliaDetected) {
			plugin.getServer()
			.getGlobalRegionScheduler()
			.runDelayed(plugin, t -> task.run(), delayTicks);
		} else {
			Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
		}
	}

	/**
	 * Schedule a task to run right away on the main thread.
	 *
	 * @param task what to run
	 */
	private void scheduleImmediate(Runnable task) {
		if (foliaDetected) {
			plugin.getServer().getGlobalRegionScheduler().execute(plugin, task);
		} else {
			Bukkit.getScheduler().runTask(plugin, task);
		}
	}

	/**
	 * Set a GameRule value in a world.
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