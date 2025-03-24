package main;

/**
 * Facilitates traversal of chunks within regions using a spiral pattern.
 * This mutable, synchronized implementation now preserves the original spiral behavior.
 */
public final class RegionChunkIterator {
	private static final int REGION_SIZE = 32;
	private static final int DIRECTIONS_COUNT = 4;
	private static final int[] DX = {1, 0, -1, 0}; // East, North, West, South
	private static final int[] DZ = {0, 1, 0, -1};

	// Mutable state variables
	private int currentRegionX = 0;
	private int currentRegionZ = 0;
	private int directionIndex = 0;
	private int stepsRemaining = 1;
	private int stepsToChange = 1;
	private int chunkX = 0;
	private int chunkZ = 0;

	/**
	 * Retrieves the next chunk coordinates in the spiral order.
	 * When a region’s chunks are fully iterated, it moves to the next region.
	 *
	 * @return the next chunk’s global coordinates along with a flag indicating a new region.
	 */
	public synchronized NextChunkResult getNextChunkCoordinates() {
		// Check if the current region's chunks have been fully iterated.
		if (isRegionComplete()) {
			moveToNextRegion();
			// Reset the chunk iteration for the new region.
			chunkX = 0;
			chunkZ = 0;
			if (isTraversalComplete()) {
				return null;
			}
			return new NextChunkResult(ChunkPos.get(globalX(), globalZ()), true);
		}
		// Return the current global chunk position, then advance the internal chunk iteration.
		NextChunkResult result = new NextChunkResult(ChunkPos.get(globalX(), globalZ()), false);
		advanceChunk();
		return result;
	}

	/**
	 * Determines if the current region's chunk iteration is complete.
	 * In this implementation, we consider the region complete when both chunkX and chunkZ
	 * have reached (or exceeded) REGION_SIZE.
	 *
	 * @return true if all chunks in the region have been iterated.
	 */
	private boolean isRegionComplete() {
		return chunkX >= REGION_SIZE && chunkZ >= REGION_SIZE;
	}

	/**
	 * Advances the chunk coordinates within the current region.
	 * When the end of a row is reached, resets chunkZ and increments chunkX.
	 * When chunkX reaches REGION_SIZE, forces both coordinates to REGION_SIZE to signal completion.
	 */
	private void advanceChunk() {
		chunkZ++;
		if (chunkZ >= REGION_SIZE) {
			chunkZ = 0;
			chunkX++;
		}
		if (chunkX >= REGION_SIZE) {
			// Force region complete state.
			chunkX = REGION_SIZE;
			chunkZ = REGION_SIZE;
		}
	}

	/**
	 * Advances the iterator to the next region using a spiral pattern.
	 * This logic is equivalent to the original immutable state's prepareNextRegion() method.
	 */
	private void moveToNextRegion() {
		if (stepsRemaining == 0) {
			directionIndex = (directionIndex + 1) & (DIRECTIONS_COUNT - 1);
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
	 * (You can define a termination condition here if needed.)
	 *
	 * @return false as a default.
	 */
	private boolean isTraversalComplete() {
		return false;
	}

	/**
	 * Computes the global x-coordinate.
	 */
	private int globalX() {
		return (currentRegionX << 5) + chunkX;
	}

	/**
	 * Computes the global z-coordinate.
	 */
	private int globalZ() {
		return (currentRegionZ << 5) + chunkZ;
	}

	/**
	 * Resets the iterator to its initial state.
	 */
	public synchronized void reset() {
		currentRegionX = 0;
		currentRegionZ = 0;
		directionIndex = 0;
		stepsRemaining = 1;
		stepsToChange = 1;
		chunkX = 0;
		chunkZ = 0;
	}

	/**
	 * Sets the state of the iterator for saving or loading purposes.
	 *
	 * @param currentRegionX the current region's X coordinate
	 * @param currentRegionZ the current region's Z coordinate
	 * @param directionIndex the current direction index
	 * @param stepsRemaining the steps remaining in the current direction
	 * @param stepsToChange  the number of steps before changing direction
	 * @param chunkIndex     the chunk index within the region (encoded as (chunkX << 5) | chunkZ)
	 */
	public synchronized void setState(int currentRegionX, int currentRegionZ, int directionIndex, int stepsRemaining, int stepsToChange, int chunkIndex) {
		this.currentRegionX = currentRegionX;
		this.currentRegionZ = currentRegionZ;
		this.directionIndex = directionIndex;
		this.stepsRemaining = stepsRemaining;
		this.stepsToChange = stepsToChange;
		this.chunkX = chunkIndex >> 5;
		this.chunkZ = chunkIndex & 31;
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
	 * Returns the current chunk index within the region (encoded as (chunkX << 5) | chunkZ).
	 */
	public synchronized int getChunkIndex() {
		return (chunkX << 5) | chunkZ;
	}

	/**
	 * Represents the result of getting the next chunk, including whether a region was completed.
	 */
	public static final class NextChunkResult {
		public final ChunkPos chunkPos;
		public final boolean regionCompleted;

		public NextChunkResult(ChunkPos chunkPos, boolean regionCompleted) {
			this.chunkPos = chunkPos;
			this.regionCompleted = regionCompleted;
		}
	}
}
