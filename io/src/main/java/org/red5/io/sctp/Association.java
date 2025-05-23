/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License") + you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.red5.io.sctp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.Random;

import org.red5.io.sctp.packet.SctpPacket;
import org.red5.io.sctp.packet.chunks.Init;

/**
 * <p>Association class.</p>
 *
 * @author mondain
 */
public class Association implements IAssociationControl {

    @SuppressWarnings("unused")
    private Timestamp creationTimestamp;

    private int verificationTagSource;

    private int verificationTagDestination;

    private int initialTSNSource;

    @SuppressWarnings("unused")
    private int initialTSNDestination;

    private State state;

    private DatagramSocket source;

    private InetSocketAddress destination;

    private Random random;

    /**
     * <p>Constructor for Association.</p>
     *
     * @param random a {@link java.util.Random} object
     * @param sourceAddress a {@link java.net.InetSocketAddress} object
     * @param initialTSN a int
     * @param verificationTag a int
     * @throws java.net.SocketException if any.
     */
    public Association(final Random random, InetSocketAddress sourceAddress, int initialTSN, int verificationTag) throws SocketException {
        this.random = random;
        setState(State.CLOSED);
        setVerificationTagItself(random.nextInt());
        source = new DatagramSocket(sourceAddress);
        creationTimestamp = new Timestamp(System.currentTimeMillis());
    }

    /**
     * <p>Constructor for Association.</p>
     *
     * @param random a {@link java.util.Random} object
     * @param sourceAddress a {@link java.net.InetSocketAddress} object
     * @throws java.net.SocketException if any.
     */
    public Association(final Random random, InetSocketAddress sourceAddress) throws SocketException {
        this.random = random;
        setState(State.CLOSED);
        setVerificationTagItself(random.nextInt());
        source = new DatagramSocket(sourceAddress);
        creationTimestamp = new Timestamp(System.currentTimeMillis());
    }

    /**
     * <p>setUp.</p>
     *
     * @param address a {@link java.net.InetSocketAddress} object
     * @return a boolean
     * @throws java.io.IOException if any.
     * @throws org.red5.io.sctp.SctpException if any.
     * @throws java.security.InvalidKeyException if any.
     * @throws java.security.NoSuchAlgorithmException if any.
     */
    public boolean setUp(InetSocketAddress address) throws IOException, SctpException, InvalidKeyException, NoSuchAlgorithmException {
        destination = address;

        // initialize association and send INIT
        initialTSNSource = random.nextInt();
        Init initChunk = new Init(verificationTagSource, initialTSNSource);
        SctpPacket packet = new SctpPacket((short) source.getLocalPort(), (short) destination.getPort(), 0, initChunk);
        byte[] data = packet.getBytes();
        source.send(new DatagramPacket(data, data.length, destination));
        state = State.COOKIE_WAIT;

        // wait & receive INIT_ACK
        byte[] buffer = new byte[1024];
        DatagramPacket udpPacket = new DatagramPacket(buffer, buffer.length);
        source.receive(udpPacket);
        try {
            packet = new SctpPacket(buffer, 0, udpPacket.getLength());
        } catch (SctpException e) {
            e.printStackTrace();
        }

        // handle INIT ACK packet - send COOKIE ECHO
        packet.apply(this);

        // handle COOKIE ACK
        source.receive(udpPacket);
        try {
            packet = new SctpPacket(buffer, 0, udpPacket.getLength());
        } catch (SctpException e) {
            e.printStackTrace();
        }
        packet.apply(this);

        return state == State.ESTABLISHED;
    }

    /** {@inheritDoc} */
    @Override
    public State getState() {
        return state;
    }

    /**
     * <p>Setter for the field <code>source</code>.</p>
     *
     * @param source a {@link java.net.DatagramSocket} object
     */
    public void setSource(DatagramSocket source) {
        this.source = source;
    }

    /** {@inheritDoc} */
    @Override
    public int getVerificationTag() {
        return verificationTagDestination;
    }

    /**
     * <p>getVerificationTagItself.</p>
     *
     * @return a int
     */
    public int getVerificationTagItself() {
        return verificationTagSource;
    }

    /**
     * <p>setVerificationTagItself.</p>
     *
     * @param verificationTagItself a int
     */
    public void setVerificationTagItself(int verificationTagItself) {
        this.verificationTagSource = verificationTagItself;
    }

    /** {@inheritDoc} */
    @Override
    public void setState(State state) {
        this.state = state;
    }

    /** {@inheritDoc} */
    @Override
    public void sendPacket(SctpPacket packet) throws IOException {
        byte[] data = packet.getBytes();
        source.send(new DatagramPacket(data, data.length));
    }

    /** {@inheritDoc} */
    @Override
    public int getDestinationPort() {
        return destination.getPort();
    }

    /** {@inheritDoc} */
    @Override
    public int getSourcePort() {
        return source.getLocalPort();
    }
}
