package org.red5.server.net.rtmp.codec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;
import org.red5.io.utils.IOUtils;
import org.red5.server.api.Red5;
import org.red5.server.net.rtmp.IRTMPHandler;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.RTMPMinaConnection;
import org.red5.server.net.rtmp.event.Notify;
import org.red5.server.net.rtmp.message.Packet;

public class RTMPProtocolDecoderTest {

    @Test
    public void testDecodeSetDataFrameOnStreamIdZero() {
        RTMPProtocolDecoder decoder = new RTMPProtocolDecoder();
        RTMPConnection connection = new RTMPMinaConnection();
        connection.getState().setState(RTMP.STATE_CONNECTED);
        connection.setHandler(new NoOpRTMPHandler());
        Red5.setConnectionLocal(connection);
        try {
            IoBuffer buffer = IoBuffer.wrap(IOUtils.hexStringToByteArray("04000000000038120000000002000d40736574446174614672616d6502000a6f6e4d65746144617461080000000100086475726174696f6e000000000000000000000009"));

            List<Object> objects = decoder.decodeBuffer(connection, buffer);

            assertNotNull("Objects should not be null", objects);
            assertFalse("Objects should not be empty", objects.isEmpty());
            Object message = ((Packet) objects.get(0)).getMessage();
            assertEquals(Notify.class, message.getClass());
            assertEquals("onMetaData", ((Notify) message).getAction());
        } finally {
            Red5.setConnectionLocal(null);
            connection.close();
        }
    }

    private static final class NoOpRTMPHandler implements IRTMPHandler {

        @Override
        public void connectionOpened(RTMPConnection conn) {
        }

        @Override
        public void messageReceived(RTMPConnection conn, Packet packet) throws Exception {
        }

        @Override
        public void messageSent(RTMPConnection conn, Packet packet) {
        }

        @Override
        public void connectionClosed(RTMPConnection conn) {
        }
    }
}
