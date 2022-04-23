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

public interface IAssociationControl {

    public static enum State {
        CLOSED, COOKIE_WAIT, COOKIE_ECHOED, ESTABLISHED
    }

    static final int VALID_COOKIE_TIME = 60; // in seconds

    static final int DEFAULT_ADVERTISE_RECEIVE_WINDOW_CREDIT = 1024;

    static final int DEFAULT_NUMBER_OF_OUTBOUND_STREAM = 1;

    static final int DEFAULT_NUMBER_OF_INBOUND_STREAM = 1;

    State getState();

    void setState(State state);

    int getDestinationPort();

    int getSourcePort();

    void sendPacket(SctpPacket packet) throws IOException;

    int getVerificationTag();
}
