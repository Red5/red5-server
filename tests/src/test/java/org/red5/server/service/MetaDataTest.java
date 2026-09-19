package org.red5.server.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.red5.io.flv.meta.MetaData;

public class MetaDataTest {

    private MetaData<?, ?> data;

    @Before
    public void setUp() {
        data = new MetaData<Object, Object>();
        data.setCanSeekToEnd(true);
        data.setDuration(7.347);
        data.setFrameRate(15);
        data.setHeight(333);
        data.setVideoCodecId(4);
        data.setVideoDataRate(400);
        data.setWidth(300);
    }

    @Test
    public void testCanSeekToEnd() {
        assertTrue(data.getCanSeekToEnd());
    }

    @Test
    public void testDuration() {
        assertEquals(7.347, data.getDuration(), 0);
    }

    @Test
    public void testFrameRate() {
        assertEquals(15.0, data.getFrameRate(), 0);
    }

    @Test
    public void testHeight() {
        assertEquals(333, data.getHeight());
    }

    @Test
    public void testVideoCodecId() {
        assertEquals(4, data.getVideoCodecId());
    }

    @Test
    public void testVideoDataRate() {
        assertEquals(400, data.getVideoDataRate());
    }

    @Test
    public void testWidth() {
        assertEquals(300, data.getWidth());
    }

}
