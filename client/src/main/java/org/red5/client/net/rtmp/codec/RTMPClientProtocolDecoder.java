package org.red5.client.net.rtmp.codec;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.server.net.protocol.RTMPDecodeState;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.codec.RTMPProtocolDecoder;

/**
 * Class to specifically handle the client side of the handshake routine.
 */
public class RTMPClientProtocolDecoder extends RTMPProtocolDecoder {

    {
        //log = LoggerFactory.getLogger(RTMPClientProtocolDecoder.class);
    }

    /**
     * Decode first server response S1.
     *
     * @param conn connection
     * @param state decode state
     * @param in incoming data
     * @return server handshake bytes for S1 or null
     */
    public IoBuffer decodeHandshakeS1(RTMPConnection conn, RTMPDecodeState state, IoBuffer in) {
        throw new UnsupportedOperationException("Not used, use RTMPEIoFilter filter");
    }

    /**
     * Decode second server response S2.
     *
     * @param conn connection
     * @param state decode state
     * @param in incoming data
     * @return null
     */
    public IoBuffer decodeHandshakeS2(RTMPConnection conn, RTMPDecodeState state, IoBuffer in) {
        throw new UnsupportedOperationException("Not used, use RTMPEIoFilter filter");
    }

}
