package main;

/**
 * Fast key packing for (x, z).
 */
public final class MortonCode {

	private MortonCode() {
	}

	public static long encode(int x, int z) {
		return (((long) x) << 32) | (z & 0xFFFF_FFFFL);
	}

	public static int getX(long code) {
		return (int) (code >> 32);
	}

	public static int getZ(long code) {
		return (int) code;
	}
}
