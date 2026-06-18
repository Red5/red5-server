package org.red5.codec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class VideoPacketModExTypeTest {

    @Test
    public void testTimestampOffsetNano() {
        VideoPacketModExType type = VideoPacketModExType.valueOf(0);
        assertNotNull(type);
        assertEquals(VideoPacketModExType.TimestampOffsetNano, type);
        assertEquals(0, type.getValue());
    }

    @Test
    public void testUnknownValue() {
        assertNull(VideoPacketModExType.valueOf(15));
    }
}
