package org.red5.io.mp4.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.red5.io.ITag;
import org.red5.io.IoConstants;
import org.red5.io.flv.IKeyFrameDataAnalyzer.KeyFrameMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MP4ReaderTest {

    private static Logger log = LoggerFactory.getLogger(MP4ReaderTest.class);

    @Test
    public void testReadH264Aac() throws Exception {
        // avc1 h264 video / aac audio
        File file = new File("target/test-classes/fixtures/mov_h264.mp4");
        assertTrue("Missing fixture: " + file, file.isFile());
        MP4Reader reader = new MP4Reader(file);
        try {
            KeyFrameMeta meta = reader.analyzeKeyFrames();
            log.debug("Meta: {}", meta);
            assertNotNull(meta);
            assertFalse(meta.audioOnly);
            assertTrue("no keyframe positions", meta.positions.length > 0);
            assertEquals(meta.positions.length, meta.timestamps.length);
            assertTrue("duration should be positive", meta.duration > 0);
            Set<Byte> types = new HashSet<>();
            int lastTimestamp = -1;
            ITag first = reader.readTag();
            assertNotNull(first);
            assertEquals("first tag should be metadata", IoConstants.TYPE_METADATA, first.getDataType());
            types.add(first.getDataType());
            for (int t = 1; t < 32; t++) {
                assertTrue("ran out of tags at " + t, reader.hasMoreTags());
                ITag tag = reader.readTag();
                log.debug("Tag: {}", tag);
                assertNotNull("tag " + t + " is null", tag);
                assertNotNull("tag " + t + " has no body", tag.getBody());
                assertTrue("timestamps must not go backwards at " + t, tag.getTimestamp() >= lastTimestamp);
                lastTimestamp = tag.getTimestamp();
                types.add(tag.getDataType());
            }
            assertTrue("expected video tags", types.contains(IoConstants.TYPE_VIDEO));
            assertTrue("expected audio tags", types.contains(IoConstants.TYPE_AUDIO));
        } finally {
            reader.close();
        }
    }

    @Test
    public void testBigEndianConversions() {
        byte[] width = { 0x00, 0x40, (byte) 0x94, 0x00, 0x00, 0x00, 0x00, 0x00 };
        assertEquals(0x0040940000000000L, bytesToLong(width));
        byte[] arr = { 0, 0, 0x10, 0 };
        assertEquals(0x1000, bytesToInt(arr));
        assertEquals((short) 0x0102, bytesToShort(new byte[] { 0x01, 0x02 }));
        assertEquals((byte) 0x7f, bytesToByte(new byte[] { 0x7f }));
    }

    public static long bytesToLong(byte[] data) {
        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.put(data);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.flip();
        return buf.getLong();
    }

    public static int bytesToInt(byte[] data) {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.put(data);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.flip();
        return buf.getInt();
    }

    public static short bytesToShort(byte[] data) {
        ByteBuffer buf = ByteBuffer.allocate(2);
        buf.put(data);
        buf.flip();
        return buf.getShort();
    }

    public static byte bytesToByte(byte[] data) {
        ByteBuffer buf = ByteBuffer.allocate(1);
        buf.put(data);
        buf.flip();
        return buf.get();
    }

}
