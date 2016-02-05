/*
 * RED5 Open Source Flash Server - https://github.com/Red5/
 * 
 * Copyright 2006-2016 by respective authors (see below). All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.red5.server.net.rtmpe;

import java.util.Arrays;

import javax.crypto.Cipher;

import org.apache.commons.codec.binary.Hex;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.core.write.WriteRequestWrapper;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.red5.server.net.rtmp.InboundHandshake;
import org.red5.server.net.rtmp.RTMPConnManager;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.RTMPMinaConnection;
import org.red5.server.net.rtmp.codec.RTMP;
import org.red5.server.net.rtmp.codec.RTMPMinaCodecFactory;
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

    @Override
    public void messageReceived(NextFilter nextFilter, IoSession session, Object obj) throws Exception {
        log.trace("messageReceived nextFilter: {} session: {} message: {}", nextFilter, session, obj);
        String sessionId = (String) session.getAttribute(RTMPConnection.RTMP_SESSION_ID);
        if (sessionId != null) {
            log.trace("Session id: {}", sessionId);
            RTMPMinaConnection conn = (RTMPMinaConnection) RTMPConnManager.getInstance().getConnectionBySessionId(sessionId);
            // filter based on current connection state
            RTMP rtmp = conn.getState();
            final byte connectionState = conn.getStateCode();
            // assume message is an IoBuffer
            IoBuffer message = (IoBuffer) obj;
            IoBuffer buf;
            log.trace("Buffer: {}", Hex.encodeHexString(Arrays.copyOfRange(message.array(), message.position(), message.limit())));
            // client handshake handling
            InboundHandshake handshake = null;
            int remaining = 0;
            switch (connectionState) {
                case RTMP.STATE_CONNECT:
                    // create a buffer and store it on the session
                    buf = (IoBuffer) session.getAttribute("handshakeBuffer");
                    if (buf == null) {
                        buf = IoBuffer.allocate(Constants.HANDSHAKE_SIZE);
                        buf.setAutoExpand(true);
                        session.setAttribute("handshakeBuffer", buf);
                    }
                    buf.put(message);
                    buf.flip();
                    // we're expecting C0+C1 here
                    //log.trace("C0C1 byte order: {}", message.order());
                    // get the handshake from the session
                    handshake = (InboundHandshake) session.getAttribute(RTMPConnection.RTMP_HANDSHAKE);
                    // set handshake to match client requested type
                    buf.mark();
                    handshake.setHandshakeType(buf.get());
                    buf.reset();
                    log.debug("decodeHandshakeC0C1 - buffer: {}", buf);
                    // we want 1537 bytes for C0C1
                    remaining = buf.remaining();
                    log.trace("Incoming C0C1 size: {}", remaining);
                    if (remaining >= (Constants.HANDSHAKE_SIZE + 1)) {
                        // get the connection type byte, may want to set this on the conn in the future
                        byte connectionType = buf.get();
                        log.trace("Incoming C0 connection type: {}", connectionType);
                        // create array for decode
                        byte[] dst = new byte[Constants.HANDSHAKE_SIZE];
                        // copy out 1536 bytes
                        buf.get(dst);
                        //log.debug("C1 - buffer: {}", Hex.encodeHexString(dst));
                        // set state to indicate we're waiting for C2
                        rtmp.setState(RTMP.STATE_HANDSHAKE);
                        // buffer any extra bytes
                        remaining = buf.remaining();
                        if (log.isTraceEnabled()) {
                            log.trace("Incoming C1 remaining size: {}", remaining);
                        }
                        if (remaining > 0) {
                            // store the remaining bytes in a thread local for use by C2 decoding
                            byte[] remainder = new byte[remaining];
                            buf.get(remainder);
                            session.setAttribute("handshake.buffer", remainder);
                            log.trace("Stored {} bytes for later decoding", remaining);
                        }
                        IoBuffer s1 = handshake.decodeClientRequest1(IoBuffer.wrap(dst));
                        if (s1 != null) {
                            //log.trace("S1 byte order: {}", s1.order());
                            session.write(s1);
                            buf.compact();
                        } else {
                            log.warn("Client was rejected due to invalid handshake");
                            conn.close();
                        }
                    }
                    break;
                case RTMP.STATE_HANDSHAKE:
                    // getting a buffer from the session
                    buf = (IoBuffer) session.getAttribute("handshakeBuffer");
                    buf.put(message);
                    buf.flip();
                    // we're expecting C2 here
                    //log.trace("C2 byte order: {}", message.order());
                    // get the handshake from the session
                    handshake = (InboundHandshake) session.getAttribute(RTMPConnection.RTMP_HANDSHAKE);
                    log.debug("decodeHandshakeC2 - buffer: {}", buf);
                    remaining = buf.remaining();
                    // check for remaining stored bytes left over from C0C1
                    byte[] remainder = null;
                    if (session.containsAttribute("handshake.buffer")) {
                        remainder = (byte[]) session.getAttribute("handshake.buffer");
                        remaining += remainder.length;
                        log.trace("Remainder: {}", Hex.encodeHexString(remainder));
                    }
                    // no connection type byte is supposed to be in C2 data
                    log.trace("Incoming C2 size: {}", remaining);
                    if (remaining >= Constants.HANDSHAKE_SIZE) {
                        // create array for decode
                        byte[] dst = new byte[Constants.HANDSHAKE_SIZE];
                        // check for remaining stored bytes left over from C0C1 and prepend to the dst array
                        if (remainder != null) {
                            // copy into dst
                            System.arraycopy(remainder, 0, dst, 0, remainder.length);
                            log.trace("Copied {} from buffer {}", remainder.length, Hex.encodeHexString(dst));
                            // copy
                            buf.get(dst, remainder.length, (Constants.HANDSHAKE_SIZE - remainder.length));
                            log.trace("Copied {} from message {}", (Constants.HANDSHAKE_SIZE - remainder.length), Hex.encodeHexString(dst));
                            // remove buffer
                            session.removeAttribute("handshake.buffer");
                        } else {
                            // copy
                            buf.get(dst);
                            log.trace("Copied {}", Hex.encodeHexString(dst));
                        }
                        //if (log.isTraceEnabled()) {
                        //    log.trace("C2 - buffer: {}", Hex.encodeHexString(dst));
                        //}
                        if (handshake.decodeClientRequest2(IoBuffer.wrap(dst))) {
                            log.debug("Connected, removing handshake data and adding rtmp protocol filter");
                            // set state to indicate we're connected
                            buf.compact();
                            buf.flip();
                            rtmp.setState(RTMP.STATE_CONNECTED);
                            // set encryption flag the rtmp state
                            if (handshake.useEncryption()) {
                                log.debug("Using encrypted communications, adding ciphers to the session");
                                rtmp.setEncrypted(true);
                                session.setAttribute(RTMPConnection.RTMPE_CIPHER_IN, handshake.getCipherIn());
                                session.setAttribute(RTMPConnection.RTMPE_CIPHER_OUT, handshake.getCipherOut());
                            } 
                            // remove handshake from session now that we are connected
                            session.removeAttribute(RTMPConnection.RTMP_HANDSHAKE);
                            // add protocol filter as the last one in the chain
                            log.debug("Adding RTMP protocol filter");
                            session.getFilterChain().addAfter("rtmpeFilter", "protocolFilter", new ProtocolCodecFilter(new RTMPMinaCodecFactory()));
                            sendDecryptedMessage(rtmp, buf, nextFilter, session);
                            buf.clear();
                        } else {
                            log.warn("Client was rejected due to invalid handshake");
                            conn.close();
                        }
                    } else {
                        log.debug("Do not have enough data for handshake");
                    }
                    break;
                case RTMP.STATE_CONNECTED:
                    sendDecryptedMessage(rtmp, message, nextFilter, session);
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

    @Override
    public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest request) throws Exception {
        log.trace("filterWrite nextFilter: {} session: {} request: {}", nextFilter, session, request);
        RTMPMinaConnection conn = (RTMPMinaConnection) RTMPConnManager.getInstance().getConnectionBySessionId((String) session.getAttribute(RTMPConnection.RTMP_SESSION_ID));
        if (conn.getState().isEncrypted()) {
            Cipher cipher = (Cipher) session.getAttribute(RTMPConnection.RTMPE_CIPHER_OUT);
            IoBuffer message = (IoBuffer) request.getMessage();
            if (!message.hasRemaining()) {
                if (log.isTraceEnabled()) {
                    log.trace("Ignoring empty message");
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Encrypting message: {}", message);
                }
                byte[] plain = new byte[message.remaining()];
                message.get(plain);
                message.clear();
                message.free();
                //encrypt and write
                byte[] encrypted = cipher.update(plain);
                IoBuffer messageEncrypted = IoBuffer.wrap(encrypted);
                if (log.isDebugEnabled()) {
                    log.debug("Writing encrypted message: {}", messageEncrypted);
                }
                nextFilter.filterWrite(session, new EncryptedWriteRequest(request, messageEncrypted));
            }
        } else {
            log.trace("Writing message");
            nextFilter.filterWrite(session, request);
        }
    }

    private void sendDecryptedMessage(RTMP rtmp, IoBuffer message, NextFilter nextFilter, IoSession session)
    {
        // assuming majority of connections will not be encrypted
        if (!rtmp.isEncrypted()) {
            log.trace("Receiving message: {}", message);
            nextFilter.messageReceived(session, message);
        } else {
            log.trace("Receiving encrypted message: {}", message);
            Cipher cipher = (Cipher) session.getAttribute(RTMPConnection.RTMPE_CIPHER_IN);
            if (cipher != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Decrypting message: {}", message);
                }
                byte[] encrypted = new byte[message.remaining()];
                message.get(encrypted);
                message.clear();
                message.free();
                byte[] plain = cipher.update(encrypted);
                IoBuffer messageDecrypted = IoBuffer.wrap(plain);
                if (log.isDebugEnabled()) {
                    log.debug("Receiving decrypted message: {}", messageDecrypted);
                }
                nextFilter.messageReceived(session, messageDecrypted);
            }
        }
    }

    private static class EncryptedWriteRequest extends WriteRequestWrapper {
        private final IoBuffer encryptedMessage;

        private EncryptedWriteRequest(WriteRequest writeRequest, IoBuffer encryptedMessage) {
            super(writeRequest);
            this.encryptedMessage = encryptedMessage;
        }

        @Override
        public Object getMessage() {
            return encryptedMessage;
        }
    }

}
