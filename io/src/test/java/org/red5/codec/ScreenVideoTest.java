package org.red5.codec;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;

/**
 * ScreenVideo must not retain large codec allocations for frames whose declared
 * dimensions cannot be backed by the packet (issue #455).
 */
public class ScreenVideoTest {

    private static final byte KEYFRAME_SCREEN_VIDEO = 0x13;

    private static Object field(Object o, String name) throws Exception {
        Field f = ScreenVideo.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    @Test
    public void testMaxDimensionHeaderWithTinyPayloadIsRejectedWithoutAllocation() throws Exception {
        // 12-bit width/height = 4095, block size 16x16 -> 65536 blocks -> ~51 MiB retained
        IoBuffer data = IoBuffer.allocate(16);
        data.put(KEYFRAME_SCREEN_VIDEO);
        data.putShort((short) 0x0fff);
        data.putShort((short) 0x0fff);
        data.putShort((short) 0); // a single "unchanged" block size
        data.flip();
        ScreenVideo video = new ScreenVideo();
        assertTrue(video.canHandleData(data));
        assertFalse(video.addData(data));
        assertNull("codec block storage must not be allocated for an impossible frame", field(video, "blockData"));
    }

    @Test
    public void testSmallFrameIsAccepted() throws Exception {
        // 16x16 frame, 16x16 blocks -> exactly one block
        IoBuffer data = IoBuffer.allocate(64);
        data.put(KEYFRAME_SCREEN_VIDEO);
        data.putShort((short) 0x0010);
        data.putShort((short) 0x0010);
        data.putShort((short) 4);
        data.put(new byte[] { 1, 2, 3, 4 });
        data.flip();
        ScreenVideo video = new ScreenVideo();
        assertTrue(video.canHandleData(data));
        assertTrue(video.addData(data));
    }
}
