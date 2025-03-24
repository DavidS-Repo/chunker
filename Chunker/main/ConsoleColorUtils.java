package main;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.util.Map;
import java.util.logging.Level;

/**
 * The ConsoleColorUtils class provides utility methods and constants for handling console colors,
 * text formatting, and formatted messaging within the Minecraft server environment.
 *
 * The class supports both ANSI codes for console messages and Minecraft color/formatting codes
 * for in-game messages. It ensures proper handling of multiple formats (e.g., bold, underline)
 * and applies appropriate reset codes to avoid formatting bleed.
 */
public class ConsoleColorUtils {

	// Define color constants with dynamic ANSI and Minecraft formatting
	public static final ColorPair COLOR_RESET = ColorPair.of("\033[0m", "§r");
	public static final ColorPair BLACK = ColorPair.of("\033[0;30m", "§0");
	public static final ColorPair DARK_BLUE = ColorPair.of("\033[0;34m", "§1");
	public static final ColorPair DARK_GREEN = ColorPair.of("\033[0;32m", "§2");
	public static final ColorPair DARK_AQUA = ColorPair.of("\033[0;36m", "§3");
	public static final ColorPair DARK_RED = ColorPair.of("\033[0;31m", "§4");
	public static final ColorPair DARK_PURPLE = ColorPair.of("\033[0;35m", "§5");
	public static final ColorPair GOLD = ColorPair.of("\033[0;33m", "§6");
	public static final ColorPair GRAY = ColorPair.of("\033[0;37m", "§7");
	public static final ColorPair DARK_GRAY = ColorPair.of("\033[1;30m", "§8");
	public static final ColorPair BLUE = ColorPair.of("\033[1;34m", "§9");
	public static final ColorPair GREEN = ColorPair.of("\033[1;32m", "§a");
	public static final ColorPair AQUA = ColorPair.of("\033[1;36m", "§b");
	public static final ColorPair RED = ColorPair.of("\033[1;31m", "§c");
	public static final ColorPair LIGHT_PURPLE = ColorPair.of("\033[1;35m", "§d");
	public static final ColorPair YELLOW = ColorPair.of("\033[1;33m", "§e");
	public static final ColorPair WHITE = ColorPair.of("\033[1;37m", "§f");

	// Define formatting constants
	public static final ColorPair BOLD = ColorPair.of("\033[1m", "§l"); 
	public static final ColorPair ITALIC = ColorPair.of("", "§o");
	public static final ColorPair UNDERLINE = ColorPair.of("\033[4m", "§n");
	public static final ColorPair STRIKETHROUGH = ColorPair.of("", "§m");
	public static final ColorPair OBFUSCATED = ColorPair.of("", "§k");

	// Immutable map to store world name mappings
	private static final Map<String, String> WORLD_SHORT_NAMES = Map.of(
			"world", "world",
			"world_nether", "nether",
			"world_the_end", "end"
			);
	private static final int MAX_WORLD_NAME_LEN = 6;

	/**
	 * Sends a message to a CommandSender with optional color and formatting.
	 * Automatically adjusts between ANSI and Minecraft formatting based on the sender type.
	 *
	 * @param sender    The recipient of the message (e.g., a player or console).
	 * @param baseColor The base ColorPair (e.g., RED, DARK_BLUE).
	 * @param message   The message to send.
	 * @param formats   Optional additional formatting (e.g., BOLD, UNDERLINE).
	 */
	public static void colorMessage(CommandSender sender, ColorPair baseColor, String message, ColorPair... formats) {
		if (sender instanceof ConsoleCommandSender) {
			// Handle ANSI formatting for console messages
			String combinedAnsi = combineAnsi(baseColor, formats);
			sender.sendMessage(combinedAnsi + message + COLOR_RESET.getAnsi());
		} else {
			// Handle Minecraft formatting for in-game messages
			String combinedMinecraft = combineMinecraft(baseColor, message, formats);
			sender.sendMessage(combinedMinecraft);
		}
	}

	/**
	 * Combines ANSI codes for console messages.
	 * Constructs a single string by concatenating the base color and all additional formats.
	 *
	 * @param baseColor The base ColorPair (e.g., RED, DARK_BLUE).
	 * @param formats   Additional formatting options (e.g., BOLD, UNDERLINE).
	 * @return Combined ANSI codes as a single string.
	 */
	private static String combineAnsi(ColorPair baseColor, ColorPair... formats) {
		StringBuilder ansiBuilder = new StringBuilder(baseColor.getAnsi());
		for (ColorPair format : formats) {
			ansiBuilder.append(format.getAnsi());
		}
		return ansiBuilder.toString();
	}

	/**
	 * Combines Minecraft formatting codes for in-game messages.
	 * Surrounds the message with all applicable formatting codes and resets appropriately.
	 *
	 * @param baseColor The base ColorPair (e.g., RED, DARK_BLUE).
	 * @param message   The message to format and surround.
	 * @param formats   Additional formatting options (e.g., BOLD, UNDERLINE).
	 * @return The Minecraft-formatted message string.
	 */
	private static String combineMinecraft(ColorPair baseColor, String message, ColorPair... formats) {
		StringBuilder prefixBuilder = new StringBuilder(baseColor.getMinecraft());
		StringBuilder suffixBuilder = new StringBuilder(COLOR_RESET.getMinecraft());

		for (ColorPair format : formats) {
			prefixBuilder.append(format.getMinecraft());
			suffixBuilder.insert(0, format.getMinecraft());
		}

		return prefixBuilder + message + suffixBuilder;
	}

	/**
	 * Logs a colored message to the server console using ANSI colors.
	 * Applies optional formatting and ensures proper reset codes.
	 *
	 * @param baseColor The base ColorPair (e.g., RED, DARK_BLUE).
	 * @param message   The message to log.
	 * @param formats   Optional additional formatting (e.g., BOLD, UNDERLINE).
	 */
	public static void logColor(ColorPair baseColor, String message, ColorPair... formats) {
		String combinedAnsi = combineAnsi(baseColor, formats);
		Bukkit.getLogger().info(combinedAnsi + message + COLOR_RESET.getAnsi());
	}

	/**
	 * Logs a plain message to the server console without any color or formatting.
	 *
	 * @param message The plain message to log.
	 */
	public static void logPlain(String message) {
		Bukkit.getLogger().info(message);
	}

	/**
	 * Formats an object into a colored and optionally formatted string for console output.
	 * Handles ANSI formatting, combining the base color with optional additional formats
	 * (e.g., bold, underline). The formatted string ends with a reset code to ensure proper
	 * output without formatting bleed.
	 *
	 * @param baseColor The base ColorPair (e.g., RED, DARK_BLUE).
	 * @param value     The object to format (e.g., a string or number).
	 * @param formats   Optional additional formatting (e.g., BOLD, UNDERLINE).
	 * @return A formatted ANSI string for console output.
	 */
	public static String formatColorObject(ColorPair baseColor, Object value, ColorPair... formats) {
		String combinedAnsi = combineAnsi(baseColor, formats);
		return combinedAnsi + value + COLOR_RESET.getAnsi();
	}

	/**
	 * Logs a message with a specific logging level and optional formatting.
	 *
	 * @param level     The logging level (e.g., INFO, WARNING, SEVERE).
	 * @param baseColor The base ColorPair (e.g., RED, DARK_BLUE).
	 * @param message   The message to log.
	 * @param formats   Optional additional formatting (e.g., BOLD, UNDERLINE).
	 */
	public static void log(Level level, ColorPair baseColor, String message, ColorPair... formats) {
		String combinedAnsi = combineAnsi(baseColor, formats);
		Bukkit.getLogger().log(level, combinedAnsi + message + COLOR_RESET.getAnsi());
	}

	/**
	 * Logs an exception message in red with optional formatting.
	 *
	 * @param message The exception message to log.
	 * @param formats Optional additional formatting (e.g., BOLD, UNDERLINE).
	 */
	public static void exceptionMsg(String message, ColorPair... formats) {
		logColor(RED, "Exception: " + message, formats);
	}

	/**
	 * Formats an object into a fixed-width string with optional color and formatting.
	 * Ensures the string is aligned to the specified width.
	 *
	 * @param baseColor The base ColorPair (e.g., RED, DARK_BLUE).
	 * @param value     The object to format.
	 * @param width     The fixed width for the formatted string.
	 * @param formats   Optional additional formatting (e.g., BOLD, UNDERLINE).
	 * @return A fixed-width, formatted string.
	 */
	public static String formatAligned(ColorPair baseColor, Object value, int width, ColorPair... formats) {
		String combinedAnsi = combineAnsi(baseColor, formats);
		String formattedValue = String.format("%-" + width + "s", value);
		return combinedAnsi + formattedValue + COLOR_RESET.getAnsi();
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
	 * Retrieves the shortened world name based on the mapping.
	 * Falls back to the original name if no mapping exists.
	 *
	 * @param worldName The original world name.
	 * @return The shortened world name or the original name if no mapping exists.
	 */
	public static String getWorldShortName(String worldName) {
		return WORLD_SHORT_NAMES.getOrDefault(worldName, worldName);
	}

	/**
	 * Inner class to store both ANSI and Minecraft color/formatting codes.
	 * Provides methods for chaining multiple formats and colors.
	 */
	public static class ColorPair {
		private final String ansi;
		private final String minecraft;

		private ColorPair(String ansi, String minecraft) {
			this.ansi = ansi;
			this.minecraft = minecraft;
		}

		/**
		 * Factory method to create a new ColorPair instance.
		 *
		 * @param ansi      The ANSI color code or formatting code.
		 * @param minecraft The Minecraft color code or formatting code.
		 * @return A new ColorPair instance.
		 */
		public static ColorPair of(String ansi, String minecraft) {
			return new ColorPair(ansi, minecraft);
		}

		public String getAnsi() {
			return ansi;
		}

		public String getMinecraft() {
			return minecraft;
		}

		/**
		 * Combines this ColorPair with another to apply multiple styles.
		 *
		 * @param other The other ColorPair to combine with.
		 * @return A new ColorPair combining both styles.
		 */
		public ColorPair combine(ColorPair other) {
			return new ColorPair(this.ansi + other.ansi, this.minecraft + other.minecraft);
		}
	}
}