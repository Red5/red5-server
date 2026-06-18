package org.red5.codec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModExTest {

    private static Logger log = LoggerFactory.getLogger(ModExTest.class);

    /**
     * Test that a video packet with ModEx wrapping a CodedFramesX keyframe is correctly unwrapped.
     * Wire format:
     *   byte 0: 1_001_0111 = 0x97 (enhanced + keyframe + ModEx)
     *   bytes 1-4: FourCC 'hvc1'
     *   byte 5: modExDataSize - 1 = 0x02 (so size = 3 bytes)
     *   bytes 6-8: modExData = UI24 nanosecond offset (0x000FA0 = 4000 ns)
     *   byte 9: upper nibble = TimestampOffsetNano (0), lower nibble = CodedFramesX (3) -> 0x03
     *   bytes 10+: coded video data
     */
    @Test
    public void testVideoModExTimestampNanoUnwrap() {
        log.info("testVideoModExTimestampNanoUnwrap");
        IoBuffer data = IoBuffer.allocate(32);
        // enhanced + keyframe + ModEx (0x07)
        data.put((byte) 0x97); // 1_001_0111
        // FourCC for HEVC
        data.put((byte) 'h');
        data.put((byte) 'v');
        data.put((byte) 'c');
        data.put((byte) '1');
        // ModEx: size-1 = 2 (means 3 bytes of modExData)
        data.put((byte) 0x02);
        // modExData: UI24 = 4000 nanoseconds
        data.put((byte) 0x00);
        data.put((byte) 0x0F);
        data.put((byte) 0xA0);
        // Next byte: upper nibble = TimestampOffsetNano (0), lower nibble = CodedFramesX (3)
        data.put((byte) 0x03);
        // Fake coded data
        data.put(new byte[] { 0x01, 0x02, 0x03, 0x04 });
        data.flip();

        HEVCVideo video = new HEVCVideo();
        // addData should unwrap ModEx and handle the inner CodedFramesX
        assertTrue(video.addData(data, 5000));
        // After unwrap, the effective packet type should be CodedFramesX
        assertEquals(VideoPacketType.CodedFramesX, video.getPacketType());
    }

    /**
     * Test ModEx with large data size (256 triggers UI16 read).
     */
    @Test
    public void testVideoModExLargeDataSize() {
        log.info("testVideoModExLargeDataSize");
        IoBuffer data = IoBuffer.allocate(300);
        // enhanced + keyframe + ModEx
        data.put((byte) 0x97);
        // FourCC for HEVC
        data.put((byte) 'h');
        data.put((byte) 'v');
        data.put((byte) 'c');
        data.put((byte) '1');
        // ModEx: UI8 = 0xFF -> size = 256, triggers UI16 read
        data.put((byte) 0xFF);
        // UI16 = 0x0002 -> actual size = 3
        data.put((byte) 0x00);
        data.put((byte) 0x02);
        // modExData: 3 bytes (UI24 nano offset)
        data.put((byte) 0x00);
        data.put((byte) 0x00);
        data.put((byte) 0x64); // 100 ns
        // Next byte: TimestampOffsetNano (0) + SequenceStart (0) = 0x00
        data.put((byte) 0x00);
        // Fake config data
        data.put(new byte[] { 0x01, 0x02, 0x03, 0x04 });
        data.flip();

        HEVCVideo video = new HEVCVideo();
        assertTrue(video.addData(data, 0));
        assertEquals(VideoPacketType.SequenceStart, video.getPacketType());
    }
}
