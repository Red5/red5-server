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

    private static String[] cipherSuites = new String[] { "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256", "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256", "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256", "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256", "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA", "TLS_RSA_WITH_AES_128_GCM_SHA256", "TLS_RSA_WITH_AES_128_CBC_SHA256", "TLS_RSA_WITH_AES_128_CBC_SHA" };

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
    }

    @SuppressWarnings({ "rawtypes" })
    @Override
    protected void startConnector(String server, int port) {
        socketConnector = new NioSocketConnector();
        socketConnector.setHandler(ioHandler);
        future = socketConnector.connect(new InetSocketAddress(server, port));
        future.addListener(new IoFutureListener() {
            @Override
            public void operationComplete(IoFuture future) {
                try {
                    // will throw RuntimeException after connection error
                    future.getSession();
                } catch (Throwable e) {
                    //if there isn't an ClientExceptionHandler set, a
                    //RuntimeException may be thrown in handleException
                    handleException(e);
                }
            }
        });
        // Do the close requesting that the pending messages are sent before
        // the session is closed
        //future.getSession().close(false);
        // Now wait for the close to be completed
        future.awaitUninterruptibly(CONNECTOR_WORKER_TIMEOUT);
        // We can now dispose the connector
        //socketConnector.dispose();
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

    private class RTMPSClientIoHandler extends RTMPMinaIoHandler {

        /** {@inheritDoc} */
        @Override
        public void sessionOpened(IoSession session) throws Exception {
            // START OF NATIVE SSL STUFF
            SSLContext context = TLSFactory.getTLSContext(keyStoreType, password);
            SslFilter sslFilter = new SslFilter(context);
            if (sslFilter != null) {
                // we are a client
                sslFilter.setUseClientMode(true);
                // set the cipher suites
                //sslFilter.setEnabledCipherSuites(cipherSuites);
                session.getFilterChain().addFirst("sslFilter", sslFilter);
            }
            // END OF NATIVE SSL STUFF
            super.sessionOpened(session);
        }

        /** {@inheritDoc} */
        @Override
        public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
            log.warn("Exception caught {}", cause.getMessage());
            if (log.isDebugEnabled()) {
                log.error("Exception detail", cause);
            }
            //if there are any errors using ssl, kill the session
            session.closeNow();
        }

    }

}
