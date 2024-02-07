/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2013 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.client.net.rtmpe;

import java.util.Optional;

import javax.crypto.Cipher;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.red5.client.net.rtmp.BaseRTMPClientHandler;
import org.red5.client.net.rtmp.OutboundHandshake;
import org.red5.client.net.rtmp.RTMPClientConnManager;
import org.red5.client.net.rtmp.codec.RTMPMinaCodecFactory;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.RTMPMinaConnection;
import org.red5.server.net.rtmp.ReadBuffer;
import org.red5.server.net.rtmp.codec.RTMP;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.net.rtmpe.EncryptedWriteRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RTMPE IO filter - Client version.
 *
 * @author Peter Thomas (ptrthomas@gmail.com)
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class RTMPEIoFilter extends IoFilterAdapter {

    private static final Logger log = LoggerFactory.getLogger(RTMPEIoFilter.class);

    @Override
    public void messageReceived(NextFilter nextFilter, IoSession session, Object obj) throws Exception {
        String sessionId = (String) session.getAttribute(RTMPConnection.RTMP_SESSION_ID);
        log.trace("Session id: {}", sessionId);
        RTMPMinaConnection conn = (RTMPMinaConnection) RTMPClientConnManager.getInstance().getConnectionBySessionId(sessionId);
        if (conn == null) {
            throw new Exception("Receive on unavailable connection - session id: " + sessionId);
        }
        if (log.isTraceEnabled()) {
            log.trace("Bytes read: {} written: {}", conn.getReadBytes(), conn.getWrittenBytes());
        }
        // filter based on current connection state
        final byte connectionState = conn.getStateCode();
        // get a buffer for incoming data
        ReadBuffer buffer = Optional.ofNullable((ReadBuffer) session.getAttribute(RTMPConnection.RTMP_BUFFER)).orElseGet(() -> {
            // add the attribute
            session.setAttribute(RTMPConnection.RTMP_BUFFER, new ReadBuffer());
            // get for return
            return (ReadBuffer) session.getAttribute(RTMPConnection.RTMP_BUFFER);
        });
        // buffer the incoming data
        buffer.addBuffer((IoBuffer) obj);
        // client handshake handling
        OutboundHandshake handshake = session.containsAttribute(RTMPConnection.RTMP_HANDSHAKE) ? (OutboundHandshake) session.getAttribute(RTMPConnection.RTMP_HANDSHAKE) : null;
        switch (connectionState) {
            case RTMP.STATE_CONNECT:
                // get the handshake from the session and process S0+S1 if we have enough bytes
                // we want 1537 bytes for S0S1
                int s0s1Size = buffer.getBufferSize();
                log.trace("Incoming S0S1 size: {}", s0s1Size);
                if (s0s1Size >= (Constants.HANDSHAKE_SIZE + 1)) {
                    log.debug("decodeHandshakeS0S1");
                    byte[] dst = buffer.getBuffer(Constants.HANDSHAKE_SIZE + 1);
                    // set handshake to match client requested type
                    byte connectionType = dst[0];
                    log.trace("Incoming S0 connection type: {}", connectionType);
                    if (handshake != null) {
                        if (handshake.getHandshakeType() != connectionType) {
                            log.debug("Server requested handshake type: {} client requested: {}", connectionType, handshake.getHandshakeType());
                        }
                        // XXX do we go ahead with what the server requested?
                        handshake.setHandshakeType(connectionType);
                        // wrap the buffer for decoding
                        IoBuffer decBuffer = IoBuffer.wrap(dst);
                        // skip the connection type; should be 1536 after
                        decBuffer.get();
                        //log.debug("S1 - buffer: {}", Hex.encodeHexString(dst));
                        // keep remaining bytes in a thread local for use by S2 decoding
                        IoBuffer c2 = handshake.decodeServerResponse1(decBuffer);
                        if (c2 != null) {
                            // clean up
                            decBuffer.clear();
                            // set state to indicate we're waiting for S2
                            conn.setStateCode(RTMP.STATE_HANDSHAKE);
                            //log.trace("C2 byte order: {}", c2.order());
                            session.write(c2);
                            // if we got S0S1+S2 continue processing
                            if (buffer.getBufferSize() >= Constants.HANDSHAKE_SIZE) {
                                log.debug("decodeHandshakeS2");
                                if (handshake.decodeServerResponse2(buffer.getBuffer(Constants.HANDSHAKE_SIZE))) {
                                    log.debug("S2 decoding successful");
                                } else {
                                    log.warn("Handshake failed on S2 processing");
                                }
                                completeConnection(session, conn, handshake);
                            }
                        } else {
                            conn.close();
                        }
                    } else {
                        log.warn("Handshake is missing from the session");
                        conn.close();
                    }
                }
                break;
            case RTMP.STATE_HANDSHAKE:
                // get the handshake from the session and process S2 if we have enough bytes
                int s2Size = buffer.getBufferSize();
                log.trace("Incoming S2 size: {}", s2Size);
                if (s2Size >= Constants.HANDSHAKE_SIZE) {
                    log.debug("decodeHandshakeS2");
                    if (handshake != null) {
                        if (handshake.decodeServerResponse2(buffer.getBuffer(Constants.HANDSHAKE_SIZE))) {
                            log.debug("S2 decoding successful");
                        } else {
                            log.warn("Handshake failed on S2 processing");
                        }
                        // complete the connection regardless of the S2 success or failure
                        completeConnection(session, conn, handshake);
                    } else {
                        log.warn("Handshake is missing from the session");
                        conn.close();
                    }
                } else {
                    // don't fall through to connected process if we didn't have enough for the handshake
                    break;
                }
                // allow fall-through
            case RTMP.STATE_CONNECTED:
                // skip empty buffer
                if (buffer.getBufferSize() > 0) {
                    IoBuffer message = buffer.getBufferAsIoBuffer();
                    // assuming majority of connections will not be encrypted
                    if (!((RTMPConnection) conn).isEncrypted()) {
                        log.trace("Receiving message: {}", message);
                        nextFilter.messageReceived(session, message);
                    } else {
                        Cipher cipher = (Cipher) session.getAttribute(RTMPConnection.RTMPE_CIPHER_IN);
                        if (cipher != null) {
                            if (log.isDebugEnabled()) {
                                log.debug("Decrypting message: {}", message);
                            }
                            byte[] encrypted = new byte[message.remaining()];
                            message.get(encrypted);
                            message.free();
                            byte[] plain = cipher.update(encrypted);
                            IoBuffer messageDecrypted = IoBuffer.wrap(plain);
                            if (log.isDebugEnabled()) {
                                log.debug("Decrypted buffer: {}", messageDecrypted);
                            }
                            nextFilter.messageReceived(session, messageDecrypted);
                        } else {
                            log.warn("Decryption cipher is missing from the session");
                        }
                    }
                }
                break;
            case RTMP.STATE_ERROR:
            case RTMP.STATE_DISCONNECTING:
            case RTMP.STATE_DISCONNECTED:
                // do nothing, really
                log.debug("Nothing to do, connection state: {}", RTMP.states[connectionState]);
                break;
            default:
                throw new IllegalStateException("Invalid RTMP state: " + connectionState);
        }
    }

    /**
     * Provides connection completion.
     *
     * @param session
     * @param conn
     * @param rtmp
     * @param handshake
     */
    private static void completeConnection(IoSession session, RTMPMinaConnection conn, OutboundHandshake handshake) {
        // set state to indicate we're connected
        conn.setStateCode(RTMP.STATE_CONNECTED);
        // configure encryption
        if (handshake.useEncryption()) {
            log.debug("Connected, setting up encryption and removing handshake data");
            // set encryption flag
            ((RTMPConnection) conn).setEncrypted(true);
            // add the ciphers
            log.debug("Adding ciphers to the session");
            // seems counter intuitive, but it works
            session.setAttribute(RTMPConnection.RTMPE_CIPHER_IN, handshake.getCipherOut());
            session.setAttribute(RTMPConnection.RTMPE_CIPHER_OUT, handshake.getCipherIn());
            log.trace("Ciphers in: {} out: {}", handshake.getCipherIn(), handshake.getCipherOut());
        } else {
            log.debug("Connected, removing handshake data");
        }
        // remove handshake from session now that we are connected
        session.removeAttribute(RTMPConnection.RTMP_HANDSHAKE);
        // add protocol filter as the last one in the chain
        log.debug("Adding RTMP protocol filter");
        session.getFilterChain().addAfter("rtmpeFilter", "protocolFilter", new ProtocolCodecFilter(new RTMPMinaCodecFactory()));
        // let the client know it may proceed
        BaseRTMPClientHandler handler = (BaseRTMPClientHandler) session.getAttribute(RTMPConnection.RTMP_HANDLER);
        handler.connectionOpened(conn);
    }

    @Override
    public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest request) throws Exception {
        // grab the message
        Object message = request.getMessage();
        // if its bytes, we may encrypt thme
        if (message instanceof IoBuffer) {
            // filter based on current connection state
            Cipher cipher = (Cipher) session.getAttribute(RTMPConnection.RTMPE_CIPHER_OUT);
            if (cipher != null) {
                IoBuffer buf = (IoBuffer) message;
                int remaining = buf.remaining();
                if (remaining > 0) {
                    if (log.isDebugEnabled()) {
                        log.debug("Encrypting {} bytes, message: {}", remaining, buf);
                    }
                    byte[] plain = new byte[remaining];
                    buf.get(plain);
                    buf.free();
                    // encrypt and write
                    byte[] encrypted = cipher.update(plain);
                    buf = IoBuffer.wrap(encrypted);
                    if (log.isDebugEnabled()) {
                        log.debug("Encrypted message: {}", buf);
                    }
                }
                nextFilter.filterWrite(session, new EncryptedWriteRequest(request, buf));
            } else {
                log.trace("Non-encrypted message");
                nextFilter.filterWrite(session, request);
            }
        } else {
            log.trace("Passing through packet");
            nextFilter.filterWrite(session, request);
        }
    }

}
