package main;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves 26.1+ worlds by their canonical dimension keys.
 */
public final class WorldRegistry {
	private static final String DIMENSIONS_DIR = "dimensions";
	private static final String STATE_SUFFIX = "_pregenerator.txt";

	private WorldRegistry() {
	}

	public static String id(World world) {
		return keyString(world.getKey());
	}

	public static String keyString(NamespacedKey key) {
		return key.getNamespace() + ":" + key.getKey();
	}

	public static List<String> discoverWorldIds(JavaPlugin plugin) {
		LinkedHashSet<String> ids = new LinkedHashSet<>();

		for (World world : Bukkit.getWorlds()) {
			ids.add(id(world));
		}

		File[] rootFolders = plugin.getServer().getWorldContainer().listFiles(File::isDirectory);
		if (rootFolders == null) {
			return new ArrayList<>(ids);
		}

		for (File rootFolder : rootFolders) {
			if (!new File(rootFolder, "level.dat").isFile()) continue;
			scanDimensionFolder(rootFolder, ids);
		}

		return new ArrayList<>(ids);
	}

	public static List<String> worldSuggestions(JavaPlugin plugin) {
		return discoverWorldIds(plugin);
	}

	public static World resolveWorld(String input, boolean loadIfMissing) {
		NamespacedKey key = parseKey(input);
		if (key == null) return null;

		World world = Bukkit.getWorld(key);
		return world != null || !loadIfMissing ? world : WorldCreator.ofKey(key).createWorld();
	}

	public static File stateFile(JavaPlugin plugin, World world) {
		return stateFile(plugin, id(world));
	}

	public static File stateFile(JavaPlugin plugin, String worldId) {
		return new File(plugin.getDataFolder(), safeFileBase(worldId) + STATE_SUFFIX);
	}

	public static List<File> stateFiles(JavaPlugin plugin, World world) {
		return List.of(stateFile(plugin, world));
	}

	public static List<File> stateFiles(JavaPlugin plugin, String worldId) {
		return List.of(stateFile(plugin, worldId));
	}

	public static List<File> stateFilesForInput(JavaPlugin plugin, String input) {
		NamespacedKey key = parseKey(input);
		if (key == null) return List.of();
		return List.of(stateFile(plugin, keyString(key)));
	}

	public static List<String> pregeneratorStateIds(JavaPlugin plugin) {
		LinkedHashSet<String> result = new LinkedHashSet<>();
		for (String worldId : discoverWorldIds(plugin)) {
			if (stateFile(plugin, worldId).isFile()) {
				result.add(worldId);
			}
		}
		return new ArrayList<>(result);
	}

	private static void scanDimensionFolder(File rootFolder, Set<String> ids) {
		File dimensionsFolder = new File(rootFolder, DIMENSIONS_DIR);
		File[] namespaceFolders = dimensionsFolder.listFiles(File::isDirectory);
		if (namespaceFolders == null) return;

		for (File namespaceFolder : namespaceFolders) {
			String namespace = namespaceFolder.getName();
			scanDimensionNamespace(namespaceFolder.toPath(), namespaceFolder, namespace, ids);
		}
	}

	private static void scanDimensionNamespace(Path namespaceRoot, File folder, String namespace, Set<String> ids) {
		if (isDimensionFolder(folder)) {
			String key = namespaceRoot.relativize(folder.toPath()).toString().replace(File.separatorChar, '/');
			if (!key.isEmpty()) {
				ids.add(namespace + ":" + key);
			}
			return;
		}

		File[] children = folder.listFiles(File::isDirectory);
		if (children == null) return;

		for (File child : children) {
			scanDimensionNamespace(namespaceRoot, child, namespace, ids);
		}
	}

	private static boolean isDimensionFolder(File folder) {
		return new File(folder, "paper-world.yml").isFile()
				|| new File(folder, "region").isDirectory()
				|| new File(folder, "poi").isDirectory()
				|| new File(folder, "entities").isDirectory()
				|| new File(folder, "data").isDirectory();
	}

	private static NamespacedKey parseKey(String input) {
		String trimmed = trimToNull(input);
		if (trimmed == null || trimmed.indexOf(':') < 0) return null;

		try {
			return NamespacedKey.fromString(trimmed);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static String safeFileBase(String value) {
		String trimmed = trimToNull(value);
		if (trimmed == null) return "unknown";

		StringBuilder builder = new StringBuilder(trimmed.length());
		for (int i = 0; i < trimmed.length(); i++) {
			char c = trimmed.charAt(i);
			if ((c >= 'a' && c <= 'z')
					|| (c >= 'A' && c <= 'Z')
					|| (c >= '0' && c <= '9')
					|| c == '.'
					|| c == '_'
					|| c == '-') {
				builder.append(c);
			} else {
				builder.append('_');
			}
		}
		return builder.toString();
	}

	private static String trimToNull(String value) {
		if (value == null) return null;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}