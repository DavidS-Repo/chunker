package main;

/**
 * Represents the (x, z) coordinates of a chunk along with its packed key.
 */
public final class ChunkPos {
	private final int x;
	private final int z;
	private final long mortonCode;

	// Bounded primitive-key cache to avoid Long boxing and reduce GC.
	private static final LongChunkPosCache CACHE = new LongChunkPosCache(8192);

	private ChunkPos(int x, int z, long mortonCode) {
		this.x = x;
		this.z = z;
		this.mortonCode = mortonCode;
	}

	/**
	 * Returns a cached instance of ChunkPos for the given coordinates.
	 */
	public static ChunkPos get(int x, int z) {
		long key = MortonCode.encode(x, z);
		ChunkPos cached = CACHE.get(key);
		if (cached != null) return cached;

		ChunkPos pos = new ChunkPos(x, z, key);
		CACHE.put(key, pos);
		return pos;
	}

	/**
	 * Returns a cached instance of ChunkPos for the given packed key.
	 */
	public static ChunkPos fromMorton(long mortonCode) {
		ChunkPos cached = CACHE.get(mortonCode);
		if (cached != null) return cached;

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

	/**
	 * Fast bounded cache mapping primitive long -> ChunkPos.
	 * 4-way set associative + lock striping.
	 */
	private static final class LongChunkPosCache {
		private final int setsMask;
		private final long[] keys;
		private final ChunkPos[] values;
		private final byte[] used;

		private final Object[] locks;
		private final int lockMask;

		LongChunkPosCache(int maxEntries) {
			int sets = ceilPow2((maxEntries + 3) / 4);
			this.setsMask = sets - 1;

			int capacity = sets * 4;
			this.keys = new long[capacity];
			this.values = new ChunkPos[capacity];
			this.used = new byte[capacity];

			int lockCount = 256;
			this.locks = new Object[lockCount];
			for (int i = 0; i < lockCount; i++) this.locks[i] = new Object();
			this.lockMask = lockCount - 1;
		}

		ChunkPos get(long key) {
			int h = hash(key);
			int set = h & setsMask;
			Object lock = locks[set & lockMask];

			synchronized (lock) {
				int base = set << 2;
				for (int i = 0; i < 4; i++) {
					int idx = base + i;
					if (used[idx] != 0 && keys[idx] == key) return values[idx];
				}
				return null;
			}
		}

		void put(long key, ChunkPos value) {
			int h = hash(key);
			int set = h & setsMask;
			Object lock = locks[set & lockMask];

			synchronized (lock) {
				int base = set << 2;

				for (int i = 0; i < 4; i++) {
					int idx = base + i;
					if (used[idx] != 0 && keys[idx] == key) {
						values[idx] = value;
						return;
					}
				}

				for (int i = 0; i < 4; i++) {
					int idx = base + i;
					if (used[idx] == 0) {
						used[idx] = 1;
						keys[idx] = key;
						values[idx] = value;
						return;
					}
				}

				int victim = (h >>> 8) & 3;
				int idx = base + victim;
				used[idx] = 1;
				keys[idx] = key;
				values[idx] = value;
			}
		}

		private static int ceilPow2(int x) {
			int n = 1;
			while (n < x) n <<= 1;
			return n;
		}

		private static int hash(long k) {
			int h = (int) (k ^ (k >>> 32));
			return h ^ (h >>> 16);
		}
	}
}
