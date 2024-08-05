package main;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

//Console Colors
public class cC {
    public static final String 
        RESET = "\033[0m", BLACK = "\033[0;30m", DARK_BLUE = "\033[0;34m", DARK_GREEN = "\033[0;32m", 
        DARK_AQUA = "\033[0;36m", DARK_RED = "\033[0;31m", DARK_PURPLE = "\033[0;35m", GOLD = "\033[0;33m", 
        GRAY = "\033[0;37m", DARK_GRAY = "\033[1;30m", BLUE = "\033[1;34m", GREEN = "\033[1;32m", AQUA = "\033[1;36m",
        RED = "\033[1;31m", LIGHT_PURPLE = "\033[1;35m", YELLOW = "\033[1;33m", WHITE = "\033[1;37m";
    
    public static void logS(String colorCode, String message) {
        Bukkit.getLogger().info(colorCode + message + cC.RESET);
    }
    
    public static void logSB(String message) {
        Bukkit.getLogger().info(message);
    }
    
    public static void sendS(CommandSender sender, String colorCode, String message) {
        sender.sendMessage(colorCode + message + cC.RESET);
    }
    
    public static String logO(String colorCode, Object value) {
        return colorCode + String.valueOf(value) + cC.RESET;
    }
    
    public static String fA(String colorCode, Object value, int width) {
        return colorCode + String.format("%-" + width + "s", value) + cC.RESET;
    }
}
