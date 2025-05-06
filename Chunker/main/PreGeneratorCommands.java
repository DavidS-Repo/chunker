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
 * Handles the /pregen and /pregenoff commands for chunk pre-generation.
 */
public class PreGeneratorCommands implements CommandExecutor, TabCompleter {
	private final PreGenerator preGen;
	private long currentBorderChunkCount;
	private int delayValue;
	private long radiusValue;
	private char delayTimeUnit;
	private char radiusUnit;
	private static final int TICKS_PER_SECOND = 20;
	private static final int TICKS_PER_MINUTE = TICKS_PER_SECOND * 60;
	private static final int TICKS_PER_HOUR   = TICKS_PER_MINUTE * 60;
	private final Set<String> generatingWorlds = new HashSet<>();
	private static final String INVALID_INPUT  = "Invalid numbers provided.";
	private static final String COMMAND_USAGE  = 
			"Usage: /pregen <threads> <delay[s/m/h]> <world> <radius[b/c/r]|default>";

	/**
	 * @param preGen   the pregenerator instance
	 * @param settings unused for now
	 */
	public PreGeneratorCommands(PreGenerator preGen, PluginSettings settings) {
		this.preGen = preGen;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (label.equalsIgnoreCase("pregen")) {
			if (args.length == 4) {
				handlePreGenCommand(sender, args);
			} else {
				colorMessage(sender, RED, COMMAND_USAGE);
			}
			return true;
		}
		if (label.equalsIgnoreCase("pregenoff")) {
			handlePreGenOffCommand(sender, args);
			return true;
		}
		return false;
	}

	/**
	 * Starts pre-generation for a single world.
	 */
	private void handlePreGenCommand(CommandSender sender, String[] args) {
		try {
			int threadCount = Integer.parseInt(args[0]);
			int printTicks  = parseDelay(args[1]);
			if (printTicks < 0) {
				colorMessage(sender, RED, INVALID_INPUT);
				return;
			}

			String worldName = args[2];
			World world = Bukkit.getWorld(worldName);
			if (world == null) {
				sender.sendMessage("World '" + worldName + "' not loaded. Loading now...");
				world = new WorldCreator(worldName).createWorld();
				if (world != null) {
					sender.sendMessage("World '" + worldName + "' loaded.");
				}
			}
			if (world == null) {
				sender.sendMessage("World not found: " + worldName);
				return;
			}

			currentBorderChunkCount = calculateChunksInBorder(world);
			long chunks = parseRadius(args[3]);
			if (chunks < 0) {
				colorMessage(sender, RED, INVALID_INPUT);
				return;
			}

			preGen.enable(
					threadCount,
					delayTimeUnit, delayValue,
					printTicks,
					world,
					chunks
					);
			generatingWorlds.add(worldName);

		} catch (NumberFormatException e) {
			colorMessage(sender, RED, INVALID_INPUT);
		}
	}

	/**
	 * Stops pre-generation for one world or all worlds.
	 */
	public void handlePreGenOffCommand(CommandSender sender, String[] args) {
		if (args.length == 0) {
			for (String name : generatingWorlds) {
				preGen.disable(Bukkit.getWorld(name));
			}
			generatingWorlds.clear();
		} else if (args.length == 1) {
			String worldName = args[0];
			World world = Bukkit.getWorld(worldName);
			if (world == null) {
				colorMessage(sender, RED, "World not found: " + worldName);
				return;
			}
			preGen.disable(world);
			generatingWorlds.remove(worldName);
		} else {
			colorMessage(sender, RED, "Usage: /pregenoff [world]");
		}
	}

	/**
	 * Converts a delay string (5s, 2m, 1h) into server ticks.
	 */
	private int parseDelay(String input) {
		try {
			delayValue   = Integer.parseInt(input.substring(0, input.length() - 1));
			delayTimeUnit = Character.toLowerCase(input.charAt(input.length() - 1));
			switch (delayTimeUnit) {
			case 's': return delayValue * TICKS_PER_SECOND;
			case 'm': return delayValue * TICKS_PER_MINUTE;
			case 'h': return delayValue * TICKS_PER_HOUR;
			default:  return -1;
			}
		} catch (Exception e) {
			return -1;
		}
	}

	/**
	 * Converts a radius argument (b, c, r, or default) into chunk count.
	 */
	private long parseRadius(String input) {
		try {
			if (input.equalsIgnoreCase("default")) {
				return currentBorderChunkCount;
			}
			radiusValue = Long.parseLong(input.substring(0, input.length() - 1));
			radiusUnit  = Character.toLowerCase(input.charAt(input.length() - 1));
			switch (radiusUnit) {
			case 'b': // blocks
				long blocks = radiusValue;
				long sideChunks = blocks / 16;
				return sideChunks * sideChunks;
			case 'c': // chunks
				return radiusValue * radiusValue;
			case 'r': // regions (32 chunks)
				long side = radiusValue * 32;
				return side * side;
			default:
				return -1;
			}
		} catch (Exception e) {
			return -1;
		}
	}

	/**
	 * Auto-loads and starts pre-generation for any worlds with auto_run=true.
	 */
	public void checkAndRunAutoPreGenerators() {
		for (String name : getAllWorldNames()) {
			if (PluginSettings.getAutoRun(name) && Bukkit.getWorld(name) == null) {
				Bukkit.getConsoleSender().sendMessage(
						"Loading world '" + name + "' for auto pregeneration..."
						);
				new WorldCreator(name).createWorld();
			}
		}

		int totalCores = PluginSettings.getAvailableProcessors();
		int autoCount = 0;
		Map<String, Integer> coresByWorld = new HashMap<>();

		// allocate fixed cores, count the auto ones
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

		// split remaining cores among auto worlds
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
			String name = entry.getKey();
			World world = Bukkit.getWorld(name);
			if (world == null) continue;

			currentBorderChunkCount = calculateChunksInBorder(world);
			int printTicks = parseDelay(PluginSettings.getPrintUpdateDelay(name));
			long chunks    = parseRadius(PluginSettings.getRadius(name));

			preGen.enable(
					entry.getValue(),
					delayTimeUnit, delayValue,
					printTicks,
					world,
					chunks
					);
			generatingWorlds.add(name);
		}
	}

	/**
	 * Calculates chunk count inside the world border.
	 */
	public long calculateChunksInBorder(World world) {
		WorldBorder border = world.getWorldBorder();
		double diameter     = border.getSize();
		double halfBlocks   = diameter / 2.0;
		double halfChunks   = Math.ceil(halfBlocks / 16.0);
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
				return Collections.singletonList("<ParallelTasksMultiplier>");
			}
			if (args.length == 2) {
				return Collections.singletonList("<PrintUpdateDelayin(Seconds/Minutes/Hours)>");
			}
			if (args.length == 3) {
				return getAllWorldNames();
			}
			if (args.length == 4) {
				return Arrays.asList("<Radius(Blocks/Chunks/Regions)>", "default");
			}
		}
		if (command.getName().equalsIgnoreCase("pregenoff")) {
			if (args.length == 1) {
				return getAllWorldNames();
			}
		}
		return Collections.emptyList();
	}

	/**
	 * Finds every folder with a level.dat and returns its name.
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
	 * Registers the commands with Bukkit.
	 */
	public static void registerCommands(
			JavaPlugin plugin,
			PreGenerator preGen
			) {
		PreGeneratorCommands cmds = new PreGeneratorCommands(
				preGen,
				new PluginSettings(plugin)
				);
		plugin.getCommand("pregen").setExecutor(cmds);
		plugin.getCommand("pregen").setTabCompleter(cmds);
		plugin.getCommand("pregenoff").setExecutor(cmds);
		plugin.getCommand("pregenoff").setTabCompleter(cmds);
	}
}
