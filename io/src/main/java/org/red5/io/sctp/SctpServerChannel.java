/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License") + you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.red5.io.sctp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.SelectorProvider;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

public abstract class SctpServerChannel extends AbstractSelectableChannel {

    protected SctpServerChannel(SelectorProvider provider) {
        super(provider);
    }

    public abstract SctpChannel accept() throws IOException, SctpException, InvalidKeyException, NoSuchAlgorithmException;

    public abstract SctpServerChannel bind(SocketAddress local, int backlog) throws IOException;

    public abstract SctpServerChannel bindAddress(InetAddress address) throws IOException;

    public abstract Set<SocketAddress> getAllLocalAddresses() throws IOException;

    public abstract SctpServerChannel unbindAddress(InetAddress address) throws IOException;

    public abstract <T> SctpServerChannel setOption(SctpSocketOption<T> name, T value) throws IOException;

    public abstract Set<SctpSocketOption<?>> supportedOptions();

    public static SctpServerChannel open() throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        return new SctpServerChanneOverUDP((SelectorProvider) null);
    }

    public final SctpServerChannel bind(SocketAddress local) throws IOException {
        return bind(local, 0);
    }

    @Override
    public int validOps() {
        return SelectionKey.OP_ACCEPT;
    }

}
