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
import org.red5.io.sctp.IAssociationControl.State;
import org.red5.io.sctp.packet.SctpPacket;

public class CookieEcho extends Chunk {

    private byte[] cookie;

    public CookieEcho(byte[] data, int offset, int length) throws SctpException {
        super(data, offset, length);
        cookie = new byte[length - super.getSize()];
        System.arraycopy(data, offset, cookie, 0, cookie.length);
    }

    public CookieEcho(byte[] cookie) {
        super(ChunkType.COOKIE_ECHO, (byte) 0x00);
        this.cookie = cookie;
    }

    @Override
    public byte[] getBytes() {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(super.getSize() + cookie.length);
        byte[] data = super.getBytes();
        byteBuffer.put(data);
        byteBuffer.put(cookie);

        byteBuffer.clear();
        byte[] result = new byte[byteBuffer.capacity()];
        byteBuffer.get(result, 0, result.length);
        return result;
    }

    @Override
    public void apply(IAssociationControl channel) throws SctpException, IOException {
    }

    @Override
    public void apply(InetSocketAddress address, IServerChannelControl server) throws SctpException, InvalidKeyException, NoSuchAlgorithmException, IOException {
        // validate state cookie info
        StateCookie stateCookie = new StateCookie(cookie, 0, cookie.length);
        if (stateCookie.isValid(server.getMac())) {
            // create channel & send cookie ack
            server.addPendingChannel(address, stateCookie.getInitialTSN(), stateCookie.getVerificationTag());
            IAssociationControl channel = server.getPendingChannel(address);
            CookieAck cookieAck = new CookieAck();
            @SuppressWarnings("unused")
            SctpPacket packet = new SctpPacket((short) server.getPort(), (short) address.getPort(), stateCookie.getVerificationTag(), cookieAck);
            channel.setState(State.ESTABLISHED);
        }
    }

}
