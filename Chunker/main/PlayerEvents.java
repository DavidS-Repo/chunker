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
					synchronized (task.playerChunkLock) {
						LongOpenHashSet set = task.playerChunkMap.get(playerId);
						if (set == null) {
							set = new LongOpenHashSet();
							task.playerChunkMap.put(playerId, set);
						}
						if (set.remove(fromKey)) {
							int c = task.playerChunkRefCount.get(fromKey) - 1;
							if (c <= 0) {
								task.playerChunkRefCount.remove(fromKey);
								task.playerLoadedChunks.remove(fromKey);
							} else {
								task.playerChunkRefCount.put(fromKey, c);
							}
						}
						if (set.add(toKey)) {
							int c = task.playerChunkRefCount.get(toKey);
							if (c == 0) {
								task.playerLoadedChunks.add(toKey);
								task.playerChunkRefCount.put(toKey, 1);
							} else {
								task.playerChunkRefCount.put(toKey, c + 1);
							}
						}
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
						LongOpenHashSet set = task.playerChunkMap.remove(playerId);
						if (set == null) continue;

						for (long key : set) {
							int c = task.playerChunkRefCount.get(key) - 1;
							if (c <= 0) {
								task.playerChunkRefCount.remove(key);
								task.playerLoadedChunks.remove(key);
							} else {
								task.playerChunkRefCount.put(key, c);
							}
						}
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
						LongOpenHashSet oldSet = task.playerChunkMap.remove(playerId);
						if (oldSet != null) {
							for (long key : oldSet) {
								int c = task.playerChunkRefCount.get(key) - 1;
								if (c <= 0) {
									task.playerChunkRefCount.remove(key);
									task.playerLoadedChunks.remove(key);
								} else {
									task.playerChunkRefCount.put(key, c);
								}
							}
						}
						if (task.world.equals(newWorld)) {
							LongOpenHashSet set = task.playerChunkMap.get(playerId);
							if (set == null) {
								set = new LongOpenHashSet();
								task.playerChunkMap.put(playerId, set);
							}
							if (set.add(toKey)) {
								int c = task.playerChunkRefCount.get(toKey);
								if (c == 0) {
									task.playerLoadedChunks.add(toKey);
									task.playerChunkRefCount.put(toKey, 1);
								} else {
									task.playerChunkRefCount.put(toKey, c + 1);
								}
							}
						}
					}
				}
			}
		} catch (Exception e) {
			exceptionMsg("Exception in onPlayerChangedWorld: " + e.getMessage());
			e.printStackTrace();
		}
	}
}
