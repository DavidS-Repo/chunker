package main;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import static main.ConsoleColorUtils.*;

/**
 * Handles the /pregen, /pregenoff and /chunker reset [world] commands for chunk pre-generation.
 */
public class PreGeneratorCommands implements CommandExecutor, TabCompleter {
	private final PreGenerator preGenerator;
	private final JavaPlugin plugin;
	private long currentBorderChunks;
	private int delayAmount;
	private long radiusAmount;
	private long targetSideChunks;
	private char delayUnit;
	private char radiusUnit;

	private static final int TICKS_PER_SECOND = 20;
	private static final int TICKS_PER_MINUTE = TICKS_PER_SECOND * 60;
	private static final int TICKS_PER_HOUR   = TICKS_PER_MINUTE * 60;
	private static final long GENERATION_EDGE_COMPENSATION_CHUNKS = 2L;


	private final Set<String> activePreGenWorlds = ConcurrentHashMap.newKeySet();

	private static final String INVALID_INPUT = "Invalid numbers provided.";
	private static final String COMMAND_USAGE = "Usage: /pregen <ParallelTasksMultiplier> <PrintUpdateDelayin(Seconds/Minutes/Hours)> <world> <Radius(Blocks/Chunks/Regions)> [safety]";
	private static final String ENABLED_WARNING = "pre-generator is already enabled.";
	private static final String DISABLED_WARNING = "pre-generator is already disabled.";

	public PreGeneratorCommands(PreGenerator preGenerator, PluginSettings settings, JavaPlugin plugin) {
		this.preGenerator = preGenerator;
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (cmd.getName().equalsIgnoreCase("pregen")) {
			if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
				if (!sender.hasPermission("chunker.reset")) {
					colorMessage(sender, RED, "You do not have permission to reset pre-generation data.");
					return true;
				}
				String world = args[1];
				boolean result = ResetPreGenState.reset(plugin, world);
				if (result) {
					colorMessage(sender, GREEN, "Pregeneration data reset for world: " + world);
				} else {
					colorMessage(sender, RED, "No pregeneration data found for: " + world);
				}
				return true;
			}
			if (args.length == 4 || args.length == 5) {
				handleEnableCommand(sender, args);
			} else {
				colorMessage(sender, RED, COMMAND_USAGE + "\n/pregen reset <world>");
			}
			return true;
		}

		if (label.equalsIgnoreCase("pregenoff")) {
			handleDisableCommand(sender, args);
			return true;
		}

		return false;
	}

	/**
	 * Parses args and turns on pre-gen for a single world.
	 */
	private void handleEnableCommand(CommandSender sender, String[] args) {
		try {
			int threadCount = Integer.parseInt(args[0]);
			if (threadCount <= 0) {
				colorMessage(sender, RED, INVALID_INPUT);
				return;
			}
			int printTicks  = parseDelay(args[1]);
			if (printTicks < 0) {
				colorMessage(sender, RED, INVALID_INPUT);
				return;
			}
			String requestedWorld = args[2];
			World world = resolveWorld(sender, requestedWorld, true);
			if (world == null) {
				colorMessage(sender, RED, "World not found: " + requestedWorld);
				return;
			}
			String worldName = WorldRegistry.id(world);
			if (activePreGenWorlds.contains(worldName)) {
				colorMessage(sender, YELLOW, worldName + " " + ENABLED_WARNING);
				return;
			}
			currentBorderChunks = calculateChunksInBorder(world);

			long chunks = parseRadius(args[3]);
			if (chunks < 0) {
				colorMessage(sender, RED, INVALID_INPUT);
				return;
			}
			boolean forceChunkSafety = parseSafetyMode(sender, args);
			if (args.length == 5 && !forceChunkSafety) {
				return;
			}
			boolean started = preGenerator.enable(
					sender,
					threadCount,
					delayUnit, delayAmount,
					printTicks,
					world,
					chunks,
					targetSideChunks,
					forceChunkSafety
					);
			if (started) {
				activePreGenWorlds.add(worldName);
				colorMessage(sender, GREEN, "pregeneration enabled for " + worldName);
			}
		} catch (NumberFormatException e) {
			colorMessage(sender, RED, INVALID_INPUT);
		}
	}

	/**
	 * Turns off pre-gen for one world or all worlds.
	 */
	public void handleDisableCommand(CommandSender sender, String[] args) {
		if (args.length == 0) {
			if (activePreGenWorlds.isEmpty()) {
				colorMessage(sender, YELLOW, DISABLED_WARNING);
				return;
			}
			String[] activeWorlds = activePreGenWorlds.toArray(String[]::new);
			for (String worldName : activeWorlds) {
				World w = WorldRegistry.resolveWorld(worldName, false);
				if (w != null) {
					preGenerator.disable(sender, w, true);
				}
			}
			activePreGenWorlds.clear();
			colorMessage(sender, RED, "pregeneration disabled for all worlds");
		} else if (args.length == 1) {
			String requestedWorld = args[0];
			World world = WorldRegistry.resolveWorld(requestedWorld, false);
			String worldName = world == null ? requestedWorld : WorldRegistry.id(world);
			if (!activePreGenWorlds.contains(worldName)) {
				colorMessage(sender, YELLOW, worldName + " " + DISABLED_WARNING);
				return;
			}
			if (world == null) {
				colorMessage(sender, RED, "World not found: " + requestedWorld);
				return;
			}
			preGenerator.disable(sender, world, true);
			activePreGenWorlds.remove(worldName);
			colorMessage(sender, RED, "pregeneration disabled for " + worldName);
		} else {
			colorMessage(sender, RED, "Usage: /pregenoff [world]");
		}
	}

	/**
	 * Gets the set of worlds currently running pre-generation.
	 */
	public Set<String> getActivePreGenWorlds() {
		return activePreGenWorlds;
	}

	/**
	 * Clears the set of active pre-generation worlds.
	 */
	public void clearActivePreGenWorlds() {
		activePreGenWorlds.clear();
	}

	/**
	 * Gets the PreGenerator instance for direct control.
	 */
	public PreGenerator getPreGenerator() {
		return preGenerator;
	}

	/**
	 * Converts a delay string (like "5s", "2m", "1h") into server ticks.
	 */
	private int parseDelay(String input) {
		try {
			delayAmount = Integer.parseInt(input.substring(0, input.length() - 1));
			delayUnit   = Character.toLowerCase(input.charAt(input.length() - 1));
			return switch (delayUnit) {
			case 's' -> Math.multiplyExact(delayAmount, TICKS_PER_SECOND);
			case 'm' -> Math.multiplyExact(delayAmount, TICKS_PER_MINUTE);
			case 'h' -> Math.multiplyExact(delayAmount, TICKS_PER_HOUR);
			default -> -1;
			};
		} catch (Exception e) {
			return -1;
		}
	}

	/**
	 * Converts radius input to the chunk side length to request from the server.
	 * Paper/Mojang generation expands about two chunks past requested chunks,
	 * so explicit radii are reduced by two chunks on each edge.
	 */
	private long parseRadius(String input) {
		try {
			if (input.equalsIgnoreCase("default")) {
				targetSideChunks = 0L;
				return currentBorderChunks;
			}
			radiusAmount = Long.parseLong(input.substring(0, input.length() - 1));
			radiusUnit   = Character.toLowerCase(input.charAt(input.length() - 1));

			long requestedSideChunks = switch (radiusUnit) {
			case 'b' -> Math.ceilDiv(Math.multiplyExact(radiusAmount, 2L), 16L);
			case 'c' -> Math.multiplyExact(radiusAmount, 2L);
			case 'r' -> Math.multiplyExact(Math.multiplyExact(radiusAmount, 2L), 32L);
			default -> -1L;
			};
			if (requestedSideChunks < 0L) return -1L;

			targetSideChunks = compensatedSideChunks(requestedSideChunks);
			if (targetSideChunks > Integer.MAX_VALUE) return -1L;
			return chunksForChunkSide(targetSideChunks);
		} catch (Exception e) {
			targetSideChunks = 0L;
			return -1;
		}
	}

	private long compensatedSideChunks(long requestedSideChunks) {
		if (requestedSideChunks <= 0L) return 0L;
		long compensation = Math.multiplyExact(GENERATION_EDGE_COMPENSATION_CHUNKS, 2L);
		return Math.max(1L, Math.subtractExact(requestedSideChunks, compensation));
	}

	private long chunksForChunkSide(long sideChunks) {
		return Math.multiplyExact(sideChunks, sideChunks);
	}

	/**
	 * Auto-loads and kicks off pre-gen for any worlds with auto_run=true.
	 * Now takes a sender so messages go to console or player correctly.
	 */
	public void checkAndRunAutoPreGenerators(CommandSender sender) {
		if (!PluginSettings.isInitialized()) {
			colorMessage(sender, YELLOW, "Settings not initialized, skipping auto-pregeneration");
			return;
		}

		List<String> allWorldNames = getAllWorldNames();

		for (String worldName : allWorldNames) {
			if (PluginSettings.getAutoRun(worldName) && WorldRegistry.resolveWorld(worldName, false) == null) {
				colorMessage(sender, GREEN, "Loading world '" + worldName + "' for auto pregeneration...");
				WorldRegistry.resolveWorld(worldName, true);
			}
		}
		int totalCores = PluginSettings.getAvailableProcessors();
		int autoCount  = 0;
		Object2IntOpenHashMap<String> coresByWorld = new Object2IntOpenHashMap<>();

		for (String name : allWorldNames) {
			if (!PluginSettings.getAutoRun(name)) continue;
			String mult = PluginSettings.getParallelTasksMultiplier(name);
			if (!"auto".equalsIgnoreCase(mult)) {
				try {
					int c = Integer.parseInt(mult);
					coresByWorld.put(name, c);
					totalCores -= c;
				} catch (NumberFormatException e) {
					colorMessage(sender, YELLOW, "Invalid parallel_tasks_multiplier for " + name + ", using auto");
					autoCount++;
				}
			} else {
				autoCount++;
			}
		}
		int coresPerAuto = autoCount > 0 ? Math.max(1, totalCores / autoCount) : 0;
		for (String name : allWorldNames) {
			if (PluginSettings.getAutoRun(name)
					&& "auto".equalsIgnoreCase(PluginSettings.getParallelTasksMultiplier(name))) {
				coresByWorld.put(name, coresPerAuto);
			}
		}
		if (coresByWorld.isEmpty()) return;
		boolean noPlayers = Bukkit.getOnlinePlayers().isEmpty();

		for (Object2IntMap.Entry<String> entry : coresByWorld.object2IntEntrySet()) {
			if (!noPlayers) break;
			String worldName = entry.getKey();
			World world = WorldRegistry.resolveWorld(worldName, false);
			if (world == null) continue;
			currentBorderChunks = calculateChunksInBorder(world);
			String radiusConfig = PluginSettings.getRadius(worldName);
			long chunks = parseRadius(radiusConfig);

			if (chunks <= 0) {
				colorMessage(sender, YELLOW, "Invalid radius for " + worldName + " (got " + chunks + " chunks), skipping");
				continue;
			}

			int printTicks = parseDelay(PluginSettings.getPrintUpdateDelay(worldName));
			if (printTicks <= 0) {
				colorMessage(sender, YELLOW, "Invalid print_update_delay for " + worldName + ", using 5s");
				printTicks = 100;
				delayUnit = 's';
				delayAmount = 5;
			}

			boolean started = preGenerator.enable(
					sender,
					entry.getIntValue(),
					delayUnit, delayAmount,
					printTicks,
					world,
					chunks,
					targetSideChunks,
					false
					);

			if (started) {
				activePreGenWorlds.add(worldName);
				colorMessage(sender, GREEN, "pregeneration enabled for " + worldName + " with " + chunks + " chunks");
			}
		}
	}

	/**
	 * Calculate how many chunks intersect the world border without adding the old edge padding.
	 */
	public long calculateChunksInBorder(World world) {
		WorldBorder border = world.getWorldBorder();
		Location center = border.getCenter();
		double halfBlocks = border.getSize() * 0.5D;

		int minChunkX = chunkAtBlock(center.getX() - halfBlocks);
		int maxChunkX = chunkAtBlock(Math.nextDown(center.getX() + halfBlocks));
		int minChunkZ = chunkAtBlock(center.getZ() - halfBlocks);
		int maxChunkZ = chunkAtBlock(Math.nextDown(center.getZ() + halfBlocks));

		long chunksX = (long) maxChunkX - minChunkX + 1L;
		long chunksZ = (long) maxChunkZ - minChunkZ + 1L;
		return Math.multiplyExact(chunksX, chunksZ);
	}

	private static int chunkAtBlock(double blockCoordinate) {
		return (int) Math.floor(blockCoordinate / 16.0D);
	}

	@Override
	public List<String> onTabComplete(
			CommandSender sender,
			Command command,
			String alias,
			String[] args
			) {
		if (command.getName().equalsIgnoreCase("pregen")) {
			if (args.length == 1) {
				return List.of("<ParallelTasksMultiplier>", "reset");
			}
			if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
				return filterCompletions(getPregeneratorWorlds(plugin), args[1]);
			}
			if (args.length == 2) return List.of("<PrintUpdateDelayin(Seconds/Minutes/Hours)>");
			if (args.length == 3) return filterCompletions(getWorldSuggestions(), args[2]);
			if (args.length == 4) return List.of("<Radius(Blocks/Chunks/Regions)>", "default");
			if (args.length == 5) return filterCompletions(List.of("safety"), args[4]);
		}
		if (command.getName().equalsIgnoreCase("pregenoff")) {
			if (args.length == 1) return filterCompletions(getWorldSuggestions(), args[0]);
		}
		return Collections.emptyList();
	}

	/**
	 * Scans available 26.1+ dimensions.
	 */
	private List<String> getAllWorldNames() {
		return WorldRegistry.discoverWorldIds(plugin);
	}

	private List<String> getWorldSuggestions() {
		return WorldRegistry.worldSuggestions(plugin);
	}

	/**
	 * Scans the plugin's data folder for pregenerator data files and returns corresponding dimension keys.
	 *
	 * @param plugin the JavaPlugin instance
	 * @return a list of dimension keys that have pregenerator data
	 */
	private List<String> getPregeneratorWorlds(JavaPlugin plugin) {
		return WorldRegistry.pregeneratorStateIds(plugin);
	}

	private World resolveWorld(CommandSender sender, String input, boolean loadIfMissing) {
		World world = WorldRegistry.resolveWorld(input, false);
		if (world != null || !loadIfMissing) return world;

		colorMessage(sender, GOLD, "World '" + input + "' not loaded. Loading now...");
		world = WorldRegistry.resolveWorld(input, true);
		if (world != null) {
			colorMessage(sender, GOLD, "World '" + WorldRegistry.id(world) + "' loaded.");
		}
		return world;
	}

	private List<String> filterCompletions(List<String> values, String input) {
		if (input == null || input.isEmpty()) return values;

		String prefix = input.toLowerCase(Locale.ROOT);
		List<String> matches = new ArrayList<>();
		for (String value : values) {
			if (value.toLowerCase(Locale.ROOT).startsWith(prefix)) {
				matches.add(value);
			}
		}
		return matches;
	}

	private boolean parseSafetyMode(CommandSender sender, String[] args) {
		if (args.length == 4) return false;
		if (args[4].equalsIgnoreCase("safety")) return true;

		colorMessage(sender, RED, "Unknown optional argument '" + args[4] + "'. Use 'safety' or omit it.");
		return false;
	}

	/**
	 * Registers the commands (/pregen, /pregenoff, /chunker) with the plugin.
	 */
	public static void registerCommands(JavaPlugin plugin, PreGenerator preGen) {
		PreGeneratorCommands cmds = new PreGeneratorCommands(preGen, new PluginSettings(plugin), plugin);
		plugin.getCommand("pregen").setExecutor(cmds);
		plugin.getCommand("pregen").setTabCompleter(cmds);
		plugin.getCommand("pregenoff").setExecutor(cmds);
		plugin.getCommand("pregenoff").setTabCompleter(cmds);
	}
}