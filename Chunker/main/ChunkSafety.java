package main;

import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;

/**
 * Utility class to safely preload, test-load (may or may not be used later), and unload chunks using dummy entity teleportation.
 * Useful for debugging or forcing chunk generation before normal operations.
 */
public class ChunkSafety {

	/**
	 * Spawns a dummy ArmorStand at a safe "home" location and runs two teleport-in-and-out cycles
	 * to pre-load a target chunk and then unload it. After that, the chunk is loaded again officially
	 * (with saving enabled) to ensure it was generated and stable. Dummy gets removed after all steps.
	 *
	 * @param plugin   the plugin instance used for scheduling tasks and logging
	 * @param world    the world the chunk is in
	 * @param home     safe home location to return the dummy to
	 * @param chunkPos target chunk position to prepare
	 */
	public static void spawnDummyAndProcess(Plugin plugin, World world, Location home, ChunkPos chunkPos) {
		Entity dummy = world.spawnEntity(home, EntityType.ARMOR_STAND);

		doTeleportCycles(dummy, home, chunkPos, world, plugin)
		.thenCompose(success -> {
			if (!success) {
				removeDummy(dummy, plugin);
				return CompletableFuture.failedFuture(new RuntimeException("Teleport cycle failed"));
			}
			return world.getChunkAtAsync(chunkPos.getX(), chunkPos.getZ(), true);
		})
		.thenAccept(chunk -> {
			if (chunk != null && chunk.isLoaded()) {
				chunk.unload(true);
			}
			removeDummy(dummy, plugin);
		})
		.exceptionally(ex -> {
			removeDummy(dummy, plugin);
			return null;
		});
	}

	/**
	 * Runs two full teleport-in and teleport-out cycles using the dummy entity.
	 * Each teleport-in loads the chunk. Each teleport-out is followed by a chunk unload (without saving).
	 * This helps ensure chunk generation is triggered and any chunk failures show early.
	 *
	 * @param dummy  dummy entity used for teleporting
	 * @param home   safe return location
	 * @param cp     target chunk position
	 * @param world  world where the chunk is
	 * @param plugin plugin instance for running main-thread tasks
	 * @return future that completes with true if all teleport steps succeeded
	 */
	private static CompletableFuture<Boolean> doTeleportCycles(
			Entity dummy,
			Location home,
			ChunkPos cp,
			World world,
			Plugin plugin
			) {
		double x = cp.getX() * 16 + 0.5;
		double z = cp.getZ() * 16 + 0.5;
		int y = world.getHighestBlockYAt(cp.getX(), cp.getZ()) + 1;
		Location target = new Location(world, x, y, z);

		return tryTeleport(dummy, target, true, 2).thenCompose(ok1 -> {
			if (!ok1) return CompletableFuture.completedFuture(false);
			return tryTeleport(dummy, home, false, 2).thenApply(ok2 -> {
				if (!ok2) return false;
				unloadChunkSync(cp, world, false, plugin);
				return true;
			});
		}).thenCompose(success1 -> {
			if (!success1) return CompletableFuture.completedFuture(false);
			return tryTeleport(dummy, target, true, 2).thenCompose(ok3 -> {
				if (!ok3) return CompletableFuture.completedFuture(false);
				return tryTeleport(dummy, home, false, 2).thenApply(ok4 -> {
					if (!ok4) return false;
					unloadChunkSync(cp, world, false, plugin);
					return true;
				});
			});
		});
	}

	/**
	 * Retries teleport attempts a few times before giving up. Used to reduce chance of silent failure.
	 *
	 * @param entity         entity to teleport
	 * @param loc            target location
	 * @param withPassengers true if it should keep vehicle/passenger data
	 * @param retries        how many attempts to try
	 * @return future with true if successful
	 */
	private static CompletableFuture<Boolean> tryTeleport(Entity entity, Location loc, boolean withPassengers, int retries) {
		CompletableFuture<Boolean> future = new CompletableFuture<>();
		CompletableFuture<Boolean> base = withPassengers
				? teleportAsyncWithPassengers(entity, loc)
						: teleportAsync(entity, loc);

		base.thenAccept(success -> {
			if (success) {
				future.complete(true);
			} else if (retries > 0) {
				Bukkit.getScheduler().runTaskLater(entity.getServer().getPluginManager().getPlugins()[0],
						() -> tryTeleport(entity, loc, withPassengers, retries - 1).thenAccept(future::complete),
						1L);
			} else {
				future.complete(false);
			}
		}).exceptionally(ex -> {
			future.complete(false);
			return null;
		});

		return future;
	}

	/**
	 * Async teleport using default Paper method (no passenger or vehicle flags).
	 *
	 * @param entity   entity to teleport
	 * @param location destination location
	 * @return future that completes with true if teleport succeeded
	 */
	public static CompletableFuture<Boolean> teleportAsync(Entity entity, Location location) {
		return entity.teleportAsync(location);
	}

	/**
	 * Async teleport with passenger and vehicle retention. Used to better simulate "real" teleports.
	 *
	 * @param entity   entity to teleport
	 * @param location destination location
	 * @return future that completes with true if teleport succeeded
	 */
	public static CompletableFuture<Boolean> teleportAsyncWithPassengers(Entity entity, Location location) {
		return entity.teleportAsync(
				location,
				TeleportCause.PLUGIN,
				TeleportFlag.EntityState.RETAIN_PASSENGERS,
				TeleportFlag.EntityState.RETAIN_VEHICLE
				);
	}

	/**
	 * Runs a chunk unload on the main thread. Used after dummy leaves the chunk.
	 *
	 * @param cp     target chunk
	 * @param world  world containing the chunk
	 * @param save   whether to save changes before unload
	 * @param plugin plugin instance to schedule task
	 */
	private static void unloadChunkSync(ChunkPos cp, World world, boolean save, Plugin plugin) {
		Bukkit.getScheduler().runTask(plugin, () -> {
			Chunk chunk = world.getChunkAt(cp.getX(), cp.getZ());
			if (chunk.isLoaded()) chunk.unload(save);
		});
	}

	/**
	 * Removes the dummy entity safely on the main thread.
	 */
	private static void removeDummy(Entity dummy, Plugin plugin) {
		Bukkit.getScheduler().runTask(plugin, dummy::remove);
	}
}