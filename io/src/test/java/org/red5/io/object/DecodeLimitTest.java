package org.red5.io.object;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;

/**
 * Verifies AMF decode depth and declared-count limits (issue #469).
 */
public class DecodeLimitTest {

    @Test
    public void testDeeplyNestedAmf3ArraysFailWithLimit() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < 100_000; i++) {
            out.write(0x09); // array
            writeU29(out, (1 << 1) | 1); // one dense element
            out.write(0x01); // empty associative key
        }
        out.write(0x01); // null
        assertLimit(amf3(out.toByteArray()));
    }

    @Test
    public void testDeeplyNestedAmf3VectorsFailWithLimit() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x10); // vector of object
        for (int i = 0; i < 100_000; i++) {
            writeU29(out, (1 << 1) | 1); // one element
            out.write(0x00); // fixed flag
            out.write(0x01); // empty type name
            out.write(0x10); // nested vector of object
        }
        assertLimit(amf3(out.toByteArray()));
    }

    @Test
    public void testDeeplyNestedAmf0ArraysFailWithLimit() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < 100_000; i++) {
            out.write(0x0A); // strict array
            out.writeBytes(new byte[] { 0, 0, 0, 1 });
        }
        out.write(0x05); // null
        assertLimit(new org.red5.io.amf.Input(IoBuffer.wrap(out.toByteArray())));
    }

    /**
     * An AMF3 value switched to from AMF0 continues the depth of the AMF0 values around it, the switched value taking the level of its
     * marker: AMF0 strict arrays with AMF3 arrays inside decode up to the limit in total and fail beyond it, with the plain AMF0 input as
     * with the AMF3 input in its AMF0 context.
     */
    @Test
    public void testSwitchedValuesContinueTheAmf0Depth() {
        int amf0Levels = BaseInput.MAX_DEPTH / 2;
        int amf3Levels = BaseInput.MAX_DEPTH - amf0Levels;
        byte[] deepest = amf0WithSwitchedAmf3(amf0Levels, amf3Levels);
        byte[] tooDeep = amf0WithSwitchedAmf3(amf0Levels, amf3Levels + 1);
        for (boolean plain : new boolean[] { true, false }) {
            Object result = Deserializer.deserialize(amf0Input(deepest, plain), Object.class);
            int depth = 0;
            while (result instanceof List<?> list && !list.isEmpty()) {
                result = list.get(0);
                depth++;
            }
            // MAX_DEPTH arrays, the innermost empty
            assertEquals(BaseInput.MAX_DEPTH - 1, depth);
            assertLimit(amf0Input(tooDeep, plain));
        }
    }

    private static Input amf0Input(byte[] data, boolean plain) {
        IoBuffer buf = IoBuffer.wrap(data);
        return plain ? new org.red5.io.amf.Input(buf) : new org.red5.io.amf3.Input(buf);
    }

    private static byte[] amf0WithSwitchedAmf3(int amf0Levels, int amf3Levels) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < amf0Levels; i++) {
            out.write(0x0A); // strict array of one element
            out.writeBytes(new byte[] { 0, 0, 0, 1 });
        }
        out.write(0x11); // avmplus-object marker
        for (int i = 1; i < amf3Levels; i++) {
            out.write(0x09); // array of one dense element
            writeU29(out, (1 << 1) | 1);
            out.write(0x01);
        }
        out.writeBytes(new byte[] { 0x09, 0x01, 0x01 }); // empty array
        return out.toByteArray();
    }

    @Test
    public void testModerateNestingDecodes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int levels = 50;
        for (int i = 0; i < levels; i++) {
            out.write(0x09);
            writeU29(out, (1 << 1) | 1);
            out.write(0x01);
        }
        out.write(0x01);
        Object result = Deserializer.deserialize(amf3(out.toByteArray()), Object.class);
        int depth = 0;
        while (result instanceof List) {
            result = ((List<?>) result).get(0);
            depth++;
        }
        assertEquals(levels, depth);
    }

    @Test
    public void testOversizedAmf3ArrayCount() {
        assertLimit(amf3(concat(new byte[] { 0x09 }, u29((10_000_000 << 1) | 1), new byte[] { 0x01 })));
    }

    @Test
    public void testOversizedAmf3ByteArray() {
        assertLimit(amf3(concat(new byte[] { 0x0C }, u29((10_000_000 << 1) | 1))));
    }

    @Test
    public void testOversizedAmf3Vectors() {
        for (byte marker : new byte[] { 0x0D, 0x0E, 0x0F, 0x10 }) {
            assertLimit(amf3(concat(new byte[] { marker }, u29((10_000_000 << 1) | 1), new byte[] { 0x00, 0x01 })));
        }
    }

    @Test
    public void testMaximumAmf3VectorLength() {
        // the largest four byte U29 length; headers are unsigned, so this is a count of 2^28 - 1 elements, far beyond the input
        assertLimit(amf3(new byte[] { 0x0D, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x00 }));
    }

    @Test
    public void testOversizedAmf3SealedTraitCount() {
        // inline traits, sealed member count far beyond the input
        int traits = (10_000_000 << 4) | 0x03;
        assertLimit(amf3(concat(new byte[] { 0x0A }, u29(traits), new byte[] { 0x01 })));
    }

    private static void assertLimit(Input in) {
        try {
            Deserializer.deserialize(in, Object.class);
            fail("Expected DecodeLimitException");
        } catch (DecodeLimitException expected) {
            assertTrue(expected.getMessage() != null);
        }
    }

    private static org.red5.io.amf3.Input amf3(byte[] data) {
        // prefix the AMF0 "switch to AMF3" marker as RTMP does
        return new org.red5.io.amf3.Input(IoBuffer.wrap(concat(new byte[] { 0x11 }, data)));
    }

    private static byte[] u29(int value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeU29(out, value);
        return out.toByteArray();
    }

    private static void writeU29(ByteArrayOutputStream out, int value) {
        value &= 0x1FFFFFFF;
        if (value < 0x80) {
            out.write(value);
        } else if (value < 0x4000) {
            out.write(((value >> 7) & 0x7F) | 0x80);
            out.write(value & 0x7F);
        } else if (value < 0x200000) {
            out.write(((value >> 14) & 0x7F) | 0x80);
            out.write(((value >> 7) & 0x7F) | 0x80);
            out.write(value & 0x7F);
        } else {
            out.write(((value >> 22) & 0x7F) | 0x80);
            out.write(((value >> 15) & 0x7F) | 0x80);
            out.write(((value >> 8) & 0x7F) | 0x80);
            out.write(value & 0xFF);
        }
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

}
