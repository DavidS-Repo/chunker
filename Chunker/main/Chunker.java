package main;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class Chunker extends JavaPlugin implements Listener {
	private PluginSettings settings;

	@Override
	public void onEnable() {
		getServer().getPluginManager().registerEvents(this, this);
		settings = new PluginSettings(this);

		PreGenerator preGenerator;
		try {
			preGenerator = new PreGenerator(this);
		} catch (RuntimeException e) {
			e.printStackTrace();
			getLogger().severe("Failed to initialize PreGenerator: " + e.getMessage());
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		getServer().getPluginManager().registerEvents(preGenerator, this);

		PreGeneratorCommands preGeneratorCommands = new PreGeneratorCommands(preGenerator, settings, this);
		getCommand("pregen").setExecutor(preGeneratorCommands);
		getCommand("pregen").setTabCompleter(preGeneratorCommands);
		getCommand("pregenoff").setExecutor(preGeneratorCommands);
		getCommand("pregenoff").setTabCompleter(preGeneratorCommands);

		new ServerStateManager(this, preGeneratorCommands);
	}
}