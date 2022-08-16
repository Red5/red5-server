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
public abstract class Chunk {

    // type(1 byte) + flags(1 byte) + length(2 byte)
    protected static final int CHUNK_HEADER_SIZE = 4;

    private ChunkType type;

    private byte flags;

    @SuppressWarnings("unused")
    private int length;

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

    public Chunk(final ChunkType type, final byte flags, final short length) {
        this.type = type;
        this.flags = flags;
        this.length = length;
    }

    public Chunk(final ChunkType type, final byte flags) {
        this.type = type;
        this.flags = flags;
    }

    public abstract void apply(IAssociationControl channel) throws SctpException, IOException, InvalidKeyException, NoSuchAlgorithmException;

    public abstract void apply(InetSocketAddress address, IServerChannelControl server) throws SctpException, InvalidKeyException, NoSuchAlgorithmException, IOException;

    public int getSize() {
        return CHUNK_HEADER_SIZE;
    }

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

    protected void setLength(int length) {
        this.length = length;
    }
}
