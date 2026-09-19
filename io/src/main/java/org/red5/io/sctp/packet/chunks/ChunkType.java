/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License") + you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.red5.io.sctp.packet.chunks;

/**
 * <p>ChunkType class.</p>
 *
 * @author mondain
 */
public enum ChunkType {

    /** Payload Data chunk. */
    DATA(0), // Payload Data
    /** Initiation chunk. */
    INIT(1), // Initiation
    /** Initiation Acknowledgement chunk. */
    INIT_ACK(2), // Initiation Acknowledgement
    /** Selective Acknowledgement chunk. */
    SACK(3), // Selective Acknowledgement
    /** Heartbeat Request chunk. */
    HEARTBEAT(4), // Heartbeat Request
    /** Heartbeat Acknowledgement chunk. */
    HEARTBEAT_ACK(5), // Heartbeat Acknowledgement
    /** Abort chunk. */
    ABORT(6), // Abort
    /** Shutdown chunk. */
    SHUTDOWN(7), // Shutdown
    /** Shutdown Acknowledgement chunk. */
    SHUTDOWN_ACK(8), // Shutdown Acknowledgement
    /** Operation Error chunk. */
    ERROR(9), // Operation Error
    /** State Cookie chunk. */
    COOKIE_ECHO(10), // State Cookie
    /** Cookie Acknowledgement chunk. */
    COOKIE_ACK(11), // Cookie Acknowledgement
    /** Reserved for Explicit Congestion Notification Echo. */
    ECNE(12), // Reserved for Explicit Congestion Notification Echo
    /** Reserved for Congestion Window Reduced. */
    CWR(13), // Reserved for Congestion Window Reduced
    /** Shutdown Complete chunk. */
    SHUTDOWN_COMPLETE(14);

    private int value;

    private ChunkType(final int value) {
        this.value = value;
    }

    /**
     * <p>Getter for the field <code>value</code>.</p>
     *
     * @return a int
     */
    public int getValue() {
        return value;
    }
}
