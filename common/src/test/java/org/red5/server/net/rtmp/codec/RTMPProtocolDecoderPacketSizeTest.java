package org.red5.server.net.rtmp.codec;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.red5.server.net.protocol.ProtocolException;
import org.red5.server.net.rtmp.StubRTMPConnection;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.net.rtmp.message.Packet;

/**
 * The configured maximum packet size must be enforced before a Packet buffer is allocated (issue #456).
 */
public class RTMPProtocolDecoderPacketSizeTest implements Constants {

    private static final int LIMIT = 1024;

    private static final int CHANNEL = 3;

    private RTMPProtocolDecoder decoder;

    @Before
    public void setUp() {
        decoder = new RTMPProtocolDecoder();
        RTMPProtocolDecoder.setMaxPacketSize(LIMIT);
    }

    @After
    public void tearDown() {
        RTMPProtocolDecoder.setMaxPacketSize(3145728);
    }

    /** Type 0 chunk header on channel 3 declaring the given message size, followed by a few payload bytes. */
    private static IoBuffer type0(int size) {
        IoBuffer in = IoBuffer.allocate(64);
        in.put((byte) CHANNEL); // fmt 0, csid 3
        in.put(new byte[] { 0, 0, 0 }); // timestamp
        in.put((byte) ((size >> 16) & 0xff));
        in.put((byte) ((size >> 8) & 0xff));
        in.put((byte) (size & 0xff));
        in.put(TYPE_INVOKE);
        in.putInt(0); // stream id
        in.put(new byte[16]); // partial body
        in.flip();
        return in;
    }

    @Test
    public void testDeclaredSizeOverLimitIsRejectedAndChannelReleased() {
        StubRTMPConnection conn = new StubRTMPConnection("over");
        try {
            decoder.decodePacket(conn, conn.getDecoderState(), type0(LIMIT + 1));
            fail("Expected ProtocolException");
        } catch (ProtocolException expected) {
        }
        assertNull("partial packet must not be retained", conn.getState().getLastReadPacket(CHANNEL));
    }

    @Test
    public void testMaximumDeclaredSizeIsRejected() {
        StubRTMPConnection conn = new StubRTMPConnection("max");
        try {
            decoder.decodePacket(conn, conn.getDecoderState(), type0(0xffffff));
            fail("Expected ProtocolException");
        } catch (ProtocolException expected) {
        }
        assertNull(conn.getState().getLastReadPacket(CHANNEL));
    }

    @Test
    public void testDeclaredSizeAtLimitIsAccepted() {
        StubRTMPConnection conn = new StubRTMPConnection("at");
        Packet packet = decoder.decodePacket(conn, conn.getDecoderState(), type0(LIMIT));
        // body is incomplete so no packet is returned yet, but the partial packet is retained for the channel
        assertNull(packet);
        assertNotNull(conn.getState().getLastReadPacket(CHANNEL));
    }
}
