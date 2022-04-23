package org.red5.io.utils;

import static org.junit.Assert.*;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;

public class IOUtilsTest {

    @Test
    public void testWriteReverseInt() {
        final int ok = -771489792; // reversed int of 1234
        int source = 1234;
        IoBuffer out = IoBuffer.allocate(4);
        IOUtils.writeReverseInt(out, source);
        out.flip();
        int result = out.getInt();
        System.out.printf("Results - source: %d result: %d\n", source, result);
        out.flip();
        assertTrue(ok == result);
        // old method
        out.mark();
        byte[] bytes = new byte[4];
        IoBuffer rev = IoBuffer.allocate(4);
        rev.putInt(source);
        rev.flip();
        bytes[3] = rev.get();
        bytes[2] = rev.get();
        bytes[1] = rev.get();
        bytes[0] = rev.get();
        out.put(bytes);
        rev.free();
        rev = null;
        out.reset();
        result = out.getInt();
        System.out.printf("Result #1 - result: %d\n", result);
        out.flip();
        assertTrue(ok == result);
        // optimized
        out.mark();
        out.putInt((int) ((source & 0xFF) << 24 | ((source >> 8) & 0x00FF) << 16 | ((source >>> 16) & 0x000000FF) << 8 | ((source >>> 24) & 0x000000FF)));
        out.reset();
        result = out.getInt();
        System.out.printf("Result #2 - result: %d\n", result);
        out.flip();
        out.free();
        assertTrue(ok == result);
    }

    @Test
    public void testReadReverseInt() {
        final int ok = -771489792;
        int source = 1234;
        IoBuffer in = IoBuffer.allocate(4);
        in.putInt(source);
        in.flip();
        int result = IOUtils.readReverseInt(in);
        System.out.printf("Results - source: %d result: %d\n", source, result);
        assertTrue(ok == result);
        // older method
        in.flip();
        byte[] bytes = new byte[4];
        in.get(bytes);
        int value = 0;
        value += bytes[3] * 256 * 256 * 256;
        value += bytes[2] * 256 * 256;
        value += bytes[1] * 256;
        value += bytes[0];
        System.out.printf("Results #1 - result: %d\n", value);
        assertTrue(ok == value);
        // optimized 
        in.flip();
        value = in.getInt();
        value = ((value & 0xFF) << 24 | ((value >> 8) & 0x00FF) << 16 | ((value >>> 16) & 0x000000FF) << 8 | ((value >>> 24) & 0x000000FF));
        System.out.printf("Results #2 - result: %d\n", value);
        assertTrue(ok == value);
    }

}
