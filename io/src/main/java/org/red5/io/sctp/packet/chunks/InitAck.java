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

import javax.crypto.Mac;

import org.red5.io.sctp.IAssociationControl;
import org.red5.io.sctp.IServerChannelControl;
import org.red5.io.sctp.SctpException;
import org.red5.io.sctp.IAssociationControl.State;
import org.red5.io.sctp.packet.SctpPacket;

public final class InitAck extends Chunk {

    private static final int MANDATORY_FIELD_SIZE = 16;

    private int initiateTag;

    private int advertisedReceiverWindowCredit;

    private int numberOfOutboundStreams;

    private int numberOfInboundStreams;

    private int initialTSN;

    StateCookie stateCookie;

    byte[] stateCookieBytes;

    public InitAck(final byte[] data, int offset, int length) throws SctpException {
        super(data, offset, length);
        assert length - offset - CHUNK_HEADER_SIZE > MANDATORY_FIELD_SIZE;
        ByteBuffer byteBuffer = ByteBuffer.wrap(data, offset + CHUNK_HEADER_SIZE, MANDATORY_FIELD_SIZE);
        initiateTag = byteBuffer.getInt();
        advertisedReceiverWindowCredit = byteBuffer.getInt();
        numberOfOutboundStreams = byteBuffer.getShort() & 0xffff;
        numberOfInboundStreams = byteBuffer.getShort() & 0xffff;
        initialTSN = byteBuffer.getInt();
        stateCookie = new StateCookie(data, offset + MANDATORY_FIELD_SIZE + CHUNK_HEADER_SIZE, length - (MANDATORY_FIELD_SIZE + CHUNK_HEADER_SIZE));
    }

    public InitAck(int initiateTag, int initialTSN, StateCookie stateCookie, Mac mac) throws InvalidKeyException, NoSuchAlgorithmException {
        super(ChunkType.INIT_ACK, (byte) 0x00);
        this.initiateTag = initiateTag;
        this.initialTSN = initialTSN;
        this.stateCookie = stateCookie;
        stateCookieBytes = stateCookie.getBytes(mac);
        this.numberOfInboundStreams = IAssociationControl.DEFAULT_NUMBER_OF_INBOUND_STREAM;
        this.numberOfOutboundStreams = IAssociationControl.DEFAULT_NUMBER_OF_OUTBOUND_STREAM;
        this.advertisedReceiverWindowCredit = IAssociationControl.DEFAULT_ADVERTISE_RECEIVE_WINDOW_CREDIT;
        super.setLength(MANDATORY_FIELD_SIZE + stateCookieBytes.length + super.getSize());
    }

    @Override
    public byte[] getBytes() {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(MANDATORY_FIELD_SIZE + super.getSize() + stateCookieBytes.length);
        byte[] data = super.getBytes();
        byteBuffer.put(data);
        byteBuffer.putInt(initiateTag);
        byteBuffer.putInt(advertisedReceiverWindowCredit);
        byteBuffer.putShort((short) numberOfOutboundStreams);
        byteBuffer.putShort((short) numberOfInboundStreams);
        byteBuffer.putInt(initialTSN);
        byteBuffer.put(stateCookieBytes);

        byteBuffer.clear();
        byte[] result = new byte[byteBuffer.capacity()];
        byteBuffer.get(result, 0, result.length);
        return result;
    }

    @Override
    public int getSize() {
        return MANDATORY_FIELD_SIZE + stateCookie.getSize() + super.getSize();
    }

    @Override
    public void apply(IAssociationControl channel) throws IOException, InvalidKeyException, NoSuchAlgorithmException, SctpException {
        if (channel.getState() != State.COOKIE_WAIT) {
            throw new SctpException("init ack chunk : wrong state " + channel.getState());
        }

        // send cookie echo
        CookieEcho cookieEcho = new CookieEcho(stateCookie.getBytes(null));
        SctpPacket packet = new SctpPacket((short) channel.getSourcePort(), (short) channel.getDestinationPort(), 0, cookieEcho);
        channel.sendPacket(packet);
        channel.setState(State.COOKIE_ECHOED);
    }

    @Override
    public void apply(InetSocketAddress address, IServerChannelControl server) throws SctpException {
        throw new SctpException("init ack chunk : not using with server");
    }

    public int getInitiateTag() {
        return initiateTag;
    }

    public int getAdvertisedReceiverWindowCredit() {
        return advertisedReceiverWindowCredit;
    }

    public int getNumberOfOutboundStreams() {
        return numberOfOutboundStreams;
    }

    public int getNumberOfInboundStreams() {
        return numberOfInboundStreams;
    }

    public int getInitialTSN() {
        return initialTSN;
    }
}
