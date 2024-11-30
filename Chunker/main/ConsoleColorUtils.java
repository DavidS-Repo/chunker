package main;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.util.HashMap;
import java.util.Map;

/**
 * The ConsoleColorUtils class provides utility methods and constants for handling console colors
 * and formatted messaging within the Minecraft server environment.
 * 
 * Allows the use of color constants like which automatically
 * switch between ANSI codes for the console and Minecraft color codes for in-game messages.
 */
public class ConsoleColorUtils {

	// Define color constants that dynamically switch between ANSI and Minecraft color codes
	public static final ColorPair COLOR_RESET = new ColorPair("\033[0m", "§r");
	public static final ColorPair BLACK = new ColorPair("\033[0;30m", "§0");
	public static final ColorPair DARK_BLUE = new ColorPair("\033[0;34m", "§1");
	public static final ColorPair DARK_GREEN = new ColorPair("\033[0;32m", "§2");
	public static final ColorPair DARK_AQUA = new ColorPair("\033[0;36m", "§3");
	public static final ColorPair DARK_RED = new ColorPair("\033[0;31m", "§4");
	public static final ColorPair DARK_PURPLE = new ColorPair("\033[0;35m", "§5");
	public static final ColorPair GOLD = new ColorPair("\033[0;33m", "§6");
	public static final ColorPair GRAY = new ColorPair("\033[0;37m", "§7");
	public static final ColorPair DARK_GRAY = new ColorPair("\033[1;30m", "§8");
	public static final ColorPair BLUE = new ColorPair("\033[1;34m", "§9");
	public static final ColorPair GREEN = new ColorPair("\033[1;32m", "§a");
	public static final ColorPair AQUA = new ColorPair("\033[1;36m", "§b");
	public static final ColorPair RED = new ColorPair("\033[1;31m", "§c");
	public static final ColorPair LIGHT_PURPLE = new ColorPair("\033[1;35m", "§d");
	public static final ColorPair YELLOW = new ColorPair("\033[1;33m", "§e");
	public static final ColorPair WHITE = new ColorPair("\033[1;37m", "§f");

	// Map to store world name mappings
	private static final Map<String, String> WORLD_SHORT_NAMES = new HashMap<>();
	private static final int MAX_WORLD_NAME_LEN;

	static {
		WORLD_SHORT_NAMES.put("world", "world");
		WORLD_SHORT_NAMES.put("world_nether", "nether");
		WORLD_SHORT_NAMES.put("world_the_end", "end");
		MAX_WORLD_NAME_LEN = 6;
	}

	/**
	 * Retrieves the shortened world name based on the mapping.
	 * 
	 * @param worldName The original world name.
	 * @return The shortened world name.
	 */
	public static String getWorldShortName(String worldName) {
		return WORLD_SHORT_NAMES.getOrDefault(worldName, worldName);
	}

	/**
	 * Logs a colored message to the server console using ANSI colors.
	 * 
	 * @param color The ColorPair object for the desired color.
	 * @param message The message to be logged.
	 */
	public static void logColor(ColorPair color, String message) {
		Bukkit.getLogger().info(color.getAnsi() + message + COLOR_RESET.getAnsi());
	}

	/**
	 * Logs a plain message to the server console without any color formatting.
	 * 
	 * @param message The message to be logged.
	 */
	public static void logPlain(String message) {
		Bukkit.getLogger().info(message);
	}
	
	/**
     * Logs an exception message in red to the server console.
     * 
     * @param msg The exception message to log.
     */
    public static void exceptionMsg(String msg) {
    	logColor(RED, "Exception: " + msg);
    }

	/**
	 * Sends a colored message to a specific CommandSender (player or console).
	 * Automatically uses ANSI codes for ConsoleCommandSender and Minecraft color codes for in-game CommandSenders.
	 * 
	 * @param sender The recipient of the message.
	 * @param color The ColorPair object for the desired color.
	 * @param message The message to be sent.
	 */
	public static void colorMessage(CommandSender sender, ColorPair color, String message) {
		if (sender instanceof ConsoleCommandSender) {
			// Send ANSI color-coded message to console
			sender.sendMessage(color.getAnsi() + message + COLOR_RESET.getAnsi());
		} else {
			// Send Minecraft color-coded message to in-game CommandSender
			sender.sendMessage(color.getMinecraft() + message + COLOR_RESET.getMinecraft());
		}
	}

	/**
	 * Formats an object into a colored string for console display with ANSI codes.
	 * 
	 * @param color The ColorPair object for the desired color.
	 * @param value The object to be formatted.
	 * @return A string representation of the object with the specified color in ANSI format.
	 */
	public static String formatColorObject(ColorPair color, Object value) {
		return color.getAnsi() + String.valueOf(value) + COLOR_RESET.getAnsi();
	}

	/**
	 * Formats an object into a fixed-width colored string for console display with ANSI codes.
	 * 
	 * @param color The ColorPair object for the desired color.
	 * @param value The object to be formatted.
	 * @param width The desired width of the formatted string.
	 * @return A fixed-width string representation of the object with the specified color in ANSI format.
	 */
	public static String formatAligned(ColorPair color, Object value, int width) {
		String formattedValue = String.format("%-" + width + "s", value);
		return color.getAnsi() + formattedValue + COLOR_RESET.getAnsi();
	}

	/**
	 * Pads a world name with spaces to ensure consistent formatting.
	 * 
	 * @param worldName The original world name to pad.
	 * @return The padded world name enclosed in brackets.
	 */
	public static String formatWorldName(String worldName) {
		String shortName = getWorldShortName(worldName);
		int paddingLength = MAX_WORLD_NAME_LEN - shortName.length();
		String padding = " ".repeat(Math.max(0, paddingLength));
		return "[" + shortName + "]" + padding;
	}

	/**
	 * Inner class to store both ANSI and Minecraft color codes.
	 */
	public static class ColorPair {
		private final String ansi;
		private final String minecraft;

		public ColorPair(String ansi, String minecraft) {
			this.ansi = ansi;
			this.minecraft = minecraft;
		}

		public String getAnsi() {
			return ansi;
		}

		public String getMinecraft() {
			return minecraft;
		}
	}
}