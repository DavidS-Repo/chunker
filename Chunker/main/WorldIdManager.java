package main;

import org.bukkit.NamespacedKey;
import org.bukkit.World;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages unique task IDs for dimensions based on their canonical world keys.
 */
public class WorldIdManager {
	private static final AtomicInteger idCounter = new AtomicInteger();
	private static final ConcurrentHashMap<NamespacedKey, Integer> worldIdMap = new ConcurrentHashMap<>();

	/**
	 * Retrieves a unique integer ID for the given world.
	 *
	 * @param world the world for which to get the ID
	 * @return the unique integer ID for the world
	 */
	public static int getWorldId(World world) {
		NamespacedKey worldKey = world.getKey();
		Integer existingId = worldIdMap.get(worldKey);
		if (existingId != null) {
			return existingId;
		}

		int newId = idCounter.getAndIncrement();
		Integer racedId = worldIdMap.putIfAbsent(worldKey, newId);
		return racedId != null ? racedId : newId;
	}
}
