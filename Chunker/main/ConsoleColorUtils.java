package main;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.util.Map;
import java.util.logging.Level;

/**
 * Utilities for sending colored and formatted messages.
 * Uses ANSI codes in the console and Minecraft codes in chat.
 */
public class ConsoleColorUtils {

	// color codes for console (ANSI) and chat (Minecraft)
	public static final ColorPair COLOR_RESET   = ColorPair.of("\033[0m",    "§r");
	public static final ColorPair BLACK         = ColorPair.of("\033[0;30m", "§0");
	public static final ColorPair DARK_BLUE     = ColorPair.of("\033[0;34m", "§1");
	public static final ColorPair DARK_GREEN    = ColorPair.of("\033[0;32m", "§2");
	public static final ColorPair DARK_AQUA     = ColorPair.of("\033[0;36m", "§3");
	public static final ColorPair DARK_RED      = ColorPair.of("\033[0;31m", "§4");
	public static final ColorPair DARK_PURPLE   = ColorPair.of("\033[0;35m", "§5");
	public static final ColorPair GOLD          = ColorPair.of("\033[0;33m", "§6");
	public static final ColorPair GRAY          = ColorPair.of("\033[0;37m", "§7");
	public static final ColorPair DARK_GRAY     = ColorPair.of("\033[1;30m", "§8");
	public static final ColorPair BLUE          = ColorPair.of("\033[1;34m", "§9");
	public static final ColorPair GREEN         = ColorPair.of("\033[1;32m", "§a");
	public static final ColorPair AQUA          = ColorPair.of("\033[1;36m", "§b");
	public static final ColorPair RED           = ColorPair.of("\033[1;31m", "§c");
	public static final ColorPair LIGHT_PURPLE  = ColorPair.of("\033[1;35m", "§d");
	public static final ColorPair YELLOW        = ColorPair.of("\033[1;33m", "§e");
	public static final ColorPair WHITE         = ColorPair.of("\033[1;37m", "§f");

	// text styles
	public static final ColorPair BOLD           = ColorPair.of("\033[1m",   "§l");
	public static final ColorPair ITALIC         = ColorPair.of("",          "§o");
	public static final ColorPair UNDERLINE      = ColorPair.of("\033[4m",   "§n");
	public static final ColorPair STRIKETHROUGH  = ColorPair.of("",          "§m");
	public static final ColorPair OBFUSCATED     = ColorPair.of("",          "§k");

	// known default worlds shortened
	private static final Map<String, String> WORLD_SHORT_NAMES = Map.of(
			"world",         "world",
			"world_nether",  "nether",
			"world_the_end", "end"
			);

	/**
	 * Send a colored message.
	 * Uses ANSI codes if sender is console, Minecraft codes otherwise.
	 *
	 * @param sender    who gets the message
	 * @param baseColor main color
	 * @param message   text to send
	 * @param formats   extra styles like BOLD
	 */
	public static void colorMessage(CommandSender sender,
			ColorPair baseColor,
			String message,
			ColorPair... formats) {
		if (sender instanceof ConsoleCommandSender) {
			String ansi = combineAnsi(baseColor, formats);
			sender.sendMessage(ansi + message + COLOR_RESET.getAnsi());
		} else {
			String mc = combineMinecraft(baseColor, message, formats);
			sender.sendMessage(mc);
		}
	}

	/**
	 * Log a colored message to the console.
	 *
	 * @param baseColor color to use
	 * @param message   text to log
	 * @param formats   extra styles
	 */
	public static void logColor(ColorPair baseColor,
			String message,
			ColorPair... formats) {
		String ansi = combineAnsi(baseColor, formats);
		Bukkit.getLogger().info(ansi + message + COLOR_RESET.getAnsi());
	}

	/**
	 * Log plain text to the console.
	 *
	 * @param message text to log
	 */
	public static void logPlain(String message) {
		Bukkit.getLogger().info(message);
	}

	/**
	 * Format an object into an ANSI-colored string.
	 *
	 * @param baseColor color to use
	 * @param value     object to format
	 * @param formats   extra styles
	 * @return ANSI string with reset code
	 */
	public static String formatColorObject(ColorPair baseColor,
			Object value,
			ColorPair... formats) {
		String ansi = combineAnsi(baseColor, formats);
		return ansi + value + COLOR_RESET.getAnsi();
	}

	/**
	 * Log a message at a given level with ANSI color.
	 *
	 * @param level     logging level
	 * @param baseColor color to use
	 * @param message   text to log
	 * @param formats   extra styles
	 */
	public static void log(Level level,
			ColorPair baseColor,
			String message,
			ColorPair... formats) {
		String ansi = combineAnsi(baseColor, formats);
		Bukkit.getLogger().log(level, ansi + message + COLOR_RESET.getAnsi());
	}

	/**
	 * Log an exception-style message in red.
	 *
	 * @param message error text
	 * @param formats extra styles
	 */
	public static void exceptionMsg(String message,
			ColorPair... formats) {
		logColor(RED, "Exception: " + message, formats);
	}

	/**
	 * Format a value into fixed-width for console.
	 *
	 * @param baseColor color to use
	 * @param value     object to format
	 * @param width     target width
	 * @param formats   extra styles
	 * @return padded ANSI string
	 */
	public static String formatAligned(ColorPair baseColor,
			Object value,
			int width,
			ColorPair... formats) {
		String ansi = combineAnsi(baseColor, formats);
		String padded = String.format("%-" + width + "s", value);
		return ansi + padded + COLOR_RESET.getAnsi();
	}

	/**
	 * Format a world name into a bracketed label.
	 * Pads to match the longest short name.
	 *
	 * @param worldName folder name of the world
	 * @return bracketed, padded short name
	 */
	public static String formatWorldName(String worldName) {
		String shortName = getWorldShortName(worldName);
		int maxLen = getMaxWorldNameLength();
		int pad    = Math.max(0, maxLen - shortName.length());
		return "[" + shortName + "]" + " ".repeat(pad);
	}

	/**
	 * Get the longest short-name length among all loaded worlds.
	 *
	 * @return max length
	 */
	private static int getMaxWorldNameLength() {
		int max = 0;
		for (World w : Bukkit.getWorlds()) {
			String name = getWorldShortName(w.getName());
			if (name.length() > max) {
				max = name.length();
			}
		}
		return max;
	}

	/**
	 * Map known world names to shorter labels.
	 * Falls back to original name if unknown.
	 *
	 * @param worldName folder name
	 * @return short label
	 */
	public static String getWorldShortName(String worldName) {
		return WORLD_SHORT_NAMES.getOrDefault(worldName, worldName);
	}

	// combine ANSI codes
	private static String combineAnsi(ColorPair base,
			ColorPair... fmts) {
		StringBuilder sb = new StringBuilder(base.getAnsi());
		for (ColorPair f : fmts) {
			sb.append(f.getAnsi());
		}
		return sb.toString();
	}

	// combine Minecraft codes
	private static String combineMinecraft(ColorPair base,
			String msg,
			ColorPair... fmts) {
		StringBuilder pre = new StringBuilder(base.getMinecraft());
		StringBuilder suf = new StringBuilder(COLOR_RESET.getMinecraft());
		for (ColorPair f : fmts) {
			pre.append(f.getMinecraft());
			suf.insert(0, f.getMinecraft());
		}
		return pre + msg + suf;
	}

	/**
	 * Holds both ANSI and Minecraft codes.
	 */
	public static class ColorPair {
		private final String ansi;
		private final String minecraft;

		private ColorPair(String ansi, String mc) {
			this.ansi      = ansi;
			this.minecraft = mc;
		}

		/**
		 * Create a ColorPair.
		 *
		 * @param ansi ANSI code
		 * @param mc   Minecraft code
		 * @return new pair
		 */
		public static ColorPair of(String ansi, String mc) {
			return new ColorPair(ansi, mc);
		}

		public String getAnsi()      { return ansi; }
		public String getMinecraft() { return minecraft; }

		/**
		 * Combine two styles into one.
		 *
		 * @param other another ColorPair
		 * @return combined pair
		 */
		public ColorPair combine(ColorPair other) {
			return new ColorPair(ansi + other.ansi,
					minecraft + other.minecraft);
		}
	}
}