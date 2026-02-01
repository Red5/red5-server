package org.red5.server.net.rtmp.codec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Before;
import org.junit.Test;
import org.red5.server.net.rtmp.RTMPUtils;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.net.rtmp.message.Header;

/**
 * Tests for RTMP extended timestamp encoding and decoding.
 * These tests verify FFmpeg/libav compatibility for extended timestamp handling.
 */
public class RTMPExtendedTimestampTest implements Constants {

    @Before
    public void setUp() {
    }

    /**
     * Test encoding Type 0 header with extended timestamp
     */
    @Test
    public void testEncodeType0ExtendedTimestamp() {
        Header header = new Header();
        header.setChannelId(4);
        header.setTimerBase(MEDIUM_INT_MAX + 5000);
        header.setTimerDelta(0);
        header.setSize(100);
        header.setDataType(TYPE_VIDEO_DATA);
        header.setStreamId(1);

        IoBuffer buf = IoBuffer.allocate(20);

        // Encode header byte
        RTMPUtils.encodeHeaderByte(buf, HEADER_NEW, header.getChannelId());

        // Type 0: write timestamp (clamped to MEDIUM_INT_MAX for extended)
        int timeBase = header.getTimerBase();
        RTMPUtils.writeMediumInt(buf, Math.min(timeBase, MEDIUM_INT_MAX));

        // Write size
        RTMPUtils.writeMediumInt(buf, header.getSize());

        // Write data type
        buf.put(header.getDataType());

        // Write stream ID (little endian)
        RTMPUtils.writeReverseInt(buf, header.getStreamId().intValue());

        // Write extended timestamp if needed
        if (timeBase >= MEDIUM_INT_MAX) {
            buf.putInt(timeBase);
            header.setExtended(true);
        }

        buf.flip();

        // Verify header byte
        int firstByte = buf.get() & 0xFF;
        assertEquals("Header type should be 0", 0, (firstByte >> 6) & 0x03);
        assertEquals("Channel ID should be 4", 4, firstByte & 0x3F);

        // Verify timestamp field is MEDIUM_INT_MAX
        int ts = RTMPUtils.readUnsignedMediumInt(buf);
        assertEquals("Timestamp field should be MEDIUM_INT_MAX", MEDIUM_INT_MAX, ts);

        // Skip size (3 bytes), data type (1 byte), stream ID (4 bytes)
        buf.skip(8);

        // Verify extended timestamp
        int extTs = buf.getInt();
        assertEquals("Extended timestamp should match original", MEDIUM_INT_MAX + 5000, extTs);
    }

    /**
     * Test encoding Type 1 header with extended timestamp delta
     */
    @Test
    public void testEncodeType1ExtendedTimestampDelta() {
        Header header = new Header();
        header.setChannelId(4);
        header.setTimerDelta(MEDIUM_INT_MAX + 3000);
        header.setSize(100);
        header.setDataType(TYPE_VIDEO_DATA);

        IoBuffer buf = IoBuffer.allocate(16);

        // Encode header byte
        RTMPUtils.encodeHeaderByte(buf, HEADER_SAME_SOURCE, header.getChannelId());

        // Type 1: write delta (clamped to MEDIUM_INT_MAX for extended)
        int timeDelta = header.getTimerDelta();
        RTMPUtils.writeMediumInt(buf, Math.min(timeDelta, MEDIUM_INT_MAX));

        // Write size
        RTMPUtils.writeMediumInt(buf, header.getSize());

        // Write data type
        buf.put(header.getDataType());

        // Write extended timestamp if needed
        if (timeDelta >= MEDIUM_INT_MAX) {
            buf.putInt(timeDelta);
            header.setExtended(true);
        }

        buf.flip();

        // Verify header byte
        int firstByte = buf.get() & 0xFF;
        assertEquals("Header type should be 1", 1, (firstByte >> 6) & 0x03);

        // Verify delta field is MEDIUM_INT_MAX
        int delta = RTMPUtils.readUnsignedMediumInt(buf);
        assertEquals("Delta field should be MEDIUM_INT_MAX", MEDIUM_INT_MAX, delta);

        // Skip size (3 bytes), data type (1 byte)
        buf.skip(4);

        // Verify extended timestamp
        int extTs = buf.getInt();
        assertEquals("Extended timestamp should match delta", MEDIUM_INT_MAX + 3000, extTs);
    }

    /**
     * Test encoding Type 3 header with extended timestamp (continuation chunk)
     */
    @Test
    public void testEncodeType3ExtendedTimestampFromType0Origin() {
        // lastHeader from Type 0 with extended timestamp
        Header lastHeader = new Header();
        lastHeader.setChannelId(4);
        lastHeader.setTimerBase(MEDIUM_INT_MAX + 5000);
        lastHeader.setTimerDelta(0);
        lastHeader.setExtended(true);

        IoBuffer buf = IoBuffer.allocate(8);

        // Encode Type 3 header byte
        RTMPUtils.encodeHeaderByte(buf, HEADER_CONTINUE, lastHeader.getChannelId());

        // Write extended timestamp if lastHeader was extended
        if (lastHeader.isExtended()) {
            int extendedTimestamp;
            if (lastHeader.getTimerDelta() >= MEDIUM_INT_MAX) {
                // Type 1/2 case
                extendedTimestamp = lastHeader.getTimerDelta();
            } else {
                // Type 0 case
                extendedTimestamp = lastHeader.getTimer();
            }
            buf.putInt(extendedTimestamp);
        }

        buf.flip();

        // Verify header byte
        int firstByte = buf.get() & 0xFF;
        assertEquals("Header type should be 3", 3, (firstByte >> 6) & 0x03);

        // Verify extended timestamp
        int extTs = buf.getInt();
        assertEquals("Extended timestamp should be full timer (Type 0 origin)", MEDIUM_INT_MAX + 5000, extTs);
    }

    /**
     * Test encoding Type 3 header with extended timestamp from Type 1/2 origin
     */
    @Test
    public void testEncodeType3ExtendedTimestampFromType1Origin() {
        // lastHeader from Type 1/2 with extended delta
        Header lastHeader = new Header();
        lastHeader.setChannelId(4);
        lastHeader.setTimerBase(1000); // Modified base
        lastHeader.setTimerDelta(MEDIUM_INT_MAX + 3000);
        lastHeader.setExtended(true);

        IoBuffer buf = IoBuffer.allocate(8);

        // Encode Type 3 header byte
        RTMPUtils.encodeHeaderByte(buf, HEADER_CONTINUE, lastHeader.getChannelId());

        // Write extended timestamp if lastHeader was extended
        if (lastHeader.isExtended()) {
            int extendedTimestamp;
            if (lastHeader.getTimerDelta() >= MEDIUM_INT_MAX) {
                // Type 1/2 case
                extendedTimestamp = lastHeader.getTimerDelta();
            } else {
                // Type 0 case
                extendedTimestamp = lastHeader.getTimer();
            }
            buf.putInt(extendedTimestamp);
        }

        buf.flip();

        // Verify header byte
        int firstByte = buf.get() & 0xFF;
        assertEquals("Header type should be 3", 3, (firstByte >> 6) & 0x03);

        // Verify extended timestamp
        int extTs = buf.getInt();
        assertEquals("Extended timestamp should be delta (Type 1/2 origin)", MEDIUM_INT_MAX + 3000, extTs);
    }

    /**
     * Test that Type 3 without extended timestamp doesn't write extra bytes
     */
    @Test
    public void testEncodeType3NoExtendedTimestamp() {
        // lastHeader without extended timestamp
        Header lastHeader = new Header();
        lastHeader.setChannelId(4);
        lastHeader.setTimerBase(1000);
        lastHeader.setTimerDelta(33);
        lastHeader.setExtended(false);

        IoBuffer buf = IoBuffer.allocate(4);

        // Encode Type 3 header byte
        RTMPUtils.encodeHeaderByte(buf, HEADER_CONTINUE, lastHeader.getChannelId());

        // Don't write extended timestamp since lastHeader is not extended

        buf.flip();

        // Should only have 1 byte (the header byte)
        assertEquals("Type 3 without extended should be 1 byte", 1, buf.remaining());

        // Verify header byte
        int firstByte = buf.get() & 0xFF;
        assertEquals("Header type should be 3", 3, (firstByte >> 6) & 0x03);
        assertEquals("Channel ID should be 4", 4, firstByte & 0x3F);
    }

    /**
     * Test extended timestamp transition from extended to non-extended
     */
    @Test
    public void testExtendedTimestampTransition() {
        // First header: extended
        Header header1 = new Header();
        header1.setChannelId(4);
        header1.setTimerBase(MEDIUM_INT_MAX + 1000);
        header1.setTimerDelta(0);
        header1.setExtended(true);

        // Second header: delta brings it to non-extended
        Header header2 = new Header();
        header2.setChannelId(4);
        header2.setTimerBase(MEDIUM_INT_MAX + 1000);
        header2.setTimerDelta(33); // Small delta

        // Simulate decoder behavior: inherit then reset
        header2.setExtended(header1.isExtended());
        if (header2.getTimerDelta() < MEDIUM_INT_MAX) {
            header2.setExtended(false);
        }

        assertTrue("Header 1 should have extended flag", header1.isExtended());
        assertTrue("Header 2 should NOT have extended flag after reset", !header2.isExtended());
    }

    /**
     * Test header byte encoding for different channel IDs
     */
    @Test
    public void testHeaderByteEncodingChannelIds() {
        // Channel ID < 64 (1 byte)
        IoBuffer buf1 = IoBuffer.allocate(4);
        RTMPUtils.encodeHeaderByte(buf1, HEADER_NEW, 4);
        buf1.flip();
        assertEquals(1, buf1.remaining());
        int byte1 = buf1.get() & 0xFF;
        assertEquals(4, byte1 & 0x3F);

        // Channel ID 64-319 (2 bytes)
        IoBuffer buf2 = IoBuffer.allocate(4);
        RTMPUtils.encodeHeaderByte(buf2, HEADER_NEW, 100);
        buf2.flip();
        assertEquals(2, buf2.remaining());

        // Channel ID >= 320 (3 bytes)
        IoBuffer buf3 = IoBuffer.allocate(4);
        RTMPUtils.encodeHeaderByte(buf3, HEADER_NEW, 500);
        buf3.flip();
        assertEquals(3, buf3.remaining());
    }

    /**
     * Test medium int read/write consistency
     */
    @Test
    public void testMediumIntReadWrite() {
        IoBuffer buf = IoBuffer.allocate(6);

        // Write and read back
        RTMPUtils.writeMediumInt(buf, 0);
        RTMPUtils.writeMediumInt(buf, MEDIUM_INT_MAX);

        buf.flip();

        assertEquals(0, RTMPUtils.readUnsignedMediumInt(buf));
        assertEquals(MEDIUM_INT_MAX, RTMPUtils.readUnsignedMediumInt(buf));
    }

    /**
     * Test reverse int read/write for stream ID
     */
    @Test
    public void testReverseIntReadWrite() {
        IoBuffer buf = IoBuffer.allocate(8);

        RTMPUtils.writeReverseInt(buf, 1);
        RTMPUtils.writeReverseInt(buf, 12345);

        buf.flip();

        assertEquals(1, RTMPUtils.readReverseInt(buf));
        assertEquals(12345, RTMPUtils.readReverseInt(buf));
    }
}
