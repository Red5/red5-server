/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2015 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.client.net.rtmps;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.ssl.SslFilter;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.red5.client.net.rtmp.ClientExceptionHandler;
import org.red5.client.net.rtmp.RTMPClient;
import org.red5.client.net.rtmp.RTMPMinaIoHandler;
import org.red5.io.tls.TLSFactory;

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

    private static String[] cipherSuites;

    // I/O handler
    private RTMPSClientIoHandler ioHandler;

    /**
     * Password for accessing the keystore.
     */
    private char[] keystorePassword = "password123".toCharArray(), truststorePassword = "password123".toCharArray();

    private String keystorePath, truststorePath;

    /**
     * Path to the keystore and truststore files.
     */
    private InputStream keystoreStream, truststoreStream;

    /**
     * The keystore type, valid options are JKS and PKCS12
     */
    private String keyStoreType = "PKCS12";

    {
        protocol = "rtmps";
    }

    /**
     * Constructs a new RTMPClient.
     */
    public RTMPSClient() {
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
        this.keyStoreType = keyStoreType;
        // set the password for both keystore and truststore since only one is supplied
        this.keystorePassword = this.truststorePassword = password.toCharArray();
        // create the I/O handler
        log.debug("RTMPSClient - keystoreType: {}", keyStoreType);
    }

    /**
     * Creates a new RTMPSClient with the given keystore type, password, and paths to store files. If the stores
     * are inside a jar file, use the following format: jar:file:/path/to/your.jar!/path/to/file/in/jar
     *
     * @param keyStoreType keystore type
     * @param password keystore password
     * @param keystorePath path to keystore file
     * @param truststorePath path to truststore file
     * @throws java.io.IOException
     */
    public RTMPSClient(String keyStoreType, String password, String keystorePath, String truststorePath) throws IOException {
        this(keyStoreType, password, keystorePath, password, truststorePath);
    }

    /**
     * Creates a new RTMPSClient with the given keystore type, passwords, and paths to store files. If the stores
     * are inside a jar file, use the following format: jar:file:/path/to/your.jar!/path/to/file/in/jar
     *
     * @param keyStoreType keystore type
     * @param keystorePassword keystore password
     * @param keystorePath path to keystore file
     * @param truststorePassword truststore password
     * @param truststorePath path to truststore file
     * @throws java.io.IOException
     */
    public RTMPSClient(String keyStoreType, String keystorePassword, String keystorePath, String truststorePassword, String truststorePath) throws IOException {
        // set the password for both keystore and truststore since only one is supplied
        this.keystorePassword = keystorePassword != null ? keystorePassword.toCharArray() : null;
        if (truststorePassword == null || truststorePassword.isEmpty()) {
            throw new IllegalArgumentException("Truststore password must not be null or empty");
        }
        // check the paths
        if (truststorePath == null || truststorePath.isEmpty()) {
            throw new IllegalArgumentException("Truststore path must not be null or empty");
        }
        this.keystorePath = keystorePath;
        this.truststorePath = truststorePath;
        // required for truststore
        this.truststorePassword = truststorePassword.toCharArray();
        // determine the keystore type based on the file extension; default to PKCS12
        this.keyStoreType = keyStoreType == null ? "PKCS12" : truststorePath.lastIndexOf(".p12") > 0 ? "PKCS12" : "JKS";
        log.debug("RTMPSClient - keystoreType: {}, keystorePath: {}, truststorePath: {}", keyStoreType, keystorePath, truststorePath);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({ "rawtypes" })
    @Override
    protected void startConnector(String server, int port) {
        log.debug("startConnector - server: {} port: {}", server, port);
        // if not set, check system properties for the keystore and truststore
        if (keystorePath == null) {
            // get the keystore path from system properties, default to "keystore.jks"
            String kpath = System.getProperty("javax.net.ssl.keyStore");
            if (kpath != null) {
                keystorePath = kpath;
            }
            // get the password from system properties
            String kpass = System.getProperty("javax.net.ssl.keyStorePassword");
            if (kpass != null) {
                keystorePassword = kpass.toCharArray();
            }
            log.debug("RTMPSClient - keystoreType: {}, keystorePath: {}", keyStoreType, keystorePath);
        }
        if (truststorePath == null) {
            truststorePath = System.getProperty("javax.net.ssl.trustStore", "conf/rtmps_truststore.p12");
            if (truststorePassword == null) {
                truststorePassword = System.getProperty("javax.net.ssl.trustStorePassword", "password123").toCharArray();
            }
            keyStoreType = keyStoreType == null ? "PKCS12" : truststorePath.lastIndexOf(".p12") > 0 ? "PKCS12" : "JKS";
            log.debug("RTMPSClient - keystoreType: {}, truststorePath: {}", keyStoreType, truststorePath);
        }
        // strip the end of the truststore path to use it for saving the pem file
        String pemPath = truststorePath.substring(0, truststorePath.lastIndexOf('/'));
        log.info("RTMPSClient - pemPath: {}", pemPath);
        // ensure the truststore has a certificate for the server we are connecting to
        try {
            // retrieve the certificate from the server and update the truststore
            CertificateGrabber.retrieveCertificate(server, port);
            P12StoreManager.buildTrustStore(truststorePath, truststorePassword, String.format("%s/%s.pem", pemPath, server));
            log.info("Certificate retrieved and truststore updated for {}:{}", server, port);
        } catch (Exception e) {
            // log the error and attempt to retrieve the certificate from port 443
            if (isDebug) {
                log.debug("Error retrieving certificate from {}:{}", server, port, e);
            } else {
                log.warn("Error retrieving certificate from {}:{}", server, port);
            }
            // make an attempt to continue if 443 wasn't specified
            if (port != 443) {
                log.warn("Secondary attempt since standard port 443 wasn't used: {}", port);
                try {
                    // retrieve the certificate from the server and update the truststore
                    CertificateGrabber.retrieveCertificate(server, 443);
                    P12StoreManager.buildTrustStore(truststorePath, truststorePassword, String.format("%s/%s.pem", pemPath, server));
                    log.info("Certificate retrieved and truststore updated for {}:{}", server, 443);
                } catch (Exception e2) {
                    if (isDebug) {
                        log.debug("Error retrieving certificate from {}:{}", server, port, e2);
                    } else {
                        log.warn("Error retrieving certificate from {}:{}", server, port);
                    }
                }
            }
        }
        // convert the paths to input streams
        try {
            if (keystorePath != null && !keystorePath.isEmpty()) {
                keystoreStream = Files.newInputStream(Paths.get(keystorePath));
            } else {
                log.warn("Keystore path is null or empty, using system keystore");
            }
            // truststore path is required
            truststoreStream = Files.newInputStream(Paths.get(truststorePath));
        } catch (IOException e) {
            log.error("Error reading keystore or truststore files", e);
            throw new RuntimeException("Could not read keystore or truststore files", e);
        }
        // create the I/O handler
        ioHandler = new RTMPSClientIoHandler();
        ioHandler.setHandler(this);
        // create the socket connector
        socketConnector = new NioSocketConnector();
        socketConnector.setHandler(ioHandler);
        // connect with a timeout
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
        this.keystorePassword = password.toCharArray();
    }

    /**
     * Password used to access the truststore file.
     *
     * @param password truststore password
     */
    public void setTrustStorePassword(String password) {
        this.truststorePassword = password.toCharArray();
    }

    /**
     * Set the key store type, JKS or PKCS12.
     *
     * @param keyStoreType keystore type
     */
    public void setKeyStoreType(String keyStoreType) {
        this.keyStoreType = keyStoreType;
    }

    /**
     * <p>Setter for the field <code>cipherSuites</code>.</p>
     *
     * @param cipherSuites an array of {@link java.lang.String} objects
     */
    public static void setCipherSuites(String[] cipherSuites) {
        RTMPSClient.cipherSuites = cipherSuites;
    }

    private class RTMPSClientIoHandler extends RTMPMinaIoHandler {

        /** {@inheritDoc} */
        @Override
        public void sessionOpened(IoSession session) throws Exception {
            log.debug("RTMPS sessionOpened: {}", session);
            // if we're using a input streams, pass them to the ctor
            SSLContext context = null;
            if (keystoreStream != null && truststoreStream != null) {
                context = TLSFactory.getTLSContext(keyStoreType, keystorePassword, keystoreStream, truststorePassword, truststoreStream);
            } else {
                KeyStore ts = KeyStore.getInstance(keyStoreType);
                ts.load(truststoreStream, truststorePassword);
                TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
                tmf.init(ts);
                context = SSLContext.getInstance("TLS");
                context.init(null, // No key managers needed for client
                        tmf.getTrustManagers(), // custom truststore
                        new java.security.SecureRandom());
            }
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
