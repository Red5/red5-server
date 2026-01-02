package org.red5.server.net.rtmp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;

/**
 * Tests for RTMPUtils utility methods.
 */
public class TestRTMPUtils {

    @Test
    public void testWriteMediumInt() {
        IoBuffer buf = IoBuffer.allocate(6);

        // Write zero
        RTMPUtils.writeMediumInt(buf, 0);
        // Write max medium int
        RTMPUtils.writeMediumInt(buf, 0xFFFFFF);

        buf.flip();

        // Read back and verify
        assertEquals(0, RTMPUtils.readUnsignedMediumInt(buf));
        assertEquals(0xFFFFFF, RTMPUtils.readUnsignedMediumInt(buf));
    }

    @Test
    public void testReadUnsignedMediumInt() {
        IoBuffer buf = IoBuffer.allocate(9);

        // Test various values
        RTMPUtils.writeMediumInt(buf, 0);
        RTMPUtils.writeMediumInt(buf, 1);
        RTMPUtils.writeMediumInt(buf, 0x123456);

        buf.flip();

        assertEquals(0, RTMPUtils.readUnsignedMediumInt(buf));
        assertEquals(1, RTMPUtils.readUnsignedMediumInt(buf));
        assertEquals(0x123456, RTMPUtils.readUnsignedMediumInt(buf));
    }

    @Test
    public void testReadMediumInt() {
        IoBuffer buf = IoBuffer.allocate(6);

        // Write medium ints - readMediumInt handles sign extension
        RTMPUtils.writeMediumInt(buf, 0x7FFFFF); // Max positive
        RTMPUtils.writeMediumInt(buf, 0x800000); // Would be negative in signed

        buf.flip();

        // Verify reading
        assertEquals(0x7FFFFF, RTMPUtils.readMediumInt(buf));
        // 0x800000 sign-extended as 24-bit becomes negative
        int signExtended = RTMPUtils.readMediumInt(buf);
        assertTrue("High bit value should be sign extended", signExtended < 0);
    }

    @Test
    public void testCompareTimestamps() {
        // Equal timestamps
        assertEquals(0, RTMPUtils.compareTimestamps(1000, 1000));

        // a < b
        assertEquals(-1, RTMPUtils.compareTimestamps(100, 200));

        // a > b
        assertEquals(1, RTMPUtils.compareTimestamps(200, 100));

        // Zero comparison
        assertEquals(0, RTMPUtils.compareTimestamps(0, 0));

        // Large values
        assertEquals(1, RTMPUtils.compareTimestamps(Integer.MAX_VALUE, 0));
    }

    @Test
    public void testDiffTimestamps() {
        // Same timestamps
        assertEquals(0L, RTMPUtils.diffTimestamps(1000, 1000));

        // Forward difference
        assertEquals(100L, RTMPUtils.diffTimestamps(200, 100));

        // Backward difference
        assertEquals(-100L, RTMPUtils.diffTimestamps(100, 200));

        // Large values - treated as unsigned
        long diff = RTMPUtils.diffTimestamps(-1, 0); // -1 as unsigned is 0xFFFFFFFF
        assertEquals(0xFFFFFFFFL, diff);

        // Zero difference
        assertEquals(0L, RTMPUtils.diffTimestamps(0, 0));
    }

    @Test
    public void testReverseIntReadWrite() {
        IoBuffer buf = IoBuffer.allocate(12);

        // Write various values (little-endian / reverse order)
        RTMPUtils.writeReverseInt(buf, 0);
        RTMPUtils.writeReverseInt(buf, 1);
        RTMPUtils.writeReverseInt(buf, 0x12345678);

        buf.flip();

        // Read back and verify
        assertEquals(0, RTMPUtils.readReverseInt(buf));
        assertEquals(1, RTMPUtils.readReverseInt(buf));
        assertEquals(0x12345678, RTMPUtils.readReverseInt(buf));
    }

    @Test
    public void testEncodeHeaderByte() {
        // Channel ID < 64 (1 byte encoding)
        IoBuffer buf1 = IoBuffer.allocate(4);
        RTMPUtils.encodeHeaderByte(buf1, (byte) 0, 4);
        buf1.flip();
        assertEquals(1, buf1.remaining());
        assertEquals(0x04, buf1.get() & 0xFF); // Type 0, channel 4

        // Different header types
        IoBuffer buf2 = IoBuffer.allocate(4);
        RTMPUtils.encodeHeaderByte(buf2, (byte) 1, 4); // Type 1
        buf2.flip();
        assertEquals(0x44, buf2.get() & 0xFF); // Type 1 (01 << 6) + channel 4

        IoBuffer buf3 = IoBuffer.allocate(4);
        RTMPUtils.encodeHeaderByte(buf3, (byte) 2, 4); // Type 2
        buf3.flip();
        assertEquals(0x84, buf3.get() & 0xFF); // Type 2 (10 << 6) + channel 4

        IoBuffer buf4 = IoBuffer.allocate(4);
        RTMPUtils.encodeHeaderByte(buf4, (byte) 3, 4); // Type 3
        buf4.flip();
        assertEquals(0xC4, buf4.get() & 0xFF); // Type 3 (11 << 6) + channel 4
    }

    @Test
    public void testDecodeHeaderSize() {
        // Test header size decoding from first byte
        // Header size is encoded in top 2 bits: 0=full(12), 1=same_source(8), 2=timer_change(4), 3=continue(1)

        // Type 0 (bits 00) = HEADER_NEW = 12 bytes
        assertEquals(12, RTMPUtils.getHeaderLength(RTMPUtils.decodeHeaderSize(0x04, 1)));

        // Type 1 (bits 01) = HEADER_SAME_SOURCE = 8 bytes
        assertEquals(8, RTMPUtils.getHeaderLength(RTMPUtils.decodeHeaderSize(0x44, 1)));

        // Type 2 (bits 10) = HEADER_TIMER_CHANGE = 4 bytes
        assertEquals(4, RTMPUtils.getHeaderLength(RTMPUtils.decodeHeaderSize(0x84, 1)));

        // Type 3 (bits 11) = HEADER_CONTINUE = 1 byte
        assertEquals(1, RTMPUtils.getHeaderLength(RTMPUtils.decodeHeaderSize(0xC4, 1)));
    }

    @Test
    public void testMediumIntBoundaryValues() {
        IoBuffer buf = IoBuffer.allocate(12);

        // Boundary values
        RTMPUtils.writeMediumInt(buf, 1);
        RTMPUtils.writeMediumInt(buf, 0xFFFFFE); // One below max
        RTMPUtils.writeMediumInt(buf, 0xFFFFFF); // Max (MEDIUM_INT_MAX)
        RTMPUtils.writeMediumInt(buf, 0x010000); // Powers of 256

        buf.flip();

        assertEquals(1, RTMPUtils.readUnsignedMediumInt(buf));
        assertEquals(0xFFFFFE, RTMPUtils.readUnsignedMediumInt(buf));
        assertEquals(0xFFFFFF, RTMPUtils.readUnsignedMediumInt(buf));
        assertEquals(0x010000, RTMPUtils.readUnsignedMediumInt(buf));
    }
}
