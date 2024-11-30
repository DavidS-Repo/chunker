package main;

/**
 * Represents the (x, z) coordinates of a chunk along with its Morton code.
 */
public final class ChunkPos {
	private final int x;
	private final int z;
	private final long mortonCode;

	/**
	 * Constructs a ChunkPos with the specified coordinates.
	 *
	 * @param x the x-coordinate of the chunk
	 * @param z the z-coordinate of the chunk
	 */
	public ChunkPos(int x, int z) {
		this.x = x;
		this.z = z;
		this.mortonCode = MortonCode.encode(x, z);
	}

	/**
	 * Constructs a ChunkPos from a Morton code.
	 *
	 * @param mortonCode the Morton code representing the chunk coordinates
	 */
	public ChunkPos(long mortonCode) {
		this.x = MortonCode.getX(mortonCode);
		this.z = MortonCode.getZ(mortonCode);
		this.mortonCode = mortonCode;
	}

	/**
	 * Returns the x-coordinate of the chunk.
	 *
	 * @return the x-coordinate
	 */
	public int getX() {
		return x;
	}

	/**
	 * Returns the z-coordinate of the chunk.
	 *
	 * @return the z-coordinate
	 */
	public int getZ() {
		return z;
	}

	/**
	 * Returns the Morton code of the chunk.
	 *
	 * @return the Morton code
	 */
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