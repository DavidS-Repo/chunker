package main;

/**
 * Provides methods to encode and decode 2D coordinates into Morton codes (Z-order curves).
 */
public final class MortonCode {

	private static final long OFFSET = ((long) Integer.MAX_VALUE) / 2;
	private static final long MASK_16 = 0x0000FFFF0000FFFFL;
	private static final long MASK_8  = 0x00FF00FF00FF00FFL;
	private static final long MASK_4  = 0x0F0F0F0F0F0F0F0FL;
	private static final long MASK_2  = 0x3333333333333333L;
	private static final long MASK_1  = 0x5555555555555555L;
	private static final long DEINTERLEAVE_FINAL_MASK = 0x00000000FFFFFFFFL;

	private MortonCode() {
		// Prevent instantiation
	}

	/**
	 * Encodes (x, z) coordinates into a Morton code.
	 *
	 * @param x the x-coordinate
	 * @param z the z-coordinate
	 * @return the Morton code representing the coordinates
	 */
	public static long encode(int x, int z) {
		return interleaveBits(((long) x) + OFFSET) | (interleaveBits(((long) z) + OFFSET) << 1);
	}

	/**
	 * Decodes the x-coordinate from a Morton code.
	 *
	 * @param mortonCode the Morton code
	 * @return the x-coordinate
	 */
	public static int getX(long mortonCode) {
		return (int) (deinterleaveBits(mortonCode) - OFFSET);
	}

	/**
	 * Decodes the z-coordinate from a Morton code.
	 *
	 * @param mortonCode the Morton code
	 * @return the z-coordinate
	 */
	public static int getZ(long mortonCode) {
		return (int) (deinterleaveBits(mortonCode >>> 1) - OFFSET);
	}

	/**
	 * Interleaves the bits of a 32-bit integer for Morton encoding.
	 *
	 * @param x the integer to interleave
	 * @return the interleaved bits
	 */
	private static long interleaveBits(long x) {
		x &= 0xFFFFFFFFL;
		x = (x | (x << 16)) & MASK_16;
		x = (x | (x << 8)) & MASK_8;
		x = (x | (x << 4)) & MASK_4;
		x = (x | (x << 2)) & MASK_2;
		x = (x | (x << 1)) & MASK_1;
		return x;
	}

	/**
	 * Deinterleaves bits from a Morton code to retrieve the original integer.
	 *
	 * @param x the interleaved bits
	 * @return the original integer
	 */
	private static long deinterleaveBits(long x) {
		x &= MASK_1;
		x = (x | (x >>> 1)) & MASK_2;
		x = (x | (x >>> 2)) & MASK_4;
		x = (x | (x >>> 4)) & MASK_8;
		x = (x | (x >>> 8)) & MASK_16;
		x = (x | (x >>> 16)) & DEINTERLEAVE_FINAL_MASK;
		return x;
	}
}