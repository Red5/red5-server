/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.rtmpe;

import java.util.Optional;

import javax.crypto.Cipher;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.bouncycastle.util.encoders.Hex;
import org.red5.server.net.rtmp.InboundHandshake;
import org.red5.server.net.rtmp.RTMPConnManager;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.RTMPMinaConnection;
import org.red5.server.net.rtmp.ReadBuffer;
import org.red5.server.net.rtmp.codec.RTMP;
import org.red5.server.net.rtmp.message.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RTMPE IO filter - Server version.
 *
 * @author Peter Thomas (ptrthomas@gmail.com)
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class RTMPEIoFilter extends IoFilterAdapter {

    private static final Logger log = LoggerFactory.getLogger(RTMPEIoFilter.class);

    private static boolean isDebug = log.isDebugEnabled();

    private static boolean isTrace = log.isTraceEnabled();

    /** {@inheritDoc} */
    @Override
    public void messageReceived(NextFilter nextFilter, IoSession session, Object obj) throws Exception {
        if (isTrace) {
            log.trace("messageReceived nextFilter: {} session: {} message: {}", nextFilter, session, obj);
        }
        String sessionId = (String) session.getAttribute(RTMPConnection.RTMP_SESSION_ID);
        if (sessionId != null) {
            if (isTrace) {
                log.trace("RTMP Session id: {}", sessionId);
            }
            RTMPMinaConnection conn = (RTMPMinaConnection) RTMPConnManager.getInstance().getConnectionBySessionId(sessionId);
            if (conn == null) {
                throw new Exception("Receive on unavailable connection - session id: " + sessionId);
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
            InboundHandshake handshake = session.containsAttribute(RTMPConnection.RTMP_HANDSHAKE) ? (InboundHandshake) session.getAttribute(RTMPConnection.RTMP_HANDSHAKE) : null;
            switch (connectionState) {
                case RTMP.STATE_CONNECT:
                    // get the handshake from the session and process C0+C1 if we have enough bytes
                    // check the size, we want 1537 bytes for C0C1
                    int c0c1Size = buffer.getBufferSize();
                    log.trace("Incoming C0C1 size: {}", c0c1Size);
                    if (c0c1Size >= (Constants.HANDSHAKE_SIZE + 1)) {
                        log.debug("decodeHandshakeC0C1");
                        // get the buffered bytes 1536 + connection type byte
                        byte[] dst = buffer.getBuffer(Constants.HANDSHAKE_SIZE + 1);
                        // set handshake to match client requested type
                        byte connectionType = dst[0];
                        if (handshake != null) {
                            handshake.setHandshakeType(connectionType);
                            log.trace("Incoming C0 connection type: {}", connectionType);
                            IoBuffer decBuffer = IoBuffer.wrap(dst);
                            // skip the connection type
                            decBuffer.get();
                            // decode it
                            IoBuffer s1 = handshake.decodeClientRequest1(decBuffer);
                            if (s1 != null) {
                                // set state to indicate we're waiting for C2
                                ((RTMPConnection) conn).setStateCode(RTMP.STATE_HANDSHAKE);
                                //log.trace("S1 byte order: {}", s1.order());
                                session.write(s1);
                            } else {
                                log.warn("Client was rejected due to invalid handshake");
                                conn.close();
                            }
                        } else {
                            log.warn("Handshake is null");
                            conn.close();
                        }
                    }
                    break;
                case RTMP.STATE_HANDSHAKE:
                    // get the handshake from the session and process C2 if we have enough bytes
                    // no connection type byte is supposed to be in C2 data
                    int c2Size = buffer.getBufferSize();
                    log.trace("Incoming C2 size: {}", c2Size);
                    if (c2Size >= Constants.HANDSHAKE_SIZE) {
                        log.debug("decodeHandshakeC2");
                        // create array for decode containing C2
                        byte[] dst = buffer.getBuffer(Constants.HANDSHAKE_SIZE);
                        if (handshake != null) {
                            if (handshake.decodeClientRequest2(IoBuffer.wrap(dst))) {
                                log.debug("Connected");
                                // set state to indicate we're connected
                                ((RTMPConnection) conn).setStateCode(RTMP.STATE_CONNECTED);
                                // remove handshake from session now that we are connected
                                session.removeAttribute(RTMPConnection.RTMP_HANDSHAKE);
                                // set encryption flag
                                if (handshake.useEncryption()) {
                                    log.debug("Using encrypted communications, adding ciphers to the session");
                                    ((RTMPConnection) conn).setEncrypted(true);
                                    session.setAttribute(RTMPConnection.RTMPE_CIPHER_IN, handshake.getCipherIn());
                                    session.setAttribute(RTMPConnection.RTMPE_CIPHER_OUT, handshake.getCipherOut());
                                }
                                // leave the remaining bytes in the buffer for the next step to handle / decrypt / decode
                            } else {
                                log.warn("Client was rejected due to invalid handshake");
                                conn.close();
                            }
                        } else {
                            log.warn("Handshake is null");
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
                            if (isTrace) {
                                //log.trace("Receiving message: {}", message);
                                log.trace(sessionId + " Receiving message: {}", Hex.toHexString(message.array(), message.arrayOffset(), message.remaining()));
                            }
                            nextFilter.messageReceived(session, message);
                        } else {
                            Cipher cipher = (Cipher) session.getAttribute(RTMPConnection.RTMPE_CIPHER_IN);
                            if (cipher != null) {
                                if (isDebug) {
                                    log.debug("Decrypting message: {}", message);
                                }
                                byte[] encrypted = new byte[message.remaining()];
                                message.get(encrypted);
                                message.free();
                                byte[] plain = cipher.update(encrypted);
                                IoBuffer messageDecrypted = IoBuffer.wrap(plain);
                                if (isDebug) {
                                    log.debug("Receiving decrypted message: {}", messageDecrypted);
                                }
                                nextFilter.messageReceived(session, messageDecrypted);
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
    }

    /** {@inheritDoc} */
    @Override
    public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest request) throws Exception {
        log.trace("filterWrite nextFilter: {} session: {} request: {}", nextFilter, session, request);
        Cipher cipher = (Cipher) session.getAttribute(RTMPConnection.RTMPE_CIPHER_OUT);
        if (cipher == null) {
            if (isTrace) {
                log.trace("Writing message");
            }
            nextFilter.filterWrite(session, request);
        } else {
            IoBuffer message = (IoBuffer) request.getMessage();
            if (message.hasRemaining()) {
                if (isDebug) {
                    log.debug("Encrypting message: {}", message);
                }
                byte[] plain = new byte[message.remaining()];
                message.get(plain);
                message.free();
                // encrypt and write
                byte[] encrypted = cipher.update(plain);
                IoBuffer messageEncrypted = IoBuffer.wrap(encrypted);
                if (isDebug) {
                    log.debug("Writing encrypted message: {}", messageEncrypted);
                }
                nextFilter.filterWrite(session, new EncryptedWriteRequest(request, messageEncrypted));
            }
        }
    }

}
