package main;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Facilitates traversal of chunks within regions using a spiral pattern.
 */
public final class RegionChunkIterator {

	private static final int REGION_SIZE = 32;
	private static final int DIRECTIONS_COUNT = 4;
	private static final int[] DX = {1, 0, -1, 0}; // East, North, West, South
	private static final int[] DZ = {0, 1, 0, -1};

	private final AtomicReference<State> stateRef = new AtomicReference<>(new State());

	/**
	 * Retrieves the next chunk coordinates within the current region.
	 *
	 * @return the next chunk coordinates and whether a new region has been started
	 */
	public NextChunkResult getNextChunkCoordinates() {
		while (true) {
			State oldState = stateRef.get();
			if (oldState.isTraversalComplete()) {
				return null;
			}
			State newState;
			boolean regionCompleted = false;
			if (oldState.isRegionComplete()) {
				regionCompleted = true;
				newState = oldState.prepareNextRegion();
				if (newState.isTraversalComplete()) {
					return null;
				}
			} else {
				newState = oldState.advanceChunk();
			}
			if (stateRef.compareAndSet(oldState, newState)) {
				int globalChunkX = (oldState.currentRegionX << 5) + oldState.chunkX;
				int globalChunkZ = (oldState.currentRegionZ << 5) + oldState.chunkZ;
				return new NextChunkResult(new ChunkPos(globalChunkX, globalChunkZ), regionCompleted);
			}
		}
	}

	/**
	 * Resets the iterator to its initial state.
	 */
	public void reset() {
		stateRef.set(new State());
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
	public void setState(int currentRegionX, int currentRegionZ, int directionIndex, int stepsRemaining, int stepsToChange, int chunkIndex) {
		int chunkX = chunkIndex >> 5;
				int chunkZ = chunkIndex & 31;
				stateRef.set(new State(currentRegionX, currentRegionZ, directionIndex, stepsRemaining, stepsToChange, chunkX, chunkZ));
	}

	public int getCurrentRegionX() {
		return stateRef.get().currentRegionX;
	}

	public int getCurrentRegionZ() {
		return stateRef.get().currentRegionZ;
	}

	public int getDirectionIndex() {
		return stateRef.get().directionIndex;
	}

	public int getStepsRemaining() {
		return stateRef.get().stepsRemaining;
	}

	public int getStepsToChange() {
		return stateRef.get().stepsToChange;
	}

	public int getChunkIndex() {
		State state = stateRef.get();
		return (state.chunkX << 5) | state.chunkZ;
	}

	/**
	 * Immutable class representing the iterator's traversal state.
	 */
	private static final class State {
		private final int currentRegionX;
		private final int currentRegionZ;
		private final int directionIndex;
		private final int stepsRemaining;
		private final int stepsToChange;
		private final int chunkX;
		private final int chunkZ;

		State() {
			this(0, 0, 0, 1, 1, 0, 0);
		}

		State(int currentRegionX, int currentRegionZ, int directionIndex, int stepsRemaining, int stepsToChange, int chunkX, int chunkZ) {
			this.currentRegionX = currentRegionX;
			this.currentRegionZ = currentRegionZ;
			this.directionIndex = directionIndex;
			this.stepsRemaining = stepsRemaining;
			this.stepsToChange = stepsToChange;
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
		}

		boolean isRegionComplete() {
			return chunkX >= REGION_SIZE && chunkZ >= REGION_SIZE;
		}

		boolean isTraversalComplete() {
			return false;
		}

		State advanceChunk() {
			int newChunkX = chunkX;
			int newChunkZ = chunkZ + 1;
			if (newChunkZ >= REGION_SIZE) {
				newChunkZ = 0;
				newChunkX++;
				if (newChunkX >= REGION_SIZE) {
					newChunkX = REGION_SIZE;
					newChunkZ = REGION_SIZE;
				}
			}
			return new State(currentRegionX, currentRegionZ, directionIndex, stepsRemaining, stepsToChange, newChunkX, newChunkZ);
		}

		State prepareNextRegion() {
			int newDirectionIndex = directionIndex;
			int newStepsRemaining = stepsRemaining;
			int newStepsToChange = stepsToChange;
			int newCurrentRegionX = currentRegionX;
			int newCurrentRegionZ = currentRegionZ;

			if (newStepsRemaining == 0) {
				newDirectionIndex = (newDirectionIndex + 1) & (DIRECTIONS_COUNT - 1);
				if ((newDirectionIndex & 1) == 0) {
					newStepsToChange++;
				}
				newStepsRemaining = newStepsToChange;
			}
			newCurrentRegionX += DX[newDirectionIndex];
			newCurrentRegionZ += DZ[newDirectionIndex];
			newStepsRemaining--;

			return new State(newCurrentRegionX, newCurrentRegionZ, newDirectionIndex,
					newStepsRemaining, newStepsToChange, 0, 0);
		}
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