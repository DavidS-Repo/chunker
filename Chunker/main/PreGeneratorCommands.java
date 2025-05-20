package main;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import static main.ConsoleColorUtils.*;

/**
 * Handles the /pregen, /pregenoff and /chunker reset [world] commands for chunk pre-generation.
 */
public class PreGeneratorCommands implements CommandExecutor, TabCompleter {
	private final PreGenerator preGenerator;
	private final JavaPlugin plugin; // Needed for data folder/location
	private long currentBorderChunks;
	private int delayAmount;
	private long radiusAmount;
	private char delayUnit;
	private char radiusUnit;

	private static final int TICKS_PER_SECOND = 20;
	private static final int TICKS_PER_MINUTE = TICKS_PER_SECOND * 60;
	private static final int TICKS_PER_HOUR   = TICKS_PER_MINUTE * 60;

	// keep track of which worlds have active pre-gen
	private final Set<String> activePreGenWorlds = new HashSet<>();

	private static final String INVALID_INPUT = "Invalid numbers provided.";
	private static final String COMMAND_USAGE = "Usage: /pregen <ParallelTasksMultiplier> <PrintUpdateDelayin(Seconds/Minutes/Hours)> <world> <Radius(Blocks/Chunks/Regions)>";
	private static final String ENABLED_WARNING = "pre-generator is already enabled.";
	private static final String DISABLED_WARNING = "pre-generator is already disabled.";

	public PreGeneratorCommands(PreGenerator preGenerator, PluginSettings settings, JavaPlugin plugin) {
		this.preGenerator = preGenerator;
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		// ----- /pregen reset [world] -----
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
			// ----- normal pregen -----
			if (args.length == 4) {
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
			int printTicks  = parseDelay(args[1]);
			if (printTicks < 0) {
				colorMessage(sender, RED, INVALID_INPUT);
				return;
			}
			String worldName = args[2];
			if (activePreGenWorlds.contains(worldName)) {
				colorMessage(sender, YELLOW, worldName + " " + ENABLED_WARNING);
				return;
			}
			World world = Bukkit.getWorld(worldName);
			if (world == null) {
				colorMessage(sender, GOLD, "World '" + worldName + "' not loaded. Loading now...");
				world = new WorldCreator(worldName).createWorld();
				if (world != null) {
					colorMessage(sender, GOLD, "World '" + worldName + "' loaded.");
				}
			}
			if (world == null) {
				colorMessage(sender, RED, "World not found: " + worldName);
				return;
			}
			currentBorderChunks = calculateChunksInBorder(world);

			long chunks = parseRadius(args[3]);
			if (chunks < 0) {
				colorMessage(sender, RED, INVALID_INPUT);
				return;
			}

			preGenerator.enable(
					sender,
					threadCount,
					delayUnit, delayAmount,
					printTicks,
					world,
					chunks
					);
			activePreGenWorlds.add(worldName);
			colorMessage(sender, GREEN, "pregeneration enabled for " + worldName);

		} catch (NumberFormatException e) {
			colorMessage(sender, RED, INVALID_INPUT);
		}
	}

	/**
	 * Turns off pre-gen for one world or all worlds.
	 */
	public void handleDisableCommand(CommandSender sender, String[] args) {
		// global off
		if (args.length == 0) {
			if (activePreGenWorlds.isEmpty()) {
				colorMessage(sender, YELLOW, DISABLED_WARNING);
				return;
			}
			for (String worldName : activePreGenWorlds) {
				World w = Bukkit.getWorld(worldName);
				if (w != null) {
					preGenerator.disable(sender, w);
				}
			}
			activePreGenWorlds.clear();
			colorMessage(sender, RED, "pregeneration disabled for all worlds");

			// single-world off
		} else if (args.length == 1) {
			String worldName = args[0];
			if (!activePreGenWorlds.contains(worldName)) {
				colorMessage(sender, YELLOW, worldName + " " + DISABLED_WARNING);
				return;
			}
			World world = Bukkit.getWorld(worldName);
			if (world == null) {
				colorMessage(sender, RED, "World not found: " + worldName);
				return;
			}
			preGenerator.disable(sender, world);
			activePreGenWorlds.remove(worldName);
			colorMessage(sender, RED, "pregeneration disabled for " + worldName);

		} else {
			colorMessage(sender, RED, "Usage: /pregenoff [world]");
		}
	}

	/**
	 * Converts a delay string (like "5s", "2m", "1h") into server ticks.
	 */
	private int parseDelay(String input) {
		try {
			delayAmount = Integer.parseInt(input.substring(0, input.length() - 1));
			delayUnit   = Character.toLowerCase(input.charAt(input.length() - 1));
			switch (delayUnit) {
			case 's': return delayAmount * TICKS_PER_SECOND;
			case 'm': return delayAmount * TICKS_PER_MINUTE;
			case 'h': return delayAmount * TICKS_PER_HOUR;
			default:  return -1;
			}
		} catch (Exception e) {
			return -1;
		}
	}

	/**
	 * Converts a radius arg ("default", "10b", "5c", "2r") into a chunk count.
	 */
	private long parseRadius(String input) {
		try {
			if (input.equalsIgnoreCase("default")) {
				return currentBorderChunks;
			}
			radiusAmount = Long.parseLong(input.substring(0, input.length() - 1));
			radiusUnit   = Character.toLowerCase(input.charAt(input.length() - 1));
			switch (radiusUnit) {
			case 'b': // blocks -> chunks^2
				long sideChunks = radiusAmount / 16;
				return sideChunks * sideChunks;
			case 'c': // chunks -> chunks^2
				return radiusAmount * radiusAmount;
			case 'r': // regions (32 chunks) -> (32n)^2
				long side = radiusAmount * 32;
				return side * side;
			default:
				return -1;
			}
		} catch (Exception e) {
			return -1;
		}
	}

	/**
	 * Auto-loads and kicks off pre-gen for any worlds with auto_run=true.
	 * Now takes a sender so messages go to console or player correctly.
	 */
	public void checkAndRunAutoPreGenerators(CommandSender sender) {
		for (String worldName : getAllWorldNames()) {
			if (PluginSettings.getAutoRun(worldName) && Bukkit.getWorld(worldName) == null) {
				colorMessage(sender, GREEN, "Loading world '" + worldName + "' for auto pregeneration...");
				new WorldCreator(worldName).createWorld();
			}
		}
		int totalCores = PluginSettings.getAvailableProcessors();
		int autoCount  = 0;
		Map<String, Integer> coresByWorld = new HashMap<>();

		// collect fixed cores
		for (String name : getAllWorldNames()) {
			if (!PluginSettings.getAutoRun(name)) continue;
			String mult = PluginSettings.getParallelTasksMultiplier(name);
			if (!"auto".equalsIgnoreCase(mult)) {
				int c = Integer.parseInt(mult);
				coresByWorld.put(name, c);
				totalCores -= c;
			} else {
				autoCount++;
			}
		}
		// split remaining cores
		int coresPerAuto = autoCount > 0 ? totalCores / autoCount : 0;
		for (String name : getAllWorldNames()) {
			if (PluginSettings.getAutoRun(name)
					&& "auto".equalsIgnoreCase(PluginSettings.getParallelTasksMultiplier(name))) {
				coresByWorld.put(name, coresPerAuto);
			}
		}
		if (coresByWorld.isEmpty()) return;
		boolean noPlayers = Bukkit.getOnlinePlayers().isEmpty();

		// kick off each world
		for (Map.Entry<String, Integer> entry : coresByWorld.entrySet()) {
			if (!noPlayers) break;
			String worldName = entry.getKey();
			World world = Bukkit.getWorld(worldName);
			if (world == null) continue;

			// recalc border and radius
			currentBorderChunks = calculateChunksInBorder(world);
			int printTicks      = parseDelay(PluginSettings.getPrintUpdateDelay(worldName));
			long chunks         = parseRadius(PluginSettings.getRadius(worldName));

			preGenerator.enable(
					sender,
					entry.getValue(),
					delayUnit, delayAmount,
					printTicks,
					world,
					chunks
					);
			activePreGenWorlds.add(worldName);

			colorMessage(sender, GREEN, "pregeneration enabled for " + worldName);
		}
	}

	/**
	 * Calculate how many chunks fit inside the world border.
	 */
	public long calculateChunksInBorder(World world) {
		WorldBorder border = world.getWorldBorder();
		double diameter   = border.getSize();
		double halfBlocks = diameter / 2.0;
		double halfChunks = Math.ceil(halfBlocks / 16.0);
		return (long) Math.pow(halfChunks * 2 + 1, 2);
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
				return Arrays.asList("<ParallelTasksMultiplier>", "reset");
			}
			if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
				return getPregeneratorWorlds(plugin); // worlds with data file
			}
			if (args.length == 2) return Collections.singletonList("<PrintUpdateDelayin(Seconds/Minutes/Hours)>");
			if (args.length == 3) return getAllWorldNames();
			if (args.length == 4) return Arrays.asList("<Radius(Blocks/Chunks/Regions)>", "default");
		}
		if (command.getName().equalsIgnoreCase("pregenoff")) {
			if (args.length == 1) return getAllWorldNames();
		}
		return Collections.emptyList();
	}

	/**
	 * Scans the plugin folder for world folders (level.dat present).
	 */
	private List<String> getAllWorldNames() {
		File container = Bukkit.getServer().getWorldContainer();
		File[] files = container.listFiles();
		if (files == null) return Collections.emptyList();

		List<String> names = new ArrayList<>();
		for (File f : files) {
			if (f.isDirectory() && new File(f, "level.dat").exists()) {
				names.add(f.getName());
			}
		}
		return names;
	}

	/**
	 * Scans the plugin's data folder for pregenerator data files and returns corresponding world names.
	 *
	 * @param plugin the JavaPlugin instance
	 * @return a list of world names that have pregenerator data
	 */
	private List<String> getPregeneratorWorlds(JavaPlugin plugin) {
		File dataFolder = plugin.getDataFolder();
		File[] files = dataFolder.listFiles((dir, name) -> name.endsWith("_pregenerator.txt"));
		if (files == null) return Collections.emptyList();
		List<String> result = new ArrayList<>();
		for (File f : files) {
			String n = f.getName();
			if (n.endsWith("_pregenerator.txt")) {
				result.add(n.substring(0, n.length() - "_pregenerator.txt".length()));
			}
		}
		return result;
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