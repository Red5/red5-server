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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import org.red5.io.sctp.IAssociationControl;
import org.red5.io.sctp.IServerChannelControl;
import org.red5.io.sctp.SctpException;

public class CookieAck extends Chunk {

    public CookieAck() {
        super(ChunkType.COOKIE_ACK, (byte) 0x00, (short) CHUNK_HEADER_SIZE);
    }

    @Override
    public void apply(IAssociationControl channel) throws SctpException, IOException, InvalidKeyException, NoSuchAlgorithmException {
        // TODO Auto-generated method stub

    }

    @Override
    public void apply(InetSocketAddress address, IServerChannelControl server) throws SctpException, InvalidKeyException, NoSuchAlgorithmException, IOException {
        // TODO Auto-generated method stub
    }

}
