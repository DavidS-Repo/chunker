package main;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;

public class PreGeneratorCommands implements CommandExecutor, TabCompleter {
	private final PreGenerator preGenerator;
	private long worldBorder;
	private int timeValue;
	private long radiusValue;
	private char timeUnit, radiusUnit;
	private static final int tickSecond = 20, tickMinute = 1200, tickHour = 72000;
	private final Set<String> enabledWorlds = new HashSet<>();
	private static final String
	WARNING_MESSAGE = "Invalid numbers provided.", 
	USAGE_MESSAGE = "Usage: /pregen <ParallelTasksMultiplier> <PrintUpdateDelayin(Seconds/Minutes/Hours)> <world> <Radius(Blocks/Chunks/Regions)>";

	public PreGeneratorCommands(PreGenerator preGenerator, PluginSettings settings) {
		this.preGenerator = preGenerator;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (label.equalsIgnoreCase("pregen")) {
			if (args.length == 4) {
				handlePreGenCommand(sender, args);
				return true;
			} else {
				cC.sendS(sender, cC.RED, USAGE_MESSAGE);
				return true;
			}
		} else if (label.equalsIgnoreCase("pregenoff")) {
			handlePreGenOffCommand(sender, args);
			return true;
		}
		return false;
	}

	private void handlePreGenCommand(CommandSender sender, String[] args) {
		try {
			int parallelTasksMultiplier = Integer.parseInt(args[0]);
			String printTimeArg = args[1];
			int printTime = parseTime(printTimeArg);
			if (printTime < 0) {
				cC.sendS(sender, cC.RED, WARNING_MESSAGE);
				return;
			}
			String worldName = args[2];
			World world = Bukkit.getWorld(worldName);
			worldBorder = calculateChunksInBorder(world);
			if (world == null) {
				sender.sendMessage("World not found: " + worldName);
				return;
			}
			long radius = parseRadius(args[3]);
			if (radius < 0) {
				cC.sendS(sender, cC.RED, WARNING_MESSAGE);
				return;
			}
			preGenerator.enable(parallelTasksMultiplier, timeUnit, timeValue, printTime, world, radius);
			enabledWorlds.add(worldName);
		} catch (NumberFormatException e) {
			cC.sendS(sender, cC.RED, WARNING_MESSAGE);
		}
	}

	public void handlePreGenOffCommand(CommandSender sender, String[] args) {
		if (args.length == 0) {
			for (String worldName : enabledWorlds) {
				preGenerator.disable(Bukkit.getWorld(worldName));
			}
			enabledWorlds.clear();
		} else if (args.length == 1) {
			String worldName = args[0];
			World world = Bukkit.getWorld(worldName);
			if (world == null) {
				cC.sendS(sender, cC.RED, "World not found: " + worldName);
				return;
			}
			preGenerator.disable(world);
			enabledWorlds.remove(worldName);
		} else {
			cC.sendS(sender, cC.RED, "Usage: /pregenoff [world]");
		}
	}

	private int parseTime(String timeArg) {
		try {
			timeValue = Integer.parseInt(timeArg.substring(0, timeArg.length() - 1));
			timeUnit = Character.toLowerCase(timeArg.charAt(timeArg.length() - 1));
			switch (timeUnit) {
			case 's':
				return timeValue * tickSecond;
			case 'm':
				return timeValue * tickMinute;
			case 'h':
				return timeValue * tickHour;
			default:
				return -1;
			}
		} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
			return -1;
		}
	}

	private long parseRadius(String radiusArg) {
		try {
			if (radiusArg.equalsIgnoreCase("default")) {
				return worldBorder;
			}
			radiusValue = Long.parseLong(radiusArg.substring(0, radiusArg.length() - 1));
			radiusUnit = Character.toLowerCase(radiusArg.charAt(radiusArg.length() - 1));
			switch (radiusUnit) {
			case 'b':
				return ((radiusValue / 16) * (radiusValue / 16));
			case 'c':
				return (radiusValue * radiusValue);
			case 'r':
				return ((radiusValue * 32) * (radiusValue * 32));
			default:
				return -1;
			}
		} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
			return -1;
		}
	}

	public void checkAndRunAutoPreGenerators() {
	    int totalCores = PluginSettings.THREADS();
	    int autoRunCount = 0;

	    if (PluginSettings.world_auto_run()) autoRunCount++;
	    if (PluginSettings.world_nether_auto_run()) autoRunCount++;
	    if (PluginSettings.world_the_end_auto_run()) autoRunCount++;

	    if (autoRunCount == 0) return;

	    int coresPerTask = totalCores / autoRunCount;

	    if (PluginSettings.world_auto_run() && Bukkit.getWorld("world") != null && Bukkit.getOnlinePlayers().isEmpty()) {
	        int world_printTime = parseTime(PluginSettings.world_print_update_delay());
	        worldBorder = calculateChunksInBorder(Bukkit.getWorld("world"));
	        long a = parseRadius(PluginSettings.world_radius());
	        preGenerator.enable(coresPerTask, timeUnit, timeValue, world_printTime, Bukkit.getWorld("world"), a);
	        enabledWorlds.add("world");
	    }

	    if (PluginSettings.world_nether_auto_run() && Bukkit.getWorld("world_nether") != null && Bukkit.getOnlinePlayers().isEmpty()) {
	        int world_nether_printTime = parseTime(PluginSettings.world_nether_print_update_delay());
	        worldBorder = calculateChunksInBorder(Bukkit.getWorld("world_nether"));
	        long b = parseRadius(PluginSettings.world_nether_radius());
	        preGenerator.enable(coresPerTask, timeUnit, timeValue, world_nether_printTime, Bukkit.getWorld("world_nether"), b);
	        enabledWorlds.add("world_nether");
	    }

	    if (PluginSettings.world_the_end_auto_run() && Bukkit.getWorld("world_the_end") != null && Bukkit.getOnlinePlayers().isEmpty()) {
	        int world_the_end_printTime = parseTime(PluginSettings.world_the_end_print_update_delay());
	        worldBorder = calculateChunksInBorder(Bukkit.getWorld("world_the_end"));
	        long c = parseRadius(PluginSettings.world_the_end_radius());
	        preGenerator.enable(coresPerTask, timeUnit, timeValue, world_the_end_printTime, Bukkit.getWorld("world_the_end"), c);
	        enabledWorlds.add("world_the_end");
	    }
	}

	public long calculateChunksInBorder(World world) {
		WorldBorder worldBorder = world.getWorldBorder();
		double diameter = worldBorder.getSize();
		double radiusInBlocks = diameter / 2.0;
		double radiusInChunks = Math.ceil(radiusInBlocks / 16.0);
		long totalChunks = (long) Math.pow(radiusInChunks * 2 + 1, 2);
		return totalChunks;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (command.getName().equalsIgnoreCase("pregen")) {
			if (args.length == 1) {
				return Arrays.asList("<ParallelTasksMultiplier>");
			} else if (args.length == 2) {
				return Arrays.asList("<PrintUpdateDelayin(Seconds/Minutes/Hours)>");
			} else if (args.length == 3) {
				return Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());
			} else if (args.length == 4) {
				return Arrays.asList("<Radius(Blocks/Chunks/Regions)>", "default");
			}
		} else if (command.getName().equalsIgnoreCase("pregenoff")) {
			if (args.length == 1) {
				return Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());
			}
		}
		return null;
	}

	public static void registerCommands(JavaPlugin plugin, PreGenerator preGenerator) {
		PreGeneratorCommands commands = new PreGeneratorCommands(preGenerator, new PluginSettings(plugin));
		plugin.getCommand("pregen").setExecutor(commands);
		plugin.getCommand("pregen").setTabCompleter(commands);
		plugin.getCommand("pregenoff").setExecutor(commands);
		plugin.getCommand("pregenoff").setTabCompleter(commands);
	}
}
