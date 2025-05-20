package main;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

/**
 * Utility for resetting (deleting) the world pregenerator state file.
 */
public class ResetPreGenState {
    /**
     * Deletes the pregenerator state file for a given world.
     *
     * @param plugin    Your plugin instance (used to find the data folder)
     * @param worldName The world whose state to reset
     * @return true if the file was deleted, false if not found or not deleted
     */
    public static boolean reset(JavaPlugin plugin, String worldName) {
        File file = new File(plugin.getDataFolder(), worldName + "_pregenerator.txt");
        return file.exists() && file.delete();
    }
}