package main;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

import static main.ConsoleColorUtils.*;

/**
 * Listener for player-related events to manage loaded chunks during pre-generation.
 */
public class PlayerEvents implements Listener {

	private final Int2ObjectOpenHashMap<PreGenerationTask> tasks;
	private final Object tasksLock;

	public PlayerEvents(Int2ObjectOpenHashMap<PreGenerationTask> tasks, Object tasksLock) {
		this.tasks = tasks;
		this.tasksLock = tasksLock;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	private void onPlayerMove(PlayerMoveEvent event) {
		try {
			Location from = event.getFrom();
			Location to = event.getTo();
			if (to == null || from.getWorld() != to.getWorld()) return;

			int fromChunkX = from.getBlockX() >> 4;
			int fromChunkZ = from.getBlockZ() >> 4;
			int toChunkX = to.getBlockX() >> 4;
			int toChunkZ = to.getBlockZ() >> 4;
			if (fromChunkX == toChunkX && fromChunkZ == toChunkZ) return;

			Player player = event.getPlayer();
			PreGenerationTask task = taskForWorld(player.getWorld());
			if (task == null || !task.enabled) return;

			long fromKey = MortonCode.encode(fromChunkX, fromChunkZ);
			long toKey = MortonCode.encode(toChunkX, toChunkZ);
			UUID playerId = player.getUniqueId();

			synchronized (task.playerChunkLock) {
				LongOpenHashSet set = getOrCreatePlayerChunks(task, playerId);
				removeTrackedChunk(task, set, fromKey);
				addTrackedChunk(task, set, toKey);
			}
		} catch (Exception e) {
			exceptionMsg("Exception in onPlayerMove: " + e.getMessage());
			e.printStackTrace();
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	private void onPlayerQuit(PlayerQuitEvent event) {
		try {
			UUID playerId = event.getPlayer().getUniqueId();
			PreGenerationTask[] snapshot = taskSnapshot();
			for (PreGenerationTask task : snapshot) {
				synchronized (task.playerChunkLock) {
					removePlayerFromTask(task, playerId);
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
			Location location = player.getLocation();
			UUID playerId = player.getUniqueId();
			long toKey = MortonCode.encode(location.getBlockX() >> 4, location.getBlockZ() >> 4);

			PreGenerationTask[] snapshot = taskSnapshot();
			for (PreGenerationTask task : snapshot) {
				synchronized (task.playerChunkLock) {
					removePlayerFromTask(task, playerId);

					if (task.enabled && task.world.equals(newWorld)) {
						LongOpenHashSet set = getOrCreatePlayerChunks(task, playerId);
						addTrackedChunk(task, set, toKey);
					}
				}
			}
		} catch (Exception e) {
			exceptionMsg("Exception in onPlayerChangedWorld: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private PreGenerationTask taskForWorld(World world) {
		int worldId = WorldIdManager.getWorldId(world);
		synchronized (tasksLock) {
			return tasks.get(worldId);
		}
	}

	private PreGenerationTask[] taskSnapshot() {
		synchronized (tasksLock) {
			return tasks.values().toArray(new PreGenerationTask[0]);
		}
	}

	private static LongOpenHashSet getOrCreatePlayerChunks(PreGenerationTask task, UUID playerId) {
		LongOpenHashSet set = task.playerChunkMap.get(playerId);
		if (set == null) {
			set = new LongOpenHashSet(4);
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
