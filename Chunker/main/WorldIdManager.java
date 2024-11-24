package main;

import org.bukkit.World;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class WorldIdManager {
	private static final AtomicInteger idCounter = new AtomicInteger(0);
	private static final ConcurrentHashMap<Long, Integer> worldIdMap = new ConcurrentHashMap<>();

	public static int getWorldId(World world) {
		long worldKey = world.getUID().getMostSignificantBits() ^ world.getUID().getLeastSignificantBits();
		return worldIdMap.computeIfAbsent(worldKey, key -> idCounter.getAndIncrement());
	}
}