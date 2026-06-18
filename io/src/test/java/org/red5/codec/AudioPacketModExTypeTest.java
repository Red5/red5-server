package org.red5.codec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AudioPacketModExTypeTest {

    @Test
    public void testTimestampOffsetNano() {
        AudioPacketModExType type = AudioPacketModExType.valueOf(0);
        assertNotNull(type);
        assertEquals(AudioPacketModExType.TimestampOffsetNano, type);
        assertEquals(0, type.getValue());
    }

    @Test
    public void testUnknownValue() {
        assertNull(AudioPacketModExType.valueOf(15));
    }
}
