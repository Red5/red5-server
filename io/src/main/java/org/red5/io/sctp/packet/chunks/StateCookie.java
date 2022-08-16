/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License") + you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.red5.io.sctp.packet.chunks;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;

public class StateCookie {

    // layout:
    // 	mac_length : int
    //	mac : byte[mac_length]
    //	creationTimestamp : Long
    //	lifespan : short

    //	[client information]
    //	verificationTag : int
    //	advertisedReceiverWindowCredit : int
    //	numberOfOutboundStreams : short
    //	numberOfInboundStreams : short
    //	initialTSN : int

    // creationTimestamp(8 bytes)
    // + lifespan(2 bytes)
    // + verificationTag(4 bytes)
    // + advertisedReceiverWindowCredit(4 bytes)
    // + numberOfOutboundStreams(2 bytes)
    // + numberOfInboundStreams(2 bytes)
    // + initialTSN(4 bytes)
    private static final int HEADER_STATE_COOKIE_SIZE = 26;

    private static final short LIFESPAN = 60; // in seconds

    private long creationTimestamp = System.currentTimeMillis() / 1000L; // in seconds

    private short currentLifespan;

    private int verificationTag;

    private int advertisedReceiverWindowCredit;

    private int numberOfOutboundStreams;

    private int numberOfInboundStreams;

    private int initialTSN;

    private byte[] mac;

    public StateCookie(int verificationTag, int initialTSN, int advertisedReceiverWindowCredit, int numberOfOutboundStreams, int numberOfInboundStreams) {
        this.verificationTag = verificationTag;
        this.advertisedReceiverWindowCredit = advertisedReceiverWindowCredit;
        this.numberOfOutboundStreams = numberOfOutboundStreams;
        this.numberOfInboundStreams = numberOfInboundStreams;
        this.initialTSN = initialTSN;
        currentLifespan = LIFESPAN;
    }

    public StateCookie(byte[] data, int offset, int length) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(data, offset, 4);
        int macLength = byteBuffer.getInt();

        mac = new byte[macLength];
        System.arraycopy(data, offset + 4, mac, 0, macLength);

        byteBuffer = ByteBuffer.wrap(data, offset + 4 + macLength, HEADER_STATE_COOKIE_SIZE);
        creationTimestamp = byteBuffer.getLong();
        currentLifespan = byteBuffer.getShort();
        verificationTag = byteBuffer.getInt();
        advertisedReceiverWindowCredit = byteBuffer.getInt();
        numberOfOutboundStreams = byteBuffer.getShort();
        numberOfInboundStreams = byteBuffer.getShort();
        initialTSN = byteBuffer.getInt();
    }

    public byte[] getBytes(Mac messageAuthenticationCode) throws InvalidKeyException, NoSuchAlgorithmException {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(HEADER_STATE_COOKIE_SIZE);
        byteBuffer.putLong(creationTimestamp);
        byteBuffer.putShort(currentLifespan);
        byteBuffer.putInt(verificationTag);
        byteBuffer.putInt(advertisedReceiverWindowCredit);
        byteBuffer.putShort((short) numberOfOutboundStreams);
        byteBuffer.putShort((short) numberOfInboundStreams);
        byteBuffer.putInt(initialTSN);

        byteBuffer.clear();
        byte[] data = new byte[byteBuffer.capacity()];
        byteBuffer.get(data, 0, data.length);

        if (messageAuthenticationCode != null) {
            mac = messageAuthenticationCode.doFinal(data);
        }

        byte[] macLength = ByteBuffer.allocate(4).putInt(mac.length).array(); // 4 for int length
        byte[] resultData = new byte[mac.length + data.length + macLength.length];
        System.arraycopy(macLength, 0, resultData, 0, macLength.length);
        System.arraycopy(mac, 0, resultData, macLength.length, mac.length);
        System.arraycopy(data, 0, resultData, macLength.length + mac.length, data.length);

        return resultData;
    }

    public boolean isValid(Mac mac) {
        return true;
    }

    public int getVerificationTag() {
        return verificationTag;
    }

    public int getInitialTSN() {
        return initialTSN;
    }

    public byte[] getMac() {
        return mac;
    }

    public short getCurrentLifespan() {
        return currentLifespan;
    }

    public int getSize() {
        return HEADER_STATE_COOKIE_SIZE + mac.length + 4; // 4 for mac length
    }
}
