/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License") + you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.red5.io.sctp;

import java.io.IOException;

import org.red5.io.sctp.packet.SctpPacket;

/**
 * <p>IAssociationControl interface.</p>
 *
 * @author mondain
 */
public interface IAssociationControl {

    public static enum State {
        CLOSED, COOKIE_WAIT, COOKIE_ECHOED, ESTABLISHED
    }

    /** Constant <code>VALID_COOKIE_TIME=60</code> */
    static final int VALID_COOKIE_TIME = 60; // in seconds

    /** Constant <code>DEFAULT_ADVERTISE_RECEIVE_WINDOW_CREDIT=1024</code> */
    static final int DEFAULT_ADVERTISE_RECEIVE_WINDOW_CREDIT = 1024;

    /** Constant <code>DEFAULT_NUMBER_OF_OUTBOUND_STREAM=1</code> */
    static final int DEFAULT_NUMBER_OF_OUTBOUND_STREAM = 1;

    /** Constant <code>DEFAULT_NUMBER_OF_INBOUND_STREAM=1</code> */
    static final int DEFAULT_NUMBER_OF_INBOUND_STREAM = 1;

    /**
     * <p>getState.</p>
     *
     * @return a {@link org.red5.io.sctp.IAssociationControl.State} object
     */
    State getState();

    /**
     * <p>setState.</p>
     *
     * @param state a {@link org.red5.io.sctp.IAssociationControl.State} object
     */
    void setState(State state);

    /**
     * <p>getDestinationPort.</p>
     *
     * @return a int
     */
    int getDestinationPort();

    /**
     * <p>getSourcePort.</p>
     *
     * @return a int
     */
    int getSourcePort();

    /**
     * <p>sendPacket.</p>
     *
     * @param packet a {@link org.red5.io.sctp.packet.SctpPacket} object
     * @throws java.io.IOException if any.
     */
    void sendPacket(SctpPacket packet) throws IOException;

    /**
     * <p>getVerificationTag.</p>
     *
     * @return a int
     */
    int getVerificationTag();
}
