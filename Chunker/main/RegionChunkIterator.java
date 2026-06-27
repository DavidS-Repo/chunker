package main;

/**
 * Facilitates traversal of chunks within regions using a spiral pattern.
 */
public final class RegionChunkIterator {
	private static final int REGION_SIZE = 32;
	private static final int REGION_MASK = REGION_SIZE - 1;
	private static final int MAX_CHUNK_INDEX = (REGION_SIZE * REGION_SIZE) - 1;
	private static final int[] DX = {1, 0, -1, 0};
	private static final int[] DZ = {0, 1, 0, -1};

	private int currentRegionX;
	private int currentRegionZ;
	private int directionIndex;
	private int stepsRemaining = 1;
	private int stepsToChange = 1;
	private int chunkIndex;
	private boolean bounded;
	private int minChunkX;
	private int maxChunkX;
	private int minChunkZ;
	private int maxChunkZ;

	/**
	 * Retrieves the next chunk coordinates in the spiral order.
	 *
	 * @return the next chunk coordinates with a flag indicating region completion, or null if traversal is complete
	 */
	public synchronized NextChunkResult getNextChunkCoordinates() {
		boolean regionCompleted = false;
		while (true) {
			if (chunkIndex > MAX_CHUNK_INDEX) {
				moveToNextRegion();
				chunkIndex = 0;
				if (isTraversalComplete()) {
					return null;
				}
				regionCompleted = true;
			}

			NextChunkResult result = newResult(regionCompleted);
			chunkIndex++;
			if (!bounded || isInsideBounds(result.chunkX, result.chunkZ)) {
				return result;
			}
		}
	}

	/**
	 * Advances the iterator to the next region using a spiral pattern.
	 */
	private void moveToNextRegion() {
		if (stepsRemaining == 0) {
			directionIndex = (directionIndex + 1) & 3;
			if ((directionIndex & 1) == 0) {
				stepsToChange++;
			}
			stepsRemaining = stepsToChange;
		}
		currentRegionX += DX[directionIndex];
		currentRegionZ += DZ[directionIndex];
		stepsRemaining--;
	}

	/**
	 * Determines if the overall traversal is complete.
	 *
	 * @return false by default
	 */
	private boolean isTraversalComplete() {
		return false;
	}

	private boolean isInsideBounds(int chunkX, int chunkZ) {
		return chunkX >= minChunkX && chunkX <= maxChunkX && chunkZ >= minChunkZ && chunkZ <= maxChunkZ;
	}

	private NextChunkResult newResult(boolean regionCompleted) {
		int localX = chunkIndex >> 5;
		int localZ = chunkIndex & REGION_MASK;
		int globalX = (currentRegionX << 5) + localX;
		int globalZ = (currentRegionZ << 5) + localZ;
		return new NextChunkResult(globalX, globalZ, regionCompleted);
	}

	/**
	 * Resets the iterator to its initial state at region 0,0.
	 */
	public synchronized void reset() {
		currentRegionX = 0;
		currentRegionZ = 0;
		directionIndex = 0;
		stepsRemaining = 1;
		stepsToChange = 1;
		chunkIndex = 0;
	}

	/**
	 * Sets the starting region for the spiral traversal.
	 *
	 * @param regionX starting region x coordinate
	 * @param regionZ starting region z coordinate
	 */
	public synchronized void setCenterRegion(int regionX, int regionZ) {
		currentRegionX = regionX;
		currentRegionZ = regionZ;
		directionIndex = 0;
		stepsRemaining = 1;
		stepsToChange = 1;
		chunkIndex = 0;
	}

	public synchronized void setChunkBounds(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
		this.minChunkX = minChunkX;
		this.maxChunkX = maxChunkX;
		this.minChunkZ = minChunkZ;
		this.maxChunkZ = maxChunkZ;
		this.bounded = true;
	}

	public synchronized void clearChunkBounds() {
		this.bounded = false;
	}

	/**
	 * Sets the state of the iterator for saving or loading purposes.
	 *
	 * @param currentRegionX the current region's X coordinate
	 * @param currentRegionZ the current region's Z coordinate
	 * @param directionIndex the current direction index
	 * @param stepsRemaining the steps remaining in the current direction
	 * @param stepsToChange  the number of steps before changing direction
	 * @param chunkIndex     the chunk index within the region
	 */
	public synchronized void setState(int currentRegionX, int currentRegionZ, int directionIndex, int stepsRemaining, int stepsToChange, int chunkIndex) {
		this.currentRegionX = currentRegionX;
		this.currentRegionZ = currentRegionZ;
		this.directionIndex = directionIndex & 3;
		this.stepsRemaining = Math.max(0, stepsRemaining);
		this.stepsToChange = Math.max(1, stepsToChange);
		this.chunkIndex = clampChunkIndex(chunkIndex);
	}

	public synchronized int getCurrentRegionX() {
		return currentRegionX;
	}

	public synchronized int getCurrentRegionZ() {
		return currentRegionZ;
	}

	public synchronized int getDirectionIndex() {
		return directionIndex;
	}

	public synchronized int getStepsRemaining() {
		return stepsRemaining;
	}

	public synchronized int getStepsToChange() {
		return stepsToChange;
	}

	/**
	 * Returns the current chunk index within the region.
	 */
	public synchronized int getChunkIndex() {
		return clampChunkIndex(chunkIndex);
	}

	private static int clampChunkIndex(int value) {
		if (value < 0) return 0;
		return Math.min(value, MAX_CHUNK_INDEX);
	}

	/**
	 * Represents the next chunk coordinates, kept primitive to avoid ChunkPos and boxed Long churn in the hot path.
	 */
	public static final class NextChunkResult {
		public final int chunkX;
		public final int chunkZ;
		public final long packedKey;
		public final boolean regionCompleted;

		public NextChunkResult(int chunkX, int chunkZ, boolean regionCompleted) {
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.packedKey = MortonCode.encode(chunkX, chunkZ);
			this.regionCompleted = regionCompleted;
		}
	}
}
