package org.red5.client.net.rtmp.codec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.red5.server.api.Red5;
import org.red5.server.net.rtmp.RTMPMinaConnection;
import org.red5.server.net.rtmp.codec.RTMP;
import org.red5.server.net.rtmp.codec.RTMPProtocolDecoder;
import org.red5.server.net.rtmp.event.IRTMPEvent;
import org.red5.server.net.rtmp.event.Invoke;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.net.rtmp.message.Header;
import org.red5.server.service.PendingCall;

/**
 * Verifies that the client writes an AMF3 command body with one set of AMF3 reference tables, as the server reads it.
 */
public class RTMPClientProtocolEncoderTest {

    private RTMPMinaConnection conn;

    @Before
    public void setUp() {
        conn = new RTMPMinaConnection() {
            @Override
            public Encoding getEncoding() {
                return Encoding.AMF3;
            }
        };
        conn.getState().setState(RTMP.STATE_CONNECTED);
        Red5.setConnectionLocal(conn);
    }

    @After
    public void tearDown() {
        // the connection was never opened or given a handler, so there is nothing to close
        Red5.setConnectionLocal(null);
    }

    /**
     * AMF3 string references are scoped to the whole message body. With the action written by one AMF3 output and the parameters by a
     * new one, the repeated parameter "a" was written as a reference to the client's index 0 while the server's index 0 is the action.
     */
    @Test
    public void testAMF3InvokeSharesReferencesAcrossBody() {
        Invoke invoke = new Invoke(new PendingCall("echo", new Object[] { "a", "a" }));
        invoke.setTransactionId(3);
        IoBuffer out = IoBuffer.allocate(128).setAutoExpand(true);
        new RTMPClientProtocolEncoder().encodeCommand(out, invoke);
        out.flip();
        Header header = new Header();
        header.setDataType(Constants.TYPE_INVOKE);
        header.setStreamId(0);
        IRTMPEvent event = new RTMPProtocolDecoder().decodeMessage(conn, header, out);
        assertTrue(event instanceof Invoke);
        Invoke decoded = (Invoke) event;
        assertEquals("echo", decoded.getCall().getServiceMethodName());
        assertEquals(3, decoded.getTransactionId());
        assertArrayEquals(new Object[] { "a", "a" }, decoded.getCall().getArguments());
        assertFalse(out.hasRemaining());
    }

}
