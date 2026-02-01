package org.red5.io.utils;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Hashtable;
import java.util.Vector;

import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.Integers;
import org.bouncycastle.util.encoders.Hex;
import org.bouncycastle.util.io.Streams;

/**
 * Some helper functions for the TLS API.
 *
 * @author mondain
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class TlsUtils {

    @SuppressWarnings("unused")
    private static byte[] DOWNGRADE_TLS11 = Hex.decodeStrict("444F574E47524400"), DOWNGRADE_TLS12 = Hex.decodeStrict("444F574E47524401");

    /** Constant <code>EMPTY_BYTES</code> */
    public static final byte[] EMPTY_BYTES = new byte[0];

    /** Constant <code>EMPTY_SHORTS</code> */
    public static final short[] EMPTY_SHORTS = new short[0];

    /** Constant <code>EMPTY_INTS</code> */
    public static final int[] EMPTY_INTS = new int[0];

    /** Constant <code>EMPTY_LONGS</code> */
    public static final long[] EMPTY_LONGS = new long[0];

    /** Constant <code>EMPTY_STRINGS</code> */
    public static final String[] EMPTY_STRINGS = new String[0];

    /**
     * <p>isValidUint8.</p>
     *
     * @param i a short
     * @return a boolean
     */
    public static boolean isValidUint8(short i) {
        return (i & 0xFF) == i;
    }

    /**
     * <p>isValidUint8.</p>
     *
     * @param i a int
     * @return a boolean
     */
    public static boolean isValidUint8(int i) {
        return (i & 0xFF) == i;
    }

    /**
     * <p>isValidUint8.</p>
     *
     * @param i a long
     * @return a boolean
     */
    public static boolean isValidUint8(long i) {
        return (i & 0xFFL) == i;
    }

    /**
     * <p>isValidUint16.</p>
     *
     * @param i a int
     * @return a boolean
     */
    public static boolean isValidUint16(int i) {
        return (i & 0xFFFF) == i;
    }

    /**
     * <p>isValidUint16.</p>
     *
     * @param i a long
     * @return a boolean
     */
    public static boolean isValidUint16(long i) {
        return (i & 0xFFFFL) == i;
    }

    /**
     * <p>isValidUint24.</p>
     *
     * @param i a int
     * @return a boolean
     */
    public static boolean isValidUint24(int i) {
        return (i & 0xFFFFFF) == i;
    }

    /**
     * <p>isValidUint24.</p>
     *
     * @param i a long
     * @return a boolean
     */
    public static boolean isValidUint24(long i) {
        return (i & 0xFFFFFFL) == i;
    }

    /**
     * <p>isValidUint32.</p>
     *
     * @param i a long
     * @return a boolean
     */
    public static boolean isValidUint32(long i) {
        return (i & 0xFFFFFFFFL) == i;
    }

    /**
     * <p>isValidUint48.</p>
     *
     * @param i a long
     * @return a boolean
     */
    public static boolean isValidUint48(long i) {
        return (i & 0xFFFFFFFFFFFFL) == i;
    }

    /**
     * <p>isValidUint64.</p>
     *
     * @param i a long
     * @return a boolean
     */
    public static boolean isValidUint64(long i) {
        return true;
    }

    /**
     * <p>writeUint8.</p>
     *
     * @param i a short
     * @param output a {@link java.io.OutputStream} object
     * @throws java.io.IOException if any.
     */
    public static void writeUint8(short i, OutputStream output) throws IOException {
        output.write(i);
    }

    /**
     * <p>writeUint8.</p>
     *
     * @param i a int
     * @param output a {@link java.io.OutputStream} object
     * @throws java.io.IOException if any.
     */
    public static void writeUint8(int i, OutputStream output) throws IOException {
        output.write(i);
    }

    /**
     * <p>writeUint8.</p>
     *
     * @param i a short
     * @param buf an array of {@link byte} objects
     * @param offset a int
     */
    public static void writeUint8(short i, byte[] buf, int offset) {
        buf[offset] = (byte) i;
    }

    /**
     * <p>writeUint8.</p>
     *
     * @param i a int
     * @param buf an array of {@link byte} objects
     * @param offset a int
     */
    public static void writeUint8(int i, byte[] buf, int offset) {
        buf[offset] = (byte) i;
    }

    /**
     * <p>writeUint16.</p>
     *
     * @param i a int
     * @param output a {@link java.io.OutputStream} object
     * @throws java.io.IOException if any.
     */
    public static void writeUint16(int i, OutputStream output) throws IOException {
        output.write(i >>> 8);
        output.write(i);
    }

    /**
     * <p>writeUint16.</p>
     *
     * @param i a int
     * @param buf an array of {@link byte} objects
     * @param offset a int
     */
    public static void writeUint16(int i, byte[] buf, int offset) {
        buf[offset] = (byte) (i >>> 8);
        buf[offset + 1] = (byte) i;
    }

    /**
     * <p>writeUint24.</p>
     *
     * @param i a int
     * @param output a {@link java.io.OutputStream} object
     * @throws java.io.IOException if any.
     */
    public static void writeUint24(int i, OutputStream output) throws IOException {
        output.write((byte) (i >>> 16));
        output.write((byte) (i >>> 8));
        output.write((byte) i);
    }

    /**
     * <p>writeUint24.</p>
     *
     * @param i a int
     * @param buf an array of {@link byte} objects
     * @param offset a int
     */
    public static void writeUint24(int i, byte[] buf, int offset) {
        buf[offset] = (byte) (i >>> 16);
        buf[offset + 1] = (byte) (i >>> 8);
        buf[offset + 2] = (byte) i;
    }

    /**
     * <p>writeUint32.</p>
     *
     * @param i a long
     * @param output a {@link java.io.OutputStream} object
     * @throws java.io.IOException if any.
     */
    public static void writeUint32(long i, OutputStream output) throws IOException {
        output.write((byte) (i >>> 24));
        output.write((byte) (i >>> 16));
        output.write((byte) (i >>> 8));
        output.write((byte) i);
    }

    /**
     * <p>writeUint32.</p>
     *
     * @param i a long
     * @param buf an array of {@link byte} objects
     * @param offset a int
     */
    public static void writeUint32(long i, byte[] buf, int offset) {
        buf[offset] = (byte) (i >>> 24);
        buf[offset + 1] = (byte) (i >>> 16);
        buf[offset + 2] = (byte) (i >>> 8);
        buf[offset + 3] = (byte) i;
    }

    /**
     * <p>writeUint48.</p>
     *
     * @param i a long
     * @param output a {@link java.io.OutputStream} object
     * @throws java.io.IOException if any.
     */
    public static void writeUint48(long i, OutputStream output) throws IOException {
        output.write((byte) (i >>> 40));
        output.write((byte) (i >>> 32));
        output.write((byte) (i >>> 24));
        output.write((byte) (i >>> 16));
        output.write((byte) (i >>> 8));
        output.write((byte) i);
    }

    /**
     * <p>writeUint48.</p>
     *
     * @param i a long
     * @param buf an array of {@link byte} objects
     * @param offset a int
     */
    public static void writeUint48(long i, byte[] buf, int offset) {
        buf[offset] = (byte) (i >>> 40);
        buf[offset + 1] = (byte) (i >>> 32);
        buf[offset + 2] = (byte) (i >>> 24);
        buf[offset + 3] = (byte) (i >>> 16);
        buf[offset + 4] = (byte) (i >>> 8);
        buf[offset + 5] = (byte) i;
    }

    /**
     * <p>writeUint64.</p>
     *
     * @param i a long
     * @param output a {@link java.io.OutputStream} object
     * @throws java.io.IOException if any.
     */
    public static void writeUint64(long i, OutputStream output) throws IOException {
        output.write((byte) (i >>> 56));
        output.write((byte) (i >>> 48));
        output.write((byte) (i >>> 40));
        output.write((byte) (i >>> 32));
        output.write((byte) (i >>> 24));
        output.write((byte) (i >>> 16));
        output.write((byte) (i >>> 8));
        output.write((byte) i);
    }

    /**
     * <p>writeUint64.</p>
     *
     * @param i a long
     * @param buf an array of {@link byte} objects
     * @param offset a int
     */
    public static void writeUint64(long i, byte[] buf, int offset) {
        buf[offset] = (byte) (i >>> 56);
        buf[offset + 1] = (byte) (i >>> 48);
        buf[offset + 2] = (byte) (i >>> 40);
        buf[offset + 3] = (byte) (i >>> 32);
        buf[offset + 4] = (byte) (i >>> 24);
        buf[offset + 5] = (byte) (i >>> 16);
        buf[offset + 6] = (byte) (i >>> 8);
        buf[offset + 7] = (byte) i;
    }

    /**
     * <p>writeOpaque8.</p>
     *
     * @param buf an array of {@link byte} objects
     * @param output a {@link java.io.OutputStream} object
     * @throws java.io.IOException if any.
     */
    public static void writeOpaque8(byte[] buf, OutputStream output) throws IOException {
        writeUint8(buf.length, output);
        output.write(buf);
    }

    /**
     * <p>writeOpaque8.</p>
     *
     * @param data an array of {@link byte} objects
     * @param buf an array of {@link byte} objects
     * @param off a int
     * @throws java.io.IOException if any.
     */
    public static void writeOpaque8(byte[] data, byte[] buf, int off) throws IOException {
        writeUint8(data.length, buf, off);
        System.arraycopy(data, 0, buf, off + 1, data.length);
    }

    /**
     * <p>writeOpaque16.</p>
     *
     * @param buf an array of {@link byte} objects
     * @param output a {@link java.io.OutputStream} object
     * @throws java.io.IOException if any.
     */
    public static void writeOpaque16(byte[] buf, OutputStream output) throws IOException {
        writeUint16(buf.length, output);
        output.write(buf);
    }

    /**
     * <p>writeOpaque16.</p>
     *
     * @param data an array of {@link byte} objects
     * @param buf an array of {@link byte} objects
     * @param off a int
     * @throws java.io.IOException if any.
     */
    public static void writeOpaque16(byte[] data, byte[] buf, int off) throws IOException {
        writeUint16(data.length, buf, off);
        System.arraycopy(data, 0, buf, off + 2, data.length);
    }

    /**
     * <p>writeOpaque24.</p>
     *
     * @param buf an array of {@link byte} objects
     * @param output a {@link java.io.OutputStream} object
     * @throws java.io.IOException if any.
     */
    public static void writeOpaque24(byte[] buf, OutputStream output) throws IOException {
        writeUint24(buf.length, output);
        output.write(buf);
    }

    /**
     * <p>writeOpaque24.</p>
     *
     * @param data an array of {@link byte} objects
     * @param buf an array of {@link byte} objects
     * @param off a int
     * @throws java.io.IOException if any.
     */
    public static void writeOpaque24(byte[] data, byte[] buf, int off) throws IOException {
        writeUint24(data.length, buf, off);
        System.arraycopy(data, 0, buf, off + 3, data.length);
    }

    /**
     * <p>writeUint8Array.</p>
     *
     * @param uints an array of {@link short} objects
     * @param output a {@link java.io.OutputStream} object
     * @throws java.io.IOException if any.
     */
    public static void writeUint8Array(short[] uints, OutputStream output) throws IOException {
        for (int i = 0; i < uints.length; ++i) {
            writeUint8(uints[i], output);
        }
    }

    /**
     * <p>writeUint8Array.</p>
     *
     * @param uints an array of {@link short} objects
     * @param buf an array of {@link byte} objects
     * @param offset a int
     * @throws java.io.IOException if any.
     */
    public static void writeUint8Array(short[] uints, byte[] buf, int offset) throws IOException {
        for (int i = 0; i < uints.length; ++i) {
            writeUint8(uints[i], buf, offset);
            ++offset;
        }
    }

    /**
     * <p>writeUint8ArrayWithUint8Length.</p>
     *
     * @param uints an array of {@link short} objects
     * @param output a {@link java.io.OutputStream} object
     * @throws java.io.IOException if any.
     */
    public static void writeUint8ArrayWithUint8Length(short[] uints, OutputStream output) throws IOException {
        writeUint8(uints.length, output);
        writeUint8Array(uints, output);
    }

    /**
     * <p>writeUint8ArrayWithUint8Length.</p>
     *
     * @param uints an array of {@link short} objects
     * @param buf an array of {@link byte} objects
     * @param offset a int
     * @throws java.io.IOException if any.
     */
    public static void writeUint8ArrayWithUint8Length(short[] uints, byte[] buf, int offset) throws IOException {
        writeUint8(uints.length, buf, offset);
        writeUint8Array(uints, buf, offset + 1);
    }

    /**
     * <p>writeUint16Array.</p>
     *
     * @param uints an array of {@link int} objects
     * @param output a {@link java.io.OutputStream} object
     * @throws java.io.IOException if any.
     */
    public static void writeUint16Array(int[] uints, OutputStream output) throws IOException {
        for (int i = 0; i < uints.length; ++i) {
            writeUint16(uints[i], output);
        }
    }

    /**
     * <p>writeUint16Array.</p>
     *
     * @param uints an array of {@link int} objects
     * @param buf an array of {@link byte} objects
     * @param offset a int
     * @throws java.io.IOException if any.
     */
    public static void writeUint16Array(int[] uints, byte[] buf, int offset) throws IOException {
        for (int i = 0; i < uints.length; ++i) {
            writeUint16(uints[i], buf, offset);
            offset += 2;
        }
    }

    /**
     * <p>writeUint16ArrayWithUint8Length.</p>
     *
     * @param uints an array of {@link int} objects
     * @param buf an array of {@link byte} objects
     * @param offset a int
     * @throws java.io.IOException if any.
     */
    public static void writeUint16ArrayWithUint8Length(int[] uints, byte[] buf, int offset) throws IOException {
        int length = 2 * uints.length;
        writeUint8(length, buf, offset);
        writeUint16Array(uints, buf, offset + 1);
    }

    /**
     * <p>writeUint16ArrayWithUint16Length.</p>
     *
     * @param uints an array of {@link int} objects
     * @param output a {@link java.io.OutputStream} object
     * @throws java.io.IOException if any.
     */
    public static void writeUint16ArrayWithUint16Length(int[] uints, OutputStream output) throws IOException {
        int length = 2 * uints.length;
        writeUint16(length, output);
        writeUint16Array(uints, output);
    }

    /**
     * <p>writeUint16ArrayWithUint16Length.</p>
     *
     * @param uints an array of {@link int} objects
     * @param buf an array of {@link byte} objects
     * @param offset a int
     * @throws java.io.IOException if any.
     */
    public static void writeUint16ArrayWithUint16Length(int[] uints, byte[] buf, int offset) throws IOException {
        int length = 2 * uints.length;
        writeUint16(length, buf, offset);
        writeUint16Array(uints, buf, offset + 2);
    }

    /**
     * <p>decodeOpaque8.</p>
     *
     * @param buf an array of {@link byte} objects
     * @return an array of {@link byte} objects
     * @throws java.io.IOException if any.
     */
    public static byte[] decodeOpaque8(byte[] buf) throws IOException {
        return decodeOpaque8(buf, 0);
    }

    /**
     * <p>decodeOpaque8.</p>
     *
     * @param buf an array of {@link byte} objects
     * @param minLength a int
     * @return an array of {@link byte} objects
     * @throws java.io.IOException if any.
     */
    public static byte[] decodeOpaque8(byte[] buf, int minLength) throws IOException {
        if (buf == null) {
            throw new IllegalArgumentException("'buf' cannot be null");
        }
        if (buf.length < 1) {
            throw new IOException("AlertDescription.decode_error");
        }
        short length = readUint8(buf, 0);
        if (buf.length != (length + 1) || length < minLength) {
            throw new IOException("AlertDescription.decode_error");
        }
        return copyOfRangeExact(buf, 1, buf.length);
    }

    /**
     * <p>decodeOpaque16.</p>
     *
     * @param buf an array of {@link byte} objects
     * @return an array of {@link byte} objects
     * @throws java.io.IOException if any.
     */
    public static byte[] decodeOpaque16(byte[] buf) throws IOException {
        return decodeOpaque16(buf, 0);
    }

    /**
     * <p>decodeOpaque16.</p>
     *
     * @param buf an array of {@link byte} objects
     * @param minLength a int
     * @return an array of {@link byte} objects
     * @throws java.io.IOException if any.
     */
    public static byte[] decodeOpaque16(byte[] buf, int minLength) throws IOException {
        if (buf == null) {
            throw new IllegalArgumentException("'buf' cannot be null");
        }
        if (buf.length < 2) {
            throw new IOException("AlertDescription.decode_error");
        }
        int length = readUint16(buf, 0);
        if (buf.length != (length + 2) || length < minLength) {
            throw new IOException("AlertDescription.decode_error");
        }
        return copyOfRangeExact(buf, 2, buf.length);
    }

    /**
     * <p>decodeUint8.</p>
     *
     * @param buf an array of {@link byte} objects
     * @return a short
     * @throws java.io.IOException if any.
     */
    public static short decodeUint8(byte[] buf) throws IOException {
        if (buf == null) {
            throw new IllegalArgumentException("'buf' cannot be null");
        }
        if (buf.length != 1) {
            throw new IOException("AlertDescription.decode_error");
        }
        return readUint8(buf, 0);
    }

    /**
     * <p>decodeUint8ArrayWithUint8Length.</p>
     *
     * @param buf an array of {@link byte} objects
     * @return an array of {@link short} objects
     * @throws java.io.IOException if any.
     */
    public static short[] decodeUint8ArrayWithUint8Length(byte[] buf) throws IOException {
        if (buf == null) {
            throw new IllegalArgumentException("'buf' cannot be null");
        }
        if (buf.length < 1) {
            throw new IOException("AlertDescription.decode_error");
        }

        int count = readUint8(buf, 0);
        if (buf.length != (count + 1)) {
            throw new IOException("AlertDescription.decode_error");
        }

        short[] uints = new short[count];
        for (int i = 0; i < count; ++i) {
            uints[i] = readUint8(buf, i + 1);
        }
        return uints;
    }

    /**
     * <p>decodeUint16.</p>
     *
     * @param buf an array of {@link byte} objects
     * @return a int
     * @throws java.io.IOException if any.
     */
    public static int decodeUint16(byte[] buf) throws IOException {
        if (buf == null) {
            throw new IllegalArgumentException("'buf' cannot be null");
        }
        if (buf.length != 2) {
            throw new IOException("AlertDescription.decode_error");
        }
        return readUint16(buf, 0);
    }

    /**
     * <p>decodeUint16ArrayWithUint8Length.</p>
     *
     * @param buf an array of {@link byte} objects
     * @return an array of {@link int} objects
     * @throws java.io.IOException if any.
     */
    public static int[] decodeUint16ArrayWithUint8Length(byte[] buf) throws IOException {
        if (buf == null) {
            throw new IllegalArgumentException("'buf' cannot be null");
        }

        int length = readUint8(buf, 0);
        if (buf.length != (length + 1) || (length & 1) != 0) {
            throw new IOException("AlertDescription.decode_error");
        }

        int count = length / 2, pos = 1;
        int[] uints = new int[count];
        for (int i = 0; i < count; ++i) {
            uints[i] = readUint16(buf, pos);
            pos += 2;
        }
        return uints;
    }

    /**
     * <p>decodeUint32.</p>
     *
     * @param buf an array of {@link byte} objects
     * @return a long
     * @throws java.io.IOException if any.
     */
    public static long decodeUint32(byte[] buf) throws IOException {
        if (buf == null) {
            throw new IllegalArgumentException("'buf' cannot be null");
        }
        if (buf.length != 4) {
            throw new IOException("AlertDescription.decode_error");
        }
        return readUint32(buf, 0);
    }

    /**
     * <p>encodeOpaque8.</p>
     *
     * @param buf an array of {@link byte} objects
     * @return an array of {@link byte} objects
     * @throws java.io.IOException if any.
     */
    public static byte[] encodeOpaque8(byte[] buf) throws IOException {
        return Arrays.prepend(buf, (byte) buf.length);
    }

    /**
     * <p>encodeOpaque16.</p>
     *
     * @param buf an array of {@link byte} objects
     * @return an array of {@link byte} objects
     * @throws java.io.IOException if any.
     */
    public static byte[] encodeOpaque16(byte[] buf) throws IOException {
        byte[] r = new byte[2 + buf.length];
        writeUint16(buf.length, r, 0);
        System.arraycopy(buf, 0, r, 2, buf.length);
        return r;
    }

    /**
     * <p>encodeOpaque24.</p>
     *
     * @param buf an array of {@link byte} objects
     * @return an array of {@link byte} objects
     * @throws java.io.IOException if any.
     */
    public static byte[] encodeOpaque24(byte[] buf) throws IOException {
        byte[] r = new byte[3 + buf.length];
        writeUint24(buf.length, r, 0);
        System.arraycopy(buf, 0, r, 3, buf.length);
        return r;
    }

    /**
     * <p>encodeUint8.</p>
     *
     * @param uint a short
     * @return an array of {@link byte} objects
     * @throws java.io.IOException if any.
     */
    public static byte[] encodeUint8(short uint) throws IOException {
        byte[] encoding = new byte[1];
        writeUint8(uint, encoding, 0);
        return encoding;
    }

    /**
     * <p>encodeUint8ArrayWithUint8Length.</p>
     *
     * @param uints an array of {@link short} objects
     * @return an array of {@link byte} objects
     * @throws java.io.IOException if any.
     */
    public static byte[] encodeUint8ArrayWithUint8Length(short[] uints) throws IOException {
        byte[] result = new byte[1 + uints.length];
        writeUint8ArrayWithUint8Length(uints, result, 0);
        return result;
    }

    /**
     * <p>encodeUint16.</p>
     *
     * @param uint a int
     * @return an array of {@link byte} objects
     * @throws java.io.IOException if any.
     */
    public static byte[] encodeUint16(int uint) throws IOException {
        byte[] encoding = new byte[2];
        writeUint16(uint, encoding, 0);
        return encoding;
    }

    /**
     * <p>encodeUint16ArrayWithUint8Length.</p>
     *
     * @param uints an array of {@link int} objects
     * @return an array of {@link byte} objects
     * @throws java.io.IOException if any.
     */
    public static byte[] encodeUint16ArrayWithUint8Length(int[] uints) throws IOException {
        int length = 2 * uints.length;
        byte[] result = new byte[1 + length];
        writeUint16ArrayWithUint8Length(uints, result, 0);
        return result;
    }

    /**
     * <p>encodeUint16ArrayWithUint16Length.</p>
     *
     * @param uints an array of {@link int} objects
     * @return an array of {@link byte} objects
     * @throws java.io.IOException if any.
     */
    public static byte[] encodeUint16ArrayWithUint16Length(int[] uints) throws IOException {
        int length = 2 * uints.length;
        byte[] result = new byte[2 + length];
        writeUint16ArrayWithUint16Length(uints, result, 0);
        return result;
    }

    /**
     * <p>encodeUint24.</p>
     *
     * @param uint a int
     * @return an array of {@link byte} objects
     * @throws java.io.IOException if any.
     */
    public static byte[] encodeUint24(int uint) throws IOException {
        byte[] encoding = new byte[3];
        writeUint24(uint, encoding, 0);
        return encoding;
    }

    /**
     * <p>encodeUint32.</p>
     *
     * @param uint a long
     * @return an array of {@link byte} objects
     * @throws java.io.IOException if any.
     */
    public static byte[] encodeUint32(long uint) throws IOException {
        byte[] encoding = new byte[4];
        writeUint32(uint, encoding, 0);
        return encoding;
    }

    /**
     * <p>readInt32.</p>
     *
     * @param buf an array of {@link byte} objects
     * @param offset a int
     * @return a int
     */
    public static int readInt32(byte[] buf, int offset) {
        int n = buf[offset] << 24;
        n |= (buf[++offset] & 0xff) << 16;
        n |= (buf[++offset] & 0xff) << 8;
        n |= (buf[++offset] & 0xff);
        return n;
    }

    /**
     * <p>readUint8.</p>
     *
     * @param input a {@link java.io.InputStream} object
     * @return a short
     * @throws java.io.IOException if any.
     */
    public static short readUint8(InputStream input) throws IOException {
        int i = input.read();
        if (i < 0) {
            throw new EOFException();
        }
        return (short) i;
    }

    /**
     * <p>readUint8.</p>
     *
     * @param buf an array of {@link byte} objects
     * @param offset a int
     * @return a short
     */
    public static short readUint8(byte[] buf, int offset) {
        return (short) (buf[offset] & 0xff);
    }

    /**
     * <p>readUint16.</p>
     *
     * @param input a {@link java.io.InputStream} object
     * @return a int
     * @throws java.io.IOException if any.
     */
    public static int readUint16(InputStream input) throws IOException {
        int i1 = input.read();
        if (i1 < 0) {
            throw new EOFException();
        }
        int i2 = input.read();
        if (i2 < 0) {
            throw new EOFException();
        }
        return (i1 << 8) | i2;
    }

    /**
     * <p>readUint16.</p>
     *
     * @param buf an array of {@link byte} objects
     * @param offset a int
     * @return a int
     */
    public static int readUint16(byte[] buf, int offset) {
        int n = (buf[offset] & 0xff) << 8;
        n |= (buf[++offset] & 0xff);
        return n;
    }

    /**
     * <p>readUint24.</p>
     *
     * @param input a {@link java.io.InputStream} object
     * @return a int
     * @throws java.io.IOException if any.
     */
    public static int readUint24(InputStream input) throws IOException {
        int i1 = input.read();
        if (i1 < 0) {
            throw new EOFException();
        }
        int i2 = input.read();
        if (i2 < 0) {
            throw new EOFException();
        }
        int i3 = input.read();
        if (i3 < 0) {
            throw new EOFException();
        }
        return (i1 << 16) | (i2 << 8) | i3;
    }

    /**
     * <p>readUint24.</p>
     *
     * @param buf an array of {@link byte} objects
     * @param offset a int
     * @return a int
     */
    public static int readUint24(byte[] buf, int offset) {
        int n = (buf[offset] & 0xff) << 16;
        n |= (buf[++offset] & 0xff) << 8;
        n |= (buf[++offset] & 0xff);
        return n;
    }

    /**
     * <p>readUint32.</p>
     *
     * @param input a {@link java.io.InputStream} object
     * @return a long
     * @throws java.io.IOException if any.
     */
    public static long readUint32(InputStream input) throws IOException {
        int i1 = input.read();
        if (i1 < 0) {
            throw new EOFException();
        }
        int i2 = input.read();
        if (i2 < 0) {
            throw new EOFException();
        }
        int i3 = input.read();
        if (i3 < 0) {
            throw new EOFException();
        }
        int i4 = input.read();
        if (i4 < 0) {
            throw new EOFException();
        }
        return ((i1 << 24) | (i2 << 16) | (i3 << 8) | i4) & 0xFFFFFFFFL;
    }

    /**
     * <p>readUint32.</p>
     *
     * @param buf an array of {@link byte} objects
     * @param offset a int
     * @return a long
     */
    public static long readUint32(byte[] buf, int offset) {
        int n = (buf[offset] & 0xff) << 24;
        n |= (buf[++offset] & 0xff) << 16;
        n |= (buf[++offset] & 0xff) << 8;
        n |= (buf[++offset] & 0xff);
        return n & 0xFFFFFFFFL;
    }

    /**
     * <p>readUint48.</p>
     *
     * @param input a {@link java.io.InputStream} object
     * @return a long
     * @throws java.io.IOException if any.
     */
    public static long readUint48(InputStream input) throws IOException {
        int hi = readUint24(input);
        int lo = readUint24(input);
        return ((long) (hi & 0xffffffffL) << 24) | (long) (lo & 0xffffffffL);
    }

    /**
     * <p>readUint48.</p>
     *
     * @param buf an array of {@link byte} objects
     * @param offset a int
     * @return a long
     */
    public static long readUint48(byte[] buf, int offset) {
        int hi = readUint24(buf, offset);
        int lo = readUint24(buf, offset + 3);
        return ((long) (hi & 0xffffffffL) << 24) | (long) (lo & 0xffffffffL);
    }

    /**
     * <p>readAllOrNothing.</p>
     *
     * @param length a int
     * @param input a {@link java.io.InputStream} object
     * @return an array of {@link byte} objects
     * @throws java.io.IOException if any.
     */
    public static byte[] readAllOrNothing(int length, InputStream input) throws IOException {
        if (length < 1) {
            return EMPTY_BYTES;
        }
        byte[] buf = new byte[length];
        int read = Streams.readFully(input, buf);
        if (read == 0) {
            return null;
        }
        if (read != length) {
            throw new EOFException();
        }
        return buf;
    }

    /**
     * <p>readFully.</p>
     *
     * @param length a int
     * @param input a {@link java.io.InputStream} object
     * @return an array of {@link byte} objects
     * @throws java.io.IOException if any.
     */
    public static byte[] readFully(int length, InputStream input) throws IOException {
        if (length < 1) {
            return EMPTY_BYTES;
        }
        byte[] buf = new byte[length];
        if (length != Streams.readFully(input, buf)) {
            throw new EOFException();
        }
        return buf;
    }

    /**
     * <p>readFully.</p>
     *
     * @param buf an array of {@link byte} objects
     * @param input a {@link java.io.InputStream} object
     * @throws java.io.IOException if any.
     */
    public static void readFully(byte[] buf, InputStream input) throws IOException {
        int length = buf.length;
        if (length > 0 && length != Streams.readFully(input, buf)) {
            throw new EOFException();
        }
    }

    /**
     * <p>readOpaque8.</p>
     *
     * @param input a {@link java.io.InputStream} object
     * @return an array of {@link byte} objects
     * @throws java.io.IOException if any.
     */
    public static byte[] readOpaque8(InputStream input) throws IOException {
        short length = readUint8(input);
        return readFully(length, input);
    }

    /**
     * <p>readOpaque8.</p>
     *
     * @param input a {@link java.io.InputStream} object
     * @param minLength a int
     * @return an array of {@link byte} objects
     * @throws java.io.IOException if any.
     */
    public static byte[] readOpaque8(InputStream input, int minLength) throws IOException {
        short length = readUint8(input);
        if (length < minLength) {
            throw new IOException("AlertDescription.decode_error");
        }
        return readFully(length, input);
    }

    /**
     * <p>readOpaque8.</p>
     *
     * @param input a {@link java.io.InputStream} object
     * @param minLength a int
     * @param maxLength a int
     * @return an array of {@link byte} objects
     * @throws java.io.IOException if any.
     */
    public static byte[] readOpaque8(InputStream input, int minLength, int maxLength) throws IOException {
        short length = readUint8(input);
        if (length < minLength || maxLength < length) {
            throw new IOException("AlertDescription.decode_error");
        }
        return readFully(length, input);
    }

    /**
     * <p>readOpaque16.</p>
     *
     * @param input a {@link java.io.InputStream} object
     * @return an array of {@link byte} objects
     * @throws java.io.IOException if any.
     */
    public static byte[] readOpaque16(InputStream input) throws IOException {
        int length = readUint16(input);
        return readFully(length, input);
    }

    /**
     * <p>readOpaque16.</p>
     *
     * @param input a {@link java.io.InputStream} object
     * @param minLength a int
     * @return an array of {@link byte} objects
     * @throws java.io.IOException if any.
     */
    public static byte[] readOpaque16(InputStream input, int minLength) throws IOException {
        int length = readUint16(input);
        if (length < minLength) {
            throw new IOException("AlertDescription.decode_error");
        }
        return readFully(length, input);
    }

    /**
     * <p>readOpaque24.</p>
     *
     * @param input a {@link java.io.InputStream} object
     * @return an array of {@link byte} objects
     * @throws java.io.IOException if any.
     */
    public static byte[] readOpaque24(InputStream input) throws IOException {
        int length = readUint24(input);
        return readFully(length, input);
    }

    /**
     * <p>readOpaque24.</p>
     *
     * @param input a {@link java.io.InputStream} object
     * @param minLength a int
     * @return an array of {@link byte} objects
     * @throws java.io.IOException if any.
     */
    public static byte[] readOpaque24(InputStream input, int minLength) throws IOException {
        int length = readUint24(input);
        if (length < minLength) {
            throw new IOException("AlertDescription.decode_error");
        }
        return readFully(length, input);
    }

    /**
     * <p>readUint8Array.</p>
     *
     * @param count a int
     * @param input a {@link java.io.InputStream} object
     * @return an array of {@link short} objects
     * @throws java.io.IOException if any.
     */
    public static short[] readUint8Array(int count, InputStream input) throws IOException {
        short[] uints = new short[count];
        for (int i = 0; i < count; ++i) {
            uints[i] = readUint8(input);
        }
        return uints;
    }

    /**
     * <p>readUint8ArrayWithUint8Length.</p>
     *
     * @param input a {@link java.io.InputStream} object
     * @param minLength a int
     * @return an array of {@link short} objects
     * @throws java.io.IOException if any.
     */
    public static short[] readUint8ArrayWithUint8Length(InputStream input, int minLength) throws IOException {
        int length = readUint8(input);
        if (length < minLength) {
            throw new IOException("AlertDescription.decode_error");
        }

        return readUint8Array(length, input);
    }

    /**
     * <p>readUint16Array.</p>
     *
     * @param count a int
     * @param input a {@link java.io.InputStream} object
     * @return an array of {@link int} objects
     * @throws java.io.IOException if any.
     */
    public static int[] readUint16Array(int count, InputStream input) throws IOException {
        int[] uints = new int[count];
        for (int i = 0; i < count; ++i) {
            uints[i] = readUint16(input);
        }
        return uints;
    }

    /**
     * <p>readASN1Object.</p>
     *
     * @param encoding an array of {@link byte} objects
     * @return a {@link org.bouncycastle.asn1.ASN1Primitive} object
     * @throws java.io.IOException if any.
     */
    public static ASN1Primitive readASN1Object(byte[] encoding) throws IOException {
        try (ASN1InputStream asn1 = new ASN1InputStream(encoding)) {
            ASN1Primitive result = asn1.readObject();
            if (null == result) {
                throw new IOException("AlertDescription.decode_error");
            }
            if (null != asn1.readObject()) {
                throw new IOException("AlertDescription.decode_error");
            }
            return result;
        }
    }

    /** @deprecated Will be removed. Use readASN1Object in combination with requireDEREncoding instead */
    /**
     * <p>readDERObject.</p>
     *
     * @param encoding an array of {@link byte} objects
     * @return a {@link org.bouncycastle.asn1.ASN1Primitive} object
     * @throws java.io.IOException if any.
     */
    public static ASN1Primitive readDERObject(byte[] encoding) throws IOException {
        /*
         * NOTE: The current ASN.1 parsing code can't enforce DER-only parsing, but since DER is
         * canonical, we can check it by re-encoding the result and comparing to the original.
         */
        ASN1Primitive result = readASN1Object(encoding);
        requireDEREncoding(result, encoding);
        return result;
    }

    /**
     * <p>requireDEREncoding.</p>
     *
     * @param asn1 a {@link org.bouncycastle.asn1.ASN1Object} object
     * @param encoding an array of {@link byte} objects
     * @throws java.io.IOException if any.
     */
    public static void requireDEREncoding(ASN1Object asn1, byte[] encoding) throws IOException {
        /*
         * NOTE: The current ASN.1 parsing code can't enforce DER-only parsing, but since DER is
         * canonical, we can check it by re-encoding the result and comparing to the original.
         */
        byte[] check = asn1.getEncoded(ASN1Encoding.DER);
        if (!Arrays.areEqual(check, encoding)) {
            throw new IOException("AlertDescription.decode_error");
        }
    }

    /**
     * <p>writeGMTUnixTime.</p>
     *
     * @param buf an array of {@link byte} objects
     * @param offset a int
     */
    public static void writeGMTUnixTime(byte[] buf, int offset) {
        int t = (int) (System.currentTimeMillis() / 1000L);
        buf[offset] = (byte) (t >>> 24);
        buf[offset + 1] = (byte) (t >>> 16);
        buf[offset + 2] = (byte) (t >>> 8);
        buf[offset + 3] = (byte) t;
    }

    /**
     * <p>addToSet.</p>
     *
     * @param s a {@link java.util.Vector} object
     * @param i a int
     * @return a boolean
     */
    public static boolean addToSet(Vector s, int i) {
        boolean result = !s.contains(Integers.valueOf(i));
        if (result) {
            s.add(Integers.valueOf(i));
        }
        return result;
    }

    /**
     * <p>getExtensionData.</p>
     *
     * @param extensions a {@link java.util.Hashtable} object
     * @param extensionType a {@link java.lang.Integer} object
     * @return an array of {@link byte} objects
     */
    public static byte[] getExtensionData(Hashtable extensions, Integer extensionType) {
        return extensions == null ? null : (byte[]) extensions.get(extensionType);
    }

    /**
     * <p>hasExpectedEmptyExtensionData.</p>
     *
     * @param extensions a {@link java.util.Hashtable} object
     * @param extensionType a {@link java.lang.Integer} object
     * @param alertDescription a short
     * @return a boolean
     * @throws java.io.IOException if any.
     */
    public static boolean hasExpectedEmptyExtensionData(Hashtable extensions, Integer extensionType, short alertDescription) throws IOException {
        byte[] extension_data = getExtensionData(extensions, extensionType);
        if (extension_data == null) {
            return false;
        }
        if (extension_data.length != 0) {
            throw new IOException("" + alertDescription);
        }
        return true;
    }

    /**
     * <p>isNullOrContainsNull.</p>
     *
     * @param array an array of {@link java.lang.Object} objects
     * @return a boolean
     */
    public static boolean isNullOrContainsNull(Object[] array) {
        if (null == array) {
            return true;
        }
        int count = array.length;
        for (int i = 0; i < count; ++i) {
            if (null == array[i]) {
                return true;
            }
        }
        return false;
    }

    /**
     * <p>isNullOrEmpty.</p>
     *
     * @param array an array of {@link byte} objects
     * @return a boolean
     */
    public static boolean isNullOrEmpty(byte[] array) {
        return null == array || array.length < 1;
    }

    /**
     * <p>isNullOrEmpty.</p>
     *
     * @param array an array of {@link short} objects
     * @return a boolean
     */
    public static boolean isNullOrEmpty(short[] array) {
        return null == array || array.length < 1;
    }

    /**
     * <p>isNullOrEmpty.</p>
     *
     * @param array an array of {@link int} objects
     * @return a boolean
     */
    public static boolean isNullOrEmpty(int[] array) {
        return null == array || array.length < 1;
    }

    /**
     * <p>isNullOrEmpty.</p>
     *
     * @param array an array of {@link java.lang.Object} objects
     * @return a boolean
     */
    public static boolean isNullOrEmpty(Object[] array) {
        return null == array || array.length < 1;
    }

    /**
     * <p>isNullOrEmpty.</p>
     *
     * @param s a {@link java.lang.String} object
     * @return a boolean
     */
    public static boolean isNullOrEmpty(String s) {
        return null == s || s.length() < 1;
    }

    /**
     * <p>isNullOrEmpty.</p>
     *
     * @param v a {@link java.util.Vector} object
     * @return a boolean
     */
    public static boolean isNullOrEmpty(Vector v) {
        return null == v || v.isEmpty();
    }

    /**
     * <p>clone.</p>
     *
     * @param data an array of {@link byte} objects
     * @return an array of {@link byte} objects
     */
    public static byte[] clone(byte[] data) {
        return null == data ? (byte[]) null : data.length == 0 ? EMPTY_BYTES : (byte[]) data.clone();
    }

    /**
     * <p>clone.</p>
     *
     * @param s an array of {@link java.lang.String} objects
     * @return an array of {@link java.lang.String} objects
     */
    public static String[] clone(String[] s) {
        return null == s ? (String[]) null : s.length < 1 ? EMPTY_STRINGS : (String[]) s.clone();
    }

    /**
     * <p>constantTimeAreEqual.</p>
     *
     * @param len a int
     * @param a an array of {@link byte} objects
     * @param aOff a int
     * @param b an array of {@link byte} objects
     * @param bOff a int
     * @return a boolean
     */
    public static boolean constantTimeAreEqual(int len, byte[] a, int aOff, byte[] b, int bOff) {
        int d = 0;
        for (int i = 0; i < len; ++i) {
            d |= (a[aOff + i] ^ b[bOff + i]);
        }
        return 0 == d;
    }

    /**
     * <p>copyOfRangeExact.</p>
     *
     * @param original an array of {@link byte} objects
     * @param from a int
     * @param to a int
     * @return an array of {@link byte} objects
     */
    public static byte[] copyOfRangeExact(byte[] original, int from, int to) {
        int newLength = to - from;
        byte[] copy = new byte[newLength];
        System.arraycopy(original, from, copy, 0, newLength);
        return copy;
    }

    static byte[] concat(byte[] a, byte[] b) {
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    @SuppressWarnings("unused")
    private static byte[] getCertificateVerifyHeader(String contextString) {
        int count = contextString.length();
        byte[] header = new byte[64 + count + 1];
        for (int i = 0; i < 64; ++i) {
            header[i] = 0x20;
        }
        for (int i = 0; i < count; ++i) {
            char c = contextString.charAt(i);
            header[64 + i] = (byte) c;
        }
        header[64 + count] = 0x00;
        return header;
    }

    /**
     * <p>vectorOfOne.</p>
     *
     * @param obj a {@link java.lang.Object} object
     * @return a {@link java.util.Vector} object
     */
    public static Vector vectorOfOne(Object obj) {
        Vector v = new Vector(1);
        v.addElement(obj);
        return v;
    }

    /**
     * <p>getCommonCipherSuites.</p>
     *
     * @param peerCipherSuites an array of {@link int} objects
     * @param localCipherSuites an array of {@link int} objects
     * @param useLocalOrder a boolean
     * @return an array of {@link int} objects
     */
    public static int[] getCommonCipherSuites(int[] peerCipherSuites, int[] localCipherSuites, boolean useLocalOrder) {
        int[] ordered = peerCipherSuites, unordered = localCipherSuites;
        if (useLocalOrder) {
            ordered = localCipherSuites;
            unordered = peerCipherSuites;
        }

        int count = 0, limit = Math.min(ordered.length, unordered.length);
        int[] candidates = new int[limit];
        for (int i = 0; i < ordered.length; ++i) {
            int candidate = ordered[i];
            if (!contains(candidates, 0, count, candidate) && Arrays.contains(unordered, candidate)) {
                candidates[count++] = candidate;
            }
        }

        if (count < limit) {
            candidates = Arrays.copyOf(candidates, count);
        }

        return candidates;
    }

    static boolean contains(short[] buf, int off, int len, short value) {
        for (int i = 0; i < len; ++i) {
            if (value == buf[off + i]) {
                return true;
            }
        }
        return false;
    }

    static boolean contains(int[] buf, int off, int len, int value) {
        for (int i = 0; i < len; ++i) {
            if (value == buf[off + i]) {
                return true;
            }
        }
        return false;
    }

    static boolean containsAll(short[] container, short[] elements) {
        for (int i = 0; i < elements.length; ++i) {
            if (!Arrays.contains(container, elements[i])) {
                return false;
            }
        }
        return true;
    }

    static boolean containsNot(short[] buf, int off, int len, short value) {
        for (int i = 0; i < len; ++i) {
            if (value != buf[off + i]) {
                return true;
            }
        }
        return false;
    }

    static short[] retainAll(short[] retainer, short[] elements) {
        short[] retained = new short[Math.min(retainer.length, elements.length)];

        int count = 0;
        for (int i = 0; i < elements.length; ++i) {
            if (Arrays.contains(retainer, elements[i])) {
                retained[count++] = elements[i];
            }
        }

        return truncate(retained, count);
    }

    static short[] truncate(short[] a, int n) {
        if (n >= a.length) {
            return a;
        }

        short[] t = new short[n];
        System.arraycopy(a, 0, t, 0, n);
        return t;
    }

    static int[] truncate(int[] a, int n) {
        if (n >= a.length) {
            return a;
        }

        int[] t = new int[n];
        System.arraycopy(a, 0, t, 0, n);
        return t;
    }

    /**
     * <p>containsNonAscii.</p>
     *
     * @param bs an array of {@link byte} objects
     * @return a boolean
     */
    public static boolean containsNonAscii(byte[] bs) {
        for (int i = 0; i < bs.length; ++i) {
            int c = bs[i] & 0xFF;
            ;
            if (c >= 0x80) {
                return true;
            }
        }
        return false;
    }

    /**
     * <p>containsNonAscii.</p>
     *
     * @param s a {@link java.lang.String} object
     * @return a boolean
     */
    public static boolean containsNonAscii(String s) {
        for (int i = 0; i < s.length(); ++i) {
            int c = s.charAt(i);
            if (c >= 0x80) {
                return true;
            }
        }
        return false;
    }

}
