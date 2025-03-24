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

public class ServerStateManager implements Listener {

	private JavaPlugin plugin;
	private PreGeneratorCommands preGenCommands;
	private boolean isFolia;
	// Boolean flag to ensure optimization runs only once when the server is empty.
	private boolean optimized;

	private static final int DSCR = 0, DRTS = 3;
	private static final boolean DDMS = true, DDFT = true;

	public ServerStateManager(JavaPlugin plugin, PreGeneratorCommands preGenCommands) {
		this.plugin = plugin;
		this.preGenCommands = preGenCommands;
		this.isFolia = checkIfFolia();
		this.optimized = false; // Initially, we haven't optimized yet.

		Bukkit.getPluginManager().registerEvents(this, plugin);

		// If the server starts with no players online, optimize once.
		if (areNoPlayersInServer() && !optimized) {
			optimizeForNoPlayers();
		}
	}

	private boolean checkIfFolia() {
		try {
			Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
			logColor(GREEN, "Folia detected: Enabling Folia-specific support.");
			return true;
		} catch (ClassNotFoundException ignored) {
			logColor(YELLOW, "Folia not detected: Running in standard mode.");
			return false;
		}
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		runOnGlobal(() -> {
			// Reset game rules and turn off pre-generation
			resetGameRulesToDefaults();
			preGenCommands.handlePreGenOffCommand(Bukkit.getConsoleSender(), new String[0]);
			// Reset the flag so optimization will run next time all players disconnect.
			optimized = false;
		});
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		// Delay a bit to allow the disconnect to process.
		runTaskLater(() -> {
			if (areNoPlayersInServer() && !optimized) {
				optimizeForNoPlayers();
			}
		}, 20L);
	}

	private void optimizeForNoPlayers() {
		runOnGlobal(() -> {
			logColor(WHITE, "No players detected");
			logColor(WHITE, "Optimizing for Pre-Generation");
			setGameRulesForPerformance();
			unloadAllChunksInAllWorlds();
			preGenCommands.checkAndRunAutoPreGenerators();
			// Mark that optimization has been done.
			optimized = true;
		});
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

	private void unloadAllChunksInAllWorlds() {
		for (World world : Bukkit.getWorlds()) {
			for (Chunk chunk : world.getLoadedChunks()) {
				if (isFolia) {
					Bukkit.getRegionScheduler().execute(plugin, world, chunk.getX(), chunk.getZ(), () -> {
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

	private void runTaskLater(Runnable task, long delay) {
		if (isFolia) {
			plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), delay);
		} else {
			Bukkit.getScheduler().runTaskLater(plugin, task, delay);
		}
	}

	private void runOnGlobal(Runnable task) {
		if (isFolia) {
			plugin.getServer().getGlobalRegionScheduler().execute(plugin, task);
		} else {
			Bukkit.getScheduler().runTask(plugin, task);
		}
	}

	public static void GLI(World world, GameRule<Integer> gameRule, int value) {
		world.setGameRule(gameRule, value);
	}

	public static void GLB(World world, GameRule<Boolean> gameRule, boolean value) {
		world.setGameRule(gameRule, value);
	}
}