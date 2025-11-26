/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.object;

import java.util.Arrays;

/**
 * The UnsignedByte class wraps a value of an unsigned 16 bits number.
 *
 * @author Matteo Merli (matteo.merli@gmail.com)
 */
public final class UnsignedShort extends UnsignedNumber {
    static final long serialVersionUID = 1L;

    private int value;

    /**
     * <p>Constructor for UnsignedShort.</p>
     *
     * @param c a byte
     */
    public UnsignedShort(byte c) {
        value = c;
    }

    /**
     * <p>Constructor for UnsignedShort.</p>
     *
     * @param c a short
     */
    public UnsignedShort(short c) {
        value = c;
    }

    /**
     * <p>Constructor for UnsignedShort.</p>
     *
     * @param c a int
     */
    public UnsignedShort(int c) {
        value = c & 0xFFFF;
    }

    /**
     * <p>Constructor for UnsignedShort.</p>
     *
     * @param c a long
     */
    public UnsignedShort(long c) {
        value = (int) (c & 0xFFFFL);
    }

    private UnsignedShort() {
        value = 0;
    }

    /**
     * <p>fromBytes.</p>
     *
     * @param c an array of {@link byte} objects
     * @return a {@link org.red5.io.object.UnsignedShort} object
     */
    public static UnsignedShort fromBytes(byte[] c) {
        return fromBytes(c, 0);
    }

    /**
     * <p>fromBytes.</p>
     *
     * @param c an array of {@link byte} objects
     * @param idx a int
     * @return a {@link org.red5.io.object.UnsignedShort} object
     */
    public static UnsignedShort fromBytes(byte[] c, int idx) {
        UnsignedShort number = new UnsignedShort();
        if ((c.length - idx) < 2) {
            throw new IllegalArgumentException("An UnsignedShort number is composed of 2 bytes");
        }
        number.value = ((c[idx] & 0xFF) << 8 | (c[idx + 1] & 0xFF));
        return number;
    }

    /**
     * <p>fromString.</p>
     *
     * @param c a {@link java.lang.String} object
     * @return a {@link org.red5.io.object.UnsignedShort} object
     */
    public static UnsignedShort fromString(String c) {
        return fromString(c, 10);
    }

    /**
     * <p>fromString.</p>
     *
     * @param c a {@link java.lang.String} object
     * @param radix a int
     * @return a {@link org.red5.io.object.UnsignedShort} object
     */
    public static UnsignedShort fromString(String c, int radix) {
        UnsignedShort number = new UnsignedShort();
        long v = Integer.parseInt(c, radix);
        number.value = (int) (v & 0xFFFF);
        return number;
    }

    /** {@inheritDoc} */
    @Override
    public double doubleValue() {
        return value;
    }

    /** {@inheritDoc} */
    @Override
    public float floatValue() {
        return value;
    }

    /** {@inheritDoc} */
    @Override
    public short shortValue() {
        return (short) (value & 0xFFFF);
    }

    /** {@inheritDoc} */
    @Override
    public int intValue() {
        return value & 0xFFFF;
    }

    /** {@inheritDoc} */
    @Override
    public long longValue() {
        return value & 0xFFFFL;
    }

    /** {@inheritDoc} */
    @Override
    public byte[] getBytes() {
        return new byte[] { (byte) ((value >> 8) & 0xFF), (byte) (value & 0xFF) };
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(UnsignedNumber other) {
        int otherValue = other.intValue();
        if (value > otherValue) {
            return 1;
        } else if (value < otherValue) {
            return -1;
        }
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other) {
        if (other instanceof UnsignedNumber) {
            return Arrays.equals(getBytes(), ((UnsignedNumber) other).getBytes());
        } else if (other instanceof Number) {
            long otherValue = ((Number) other).longValue() & 0xFFFFL;
            return (value & 0xFFFFL) == otherValue;
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return value;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return Integer.toString(value);
    }

    /** {@inheritDoc} */
    @Override
    public void shiftRight(int nBits) {
        if (Math.abs(nBits) > 16) {
            throw new IllegalArgumentException("Cannot right shift " + nBits + " an UnsignedShort");
        }
        value >>>= nBits;
    }

    /** {@inheritDoc} */
    @Override
    public void shiftLeft(int nBits) {
        if (Math.abs(nBits) > 16) {
            throw new IllegalArgumentException("Cannot left shift " + nBits + " an UnsignedShort");
        }
        value <<= nBits;
    }

}
