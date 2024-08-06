package main;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class Chunker extends JavaPlugin implements Listener {
	private PluginSettings settings;

	@Override public void onEnable() {
		getServer().getPluginManager().registerEvents(this, this);
		settings = new PluginSettings(this);
		PreGenerator preGenerator = new PreGenerator(this);
		getServer().getPluginManager().registerEvents(preGenerator, this);
		PreGeneratorCommands preGeneratorCommands = new PreGeneratorCommands(preGenerator, settings);
		getCommand("pregen").setExecutor(preGeneratorCommands);
		getCommand("pregen").setTabCompleter(preGeneratorCommands);
		getCommand("pregenoff").setExecutor(preGeneratorCommands);
		new ServerStateManager(this, preGeneratorCommands);
	}
}