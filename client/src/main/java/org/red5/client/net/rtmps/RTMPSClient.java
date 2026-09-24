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
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.ssl.SslFilter;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.red5.client.net.rtmp.ClientExceptionHandler;
import org.red5.client.net.rtmp.RTMPClient;
import org.red5.client.net.rtmp.RTMPMinaIoHandler;

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

    /**
     * System property that enables trust-on-first-use enrollment by default for new clients.
     */
    public static final String TRUST_ON_FIRST_USE_PROPERTY = "red5.rtmps.trust_on_first_use";

    private static String[] cipherSuites;

    /**
     * When true, the unverified certificate chain of a server with no enrolled chain is saved into the truststore before the first
     * connection. Off by default: server certificates are verified against the JDK trust store and the configured truststore.
     */
    private boolean trustOnFirstUse = Boolean.getBoolean(TRUST_ON_FIRST_USE_PROPERTY);

    // host and port of the server being connected to, used for SNI and hostname verification
    private String tlsHost;

    private int tlsPort;

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
     * @throws java.io.IOException if the keystore or truststore cannot be read
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
     * @throws java.io.IOException if the keystore or truststore cannot be read
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
        tlsHost = server;
        tlsPort = port;
        Path truststoreFile = Paths.get(truststorePath);
        if (trustOnFirstUse) {
            enrollOnFirstUse(server, port, truststoreFile);
        }
        // convert the paths to input streams
        try {
            if (keystorePath != null && !keystorePath.isEmpty()) {
                keystoreStream = Files.newInputStream(Paths.get(keystorePath));
            } else {
                log.debug("Keystore path is null or empty, no client certificate will be presented");
            }
            if (Files.exists(truststoreFile)) {
                truststoreStream = Files.newInputStream(truststoreFile);
            } else {
                log.info("Truststore {} does not exist, using the JDK default trust store only", truststorePath);
            }
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
     * Enables or disables trust-on-first-use enrollment. When enabled, connecting to a server whose certificate chain has not been
     * enrolled saves its chain, unverified, into the truststore and logs its SHA-256 fingerprint; later connections verify against
     * it. Only enable this on a trusted network and compare the fingerprint with one obtained out of band.
     *
     * @param trustOnFirstUse true to enroll unknown servers on first connection
     */
    public void setTrustOnFirstUse(boolean trustOnFirstUse) {
        this.trustOnFirstUse = trustOnFirstUse;
    }

    /**
     * Returns whether trust-on-first-use enrollment is enabled.
     *
     * @return true if unknown servers are enrolled on first connection
     */
    public boolean isTrustOnFirstUse() {
        return trustOnFirstUse;
    }

    private void enrollOnFirstUse(String server, int port, Path truststoreFile) {
        Path parentDir = truststoreFile.getParent();
        if (parentDir == null) {
            parentDir = Paths.get(".");
        }
        Path pemFile = parentDir.resolve(server + ".pem");
        if (Files.exists(pemFile)) {
            log.debug("Certificate chain for {} already enrolled at {}", server, pemFile);
            return;
        }
        try {
            Files.createDirectories(parentDir);
            CertificateGrabber.retrieveCertificate(server, port, pemFile.toString());
            P12StoreManager.buildTrustStore(truststoreFile.toString(), truststorePassword, pemFile.toString());
            log.info("Enrolled certificate chain for {}:{} into {}", server, port, truststoreFile);
        } catch (Exception e) {
            log.warn("Trust-on-first-use enrollment failed for {}:{}", server, port, e);
        }
    }

    /**
     * Creates the client TLS context. Server certificates are accepted when either the JDK default trust store or the configured
     * truststore trusts them; the SSL engine also verifies the server hostname.
     *
     * @return TLS context
     * @throws Exception if the stores cannot be loaded
     */
    protected SSLContext createSSLContext() throws Exception {
        KeyManager[] keyManagers = null;
        if (keystoreStream != null) {
            KeyStore ks = KeyStore.getInstance(keyStoreType);
            ks.load(keystoreStream, keystorePassword);
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, keystorePassword);
            keyManagers = kmf.getKeyManagers();
        }
        X509ExtendedTrustManager trustManager = trustManager(null);
        if (truststoreStream != null) {
            KeyStore ts = KeyStore.getInstance(keyStoreType);
            ts.load(truststoreStream, truststorePassword);
            trustManager = new EitherTrustManager(trustManager(ts), trustManager);
        }
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers, new TrustManager[] { trustManager }, null);
        return context;
    }

    private static X509ExtendedTrustManager trustManager(KeyStore store) throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(store);
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509ExtendedTrustManager) {
                return (X509ExtendedTrustManager) tm;
            }
        }
        throw new IllegalStateException("No X509ExtendedTrustManager available");
    }

    /**
     * Trusts a server when the primary or the fallback trust manager trusts it. Both delegates perform hostname verification when
     * the engine requests it.
     */
    private static final class EitherTrustManager extends X509ExtendedTrustManager {

        private final X509ExtendedTrustManager primary, fallback;

        EitherTrustManager(X509ExtendedTrustManager primary, X509ExtendedTrustManager fallback) {
            this.primary = primary;
            this.fallback = fallback;
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            try {
                primary.checkServerTrusted(chain, authType, engine);
            } catch (CertificateException e) {
                fallback.checkServerTrusted(chain, authType, engine);
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            try {
                primary.checkServerTrusted(chain, authType, socket);
            } catch (CertificateException e) {
                fallback.checkServerTrusted(chain, authType, socket);
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            try {
                primary.checkServerTrusted(chain, authType);
            } catch (CertificateException e) {
                fallback.checkServerTrusted(chain, authType);
            }
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            throw new CertificateException("Client certificates are not accepted");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            throw new CertificateException("Client certificates are not accepted");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("Client certificates are not accepted");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            X509Certificate[] a = primary.getAcceptedIssuers(), b = fallback.getAcceptedIssuers();
            X509Certificate[] all = new X509Certificate[a.length + b.length];
            System.arraycopy(a, 0, all, 0, a.length);
            System.arraycopy(b, 0, all, a.length, b.length);
            return all;
        }

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
            SSLContext context = createSSLContext();
            // the peer address gives the SSL engine the host name for SNI and hostname verification
            session.setAttribute(SslFilter.PEER_ADDRESS, InetSocketAddress.createUnresolved(tlsHost, tlsPort));
            SslFilter sslFilter = new SslFilter(context);
            if (sslFilter != null) {
                // we are a client
                sslFilter.setUseClientMode(true);
                sslFilter.setEndpointIdentificationAlgorithm("HTTPS");
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
