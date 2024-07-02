package main;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class Chunker extends JavaPlugin implements Listener {

	@Override public void onEnable() {
		getServer().getPluginManager().registerEvents(this, this);
		PreGenerator PG = new PreGenerator(this);
		getServer().getPluginManager().registerEvents(PG, this);
		PreGeneratorCommands cs = new PreGeneratorCommands(PG);
		getCommand("pregen").setExecutor(cs);
		getCommand("pregen").setTabCompleter(cs);
		getCommand("pregenoff").setExecutor(cs);
	}
}