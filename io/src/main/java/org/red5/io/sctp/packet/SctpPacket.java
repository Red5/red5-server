/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License") + you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.red5.io.sctp.packet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import org.red5.io.sctp.IAssociationControl;
import org.red5.io.sctp.IServerChannelControl;
import org.red5.io.sctp.SctpException;
import org.red5.io.sctp.packet.chunks.Chunk;
import org.red5.io.sctp.packet.chunks.ChunkFactory;

/**
 * <p>SctpPacket class.</p>
 *
 * @author mondain
 */
public class SctpPacket {

    private SctpHeader header;

    private ArrayList<Chunk> chunks = new ArrayList<>();

    /**
     * <p>Constructor for SctpPacket.</p>
     *
     * @param data an array of {@link byte} objects
     * @param offset a int
     * @param length a int
     * @throws org.red5.io.sctp.SctpException if any.
     */
    public SctpPacket(final byte[] data, int offset, int length) throws SctpException {
        header = new SctpHeader(data, offset, length);
        Chunk chunk = null;
        length -= header.getSize();
        for (int i = header.getSize() + offset; length != 0; i += chunk.getSize()) {
            chunk = ChunkFactory.createChunk(data, i, length);
            chunks.add(chunk);
            length -= chunk.getSize();
        }
    }

    /**
     * <p>Constructor for SctpPacket.</p>
     *
     * @param sourcePort a int
     * @param destinationPort a int
     * @param verificationTag a int
     * @param chunk a {@link org.red5.io.sctp.packet.chunks.Chunk} object
     */
    public SctpPacket(int sourcePort, int destinationPort, int verificationTag, Chunk chunk) {
        header = new SctpHeader(sourcePort, destinationPort, verificationTag, 0);
        chunks.add(chunk);
    }

    /**
     * <p>apply.</p>
     *
     * @param address a {@link java.net.InetSocketAddress} object
     * @param server a {@link org.red5.io.sctp.IServerChannelControl} object
     * @throws org.red5.io.sctp.SctpException if any.
     * @throws java.io.IOException if any.
     * @throws java.security.InvalidKeyException if any.
     * @throws java.security.NoSuchAlgorithmException if any.
     */
    public void apply(InetSocketAddress address, IServerChannelControl server) throws SctpException, IOException, InvalidKeyException, NoSuchAlgorithmException {
        for (Chunk chunk : chunks) {
            chunk.apply(address, server);
        }
    }

    /**
     * <p>apply.</p>
     *
     * @param association a {@link org.red5.io.sctp.IAssociationControl} object
     * @throws org.red5.io.sctp.SctpException if any.
     * @throws java.io.IOException if any.
     * @throws java.security.InvalidKeyException if any.
     * @throws java.security.NoSuchAlgorithmException if any.
     */
    public void apply(IAssociationControl association) throws SctpException, IOException, InvalidKeyException, NoSuchAlgorithmException {
        for (Chunk chunk : chunks) {
            chunk.apply(association);
        }
    }

    /**
     * <p>getBytes.</p>
     *
     * @return an array of {@link byte} objects
     */
    public byte[] getBytes() {
        int resultSize = header.getSize();

        for (Chunk chunk : chunks) {
            resultSize += chunk.getSize();
        }

        byte[] result = new byte[resultSize];
        System.arraycopy(header.getBytes(), 0, result, 0, header.getSize());
        int previousChunkSize = header.getSize();
        for (Chunk chunk : chunks) {
            System.arraycopy(chunk.getBytes(), 0, result, previousChunkSize, chunk.getSize());
            previousChunkSize += chunk.getSize();
        }

        return result;
    }

    /**
     * <p>getSourcePort.</p>
     *
     * @return a int
     */
    public int getSourcePort() {
        return header.getSourcePort();
    }

    /**
     * <p>getVerificationTag.</p>
     *
     * @return a int
     */
    public int getVerificationTag() {
        return header.getVerificationTag();
    }
}
