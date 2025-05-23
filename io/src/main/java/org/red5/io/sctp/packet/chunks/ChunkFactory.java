/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License") + you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.red5.io.sctp.packet.chunks;

import org.red5.io.sctp.SctpException;

/**
 * <p>ChunkFactory class.</p>
 *
 * @author mondain
 */
public class ChunkFactory {
    /**
     * <p>createChunk.</p>
     *
     * @param data an array of {@link byte} objects
     * @param offset a int
     * @param length a int
     * @return a {@link org.red5.io.sctp.packet.chunks.Chunk} object
     * @throws org.red5.io.sctp.SctpException if any.
     */
    public static Chunk createChunk(final byte[] data, int offset, int length) throws SctpException {
        assert length > 0;
        switch (ChunkType.values()[data[offset]]) {
            case INIT:
                return new Init(data, offset, length);
            case INIT_ACK:
                return new InitAck(data, offset, length);
            default:
                throw new SctpException("not supported chunk type " + data);
        }
    }
}
