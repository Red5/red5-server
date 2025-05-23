/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.rtmp;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.red5.server.api.Red5;
import org.red5.server.net.rtmp.message.Packet;

/**
 * Wraps processing of incoming messages.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public final class ReceivedMessageTask implements Callable<Packet>, Supplier<Packet> {

    private final RTMPConnection conn;

    private final IRTMPHandler handler;

    private final Packet packet;

    private final int hashCode;

    private AtomicBoolean processing = new AtomicBoolean(false);

    /**
     * <p>Constructor for ReceivedMessageTask.</p>
     *
     * @param conn a {@link org.red5.server.net.rtmp.RTMPConnection} object
     * @param packet a {@link org.red5.server.net.rtmp.message.Packet} object
     */
    public ReceivedMessageTask(RTMPConnection conn, Packet packet) {
        this.conn = conn;
        this.packet = packet;
        this.handler = conn.getHandler();
        // generate hash code
        hashCode = Objects.hash(conn.getSessionId(), packet);
    }

    /**
     * {@inheritDoc}
     *
     * Older versions of Red5 used the {@link #call()} method to process incoming messages.
     */
    @Override
    public Packet call() throws Exception {
        if (processing.compareAndSet(false, true)) {
            // set connection to thread local
            Red5.setConnectionLocal(conn);
            try {
                // pass message to the handler
                handler.messageReceived(conn, packet);
                // if we get this far, set done / completed flag
                packet.setProcessed(true);
            } finally {
                // clear thread local
                Red5.setConnectionLocal(null);
            }
        } else {
            throw new IllegalStateException("Task is already being processed");
        }
        return packet;
    }

    /**
     * {@inheritDoc}
     *
     * Newer versions of Red5 use the {@link #get()} method to process incoming messages.
     */
    @Override
    public Packet get() {
        if (processing.compareAndSet(false, true)) {
            // set connection to thread local
            Red5.setConnectionLocal(conn);
            try {
                // pass message to the handler
                handler.messageReceived(conn, packet);
                // if we get this far, set done / completed flag
                packet.setProcessed(true);
            } catch (Exception e) {
                // track the exception
                conn.setAttribute("exception", e);
            } finally {
                // clear thread local
                Red5.setConnectionLocal(null);
            }
        }
        return packet;
    }

    /**
     * <p>Getter for the field <code>packet</code>.</p>
     *
     * @return a {@link org.red5.server.net.rtmp.message.Packet} object
     */
    public Packet getPacket() {
        return packet;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return hashCode;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ReceivedMessageTask other = (ReceivedMessageTask) obj;
        if (!this.equals(other)) {
            return false;
        }
        if (!packet.getHeader().equals(other.packet.getHeader())) {
            return false;
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "[sessionId: " + conn.getSessionId() + ", processing: " + processing.get() + "]";
    }

}
