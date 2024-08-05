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

public class ServerStateManager implements Listener {

	private JavaPlugin plugin;
	private BukkitTask playerCheckTask;

	private static final int 
	DSCR = 0, DRTS = 3;
	private static final boolean 
	DDMS = true, DDFT = true;

	public ServerStateManager(JavaPlugin plugin) {
		this.plugin = plugin;
		Bukkit.getPluginManager().registerEvents(this, plugin);
		startPlayerCheckTask();
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		resetGameRulesToDefaults();
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			if (areNoPlayersInServer()) {
				optimizeForNoPlayers();
			}
		}, 20L);
	}

	private void startPlayerCheckTask() {
		playerCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			if (areNoPlayersInServer()) {
				optimizeForNoPlayers();
				if (playerCheckTask != null) {
					playerCheckTask.cancel();
					playerCheckTask = null;
				}
			}
		}, 0L, 20L);
	}

	private void optimizeForNoPlayers() {
		cC.logS(cC.WHITE, "No players detected");
		cC.logS(cC.WHITE, "Optimizing for Pre-Generation");
				setGameRulesForPerformance();
				unloadAllChunksInAllWorlds();
	}

	private boolean areNoPlayersInServer() {
		return Bukkit.getOnlinePlayers().isEmpty();
	}

	private void setGameRulesForPerformance() {
		for (World world : Bukkit.getWorlds()) {
			GLI(world, GameRule.SPAWN_CHUNK_RADIUS, 0);
			GLI(world, GameRule.RANDOM_TICK_SPEED, 0);
			GLB(world, GameRule.DO_MOB_SPAWNING, false);
			GLB(world, GameRule.DO_FIRE_TICK, false);
		}
	}

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

	private void unloadAllChunksInAllWorlds() {
		for (World world : Bukkit.getWorlds()) {
			for (Chunk chunk : world.getLoadedChunks()) {
				if (world.unloadChunk(chunk)) {
				}
			}
		}
	}
}
