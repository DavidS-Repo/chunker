package main;

import org.bukkit.World;

import java.util.concurrent.CompletableFuture;

/**
 * Modern Paper chunk safety helper.
 * Uses the urgent async chunk API as the generation authority, then saves and unloads the completed chunk.
 */
public final class ChunkSafety {

	private ChunkSafety() {
	}

	/**
	 * Generates a chunk through Paper's urgent async chunk loader, then unloads it with saving enabled.
	 *
	 * @param world    world containing the chunk
	 * @param chunkPos target chunk position
	 * @param gen      whether missing chunks should be generated
	 * @return future that completes after the generated chunk has been saved/unloaded
	 */
	public static CompletableFuture<Void> generateAndUnload(World world, ChunkPos chunkPos, boolean gen) {
		return world.getChunkAtAsync(chunkPos.getX(), chunkPos.getZ(), gen, true).thenAccept(chunk -> {
			if (chunk != null && chunk.isLoaded()) {
				chunk.unload(true);
			}
		});
	}
}