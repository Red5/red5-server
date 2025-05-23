package org.red5.io.obu;

import java.util.Arrays;

/**
 * <p>BitReader class.</p>
 *
 * @author mondain
 */
public class BitReader {

    private byte[] buf;

    private int bufSize, bufPos, bitBuffer, bitsInBuffer;

    /**
     * <p>Constructor for BitReader.</p>
     *
     * @param buf an array of {@link byte} objects
     * @param bufSize a int
     */
    public BitReader(byte[] buf, int bufSize) {
        this.buf = buf;
        this.bufSize = bufSize;
        this.bufPos = 0;
        this.bitBuffer = 0;
        this.bitsInBuffer = 0;
    }

    /**
     * <p>Constructor for BitReader.</p>
     *
     * @param buf an array of {@link byte} objects
     * @param offset a int
     * @param size a int
     */
    public BitReader(byte[] buf, int offset, int size) {
        this(Arrays.copyOfRange(buf, offset, (offset + size)), size);
    }

    /**
     * <p>readBits.</p>
     *
     * @param n a int
     * @return a int
     * @throws org.red5.io.obu.OBUParseException if any.
     */
    public int readBits(int n) throws OBUParseException {
        // only 32 bits can be read at a time
        if (n > 0 && n < 33) {
            while (bitsInBuffer < n) {
                if (bufPos >= bufSize) {
                    throw new OBUParseException("Reached end of buffer while reading bits");
                }
                bitBuffer = (bitBuffer << 8) | (buf[bufPos] & 0xFF);
                bufPos++;
                bitsInBuffer += 8;
            }
            bitsInBuffer -= n;
            return (bitBuffer >>> bitsInBuffer) & ((1 << n) - 1);
        }
        throw new OBUParseException("Cannot read more than 32 bits at a time");
    }

    /**
     * <p>getPosition.</p>
     *
     * @return a int
     */
    public int getPosition() {
        return (bufPos * 8) - bitsInBuffer;
    }

    /**
     * <p>byteAlignment.</p>
     *
     * @throws org.red5.io.obu.OBUParseException if any.
     */
    public void byteAlignment() throws OBUParseException {
        int remainder = (int) (getPosition() % 8);
        if (remainder != 0) {
            readBits(8 - remainder);
        }
    }
}
