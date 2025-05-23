/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License") + you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.red5.io.sctp.packet.chunks;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import org.red5.io.sctp.IAssociationControl;
import org.red5.io.sctp.IServerChannelControl;
import org.red5.io.sctp.SctpException;
import org.red5.io.sctp.packet.SctpPacket;

/**
 * <p>Init class.</p>
 *
 * @author mondain
 */
public class Init extends Chunk {

    // initiateTag(4 byte) + advertisedReceiverWindowCredit(4 byte) + numberOfOutboundStreams(2 byte) + numberOfInboundStreams(2 byte) + TSN(4 byte)
    private static final int MANDATORY_FIELD_SIZE = 16;

    private int initiateTag;

    private int advertisedReceiverWindowCredit;

    private int numberOfOutboundStreams;

    private int numberOfInboundStreams;

    private int initialTSN;

    /**
     * <p>Constructor for Init.</p>
     *
     * @param initialTSN a int
     * @param initiateTag a int
     */
    public Init(int initialTSN, int initiateTag) {
        super(ChunkType.INIT, (byte) 0x00);
        super.setLength(getSize());
        this.initialTSN = initialTSN;
        this.initiateTag = initiateTag;
        this.advertisedReceiverWindowCredit = IAssociationControl.DEFAULT_ADVERTISE_RECEIVE_WINDOW_CREDIT;
        this.numberOfInboundStreams = IAssociationControl.DEFAULT_NUMBER_OF_INBOUND_STREAM;
        this.numberOfOutboundStreams = IAssociationControl.DEFAULT_NUMBER_OF_OUTBOUND_STREAM;
    }

    /**
     * <p>Constructor for Init.</p>
     *
     * @param data an array of {@link byte} objects
     * @param offset a int
     * @param length a int
     * @throws org.red5.io.sctp.SctpException if any.
     */
    public Init(final byte[] data, int offset, int length) throws SctpException {
        super(data, offset, length);
        ByteBuffer byteBuffer = ByteBuffer.wrap(data, offset + CHUNK_HEADER_SIZE, data.length - (offset + CHUNK_HEADER_SIZE));
        setLength((short) (MANDATORY_FIELD_SIZE + CHUNK_HEADER_SIZE));
        initiateTag = byteBuffer.getInt();
        advertisedReceiverWindowCredit = byteBuffer.getInt();
        numberOfOutboundStreams = byteBuffer.getShort();
        numberOfInboundStreams = byteBuffer.getShort();
        initialTSN = byteBuffer.getInt();
    }

    /** {@inheritDoc} */
    @Override
    public byte[] getBytes() {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(MANDATORY_FIELD_SIZE + CHUNK_HEADER_SIZE);
        byte[] data = super.getBytes();
        byteBuffer.put(data);
        byteBuffer.putInt(initiateTag);
        byteBuffer.putInt(advertisedReceiverWindowCredit);
        byteBuffer.putShort((short) numberOfOutboundStreams);
        byteBuffer.putShort((short) numberOfInboundStreams);
        byteBuffer.putInt(initialTSN);

        byteBuffer.clear();
        byte[] result = new byte[byteBuffer.capacity()];
        byteBuffer.get(result, 0, result.length);
        return result;
    }

    /**
     * <p>Getter for the field <code>initiateTag</code>.</p>
     *
     * @return a int
     */
    public int getInitiateTag() {
        return initiateTag;
    }

    /**
     * <p>Getter for the field <code>advertisedReceiverWindowCredit</code>.</p>
     *
     * @return a int
     */
    public int getAdvertisedReceiverWindowCredit() {
        return advertisedReceiverWindowCredit;
    }

    /**
     * <p>Getter for the field <code>numberOfOutboundStreams</code>.</p>
     *
     * @return a int
     */
    public int getNumberOfOutboundStreams() {
        return numberOfOutboundStreams;
    }

    /**
     * <p>Getter for the field <code>numberOfInboundStreams</code>.</p>
     *
     * @return a int
     */
    public int getNumberOfInboundStreams() {
        return numberOfInboundStreams;
    }

    /**
     * <p>Getter for the field <code>initialTSN</code>.</p>
     *
     * @return a int
     */
    public int getInitialTSN() {
        return initialTSN;
    }

    /** {@inheritDoc} */
    @Override
    public int getSize() {
        return MANDATORY_FIELD_SIZE + super.getSize();
    }

    /** {@inheritDoc} */
    @Override
    public void apply(InetSocketAddress address, IServerChannelControl server) throws SctpException, InvalidKeyException, NoSuchAlgorithmException, IOException {
        IAssociationControl association = server.getPendingChannel(address);
        if (association != null) {
            throw new SctpException("init chunk : association already exist in pending pool");
        }

        // 1. generate state cookie & initAck chunk
        StateCookie stateCookie = new StateCookie(getInitiateTag(), getInitialTSN(), getAdvertisedReceiverWindowCredit(), getNumberOfOutboundStreams(), getNumberOfInboundStreams());
        int verificationTag = server.getRandom().nextInt();
        int initialTSN = server.getRandom().nextInt();
        Chunk initAck = new InitAck(verificationTag, initialTSN, stateCookie, server.getMac());

        // 2. pack and send packet with initAck inside
        SctpPacket packet = new SctpPacket(server.getPort(), address.getPort(), getInitiateTag(), initAck);
        server.send(packet, address);
    }

    /** {@inheritDoc} */
    @Override
    public void apply(IAssociationControl association) throws SctpException {
        throw new SctpException("init chunk : association already exist in pending pool");
    }
}
