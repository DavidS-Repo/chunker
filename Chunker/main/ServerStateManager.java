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
import org.bukkit.scheduler.BukkitTask;

import static main.ConsoleColorUtils.*;

/**
 * Manages server state by adjusting game rules and optimizing performance based on player activity.
 */
public class ServerStateManager implements Listener {

	private JavaPlugin plugin;
	private BukkitTask playerCheckTask;
	private PreGeneratorCommands preGenCommands;

	private static final int DSCR = 0, DRTS = 3;
	private static final boolean DDMS = true, DDFT = true;

	/**
	 * Constructs a ServerStateManager.
	 *
	 * @param plugin         the JavaPlugin instance
	 * @param preGenCommands the PreGeneratorCommands instance
	 */
	public ServerStateManager(JavaPlugin plugin, PreGeneratorCommands preGenCommands) {
		this.plugin = plugin;
		this.preGenCommands = preGenCommands;
		Bukkit.getPluginManager().registerEvents(this, plugin);
		startPlayerCheckTask();
	}

	/**
	 * Resets game rules to defaults when a player joins and stops pre-generation tasks.
	 *
	 * @param event the player join event
	 */
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		resetGameRulesToDefaults();
		preGenCommands.handlePreGenOffCommand(Bukkit.getConsoleSender(), new String[0]);
	}

	/**
	 * Checks for no players online when a player quits to optimize server performance.
	 *
	 * @param event the player quit event
	 */
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			if (areNoPlayersInServer()) {
				optimizeForNoPlayers();
			}
		}, 20L);
	}

	/**
	 * Starts a repeating task to check for player activity.
	 */
	private void startPlayerCheckTask() {
		playerCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			if (areNoPlayersInServer()) {
				optimizeForNoPlayers();
				if (playerCheckTask != null) {
					playerCheckTask.cancel();
					playerCheckTask = null;
				}
			}
		}, 20L, 20L);
	}

	/**
	 * Optimizes server settings when no players are online.
	 */
	private void optimizeForNoPlayers() {
		logColor(WHITE, "No players detected");
		logColor(WHITE, "Optimizing for Pre-Generation");
		setGameRulesForPerformance();
		unloadAllChunksInAllWorlds();
		preGenCommands.checkAndRunAutoPreGenerators();
	}

	/**
	 * Checks if there are no players on the server.
	 *
	 * @return true if no players are online
	 */
	private boolean areNoPlayersInServer() {
		return Bukkit.getOnlinePlayers().isEmpty();
	}

	/**
	 * Sets game rules to improve performance during pre-generation.
	 */
	private void setGameRulesForPerformance() {
		for (World world : Bukkit.getWorlds()) {
			GLI(world, GameRule.SPAWN_CHUNK_RADIUS, 0);
			GLI(world, GameRule.RANDOM_TICK_SPEED, 0);
			GLB(world, GameRule.DO_MOB_SPAWNING, false);
			GLB(world, GameRule.DO_FIRE_TICK, false);
		}
	}

	/**
	 * Resets game rules to their default values.
	 */
	private void resetGameRulesToDefaults() {
		for (World world : Bukkit.getWorlds()) {
			GLI(world, GameRule.SPAWN_CHUNK_RADIUS, DSCR);
			GLI(world, GameRule.RANDOM_TICK_SPEED, DRTS);
			GLB(world, GameRule.DO_MOB_SPAWNING, DDMS);
			GLB(world, GameRule.DO_FIRE_TICK, DDFT);
		}
	}

	public static void GLI(World world, GameRule<Integer> gameRule, int value) {
		world.setGameRule(gameRule, value);
	}

	public static void GLB(World world, GameRule<Boolean> gameRule, boolean value) {
		world.setGameRule(gameRule, value);
	}

	/**
	 * Unloads all loaded chunks in all worlds.
	 */
	private void unloadAllChunksInAllWorlds() {
		for (World world : Bukkit.getWorlds()) {
			for (Chunk chunk : world.getLoadedChunks()) {
				world.unloadChunk(chunk);
			}
		}
	}
}