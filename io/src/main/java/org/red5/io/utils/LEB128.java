/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2022 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.utils;

import org.apache.mina.core.buffer.IoBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class encodes and decodes integers in the LEB128 compression format.
 *
 * Reference examples:
 *
 * @see <a href="https://github.com/pion/rtp/blob/master/codecs/av1/obu/leb128.go">leb128.go</a>
 * @see <a href="https://github.com/hathibelagal-dev/LEB128/blob/master/lib/leb128.dart">leb128.dart</a>
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
public final class LEB128 {

    private static Logger log = LoggerFactory.getLogger(LEB128.class);

    /** Constant <code>SEVEN_LSB_BITMASK=0b01111111</code> */
    public static final int SEVEN_LSB_BITMASK = 0b01111111; // 0x7F

    /** Constant <code>MSB_BITMASK=0b10000000</code> */
    public static final int MSB_BITMASK = 0b10000000; // 0x80

    /**
     * Encodes an int into an LEB128 unsigned integer.
     *
     * @param value integer to encode
     * @return unsigned integer in LEB128 format
     */
    public static int encode(int value) {
        int out = 0;
        //int byteCount = 1;
        do {
            out |= (value & SEVEN_LSB_BITMASK);
            value >>= 7;
            if (value != 0) {
                out |= MSB_BITMASK;
                out <<= 8;
            } else {
                break;
            }
            //byteCount++;
        } while (value != 0);
        //log.trace("Encoded {} bytes", byteCount);
        return out;
    }

    /**
     * <p>encode.</p>
     *
     * @param value an array of {@link byte} objects
     * @return a int
     */
    public static int encode(byte[] value) {
        int out = 0;
        for (int i = 0; i < value.length; i++) {
            out |= (value[i] & SEVEN_LSB_BITMASK);
            if (i < value.length - 1) {
                out |= MSB_BITMASK;
                out <<= 8;
            }
        }
        return out;
    }

    /**
     * Encodes an int into an LEB128 unsigned integer and writes it to a buffer at the given offset.
     *
     * @param value integer to encode
     * @param buffer byte array to write to
     * @param offset offset to write to
     * @return number of bytes written
     */
    public static int encode(int value, byte[] buffer, int offset) {
        int byteCount = 0;
        do {
            byte b = (byte) (value & SEVEN_LSB_BITMASK);
            value >>= 7;
            if (value != 0) {
                b |= MSB_BITMASK;
            }
            buffer[offset + byteCount] = b;
            byteCount++;
        } while (value != 0);
        return byteCount;
    }

    /**
     * Decodes an LEB128 unsigned integer into a regular int.
     *
     * @param value unsigned integer in LEB128 format to decode
     * @return LEB128Result
     */
    public static LEB128Result decode(int value) {
        LEB128Result result = new LEB128Result(0, 1);
        do {
            result.value |= (value & SEVEN_LSB_BITMASK);
            value >>>= 8;
            if ((value & MSB_BITMASK) == 0) {
                break;
            }
            result.value <<= 7;
            result.bytesRead++;
        } while (value != 0);
        //log.debug("Decoded {} bytes", byteCount);
        return result;
    }

    /**
     * Decodes an LEB128 unsigned integer from a byte array into a regular int.
     *
     * @param value an array of {@link byte} objects
     * @return LEB128Result
     */
    public static LEB128Result decode(byte[] value) {
        log.debug("Decode encoded bytes: {}", HexDump.byteArrayToHexString(value));
        LEB128Result result = new LEB128Result(0, 1);
        int v = hexBytesToUnsignedInt(value);
        log.debug("Decode: {}", v);
        do {
            result.value |= (v & SEVEN_LSB_BITMASK);
            v >>>= 8;
            if ((v & MSB_BITMASK) == 0) {
                break;
            }
            result.value <<= 7;
            result.bytesRead++;
        } while (v != 0);
        log.debug("Decoded leb: {}", result);
        return result;
    }

    /**
     * <p>hexBytesToUnsignedInt.</p>
     *
     * @param hex an array of {@link byte} objects
     * @return a int
     */
    public static int hexBytesToUnsignedInt(byte[] hex) {
        //log.trace("Hex length: {}", hex.length);
        IoBuffer buf = IoBuffer.wrap(hex);
        if (hex.length == 1) {
            return buf.get() & 0xff;
        } else if (hex.length == 2) {
            return buf.getUnsignedShort();
        } else if (hex.length == 3) {
            return buf.getUnsignedMediumInt();
        }
        try {
            return Math.toIntExact(buf.getUnsignedInt());
        } catch (Exception e) {
            log.warn("Exception extrating unsigned int", e);
        }
        return buf.getInt();
    }

    /**
     * Result of a LEB128 encoding or decoding operation.
     */
    public static class LEB128Result {

        public int value;

        public int bytesRead;

        public LEB128Result(int value, int bytesRead) {
            this.value = value;
            this.bytesRead = bytesRead;
        }

        @Override
        public String toString() {
            return "LEB128Result [value=" + value + ", bytesRead=" + bytesRead + "]";
        }

    }

    /**
     * Exception thrown when a LEB128 operation fails.
     */
    public static class LEB128Exception extends Exception {

        public LEB128Exception(String message) {
            super(message);
        }

    }

}
