package main;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static main.ConsoleColorUtils.*;

/**
 * Listener for player-related events to manage loaded chunks during pre-generation.
 */
public class PlayerEvents implements Listener {

	private final ConcurrentHashMap<Integer, PreGenerationTask> tasks;

	/**
	 * Initializes the listener with a map of pre-generation tasks.
	 *
	 * @param tasks a map of pre-generation tasks keyed by their identifiers
	 */
	public PlayerEvents(ConcurrentHashMap<Integer, PreGenerationTask> tasks) {
		this.tasks = tasks;
	}

	/**
	 * Updates loaded chunks when a player moves between chunks.
	 *
	 * @param event the player move event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	private void onPlayerMove(PlayerMoveEvent event) {
		try {
			Player player = event.getPlayer();
			Chunk fromChunk = event.getFrom().getChunk();
			Chunk toChunk = event.getTo().getChunk();
			if (fromChunk.equals(toChunk)) {
				return;
			}
			ChunkPos fromChunkPos = new ChunkPos(fromChunk.getX(), fromChunk.getZ());
			ChunkPos toChunkPos = new ChunkPos(toChunk.getX(), toChunk.getZ());
			UUID playerId = player.getUniqueId();
			synchronized (tasks) {
				for (PreGenerationTask task : tasks.values()) {
					task.playerChunkMap.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());
					Set<ChunkPos> playerChunks = task.playerChunkMap.get(playerId);
					boolean removed = playerChunks.remove(fromChunkPos);
					if (removed) {
						boolean stillLoaded = task.playerChunkMap.values().stream()
								.anyMatch(set -> set.contains(fromChunkPos));
						if (!stillLoaded) {
							task.playerLoadedChunks.remove(fromChunkPos);
						}
					}
					boolean added = playerChunks.add(toChunkPos);
					if (added) {
						task.playerLoadedChunks.add(toChunkPos);
					}
				}
			}
		} catch (Exception e) {
			exceptionMsg("Exception in onPlayerMove: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Cleans up loaded chunks when a player disconnects.
	 *
	 * @param event the player quit event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	private void onPlayerQuit(PlayerQuitEvent event) {
		try {
			Player player = event.getPlayer();
			UUID playerId = player.getUniqueId();
			synchronized (tasks) {
				for (PreGenerationTask task : tasks.values()) {
					Set<ChunkPos> playerChunks = task.playerChunkMap.remove(playerId);
					if (playerChunks != null) {
						for (ChunkPos chunkPos : playerChunks) {
							boolean stillLoaded = task.playerChunkMap.values().stream()
									.anyMatch(set -> set.contains(chunkPos));
							if (!stillLoaded) {
								task.playerLoadedChunks.remove(chunkPos);
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

	/**
	 * Updates loaded chunks when a player changes worlds.
	 *
	 * @param event the player changed world event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	private void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
		try {
			Player player = event.getPlayer();
			World newWorld = player.getWorld();
			Chunk toChunk = player.getLocation().getChunk();
			UUID playerId = player.getUniqueId();
			synchronized (tasks) {
				for (PreGenerationTask task : tasks.values()) {
					Set<ChunkPos> playerChunks = task.playerChunkMap.remove(playerId);
					if (playerChunks != null) {
						for (ChunkPos chunkPos : playerChunks) {
							boolean stillLoaded = task.playerChunkMap.values().stream()
									.anyMatch(set -> set.contains(chunkPos));
							if (!stillLoaded) {
								task.playerLoadedChunks.remove(chunkPos);
							}
						}
					}
					if (task.world.equals(newWorld)) {
						task.playerChunkMap.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());
						Set<ChunkPos> newPlayerChunks = task.playerChunkMap.get(playerId);
						ChunkPos toChunkPos = new ChunkPos(toChunk.getX(), toChunk.getZ());
						if (newPlayerChunks.add(toChunkPos)) {
							task.playerLoadedChunks.add(toChunkPos);
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