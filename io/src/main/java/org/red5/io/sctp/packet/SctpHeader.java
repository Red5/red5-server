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
public final class SctpHeader {

    private int sourcePort;

    private int destinationPort;

    private int verificationTag;

    private int checksum;

    // sourcePort(2 byte) + destinationPort(2 byte) + verificationTag(4 byte) + checksum(4 byte)
    private static final int HEADER_SIZE = 12;

    public SctpHeader(int sourcePort, int destinationPort, int verificationTag, int checksum) {
        this.sourcePort = sourcePort;
        this.destinationPort = destinationPort;
        this.verificationTag = verificationTag;
        this.checksum = checksum;
    }

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

    public int getSize() {
        return HEADER_SIZE;
    }

    public byte[] getBytes() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(HEADER_SIZE);
        byteBuffer.putShort((short) getSourcePort());
        byteBuffer.putShort((short) getDestinationPort());
        byteBuffer.putInt(getVerificationTag());
        byteBuffer.putInt(checksum);

        return byteBuffer.array();
    }

    public int getSourcePort() {
        return sourcePort;
    }

    public int getDestinationPort() {
        return destinationPort;
    }

    public int getVerificationTag() {
        return verificationTag;
    }
}