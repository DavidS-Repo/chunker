package main;

import org.bukkit.World;

import java.util.concurrent.CompletableFuture;

/**
 * Modern Paper chunk safety helper.
 * Uses the urgent async chunk API as the generation authority, then queues the completed chunk for unload.
 */
public final class ChunkSafety {

	private ChunkSafety() {
	}

	/**
	 * Generates a chunk through Paper's urgent async chunk loader, then queues it for unload.
	 *
	 * @param world  world containing the chunk
	 * @param chunkX target chunk x
	 * @param chunkZ target chunk z
	 * @param gen    whether missing chunks should be generated
	 * @return future that completes after the unload request has been queued
	 */
	public static CompletableFuture<Void> generateAndUnload(World world, int chunkX, int chunkZ, boolean gen) {
		return world.getChunkAtAsync(chunkX, chunkZ, gen, true).thenAccept(chunk -> {
			if (chunk != null && chunk.isLoaded()) {
				world.unloadChunkRequest(chunkX, chunkZ);
			}
		});
	}

	/**
	 * Compatibility overload for callers that still use ChunkPos.
	 */
	public static CompletableFuture<Void> generateAndUnload(World world, ChunkPos chunkPos, boolean gen) {
		return generateAndUnload(world, chunkPos.getX(), chunkPos.getZ(), gen);
	}
}
