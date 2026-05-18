package main;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static main.ConsoleColorUtils.*;

/**
 * Listener for player-related events to manage loaded chunks during pre-generation.
 */
public class PlayerEvents implements Listener {

	private final ConcurrentHashMap<Integer, PreGenerationTask> tasks;

	public PlayerEvents(ConcurrentHashMap<Integer, PreGenerationTask> tasks) {
		this.tasks = tasks;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	private void onPlayerMove(PlayerMoveEvent event) {
		try {
			Player player = event.getPlayer();
			Chunk fromChunk = event.getFrom().getChunk();
			Chunk toChunk = event.getTo().getChunk();
			if (fromChunk.equals(toChunk)) return;

			long fromKey = MortonCode.encode(fromChunk.getX(), fromChunk.getZ());
			long toKey = MortonCode.encode(toChunk.getX(), toChunk.getZ());
			UUID playerId = player.getUniqueId();

			synchronized (tasks) {
				for (PreGenerationTask task : tasks.values()) {
					if (!task.enabled || !task.world.equals(player.getWorld())) continue;

					synchronized (task.playerChunkLock) {
						LongOpenHashSet set = getOrCreatePlayerChunks(task, playerId);
						removeTrackedChunk(task, set, fromKey);
						addTrackedChunk(task, set, toKey);
					}
				}
			}
		} catch (Exception e) {
			exceptionMsg("Exception in onPlayerMove: " + e.getMessage());
			e.printStackTrace();
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	private void onPlayerQuit(PlayerQuitEvent event) {
		try {
			Player player = event.getPlayer();
			UUID playerId = player.getUniqueId();

			synchronized (tasks) {
				for (PreGenerationTask task : tasks.values()) {
					synchronized (task.playerChunkLock) {
						removePlayerFromTask(task, playerId);
					}
				}
			}
		} catch (Exception e) {
			exceptionMsg("Exception in onPlayerQuit: " + e.getMessage());
			e.printStackTrace();
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	private void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
		try {
			Player player = event.getPlayer();
			World newWorld = player.getWorld();
			Chunk toChunk = player.getLocation().getChunk();
			UUID playerId = player.getUniqueId();
			long toKey = MortonCode.encode(toChunk.getX(), toChunk.getZ());

			synchronized (tasks) {
				for (PreGenerationTask task : tasks.values()) {
					synchronized (task.playerChunkLock) {
						removePlayerFromTask(task, playerId);

						if (task.world.equals(newWorld)) {
							LongOpenHashSet set = getOrCreatePlayerChunks(task, playerId);
							addTrackedChunk(task, set, toKey);
						}
					}
				}
			}
		} catch (Exception e) {
			exceptionMsg("Exception in onPlayerChangedWorld: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private static LongOpenHashSet getOrCreatePlayerChunks(PreGenerationTask task, UUID playerId) {
		LongOpenHashSet set = task.playerChunkMap.get(playerId);
		if (set == null) {
			set = new LongOpenHashSet();
			task.playerChunkMap.put(playerId, set);
		}
		return set;
	}

	private static void addTrackedChunk(PreGenerationTask task, LongOpenHashSet set, long key) {
		if (!set.add(key)) return;

		int count = task.playerChunkRefCount.get(key);
		if (count == 0) {
			task.playerLoadedChunks.add(key);
			task.playerChunkRefCount.put(key, 1);
			return;
		}
		task.playerChunkRefCount.put(key, count + 1);
	}

	private static void removeTrackedChunk(PreGenerationTask task, LongOpenHashSet set, long key) {
		if (!set.remove(key)) return;

		decrementTrackedChunk(task, key);
	}

	private static void removePlayerFromTask(PreGenerationTask task, UUID playerId) {
		LongOpenHashSet set = task.playerChunkMap.remove(playerId);
		if (set == null) return;

		for (long key : set) {
			decrementTrackedChunk(task, key);
		}
	}

	private static void decrementTrackedChunk(PreGenerationTask task, long key) {
		int count = task.playerChunkRefCount.get(key) - 1;
		if (count <= 0) {
			task.playerChunkRefCount.remove(key);
			task.playerLoadedChunks.remove(key);
			return;
		}
		task.playerChunkRefCount.put(key, count);
	}
}
