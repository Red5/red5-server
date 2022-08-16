/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License") + you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.red5.io.sctp;

import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SCTPTest {

    private static Logger log = LoggerFactory.getLogger(SCTPTest.class);

    private static final String CLIENT_PORT = "client.port";

    private static final String SERVER_PORT = "server.port";

    @Before
    public void before() {
        assumeTrue(System.getProperties().containsKey(CLIENT_PORT) && System.getProperties().containsKey(SERVER_PORT));
    }

    @Test
    public void testClientChannel() throws InvalidKeyException, NoSuchAlgorithmException, IOException, SctpException {
        int clientPort = Integer.parseInt(System.getProperty(CLIENT_PORT));
        int serverPort = Integer.parseInt(System.getProperty(SERVER_PORT));
        InetSocketAddress socketAddress = new InetSocketAddress(serverPort);
        SctpChannel sctpChannel = SctpChannel.open();

        sctpChannel.bind(new InetSocketAddress(clientPort));
        sctpChannel.connect(socketAddress);
    }

    @Test
    public void testServerChannel() throws InvalidKeyException, NoSuchAlgorithmException, IOException, SctpException {
        int serverPort = Integer.parseInt(System.getProperty(SERVER_PORT));
        SocketAddress serverSocketAddress = new InetSocketAddress(serverPort);
        log.debug("create and bind for sctp address");
        SctpServerChannel sctpServerChannel = SctpServerChannel.open().bind(serverSocketAddress);
        log.debug("address bind process finished successfully");

        @SuppressWarnings("unused")
        SctpChannel sctpChannel = null;
        while ((sctpChannel = sctpServerChannel.accept()) != null) {
            log.debug("client connection received");
            break;
        }
    }
}
