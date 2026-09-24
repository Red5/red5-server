package org.red5.server.net.rtmp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.red5.server.net.rtmp.message.Header;
import org.red5.server.net.rtmp.message.Packet;

/**
 * Verifies that a slow packet handler bounds the received packet backlog through read suspension and a hard limit (issue #479).
 */
public class ReceivedQueueBackpressureTest {

    private static class TrackingConnection extends StubRTMPConnection {

        final AtomicInteger suspends = new AtomicInteger(), resumes = new AtomicInteger();

        TrackingConnection() {
            super("backpressure");
        }

        @Override
        protected void suspendReceive() {
            suspends.incrementAndGet();
        }

        @Override
        protected void resumeReceive() {
            resumes.incrementAndGet();
        }
    }

    private static class BlockingHandler implements IRTMPHandler {

        final CountDownLatch release = new CountDownLatch(1);

        final AtomicInteger handled = new AtomicInteger();

        @Override
        public void connectionOpened(RTMPConnection conn) {
        }

        @Override
        public void messageReceived(RTMPConnection conn, Packet packet) throws Exception {
            release.await(10, TimeUnit.SECONDS);
            handled.incrementAndGet();
        }

        @Override
        public void messageSent(RTMPConnection conn, Packet packet) {
        }

        @Override
        public void connectionClosed(RTMPConnection conn) {
        }
    }

    @Test
    public void testSlowHandlerSuspendsAndResumesReads() throws Exception {
        TrackingConnection conn = new TrackingConnection();
        BlockingHandler handler = new BlockingHandler();
        conn.setHandler(handler);
        conn.setReceivedQueueHighWatermark(10_000);
        conn.setReceivedQueueLowWatermark(2_000);
        conn.setReceivedQueueMaxBytes(0);
        for (int i = 0; i < 50; i++) {
            conn.handleMessageReceived(packet(1_000));
        }
        // the handler is stuck on the first packet, the rest are queued
        assertTrue(conn.getReceivedQueueBytes() > 10_000);
        assertEquals("reads must be suspended once above the high watermark", 1, conn.suspends.get());
        assertEquals(0, conn.resumes.get());
        handler.release.countDown();
        for (int i = 0; i < 100 && handler.handled.get() < 50; i++) {
            Thread.sleep(20);
        }
        assertEquals(50, handler.handled.get());
        assertEquals(0, conn.getReceivedQueueBytes());
        assertEquals("reads must resume once drained below the low watermark", 1, conn.resumes.get());
        conn.close();
    }

    @Test
    public void testBacklogAboveMaximumClosesConnection() throws Exception {
        TrackingConnection conn = new TrackingConnection();
        BlockingHandler handler = new BlockingHandler();
        conn.setHandler(handler);
        conn.setReceivedQueueHighWatermark(0);
        conn.setReceivedQueueMaxBytes(20_000);
        for (int i = 0; i < 30 && !conn.isClosedByHandler(); i++) {
            conn.handleMessageReceived(packet(1_000));
        }
        assertTrue("a backlog above the maximum must close the connection", conn.isClosedByHandler());
        assertEquals(0, conn.suspends.get());
        handler.release.countDown();
    }

    @Test
    public void testFastHandlerNeverSuspends() throws Exception {
        TrackingConnection conn = new TrackingConnection();
        BlockingHandler handler = new BlockingHandler();
        handler.release.countDown();
        conn.setHandler(handler);
        for (int i = 0; i < 200; i++) {
            conn.handleMessageReceived(packet(1_000));
        }
        for (int i = 0; i < 100 && handler.handled.get() < 200; i++) {
            Thread.sleep(20);
        }
        assertEquals(200, handler.handled.get());
        assertEquals(0, conn.suspends.get());
        assertFalse(conn.isClosedByHandler());
        conn.close();
    }

    private static Packet packet(int size) {
        Header header = new Header();
        header.setSize(size);
        return new Packet(header);
    }

}
