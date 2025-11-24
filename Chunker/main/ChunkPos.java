package main;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * Represents the (x, z) coordinates of a chunk along with its Morton code.
 */
public final class ChunkPos {
	private final int x;
	private final int z;
	private final long mortonCode;

	private static final Cache<Long, ChunkPos> CACHE =
			CacheBuilder.newBuilder()
			.maximumSize(8192)
			.build();

	private ChunkPos(int x, int z, long mortonCode) {
		this.x = x;
		this.z = z;
		this.mortonCode = mortonCode;
	}

	/**
	 * Returns a cached instance of ChunkPos for the given coordinates.
	 */
	public static ChunkPos get(int x, int z) {
		long morton = MortonCode.encode(x, z);
		ChunkPos cached = CACHE.getIfPresent(morton);
		if (cached != null) {
			return cached;
		}
		ChunkPos pos = new ChunkPos(x, z, morton);
		CACHE.put(morton, pos);
		return pos;
	}

	/**
	 * Returns a cached instance of ChunkPos for the given Morton code.
	 */
	public static ChunkPos fromMorton(long mortonCode) {
		ChunkPos cached = CACHE.getIfPresent(mortonCode);
		if (cached != null) {
			return cached;
		}
		int x = MortonCode.getX(mortonCode);
		int z = MortonCode.getZ(mortonCode);
		ChunkPos pos = new ChunkPos(x, z, mortonCode);
		CACHE.put(mortonCode, pos);
		return pos;
	}

	public int getX() {
		return x;
	}

	public int getZ() {
		return z;
	}

	public long getMortonCode() {
		return mortonCode;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(mortonCode);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof ChunkPos)) return false;
		ChunkPos other = (ChunkPos) obj;
		return this.mortonCode == other.mortonCode;
	}
}
