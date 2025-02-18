package main;

import org.bukkit.World;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages unique identifiers for worlds based on their UUIDs.
 */
public class WorldIdManager {
	private static final AtomicInteger idCounter = new AtomicInteger(0);
	private static final ConcurrentHashMap<Long, Integer> worldIdMap = new ConcurrentHashMap<>();

	/**
	 * Retrieves a unique integer ID for the given world.
	 *
	 * @param world the world for which to get the ID
	 * @return the unique integer ID for the world
	 */
	public static int getWorldId(World world) {
		long worldKey = world.getUID().getMostSignificantBits() ^ world.getUID().getLeastSignificantBits();
		return worldIdMap.computeIfAbsent(worldKey, key -> idCounter.getAndIncrement());
	}
}