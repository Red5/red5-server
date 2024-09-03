/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2015 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.client.net.rtmps;

import java.net.InetSocketAddress;

import javax.net.ssl.SSLContext;

import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.ssl.SslFilter;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.red5.client.net.rtmp.ClientExceptionHandler;
import org.red5.client.net.rtmp.RTMPClient;
import org.red5.client.net.rtmp.RTMPMinaIoHandler;
import org.red5.io.tls.TLSFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RTMPS client object (RTMPS Native)
 *
 * <pre>
 * var nc:NetConnection = new NetConnection();
 * nc.proxyType = "best";
 * nc.connect("rtmps:\\localhost\app");
 * </pre>
 *
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 * @author Kevin Green (kevygreen@gmail.com)
 */
public class RTMPSClient extends RTMPClient {

    private static final Logger log = LoggerFactory.getLogger(RTMPSClient.class);

    private static String[] cipherSuites;

    // I/O handler
    private final RTMPSClientIoHandler ioHandler;

    /**
     * Password for accessing the keystore.
     */
    private char[] password = "password123".toCharArray();

    /**
     * The keystore type, valid options are JKS and PKCS12
     */
    private String keyStoreType = "PKCS12";

    /** Constructs a new RTMPClient. */
    public RTMPSClient() {
        protocol = "rtmps";
        ioHandler = new RTMPSClientIoHandler();
        ioHandler.setHandler(this);
        setExceptionHandler(new ClientExceptionHandler() {
            @Override
            public void handleException(Throwable throwable) {
                log.error("Exception", throwable);
                try {
                    ioHandler.exceptionCaught(null, throwable);
                } catch (Exception e) {
                    log.debug("Exception", e);
                }
            }
        });
    }

    /**
     * Creates a new RTMPSClient with the given keystore type and password.
     *
     * @param keyStoreType keystore type
     * @param password keystore password
     */
    public RTMPSClient(String keyStoreType, String password) {
        protocol = "rtmps";
        this.keyStoreType = keyStoreType;
        this.password = password.toCharArray();
        ioHandler = new RTMPSClientIoHandler();
        ioHandler.setHandler(this);
    }

    @SuppressWarnings({ "rawtypes" })
    @Override
    protected void startConnector(String server, int port) {
        log.debug("startConnector - server: {} port: {}", server, port);
        socketConnector = new NioSocketConnector();
        socketConnector.setHandler(ioHandler);
        future = socketConnector.connect(new InetSocketAddress(server, port));
        future.addListener(new IoFutureListener() {
            @Override
            public void operationComplete(IoFuture future) {
                try {
                    // will throw RuntimeException after connection error
                    future.getSession();
                } catch (Throwable t) {
                    try {
                        ioHandler.exceptionCaught(null, t);
                    } catch (Exception e) {
                        // no-op
                    }
                }
            }
        });
        // Now wait for the close to be completed
        future.awaitUninterruptibly(CONNECTOR_WORKER_TIMEOUT);
    }

    /**
     * Password used to access the keystore file.
     *
     * @param password keystore password
     */
    public void setKeyStorePassword(String password) {
        this.password = password.toCharArray();
    }

    /**
     * Set the key store type, JKS or PKCS12.
     *
     * @param keyStoreType keystore type
     */
    public void setKeyStoreType(String keyStoreType) {
        this.keyStoreType = keyStoreType;
    }

    public static void setCipherSuites(String[] cipherSuites) {
        RTMPSClient.cipherSuites = cipherSuites;
    }

    private class RTMPSClientIoHandler extends RTMPMinaIoHandler {

        /** {@inheritDoc} */
        @Override
        public void sessionOpened(IoSession session) throws Exception {
            log.debug("RTMPS sessionOpened: {}", session);
            // do tls stuff
            SSLContext context = TLSFactory.getTLSContext(keyStoreType, password);
            SslFilter sslFilter = new SslFilter(context);
            if (sslFilter != null) {
                // we are a client
                sslFilter.setUseClientMode(true);
                // set the cipher suites
                if (cipherSuites != null) {
                    sslFilter.setEnabledCipherSuites(cipherSuites);
                }
                session.getFilterChain().addFirst("sslFilter", sslFilter);
            }
            super.sessionOpened(session);
        }

        @Override
        public void sessionClosed(IoSession session) throws Exception {
            log.debug("RTMPS sessionClosed: {}", session);
            super.sessionClosed(session);
        }

        /** {@inheritDoc} */
        @Override
        public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
            log.warn("Exception caught: {}", cause.getMessage());
            log.debug("Exception detail", cause);
            // if there are any errors using ssl, kill the session
            if (session != null) {
                session.closeNow();
            }
            socketConnector.dispose(false);
        }

    }

}
