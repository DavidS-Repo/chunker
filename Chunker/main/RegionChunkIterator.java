package main;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The {@code RegionChunkIterator} class facilitates the traversal of chunks within regions
 * using a spiral pattern. It manages the iteration state, ensuring that each chunk within
 * a region is processed before moving to the next region in the spiral sequence.
 */
public final class RegionChunkIterator {

	// Constants defining the region size and direction vectors for spiral traversal
	private static final int REGION_SIZE = 32;
	private static final int DIRECTIONS_COUNT = 4;
	private static final int[] DX = {1, 0, -1, 0}; // East, North, West, South
	private static final int[] DZ = {0, 1, 0, -1};

	// Spiral traversal state variables
	private int currentRegionX = 0;
	private int currentRegionZ = 0;
	private int directionIndex = 0;
	private int stepsRemaining = 1;
	private int stepsToChange = 1;

	// Chunk iteration state within the current region
	private int chunkX = 0;
	private int chunkZ = 0;

	// Reusable variables to avoid frequent allocations
	private int globalChunkX;
	private int globalChunkZ;
	private long chunkKey;

	// Lock for thread safety
	private final Lock lock = new ReentrantLock();

	/**
	 * Retrieves the next chunk Morton code within the current region.
	 * If the current region has been fully iterated, it prepares the next region
	 * and signals the completion of the current region by returning {@code -1}.
	 *
	 * @return The encoded Morton chunk key, or {@code -1} if the region is complete.
	 */
	public long getNextChunkCoordinates() {
		lock.lock();
		try {
			if (isRegionComplete()) {
				prepareNextRegion();
				return -1L;
			}
			globalChunkX = (currentRegionX << 5) + chunkX; // Equivalent to currentRegionX * 32
			globalChunkZ = (currentRegionZ << 5) + chunkZ; // Equivalent to currentRegionZ * 32
			chunkKey = MortonCode.encode(globalChunkX, globalChunkZ);
			advanceChunk();
			return chunkKey;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Checks if the current region has been completely iterated.
	 *
	 * @return {@code true} if the region is complete, {@code false} otherwise.
	 */
	private boolean isRegionComplete() {
		return chunkX >= REGION_SIZE && chunkZ >= REGION_SIZE;
	}

	/**
	 * Advances the chunk indices within the current region.
	 */
	private void advanceChunk() {
		chunkZ++;
		if (chunkZ >= REGION_SIZE) {
			chunkZ = 0;
			chunkX++;
			if (chunkX >= REGION_SIZE) {
				chunkX = REGION_SIZE;
				chunkZ = REGION_SIZE;
			}
		}
	}

	/**
	 * Prepares the iterator for the next region in the spiral pattern.
	 * Resets the chunk iteration indices to start processing the new region.
	 */
	private void prepareNextRegion() {
		updateSpiralCoordinates();
		chunkX = 0;
		chunkZ = 0;
	}

	/**
	 * Updates the spiral traversal coordinates based on the current direction
	 * and the number of steps remaining in that direction. Adjusts the direction
	 * and steps to change as necessary to maintain the spiral pattern.
	 */
	private void updateSpiralCoordinates() {
		if (stepsRemaining == 0) {
			directionIndex = (directionIndex + 1) & (DIRECTIONS_COUNT - 1); // Equivalent to modulo DIRECTIONS_COUNT
			if ((directionIndex & 1) == 0) { // After East or West
				stepsToChange++;
			}
			stepsRemaining = stepsToChange;
		}
		currentRegionX += DX[directionIndex];
		currentRegionZ += DZ[directionIndex];
		stepsRemaining--;
	}

	/**
	 * Resets the iterator to its initial state, clearing all traversal and chunk
	 * iteration parameters. This allows the iterator to start fresh from the
	 * origin region.
	 */
	public void reset() {
		lock.lock();
		try {
			currentRegionX = 0;
			currentRegionZ = 0;
			directionIndex = 0;
			stepsRemaining = 1;
			stepsToChange = 1;
			chunkX = 0;
			chunkZ = 0;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Sets the state of the iterator for saving or loading purposes.
	 * This method updates all relevant traversal and chunk iteration parameters.
	 *
	 * @param currentRegionX The current region's X-coordinate.
	 * @param currentRegionZ The current region's Z-coordinate.
	 * @param directionIndex The current direction index.
	 * @param stepsRemaining The number of steps remaining in the current direction.
	 * @param stepsToChange  The number of steps to change direction after the current steps.
	 * @param chunkIndex     The current chunk index within the region.
	 */
	public void setState(int currentRegionX, int currentRegionZ, int directionIndex,
			int stepsRemaining, int stepsToChange, int chunkIndex) {
		lock.lock();
		try {
			this.currentRegionX = currentRegionX;
			this.currentRegionZ = currentRegionZ;
			this.directionIndex = directionIndex;
			this.stepsRemaining = stepsRemaining;
			this.stepsToChange = stepsToChange;
			this.chunkX = chunkIndex >> 5; // Equivalent to chunkIndex / 32
			this.chunkZ = chunkIndex & 31;  // Equivalent to chunkIndex % 32
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Retrieves the current X-coordinate of the region being iterated.
	 *
	 * @return The current region's X-coordinate.
	 */
	public int getCurrentRegionX() {
		lock.lock();
		try {
			return currentRegionX;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Retrieves the current Z-coordinate of the region being iterated.
	 *
	 * @return The current region's Z-coordinate.
	 */
	public int getCurrentRegionZ() {
		lock.lock();
		try {
			return currentRegionZ;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Retrieves the current direction index used for spiral traversal.
	 * The index corresponds to the direction vectors defined by {@code DX} and {@code DZ}.
	 *
	 * @return The current direction index.
	 */
	public int getDirectionIndex() {
		lock.lock();
		try {
			return directionIndex;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Retrieves the number of steps remaining in the current direction before changing direction.
	 *
	 * @return The number of steps remaining.
	 */
	public int getStepsRemaining() {
		lock.lock();
		try {
			return stepsRemaining;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Retrieves the number of steps to change direction after completing the current steps.
	 *
	 * @return The number of steps to change.
	 */
	public int getStepsToChange() {
		lock.lock();
		try {
			return stepsToChange;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Retrieves the current chunk index within the region based on {@code chunkX} and {@code chunkZ}.
	 *
	 * @return The current chunk index within the region.
	 */
	public int getChunkIndex() {
		lock.lock();
		try {
			return (chunkX << 5) | chunkZ; // Equivalent to chunkX * 32 + chunkZ
		} finally {
			lock.unlock();
		}
	}
}
