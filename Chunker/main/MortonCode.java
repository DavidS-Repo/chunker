package main;

/**
 * The {@code MortonCode} class provides static methods to encode and decode
 * 2D coordinates into Morton codes (also known as Z-order curves). Morton codes
 * interleave the binary representations of the coordinates to create a single
 * value that preserves spatial locality.
 * 
 * <p>This class utilizes bitwise operations and precomputed masks to efficiently
 * perform interleaving and deinterleaving of bits. It is designed to be immutable
 * and thread-safe.</p>
 */
public final class MortonCode {

	/**
	 * The offset added to coordinates to handle negative values, ensuring all
	 * Morton codes are positive.
	 */
	private static final long OFFSET = ((long) Integer.MAX_VALUE) / 2;

	/**
	 * Masks to isolate and manipulate specific bit groups during interleaving and deinterleaving.
	 */
	private static final long MASK_16 = 0x0000FFFF0000FFFFL;
	private static final long MASK_8  = 0x00FF00FF00FF00FFL;
	private static final long MASK_4  = 0x0F0F0F0F0F0F0F0FL;
	private static final long MASK_2  = 0x3333333333333333L;
	private static final long MASK_1  = 0x5555555555555555L;
	private static final long DEINTERLEAVE_FINAL_MASK = 0x00000000FFFFFFFFL;

	/**
	 * Private constructor to prevent instantiation of the {@code MortonCode} class.
	 * This class is designed to provide static utility methods only.
	 */
	private MortonCode() {}

	/**
	 * Encodes the specified (x, z) coordinates into a single Morton code.
	 * 
	 * <p>The method interleaves the bits of the x and z coordinates to produce
	 * a Morton code that preserves spatial locality.</p>
	 * 
	 * @param x the x-coordinate to encode
	 * @param z the z-coordinate to encode
	 * @return the Morton code representing the interleaved (x, z) coordinates
	 */
	public static long encode(int x, int z) {
		return interleaveBits(((long) x) + OFFSET) | (interleaveBits(((long) z) + OFFSET) << 1);
	}

	/**
	 * Decodes the x-coordinate from the specified Morton code.
	 * 
	 * <p>This method extracts the original x-coordinate by deinterleaving the
	 * bits from the Morton code.</p>
	 * 
	 * @param mortonCode the Morton code from which to decode the x-coordinate
	 * @return the decoded x-coordinate
	 */
	public static int getX(long mortonCode) {
		return (int) (deinterleaveBits(mortonCode) - OFFSET);
	}

	/**
	 * Decodes the z-coordinate from the specified Morton code.
	 * 
	 * <p>This method extracts the original z-coordinate by deinterleaving the
	 * bits from the Morton code.</p>
	 * 
	 * @param mortonCode the Morton code from which to decode the z-coordinate
	 * @return the decoded z-coordinate
	 */
	public static int getZ(long mortonCode) {
		return (int) (deinterleaveBits(mortonCode >>> 1) - OFFSET);
	}

	/**
	 * Interleaves the bits of a 32-bit integer, spreading them out so that
	 * there is one zero bit between each original bit.
	 * 
	 * <p>This method transforms the input integer by interleaving its bits with
	 * zeros to facilitate Morton encoding.</p>
	 * 
	 * @param x the integer to interleave
	 * @return the interleaved bits as a long
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
	 * Deinterleaves the bits from a Morton code to retrieve the original integer.
	 * 
	 * <p>This method reverses the interleaving process performed during encoding,
	 * extracting the original integer value from the Morton code.</p>
	 * 
	 * @param x the interleaved bits from the Morton code
	 * @return the original integer before interleaving
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