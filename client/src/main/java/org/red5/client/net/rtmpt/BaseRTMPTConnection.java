/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2015 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.client.net.rtmpt;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.server.api.Red5;
import org.red5.server.net.rtmp.IRTMPHandler;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.codec.RTMP;
import org.red5.server.net.rtmp.codec.RTMPProtocolDecoder;
import org.red5.server.net.rtmp.codec.RTMPProtocolEncoder;
import org.red5.server.net.rtmp.message.Packet;
import org.red5.server.net.rtmpt.codec.RTMPTProtocolDecoder;
import org.red5.server.net.rtmpt.codec.RTMPTProtocolEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseRTMPTConnection extends RTMPConnection {

    private static final Logger log = LoggerFactory.getLogger(BaseRTMPTConnection.class);

    /**
     * Protocol decoder
     */
    private RTMPTProtocolDecoder decoder;

    /**
     * Protocol encoder
     */
    private RTMPTProtocolEncoder encoder;

    private static class PendingData {
        private IoBuffer buffer;

        private Packet packet;

        private PendingData(IoBuffer buffer, Packet packet) {
            this.buffer = buffer;
            this.packet = packet;
        }

        private PendingData(IoBuffer buffer) {
            this.buffer = buffer;
        }

        public IoBuffer getBuffer() {
            return buffer;
        }

        public Packet getPacket() {
            return packet;
        }

        @Override
        public String toString() {
            return getClass().getName() + "(buffer=" + buffer + "; packet=" + packet + ")";
        }
    }

    /**
     * List of pending messages
     */
    private ConcurrentLinkedQueue<PendingData> pendingMessages = new ConcurrentLinkedQueue<>();

    /**
     * Closing flag
     */
    private volatile boolean closing;

    /**
     * Number of read bytes
     */
    private AtomicLong readBytes = new AtomicLong(0);

    /**
     * Number of written bytes
     */
    private AtomicLong writtenBytes = new AtomicLong(0);

    /**
     * Byte buffer
     */
    private IoBuffer buffer;

    /**
     * Clients session id, used to override the BaseConnection.sessionId for client implementations.
     */
    protected String clientSessionId;

    /**
     * RTMP events handler
     */
    private volatile IRTMPHandler handler;

    public BaseRTMPTConnection(String type) {
        super(type);
        this.buffer = IoBuffer.allocate(2048);
        this.buffer.setAutoExpand(true);
    }

    /**
     * Return any pending messages up to a given size.
     *
     * @param targetSize
     *            the size the resulting buffer should have
     * @return a buffer containing the data to send or null if no messages are pending
     */
    abstract public IoBuffer getPendingMessages(int targetSize);

    /** {@inheritDoc} */
    @Override
    public void close() {
        log.debug("close - state: {}", state.getState());
        // Defer actual closing so we can send back pending messages to the client.
        closing = true;
    }

    /**
     * Getter for property 'closing'.
     *
     * @return Value for property 'closing'.
     */
    public boolean isClosing() {
        return closing;
    }

    /**
     * Real close
     */
    public void realClose() {
        if (isClosing()) {
            if (buffer != null) {
                buffer.free();
                buffer = null;
            }
            state.setState(RTMP.STATE_DISCONNECTED);
            pendingMessages.clear();
            super.close();
        }
    }

    /**
     * Send raw data down the connection.
     *
     * @param packet
     *            the buffer containing the raw data
     */
    @Override
    public void writeRaw(IoBuffer packet) {
        pendingMessages.add(new PendingData(packet));
    }

    /** {@inheritDoc} */
    @Override
    public long getReadBytes() {
        return readBytes.get();
    }

    /** {@inheritDoc} */
    @Override
    public long getWrittenBytes() {
        return writtenBytes.get();
    }

    /** {@inheritDoc} */
    @Override
    public long getPendingMessages() {
        return pendingMessages.size();
    }

    public void setSessionId(String sessionId) {
        log.debug("Overriding generated session id {} with {}", this.sessionId, sessionId);
        this.clientSessionId = sessionId;
        // reset the session id on the decoder state to prevent confusing log messages
        //RTMPDecodeState state = this.decoderState.get();
        //if (state != null) {
        //    state.setSessionId(sessionId);
        //}
    }

    @Override
    public String getSessionId() {
        if (clientSessionId == null) {
            return sessionId;
        }
        return clientSessionId;
    }

    /**
     * Decode data sent by the client.
     *
     * @param data
     *            the data to decode
     * @return a list of decoded objects
     */
    public List<?> decode(IoBuffer data) {
        log.debug("decode - state: {}", state);
        if (closing || state.getState() == RTMP.STATE_DISCONNECTED) {
            // Connection is being closed, don't decode any new packets
            return Collections.EMPTY_LIST;
        }
        readBytes.addAndGet(data.limit());
        buffer.put(data);
        buffer.flip();
        return decoder.decodeBuffer(this, buffer);
    }

    /**
     * Send RTMP packet down the connection.
     *
     * @param packet
     *            the packet to send
     */
    @Override
    public void write(final Packet packet) {
        log.debug("write - state: {}", state);
        if (closing || state.getState() == RTMP.STATE_DISCONNECTED) {
            // Connection is being closed, don't send any new packets
            return;
        }
        IoBuffer data;
        try {
            Red5.setConnectionLocal(this);
            data = encoder.encode(packet);
        } catch (Exception e) {
            log.error("Could not encode message {}", packet, e);
            return;
        } finally {
            Red5.setConnectionLocal(null);
        }

        if (data != null) {
            // Mark packet as being written
            writingMessage(packet);
            //add to pending
            pendingMessages.add(new PendingData(data, packet));
        } else {
            log.info("Response buffer was null after encoding");
        }
    }

    protected IoBuffer foldPendingMessages(int targetSize) {
        if (pendingMessages.isEmpty()) {
            return null;
        }
        IoBuffer result = IoBuffer.allocate(2048);
        result.setAutoExpand(true);
        // We'll have to create a copy here to avoid endless recursion
        List<Packet> toNotify = new LinkedList<Packet>();
        while (!pendingMessages.isEmpty()) {
            PendingData pendingMessage = pendingMessages.remove();
            result.put(pendingMessage.getBuffer());
            if (pendingMessage.getPacket() != null) {
                toNotify.add(pendingMessage.getPacket());
            }
            if ((result.position() > targetSize)) {
                break;
            }
        }
        for (Packet message : toNotify) {
            try {
                handler.messageSent(this, message);
            } catch (Exception e) {
                log.error("Could not notify stream subsystem about sent message", e);
            }
        }
        result.flip();
        writtenBytes.addAndGet(result.limit());
        return result;
    }

    @Override
    public void setHandler(IRTMPHandler handler) {
        this.handler = handler;
    }

    public void setDecoder(RTMPProtocolDecoder decoder) {
        this.decoder = (RTMPTProtocolDecoder) decoder;
    }

    public void setEncoder(RTMPProtocolEncoder encoder) {
        this.encoder = (RTMPTProtocolEncoder) encoder;
    }
}
