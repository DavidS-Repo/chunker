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
import java.util.List;
import java.util.stream.Collectors;

public class PreGeneratorCommands implements CommandExecutor, TabCompleter {
	private final PreGenerator preGenerator; 
	private long worldBorder; 
	private static final String WARNING_MESSAGE = cC.RED + "Invalid numbers provided."; 
	private static final String USAGE_MESSAGE = cC.GREEN + "Usage: /pregen <ParallelTasksMultiplier> <PrintUpdateDelayin(Seconds/Minutes/Hours)> <world> <Radius(Blocks/Chunks/Regions)>"; 
	private static final int tickSecond = 20, tickMinute = 1200, tickHour = 72000; 
	private int timeValue; 
	private long radiusValue; 
	private char timeUnit, radiusUnit; 

	public PreGeneratorCommands(PreGenerator preGenerator) {
		this.preGenerator = preGenerator;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (label.equalsIgnoreCase("pregen")) {
			if (args.length == 4) {
				try {
					int parallelTasksMultiplier = Integer.parseInt(args[0]);
					String printTimeArg = args[1];
					int printTime = parseTime(printTimeArg);
					if (printTime < 0) {
						sender.sendMessage(WARNING_MESSAGE);
						return true;
					}
					String worldName = args[2];
					World world = Bukkit.getWorld(worldName);
					worldBorder = calculateChunksInBorder(world);
					if (world == null) {
						sender.sendMessage(cC.RED + "World not found: " + worldName);
						return true;
					}
					long radius = parseRadius(args[3]);
					if (radius < 0) {
						sender.sendMessage(WARNING_MESSAGE);
						return true;
					}
					preGenerator.enable(parallelTasksMultiplier, timeUnit, timeValue, printTime, world, radius);
					return true;
				} catch (NumberFormatException e) {
					sender.sendMessage(WARNING_MESSAGE);
					return true;
				}
			} else {
				sender.sendMessage(USAGE_MESSAGE);
				return true;
			}
		} else if (label.equalsIgnoreCase("pregenoff")) {
			preGenerator.disable();
			return true;
		}
		return false;
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

	public long calculateChunksInBorder(World world) {
		WorldBorder worldBorder = world.getWorldBorder(); 
		double diameter = worldBorder.getSize();
		double radiusInBlocks = diameter / 2.0;
		double radiusInChunks = Math.ceil(radiusInBlocks / 16.0);
		long totalChunks = (long) Math.pow(radiusInChunks * 2 + 1, 2);
		return totalChunks; 
	}

	private long parseRadius(String radiusArg) {
		if (radiusArg.equalsIgnoreCase("default")) {

			return worldBorder;
		}
		try {
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
		}
		return null;
	}

	public static void registerCommands(JavaPlugin plugin, PreGenerator preGenerator) {
		PreGeneratorCommands commands = new PreGeneratorCommands(preGenerator);
		plugin.getCommand("pregen").setExecutor(commands); 
		plugin.getCommand("pregen").setTabCompleter(commands); 
		plugin.getCommand("pregenoff").setExecutor(commands); 
	}
}
