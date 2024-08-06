package main;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.*;
import java.util.logging.Level;

public class CustomConfig {
    private final JavaPlugin plugin;
    private final Path configFilePath;

    public CustomConfig(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.configFilePath = Paths.get(plugin.getDataFolder().getPath(), fileName);
    }

    public void saveConfig(String content) {
        try (BufferedWriter writer = Files.newBufferedWriter(configFilePath)) {
            writer.write(content);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config", e);
        }
    }

    public String loadConfig() {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = Files.newBufferedReader(configFilePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load config", e);
        }
        return content.toString();
    }
}