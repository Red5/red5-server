/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License") + you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.red5.io.matroska;

import static org.red5.io.matroska.ParserUtils.BIT_IN_BYTE;

import java.util.BitSet;

/**
 * Variable size integer class <a href="http://matroska.org/technical/specs/rfc/index.html">EBML RFC</a>
 *
 * @author mondain
 */
public class VINT {

    /** Constant <code>MASK_BYTE_8=0b0000000011111111111111111111111111111111111111111111111111111111L</code> */
    public static final long MASK_BYTE_8 = 0b0000000011111111111111111111111111111111111111111111111111111111L;

    /** Constant <code>MASK_BYTE_4=0b00001111111111111111111111111111</code> */
    public static final long MASK_BYTE_4 = 0b00001111111111111111111111111111;

    /** Constant <code>MASK_BYTE_3=0b000111111111111111111111</code> */
    public static final long MASK_BYTE_3 = 0b000111111111111111111111;

    /** Constant <code>MASK_BYTE_2=0b0011111111111111</code> */
    public static final long MASK_BYTE_2 = 0b0011111111111111;

    /** Constant <code>MASK_BYTE_1=0b01111111</code> */
    public static final long MASK_BYTE_1 = 0b01111111;

    private long binary;

    private byte length;

    private long value;

    /**
     * Constructor
     *
     * @param binary
     *            - binary value of this {@link org.red5.io.matroska.VINT}, calculated from value if not specified
     * @param length
     *            - length of this {@link org.red5.io.matroska.VINT}
     * @param value
     *            - value of this {@link org.red5.io.matroska.VINT}
     */
    public VINT(long binary, byte length, long value) {
        if (binary == 0L) {
            BitSet bs = BitSet.valueOf(new long[] { value });
            bs.set(length * BIT_IN_BYTE - length);
            this.binary = bs.toLongArray()[0];
        } else {
            this.binary = binary;
        }
        this.length = length;
        this.value = value;
    }

    /**
     * getter for length
     *
     * @return - length
     */
    public byte getLength() {
        return length;
    }

    /**
     * getter for binary
     *
     * @return - binary
     */
    public long getBinary() {
        return binary;
    }

    /**
     * getter for value
     *
     * @return - value
     */
    public long getValue() {
        return value;
    }

    /**
     * method to encode {@link org.red5.io.matroska.VINT} as sequence of bytes
     *
     * @return - encoded {@link org.red5.io.matroska.VINT}
     */
    public byte[] encode() {
        return ParserUtils.getBytes(binary, length);
    }

    /**
     * {@inheritDoc}
     *
     * method to get "pretty" represented {@link VINT}
     */
    @Override
    public String toString() {
        return String.format("%s(%s)", value, length);
    }

    /**
     * method to construct {@link org.red5.io.matroska.VINT} based on its binary representation
     *
     * @param binary
     *            - binary value of {@link org.red5.io.matroska.VINT}
     * @return {@link org.red5.io.matroska.VINT} corresponding to this binary
     */
    public static VINT fromBinary(long binary) {
        BitSet bs = BitSet.valueOf(new long[] { binary });
        long mask = MASK_BYTE_1;
        byte length = 1;
        if (bs.length() > 3 * BIT_IN_BYTE) {
            mask = MASK_BYTE_4;
            length = 4;
        } else if (bs.length() > 2 * BIT_IN_BYTE) {
            mask = MASK_BYTE_3;
            length = 3;
        } else if (bs.length() > 1 * BIT_IN_BYTE) {
            mask = MASK_BYTE_2;
            length = 2;
        }
        long value = binary & mask;
        return new VINT(binary, length, value);
    }

    /**
     * method to construct {@link org.red5.io.matroska.VINT} based on its value
     *
     * @param value
     *            - value of {@link org.red5.io.matroska.VINT}
     * @return {@link org.red5.io.matroska.VINT} corresponding to this value
     */
    public static VINT fromValue(long value) {
        BitSet bs = BitSet.valueOf(new long[] { value });
        byte length = (byte) (1 + bs.length() / BIT_IN_BYTE);
        if (bs.length() == length * BIT_IN_BYTE) {
            length++;
        }
        bs.set(length * BIT_IN_BYTE - length);
        long binary = bs.toLongArray()[0];
        return new VINT(binary, length, value);
    }
}
