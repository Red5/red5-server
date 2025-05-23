/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License") + you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.red5.io.sctp.packet;

import java.nio.ByteBuffer;

import org.red5.io.sctp.SctpException;

/*
 * see https://tools.ietf.org/html/rfc4960#section-3.1
 */
/**
 * <p>SctpHeader class.</p>
 *
 * @author mondain
 */
public final class SctpHeader {

    private int sourcePort;

    private int destinationPort;

    private int verificationTag;

    private int checksum;

    // sourcePort(2 byte) + destinationPort(2 byte) + verificationTag(4 byte) + checksum(4 byte)
    private static final int HEADER_SIZE = 12;

    /**
     * <p>Constructor for SctpHeader.</p>
     *
     * @param sourcePort a int
     * @param destinationPort a int
     * @param verificationTag a int
     * @param checksum a int
     */
    public SctpHeader(int sourcePort, int destinationPort, int verificationTag, int checksum) {
        this.sourcePort = sourcePort;
        this.destinationPort = destinationPort;
        this.verificationTag = verificationTag;
        this.checksum = checksum;
    }

    /**
     * <p>Constructor for SctpHeader.</p>
     *
     * @param data an array of {@link byte} objects
     * @param offset a int
     * @param length a int
     * @throws org.red5.io.sctp.SctpException if any.
     */
    public SctpHeader(final byte[] data, int offset, int length) throws SctpException {
        if (length < HEADER_SIZE) {
            throw new SctpException("not enough data for parsing Sctp header : " + data);
        }
        ByteBuffer byteBuffer = ByteBuffer.wrap(data, offset, HEADER_SIZE);
        sourcePort = byteBuffer.getShort() & 0xffff;
        destinationPort = byteBuffer.getShort() & 0xffff;
        verificationTag = byteBuffer.getInt();
        checksum = byteBuffer.getInt();
    }

    /**
     * <p>getSize.</p>
     *
     * @return a int
     */
    public int getSize() {
        return HEADER_SIZE;
    }

    /**
     * <p>getBytes.</p>
     *
     * @return an array of {@link byte} objects
     */
    public byte[] getBytes() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(HEADER_SIZE);
        byteBuffer.putShort((short) getSourcePort());
        byteBuffer.putShort((short) getDestinationPort());
        byteBuffer.putInt(getVerificationTag());
        byteBuffer.putInt(checksum);

        return byteBuffer.array();
    }

    /**
     * <p>Getter for the field <code>sourcePort</code>.</p>
     *
     * @return a int
     */
    public int getSourcePort() {
        return sourcePort;
    }

    /**
     * <p>Getter for the field <code>destinationPort</code>.</p>
     *
     * @return a int
     */
    public int getDestinationPort() {
        return destinationPort;
    }

    /**
     * <p>Getter for the field <code>verificationTag</code>.</p>
     *
     * @return a int
     */
    public int getVerificationTag() {
        return verificationTag;
    }
}
