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

/**
 * <p>Abstract SctpServerChannel class.</p>
 *
 * @author mondain
 */
public abstract class SctpServerChannel extends AbstractSelectableChannel {

    /**
     * <p>Constructor for SctpServerChannel.</p>
     *
     * @param provider a {@link java.nio.channels.spi.SelectorProvider} object
     */
    protected SctpServerChannel(SelectorProvider provider) {
        super(provider);
    }

    /**
     * <p>accept.</p>
     *
     * @return a {@link org.red5.io.sctp.SctpChannel} object
     * @throws java.io.IOException if any.
     * @throws org.red5.io.sctp.SctpException if any.
     * @throws java.security.InvalidKeyException if any.
     * @throws java.security.NoSuchAlgorithmException if any.
     */
    public abstract SctpChannel accept() throws IOException, SctpException, InvalidKeyException, NoSuchAlgorithmException;

    /**
     * <p>bind.</p>
     *
     * @param local a {@link java.net.SocketAddress} object
     * @param backlog a int
     * @return a {@link org.red5.io.sctp.SctpServerChannel} object
     * @throws java.io.IOException if any.
     */
    public abstract SctpServerChannel bind(SocketAddress local, int backlog) throws IOException;

    /**
     * <p>bindAddress.</p>
     *
     * @param address a {@link java.net.InetAddress} object
     * @return a {@link org.red5.io.sctp.SctpServerChannel} object
     * @throws java.io.IOException if any.
     */
    public abstract SctpServerChannel bindAddress(InetAddress address) throws IOException;

    /**
     * <p>getAllLocalAddresses.</p>
     *
     * @return a {@link java.util.Set} object
     * @throws java.io.IOException if any.
     */
    public abstract Set<SocketAddress> getAllLocalAddresses() throws IOException;

    /**
     * <p>unbindAddress.</p>
     *
     * @param address a {@link java.net.InetAddress} object
     * @return a {@link org.red5.io.sctp.SctpServerChannel} object
     * @throws java.io.IOException if any.
     */
    public abstract SctpServerChannel unbindAddress(InetAddress address) throws IOException;

    /**
     * <p>setOption.</p>
     *
     * @param name a {@link org.red5.io.sctp.SctpSocketOption} object
     * @param value a T object
     * @param <T> a T class
     * @return a {@link org.red5.io.sctp.SctpServerChannel} object
     * @throws java.io.IOException if any.
     */
    public abstract <T> SctpServerChannel setOption(SctpSocketOption<T> name, T value) throws IOException;

    /**
     * <p>supportedOptions.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public abstract Set<SctpSocketOption<?>> supportedOptions();

    /**
     * <p>open.</p>
     *
     * @return a {@link org.red5.io.sctp.SctpServerChannel} object
     * @throws java.io.IOException if any.
     * @throws java.security.InvalidKeyException if any.
     * @throws java.security.NoSuchAlgorithmException if any.
     */
    public static SctpServerChannel open() throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        return new SctpServerChanneOverUDP((SelectorProvider) null);
    }

    /**
     * <p>bind.</p>
     *
     * @param local a {@link java.net.SocketAddress} object
     * @return a {@link org.red5.io.sctp.SctpServerChannel} object
     * @throws java.io.IOException if any.
     */
    public final SctpServerChannel bind(SocketAddress local) throws IOException {
        return bind(local, 0);
    }

    /** {@inheritDoc} */
    @Override
    public int validOps() {
        return SelectionKey.OP_ACCEPT;
    }

}
