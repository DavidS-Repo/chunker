package main;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;

/**
 * Chunk area submitted by a pregeneration command.
 *
 * Minecraft may create up to two neighbor chunks beyond the chunks directly requested during generation.
 * Chunker compensates by submitting chunks two chunks inside the requested edge, when the target
 * area is large enough, so the final saved area is closer to the requested radius.
 */
public final class GenerationTarget {
	private static final int GENERATION_EDGE_MARGIN_CHUNKS = 2;

	public final long totalChunks;
	public final int centerBlockX;
	public final int centerBlockZ;
	public final int centerChunkX;
	public final int centerChunkZ;
	public final int minChunkX;
	public final int maxChunkX;
	public final int minChunkZ;
	public final int maxChunkZ;

	private GenerationTarget(
			long totalChunks,
			int centerBlockX,
			int centerBlockZ,
			int centerChunkX,
			int centerChunkZ,
			int minChunkX,
			int maxChunkX,
			int minChunkZ,
			int maxChunkZ) {
		this.totalChunks = totalChunks;
		this.centerBlockX = centerBlockX;
		this.centerBlockZ = centerBlockZ;
		this.centerChunkX = centerChunkX;
		this.centerChunkZ = centerChunkZ;
		this.minChunkX = minChunkX;
		this.maxChunkX = maxChunkX;
		this.minChunkZ = minChunkZ;
		this.maxChunkZ = maxChunkZ;
	}

	public static GenerationTarget fromInput(World world, String input) {
		if (input == null) return null;
		String trimmed = input.trim();
		if (trimmed.equalsIgnoreCase("default")) {
			return fromWorldBorder(world);
		}
		if (trimmed.length() < 2) return null;

		long amount;
		char unit;
		try {
			amount = Long.parseLong(trimmed.substring(0, trimmed.length() - 1));
			unit = Character.toLowerCase(trimmed.charAt(trimmed.length() - 1));
		} catch (NumberFormatException e) {
			return null;
		}
		if (amount <= 0L) return null;

		Location center = resolveCenterLocation(world);
		int centerBlockX = center.getBlockX();
		int centerBlockZ = center.getBlockZ();
		int centerChunkX = centerBlockX >> 4;
		int centerChunkZ = centerBlockZ >> 4;

		try {
			return switch (unit) {
			case 'b' -> fromBlockRadius(centerBlockX, centerBlockZ, amount);
			case 'c' -> fromChunkRadius(centerBlockX, centerBlockZ, centerChunkX, centerChunkZ, amount);
			case 'r' -> fromChunkRadius(centerBlockX, centerBlockZ, centerChunkX, centerChunkZ, Math.multiplyExact(amount, 32L));
			default -> null;
			};
		} catch (ArithmeticException e) {
			return null;
		}
	}

	private static GenerationTarget fromBlockRadius(int centerBlockX, int centerBlockZ, long radiusBlocks) {
		long minBlockX = (long) centerBlockX - radiusBlocks;
		long maxBlockX = (long) centerBlockX + radiusBlocks - 1L;
		long minBlockZ = (long) centerBlockZ - radiusBlocks;
		long maxBlockZ = (long) centerBlockZ + radiusBlocks - 1L;

		int minChunkX = floorBlockToChunk(minBlockX);
		int maxChunkX = floorBlockToChunk(maxBlockX);
		int minChunkZ = floorBlockToChunk(minBlockZ);
		int maxChunkZ = floorBlockToChunk(maxBlockZ);
		int centerChunkX = centerBlockX >> 4;
		int centerChunkZ = centerBlockZ >> 4;
		return fromBounds(centerBlockX, centerBlockZ, centerChunkX, centerChunkZ, minChunkX, maxChunkX, minChunkZ, maxChunkZ);
	}

	private static GenerationTarget fromChunkRadius(int centerBlockX, int centerBlockZ, int centerChunkX, int centerChunkZ, long radiusChunks) {
		long minChunkX = (long) centerChunkX - radiusChunks;
		long maxChunkX = (long) centerChunkX + radiusChunks - 1L;
		long minChunkZ = (long) centerChunkZ - radiusChunks;
		long maxChunkZ = (long) centerChunkZ + radiusChunks - 1L;
		return fromBounds(
				centerBlockX,
				centerBlockZ,
				centerChunkX,
				centerChunkZ,
				toInt(minChunkX),
				toInt(maxChunkX),
				toInt(minChunkZ),
				toInt(maxChunkZ));
	}

	private static GenerationTarget fromWorldBorder(World world) {
		WorldBorder border = world.getWorldBorder();
		Location borderCenter = border.getCenter();
		if (borderCenter == null) {
			borderCenter = world.getSpawnLocation();
		}
		double halfBlocks = border.getSize() * 0.5D;

		int minChunkX = chunkAtBlock(borderCenter.getX() - halfBlocks);
		int maxChunkX = chunkAtBlock(Math.nextDown(borderCenter.getX() + halfBlocks));
		int minChunkZ = chunkAtBlock(borderCenter.getZ() - halfBlocks);
		int maxChunkZ = chunkAtBlock(Math.nextDown(borderCenter.getZ() + halfBlocks));

		Location spiralCenter = resolveCenterLocation(world);
		int centerBlockX = spiralCenter.getBlockX();
		int centerBlockZ = spiralCenter.getBlockZ();
		int centerChunkX = centerBlockX >> 4;
		int centerChunkZ = centerBlockZ >> 4;
		return fromBounds(centerBlockX, centerBlockZ, centerChunkX, centerChunkZ, minChunkX, maxChunkX, minChunkZ, maxChunkZ);
	}

	private static GenerationTarget fromBounds(
			int centerBlockX,
			int centerBlockZ,
			int centerChunkX,
			int centerChunkZ,
			int minChunkX,
			int maxChunkX,
			int minChunkZ,
			int maxChunkZ) {
		long chunksX = (long) maxChunkX - minChunkX + 1L;
		long chunksZ = (long) maxChunkZ - minChunkZ + 1L;
		if (chunksX <= 0L || chunksZ <= 0L) return null;

		if (canApplyEdgeMargin(chunksX, chunksZ)) {
			minChunkX = Math.addExact(minChunkX, GENERATION_EDGE_MARGIN_CHUNKS);
			maxChunkX = Math.subtractExact(maxChunkX, GENERATION_EDGE_MARGIN_CHUNKS);
			minChunkZ = Math.addExact(minChunkZ, GENERATION_EDGE_MARGIN_CHUNKS);
			maxChunkZ = Math.subtractExact(maxChunkZ, GENERATION_EDGE_MARGIN_CHUNKS);
			chunksX = (long) maxChunkX - minChunkX + 1L;
			chunksZ = (long) maxChunkZ - minChunkZ + 1L;
		}

		long totalChunks = Math.multiplyExact(chunksX, chunksZ);
		return new GenerationTarget(totalChunks, centerBlockX, centerBlockZ, centerChunkX, centerChunkZ, minChunkX, maxChunkX, minChunkZ, maxChunkZ);
	}

	private static boolean canApplyEdgeMargin(long chunksX, long chunksZ) {
		long requiredSize = Math.multiplyExact(GENERATION_EDGE_MARGIN_CHUNKS, 2L) + 1L;
		return chunksX >= requiredSize && chunksZ >= requiredSize;
	}

	private static Location resolveCenterLocation(World world) {
		PluginSettings.WorldSettings worldSettings = PluginSettings.getWorldSettings(world);
		String centerSetting = worldSettings.center();

		if (centerSetting == null) return defaultCenter(world);

		String trimmed = centerSetting.trim();
		if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("default")) return defaultCenter(world);
		if (trimmed.equals("~ ~")) return world.getSpawnLocation();

		int split = firstWhitespace(trimmed);
		if (split < 0) return defaultCenter(world);

		try {
			double x = Double.parseDouble(trimmed.substring(0, split));
			double z = Double.parseDouble(trimmed.substring(skipWhitespace(trimmed, split)));
			return new Location(world, x, world.getSpawnLocation().getY(), z);
		} catch (NumberFormatException e) {
			return defaultCenter(world);
		}
	}

	private static Location defaultCenter(World world) {
		Location centerLocation = world.getWorldBorder().getCenter();
		return centerLocation != null ? centerLocation : world.getSpawnLocation();
	}

	private static int firstWhitespace(String value) {
		for (int i = 0, len = value.length(); i < len; i++) {
			if (Character.isWhitespace(value.charAt(i))) return i;
		}
		return -1;
	}

	private static int skipWhitespace(String value, int index) {
		int i = index;
		int len = value.length();
		while (i < len && Character.isWhitespace(value.charAt(i))) i++;
		return i;
	}

	private static int chunkAtBlock(double blockCoordinate) {
		return (int) Math.floor(blockCoordinate / 16.0D);
	}

	private static int floorBlockToChunk(long blockCoordinate) {
		long chunk = Math.floorDiv(blockCoordinate, 16L);
		return toInt(chunk);
	}

	private static int toInt(long value) {
		if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
			throw new ArithmeticException("Coordinate outside int range: " + value);
		}
		return (int) value;
	}
}
