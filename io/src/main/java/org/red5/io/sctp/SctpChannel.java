/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License") + you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.red5.io.sctp;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * <p>SctpChannel class.</p>
 *
 * @author mondain
 */
public class SctpChannel {

    private Association association;

    /**
     * <p>Constructor for SctpChannel.</p>
     *
     * @param association a {@link org.red5.io.sctp.Association} object
     */
    public SctpChannel(Association association) {
        this.association = association;
    }

    /**
     * <p>bind.</p>
     *
     * @param address a {@link java.net.InetSocketAddress} object
     * @return a {@link org.red5.io.sctp.SctpChannel} object
     * @throws java.net.SocketException if any.
     */
    public SctpChannel bind(InetSocketAddress address) throws SocketException {
        association.setSource(new DatagramSocket(address));
        return this;
    }

    /**
     * <p>connect.</p>
     *
     * @param address a {@link java.net.InetSocketAddress} object
     * @return a boolean
     * @throws java.io.IOException if any.
     * @throws org.red5.io.sctp.SctpException if any.
     * @throws java.security.InvalidKeyException if any.
     * @throws java.security.NoSuchAlgorithmException if any.
     */
    public boolean connect(InetSocketAddress address) throws IOException, SctpException, InvalidKeyException, NoSuchAlgorithmException {
        return association.setUp(address);
    }

    /**
     * <p>send.</p>
     *
     * @param data an array of {@link byte} objects
     * @param offset a int
     * @param length a int
     */
    public void send(byte[] data, int offset, int length) {
        // TODO
    }

    /**
     * <p>receive.</p>
     *
     * @return an array of {@link byte} objects
     */
    public byte[] receive() {
        // TODO
        return null;
    }

    /**
     * <p>open.</p>
     *
     * @return a {@link org.red5.io.sctp.SctpChannel} object
     * @throws java.net.SocketException if any.
     */
    public static SctpChannel open() throws SocketException {
        return new SctpChannel(new Association(new Random(), null));
    }

}
