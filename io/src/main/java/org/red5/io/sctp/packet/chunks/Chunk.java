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

/*
 * see https://tools.ietf.org/html/rfc4960#section-3.2
 */
/**
 * <p>Abstract Chunk class.</p>
 *
 * @author mondain
 */
public abstract class Chunk {

    // type(1 byte) + flags(1 byte) + length(2 byte)
    /** Constant <code>CHUNK_HEADER_SIZE=4</code> */
    protected static final int CHUNK_HEADER_SIZE = 4;

    private ChunkType type;

    private byte flags;

    @SuppressWarnings("unused")
    private int length;

    /**
     * <p>Constructor for Chunk.</p>
     *
     * @param data an array of {@link byte} objects
     * @param offset a int
     * @param length a int
     * @throws org.red5.io.sctp.SctpException if any.
     */
    public Chunk(byte[] data, int offset, int length) throws SctpException {
        // parse common header
        if (length < CHUNK_HEADER_SIZE) {
            throw new SctpException("not enough data for parse chunk common header " + data);
        }
        ByteBuffer byteBuffer = ByteBuffer.wrap(data, offset, CHUNK_HEADER_SIZE);
        type = ChunkType.values()[byteBuffer.get()];
        flags = byteBuffer.get();
        this.length = byteBuffer.getShort() & 0xffff;
    }

    /**
     * <p>Constructor for Chunk.</p>
     *
     * @param type a {@link org.red5.io.sctp.packet.chunks.ChunkType} object
     * @param flags a byte
     * @param length a short
     */
    public Chunk(final ChunkType type, final byte flags, final short length) {
        this.type = type;
        this.flags = flags;
        this.length = length;
    }

    /**
     * <p>Constructor for Chunk.</p>
     *
     * @param type a {@link org.red5.io.sctp.packet.chunks.ChunkType} object
     * @param flags a byte
     */
    public Chunk(final ChunkType type, final byte flags) {
        this.type = type;
        this.flags = flags;
    }

    /**
     * <p>apply.</p>
     *
     * @param channel a {@link org.red5.io.sctp.IAssociationControl} object
     * @throws org.red5.io.sctp.SctpException if any.
     * @throws java.io.IOException if any.
     * @throws java.security.InvalidKeyException if any.
     * @throws java.security.NoSuchAlgorithmException if any.
     */
    public abstract void apply(IAssociationControl channel) throws SctpException, IOException, InvalidKeyException, NoSuchAlgorithmException;

    /**
     * <p>apply.</p>
     *
     * @param address a {@link java.net.InetSocketAddress} object
     * @param server a {@link org.red5.io.sctp.IServerChannelControl} object
     * @throws org.red5.io.sctp.SctpException if any.
     * @throws java.security.InvalidKeyException if any.
     * @throws java.security.NoSuchAlgorithmException if any.
     * @throws java.io.IOException if any.
     */
    public abstract void apply(InetSocketAddress address, IServerChannelControl server) throws SctpException, InvalidKeyException, NoSuchAlgorithmException, IOException;

    /**
     * <p>getSize.</p>
     *
     * @return a int
     */
    public int getSize() {
        return CHUNK_HEADER_SIZE;
    }

    /**
     * <p>getBytes.</p>
     *
     * @return an array of {@link byte} objects
     */
    public byte[] getBytes() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(CHUNK_HEADER_SIZE);
        byteBuffer.put((byte) type.getValue());
        byteBuffer.put(flags);
        byteBuffer.putShort((short) (getSize() & 0xffff));

        byteBuffer.clear();
        byte[] result = new byte[byteBuffer.capacity()];
        byteBuffer.get(result, 0, result.length);
        return result;
    }

    /**
     * <p>Setter for the field <code>length</code>.</p>
     *
     * @param length a int
     */
    protected void setLength(int length) {
        this.length = length;
    }
}
