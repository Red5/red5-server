/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2015 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.client.net.rtmpt;

import java.util.Map;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.client.net.rtmp.BaseRTMPClientHandler;
import org.red5.client.net.rtmp.OutboundHandshake;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.codec.RTMP;
import org.red5.server.net.rtmp.codec.RTMPProtocolDecoder;
import org.red5.server.net.rtmp.codec.RTMPProtocolEncoder;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.net.rtmp.message.Packet;
import org.red5.server.net.rtmpt.codec.RTMPTCodecFactory;

/**
 * RTMPT client object
 *
 * @author Anton Lebedevich
 */
public class RTMPTClient extends BaseRTMPClientHandler {

    // guarded by this
    protected RTMPTClientConnector connector;

    protected RTMPTCodecFactory codecFactory;

    public RTMPTClient() {
        protocol = "rtmpt";
        codecFactory = new RTMPTCodecFactory();
        codecFactory.init();
    }

    @Override
    public Map<String, Object> makeDefaultConnectionParams(String server, int port, String application) {
        Map<String, Object> params = super.makeDefaultConnectionParams(server, port, application);
        if (!params.containsKey("tcUrl")) {
            params.put("tcUrl", protocol + "://" + server + ':' + port + '/' + application);
        }
        return params;
    }

    @Override
    protected synchronized void startConnector(String server, int port) {
        connector = new RTMPTClientConnector(server, port, this);
        log.debug("Created connector {}", connector);
        connector.start();
    }

    /**
     * Received message object router.
     *
     * @param message
     *            an IoBuffer or Packet
     */
    public void messageReceived(Object message) {
        if (message instanceof Packet) {
            try {
                messageReceived(conn, (Packet) message);
            } catch (Exception e) {
                log.warn("Exception on packet receive", e);
            }
        } else {
            // raw buffer handling
            IoBuffer in = (IoBuffer) message;
            // filter based on current connection state
            RTMP rtmp = conn.getState();
            final byte connectionState = conn.getStateCode();
            log.trace("connectionState: {}", RTMP.states[connectionState]);
            // get the handshake
            OutboundHandshake handshake = (OutboundHandshake) conn.getAttribute(RTMPConnection.RTMP_HANDSHAKE);
            switch (connectionState) {
                case RTMP.STATE_CONNECT:
                    log.debug("Handshake - client phase 1 - size: {}", in.remaining());
                    in.get(); // 0x01
                    byte handshakeType = in.get(); // usually 0x03 (rtmp)
                    log.debug("Handshake - byte type: {}", handshakeType);
                    // copy out 1536 bytes
                    byte[] s1 = new byte[Constants.HANDSHAKE_SIZE];
                    in.get(s1);
                    // decode s1
                    IoBuffer out = handshake.decodeServerResponse1(IoBuffer.wrap(s1));
                    if (out != null) {
                        // set state to indicate we're waiting for S2
                        rtmp.setState(RTMP.STATE_HANDSHAKE);
                        conn.writeRaw(out);
                        // if we got S0S1+S2 continue processing
                        if (in.remaining() >= Constants.HANDSHAKE_SIZE) {
                            log.debug("Handshake - client phase 2 - size: {}", in.remaining());
                            byte[] s2 = new byte[Constants.HANDSHAKE_SIZE];
                            in.get(s2);
                            if (handshake.decodeServerResponse2(s2)) {
                                //                                conn.removeAttribute(RTMPConnection.RTMP_HANDSHAKE);
                                //                                conn.setStateCode(RTMP.STATE_CONNECTED);
                                //                                connectionOpened(conn);
                            } else {
                                log.warn("Handshake failed on S2 processing");
                                //conn.close();
                            }
                            // open regardless of server type
                            conn.removeAttribute(RTMPConnection.RTMP_HANDSHAKE);
                            conn.setStateCode(RTMP.STATE_CONNECTED);
                            connectionOpened(conn);
                        }
                    } else {
                        log.warn("Handshake failed on S0S1 processing");
                        conn.close();
                    }
                    break;
                case RTMP.STATE_HANDSHAKE:
                    log.debug("Handshake - client phase 2 - size: {}", in.remaining());
                    byte[] s2 = new byte[Constants.HANDSHAKE_SIZE];
                    in.get(s2);
                    if (handshake.decodeServerResponse2(s2)) {
                        //                        conn.removeAttribute(RTMPConnection.RTMP_HANDSHAKE);
                        //                        conn.setStateCode(RTMP.STATE_CONNECTED);
                        //                        connectionOpened(conn);
                    } else {
                        log.warn("Handshake failed on S2 processing");
                        //conn.close();
                    }
                    // open regardless of server type
                    conn.removeAttribute(RTMPConnection.RTMP_HANDSHAKE);
                    conn.setStateCode(RTMP.STATE_CONNECTED);
                    connectionOpened(conn);
                    break;
                default:
                    throw new IllegalStateException("Invalid RTMP state: " + connectionState);
            }
        }
    }

    @Override
    public synchronized void disconnect() {
        if (connector != null) {
            connector.setStopRequested(true);
            connector.interrupt();
        }
        super.disconnect();
    }

    public RTMPProtocolDecoder getDecoder() {
        return codecFactory.getRTMPDecoder();
    }

    public RTMPProtocolEncoder getEncoder() {
        return codecFactory.getRTMPEncoder();
    }

}
