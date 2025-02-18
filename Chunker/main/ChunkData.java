package main;

/**
 * The {@code ChunkData} class encapsulates the (x, z) coordinates of a chunk
 * along with its corresponding Morton code. This allows for efficient
 * arithmetic operations on coordinates while maintaining access to the
 * Morton code.
 */
public final class ChunkData {
	private final int x;
	private final int z;
	private final long mortonCode;

	/**
	 * Constructs a new {@code ChunkData} instance with the specified coordinates.
	 *
	 * @param x The x-coordinate of the chunk.
	 * @param z The z-coordinate of the chunk.
	 */
	public ChunkData(int x, int z) {
		this.x = x;
		this.z = z;
		this.mortonCode = MortonCode.encode(x, z);
	}

	/**
	 * Constructs a new {@code ChunkData} instance from a Morton code.
	 *
	 * @param mortonCode The Morton code of the chunk.
	 */
	public ChunkData(long mortonCode) {
		this.x = MortonCode.getX(mortonCode);
		this.z = MortonCode.getZ(mortonCode);
		this.mortonCode = mortonCode;
	}

	/**
	 * Retrieves the x-coordinate of the chunk.
	 *
	 * @return The x-coordinate.
	 */
	public int getX() {
		return x;
	}

	/**
	 * Retrieves the z-coordinate of the chunk.
	 *
	 * @return The z-coordinate.
	 */
	public int getZ() {
		return z;
	}

	/**
	 * Retrieves the Morton code of the chunk.
	 *
	 * @return The Morton code.
	 */
	public long getMortonCode() {
		return mortonCode;
	}

	/**
	 * Calculates the neighboring {@code ChunkData} in the specified direction.
	 *
	 * @param direction The direction of the neighbor.
	 * @return A new {@code ChunkData} instance representing the neighboring chunk.
	 */
	public ChunkData getNeighbor(Direction direction) {
		return new ChunkData(x + direction.deltaX, z + direction.deltaZ);
	}

	/**
	 * Enum representing possible directions for neighboring chunks, each with precomputed delta values.
	 */
	public enum Direction {
		NORTH(0, 1),
		SOUTH(0, -1),
		EAST(1, 0),
		WEST(-1, 0),
		NORTHEAST(1, 1),
		NORTHWEST(-1, 1),
		SOUTHEAST(1, -1),
		SOUTHWEST(-1, -1);

		private final int deltaX;
		private final int deltaZ;

		Direction(int deltaX, int deltaZ) {
			this.deltaX = deltaX;
			this.deltaZ = deltaZ;
		}
	}
}