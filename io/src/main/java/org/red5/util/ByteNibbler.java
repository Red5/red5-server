package org.red5.util;

/**
 * Byte and Bit manipulation routines.<br>
 * Returns Big endian bit vals, so reading 4 bits of binary'0100' returns 4 instead of 2.
 *
 * @author Paul Gregoire
 * @author Andy Shaules
 */
public class ByteNibbler {

    private final byte[] data;

    private int dataIndex = 0, bitIndex = 0;

    public ByteNibbler(byte b) {
        data = new byte[1];
        data[0] = (byte) (b & 0xff);
    }

    public ByteNibbler(byte[] b) {
        data = b;
    }

    public ByteNibbler(byte b1, byte b2) {
        bitIndex = 0;
        data = new byte[2];
        data[0] = (byte) (b1 & 0xff);
        data[1] = (byte) (b2 & 0xff);
    }

    /**
     * This methods reads bits from high to low.
     * <p>
     * Reading 2 bits will return an integer where the returned value has a potential maximum of 1<<2.
     * </p>
     *
     * @param numBits
     *            The number of bits to read.
     * @return Returns an integer with a max value up to ( 1 << bits read )
     */
    public int nibble(int numBits) {
        int ret = 0;
        while ((dataIndex < data.length) && numBits > 0) {
            ret |= (((data[dataIndex] >> (7 - bitIndex++)) & 0x1) << --numBits);
            if ((bitIndex %= 8) == 0) {
                dataIndex++;
            }
        }
        return ret;
    }

    /**
     * Returns whether or not the bit is set.
     *
     * @param b byte being checked
     * @param bit bit index
     * @return true if set and false otherwise
     */
    public static boolean isBitSet(byte b, int bit) {
        return (b & (1 << bit)) != 0;
    }

    public static String toHexString(byte[] ba) {
        StringBuilder hex = new StringBuilder(ba.length * 2);
        for (byte b : ba) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    public static String toBinaryString(byte[] ba) {
        StringBuilder binary = new StringBuilder(ba.length * 8);
        for (byte b : ba) {
            binary.append(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0'));
        }
        return binary.toString();
    }

}