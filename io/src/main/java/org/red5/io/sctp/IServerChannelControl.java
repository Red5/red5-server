/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License") + you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.red5.io.sctp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Random;

import javax.crypto.Mac;

import org.red5.io.sctp.packet.SctpPacket;

/**
 * <p>IServerChannelControl interface.</p>
 *
 * @author mondain
 */
public interface IServerChannelControl {
    /**
     * <p>removePendingChannel.</p>
     *
     * @param address a {@link java.net.InetSocketAddress} object
     */
    void removePendingChannel(InetSocketAddress address);

    /**
     * <p>addPendingChannel.</p>
     *
     * @param address a {@link java.net.InetSocketAddress} object
     * @param initialTSN a int
     * @param verificationTag a int
     * @return a boolean
     * @throws java.net.SocketException if any.
     */
    boolean addPendingChannel(InetSocketAddress address, int initialTSN, int verificationTag) throws SocketException;

    /**
     * <p>getPendingChannel.</p>
     *
     * @param address a {@link java.net.InetSocketAddress} object
     * @return a {@link org.red5.io.sctp.IAssociationControl} object
     */
    IAssociationControl getPendingChannel(InetSocketAddress address);

    /**
     * <p>getMac.</p>
     *
     * @return a {@link javax.crypto.Mac} object
     */
    Mac getMac();

    /**
     * <p>getRandom.</p>
     *
     * @return a {@link java.util.Random} object
     */
    Random getRandom();

    /**
     * <p>getPort.</p>
     *
     * @return a int
     */
    int getPort();

    /**
     * <p>send.</p>
     *
     * @param packet a {@link org.red5.io.sctp.packet.SctpPacket} object
     * @param address a {@link java.net.InetSocketAddress} object
     * @throws java.io.IOException if any.
     */
    void send(SctpPacket packet, InetSocketAddress address) throws IOException;
}
