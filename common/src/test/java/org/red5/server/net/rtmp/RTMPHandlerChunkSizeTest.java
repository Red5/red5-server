package org.red5.server.net.rtmp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.red5.server.net.rtmp.codec.RTMP;
import org.red5.server.net.rtmp.event.ChunkSize;

/**
 * Abusively small inbound chunk sizes must be refused with a controlled disconnect (issue #475).
 */
public class RTMPHandlerChunkSizeTest {

    @Test
    public void testOneByteChunkSizeIsRefusedAndConnectionClosed() {
        RTMPHandler handler = new RTMPHandler();
        StubRTMPConnection conn = new StubRTMPConnection("one");
        handler.onChunkSize(conn, null, null, new ChunkSize(1));
        assertEquals(RTMP.DEFAULT_CHUNK_SIZE, conn.getState().getReadChunkSize());
        assertTrue("connection must be closed", conn.isClosedByHandler());
    }

    @Test
    public void testNormalChunkSizeIsApplied() {
        RTMPHandler handler = new RTMPHandler();
        StubRTMPConnection conn = new StubRTMPConnection("normal");
        handler.onChunkSize(conn, null, null, new ChunkSize(4096));
        assertEquals(4096, conn.getState().getReadChunkSize());
        assertFalse(conn.isClosedByHandler());
    }

    @Test
    public void testMinimumIsConfigurableForCompatibility() {
        RTMPHandler handler = new RTMPHandler();
        handler.setMinReadChunkSize(1);
        StubRTMPConnection conn = new StubRTMPConnection("compat");
        handler.onChunkSize(conn, null, null, new ChunkSize(1));
        assertEquals(1, conn.getState().getReadChunkSize());
        assertFalse(conn.isClosedByHandler());
    }

    @Test
    public void testBoundaryValueIsAccepted() {
        RTMPHandler handler = new RTMPHandler();
        StubRTMPConnection conn = new StubRTMPConnection("boundary");
        handler.onChunkSize(conn, null, null, new ChunkSize(handler.getMinReadChunkSize()));
        assertEquals(handler.getMinReadChunkSize(), conn.getState().getReadChunkSize());
        assertFalse(conn.isClosedByHandler());
    }
}
