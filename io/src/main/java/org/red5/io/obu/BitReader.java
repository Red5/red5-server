package org.red5.io.obu;

import java.util.Arrays;

public class BitReader {

    private byte[] buf;

    private int bufSize, bufPos, bitBuffer, bitsInBuffer;

    public BitReader(byte[] buf, int bufSize) {
        this.buf = buf;
        this.bufSize = bufSize;
        this.bufPos = 0;
        this.bitBuffer = 0;
        this.bitsInBuffer = 0;
    }

    public BitReader(byte[] buf, int offset, int size) {
        this(Arrays.copyOfRange(buf, offset, (offset + size)), size);
    }

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

    public int getPosition() {
        return (bufPos * 8) - bitsInBuffer;
    }

    public void byteAlignment() throws OBUParseException {
        int remainder = (int) (getPosition() % 8);
        if (remainder != 0) {
            readBits(8 - remainder);
        }
    }
}