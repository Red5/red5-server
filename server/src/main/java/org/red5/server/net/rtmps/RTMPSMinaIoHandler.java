/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.rtmps;

import java.io.NotActiveException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Provider;
import java.security.Security;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import org.apache.mina.core.filterchain.IoFilterChain;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.ssl.SslFilter;
import org.red5.io.tls.TLSFactory;
import org.red5.server.net.rtmp.InboundHandshake;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.RTMPHandler;
import org.red5.server.net.rtmp.RTMPMinaConnection;
import org.red5.server.net.rtmp.RTMPMinaIoHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles Native RTMPS protocol events fired by the MINA framework.
 *
 * <pre>
 * var nc:NetConnection = new NetConnection();
 * nc.proxyType = "best";
 * nc.connect("rtmps:\\localhost\app");
 * </pre>
 *
 * https://issues.apache.org/jira/browse/DIRMINA-272 https://issues.apache.org/jira/browse/DIRMINA-997
 *
 * Transport Layer Security (TLS) Renegotiation Issue http://www.oracle.com/technetwork/java/javase/documentation/tlsreadme2-176330.html
 * Secure renegotiation https://jce.iaik.tugraz.at/sic/Products/Communication-Messaging-Security/iSaSiLk/documentation/Secure-Renegotiation
 * Troubleshooting a HTTPS TLSv1 handshake http://integr8consulting.blogspot.com/2012/02/troubleshooting-https-tlsv1-handshake.html
 * How to analyze Java SSL errors http://www.smartjava.org/content/how-analyze-java-ssl-errors
 *
 * @author Kevin Green (kevygreen@gmail.com)
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class RTMPSMinaIoHandler extends RTMPMinaIoHandler {

    private static Logger log = LoggerFactory.getLogger(RTMPSMinaIoHandler.class);

    /**
     * Password for accessing the keystore and / or truststore.
     */
    private String keystorePassword, truststorePassword;

    /**
     * Stores the keystore and truststore paths.
     */
    private String keystorePath, truststorePath;

    /**
     * Names of the SSL cipher suites which are currently enabled for use.
     */
    private String[] cipherSuites;

    /**
     * Names of the protocol versions which are currently enabled for use. Defaults to TLSv1.2.
     */
    private String[] protocols = new String[] { "TLSv1.2" };

    /**
     * Use client (or server) mode when handshaking.
     */
    private boolean useClientMode;

    /**
     * Request the need of client authentication.
     */
    private boolean needClientAuth;

    /**
     * Indicates that we would like to authenticate the client but if client certificates are self-signed or have no certificate chain then
     * we are still good
     */
    private boolean wantClientAuth;

    static {
        if (log.isTraceEnabled()) {
            Provider[] providers = Security.getProviders();
            for (Provider provider : providers) {
                log.trace("Provider: {} = {}", provider.getName(), provider.getInfo());
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void sessionCreated(IoSession session) throws Exception {
        log.debug("Session created: RTMPS");
        if (keystorePath == null || truststorePath == null) {
            throw new NotActiveException("Keystore or truststore are null");
        }
        // determine the keystore type by the file extension
        String keyStoreType = keystorePath.lastIndexOf(".p12") > 0 ? "PKCS12" : "JKS";
        // create the ssl context
        SSLContext sslContext = null;
        try {
            sslContext = TLSFactory.getTLSContext(keyStoreType, keystorePassword, keystorePath, truststorePassword, truststorePath);
            log.debug("SSL provider is: {}", sslContext.getProvider());
            // get ssl context parameters
            SSLParameters params = sslContext.getDefaultSSLParameters();
            //params.setApplicationProtocols(protocols);
            if (log.isDebugEnabled()) {
                Arrays.asList(params.getCipherSuites()).forEach(cipher -> log.debug("Supported cipher suite: {}", cipher));
            }
            params.setCipherSuites(cipherSuites);
            // set the endpoint identification algorithm
            //params.setEndpointIdentificationAlgorithm("RTMPS");
            //params.setProtocols(protocols);
            // choose to honor the client's preference rather than its own preference
            params.setUseCipherSuitesOrder(false);
            if (log.isDebugEnabled()) {
                log.debug("SSL context params - need client auth: {} want client auth: {} endpoint id algorithm: {}", params.getNeedClientAuth(), params.getWantClientAuth(), params.getEndpointIdentificationAlgorithm());
                String[] supportedProtocols = params.getProtocols();
                for (String protocol : supportedProtocols) {
                    log.debug("SSL context supported protocol: {}", protocol);
                }
            }
        } catch (Exception ex) {
            log.error("Exception getting SSL context", ex);
        }
        // create the ssl filter using server mode
        SslFilter sslFilter = new SslFilter(sslContext);
        sslFilter.setUseClientMode(useClientMode);
        sslFilter.setNeedClientAuth(needClientAuth);
        sslFilter.setWantClientAuth(wantClientAuth);
        if (cipherSuites != null) {
            sslFilter.setEnabledCipherSuites(cipherSuites);
        }
        if (protocols != null) {
            if (log.isDebugEnabled()) {
                log.debug("Using these protocols: {}", Arrays.toString(protocols));
            }
            sslFilter.setEnabledProtocols(protocols);
        }
        // the filter chain for this session
        IoFilterChain chain = session.getFilterChain();
        // add ssl first
        chain.addFirst("sslFilter", sslFilter);
        // use notification messages
        session.setAttribute(SslFilter.USE_NOTIFICATION, Boolean.TRUE);
        log.debug("isSslStarted: {}", sslFilter.isSslStarted(session));
        // add rtmps filter
        session.getFilterChain().addAfter("sslFilter", "rtmpsFilter", new RTMPSIoFilter());
        // create a connection
        RTMPMinaConnection conn = createRTMPMinaConnection();
        // add session to the connection
        conn.setIoSession(session);
        // add the handler
        conn.setHandler(handler);
        // add the connections session id for look up using the connection manager
        session.setAttribute(RTMPConnection.RTMP_SESSION_ID, conn.getSessionId());
        // create an inbound handshake
        InboundHandshake handshake = new InboundHandshake();
        // set whether or not unverified will be allowed
        handshake.setUnvalidatedConnectionAllowed(((RTMPHandler) handler).isUnvalidatedConnectionAllowed());
        // add the in-bound handshake, defaults to non-encrypted mode
        session.setAttribute(RTMPConnection.RTMP_HANDSHAKE, handshake);
    }

    /**
     * Password used to access the keystore file.
     *
     * @param password
     *            keystore password
     */
    public void setKeystorePassword(String password) {
        this.keystorePassword = password;
    }

    /**
     * Password used to access the truststore file.
     *
     * @param password
     *            truststore password
     */
    public void setTruststorePassword(String password) {
        this.truststorePassword = password;
    }

    /**
     * Set keystore data from a file.
     *
     * @param path
     *            contains keystore
     */
    public void setKeystorePath(String path) {
        if (Path.of(path).isAbsolute()) {
            this.keystorePath = path;
        } else {
            this.keystorePath = Paths.get(System.getProperty("user.dir"), path).toString();
        }
        this.keystorePath = path;
    }

    /**
     * Set truststore file path.
     *
     * @param path
     *            contains truststore
     */
    public void setTruststorePath(String path) {
        if (Path.of(path).isAbsolute()) {
            this.truststorePath = path;
        } else {
            this.truststorePath = Paths.get(System.getProperty("user.dir"), path).toString();
        }
        this.truststorePath = path;
    }

    /**
     * <p>Getter for the field <code>cipherSuites</code>.</p>
     *
     * @return an array of {@link java.lang.String} objects
     */
    public String[] getCipherSuites() {
        return cipherSuites;
    }

    /**
     * <p>Setter for the field <code>cipherSuites</code>.</p>
     *
     * @param cipherSuites an array of {@link java.lang.String} objects
     */
    public void setCipherSuites(String[] cipherSuites) {
        this.cipherSuites = cipherSuites;
    }

    /**
     * <p>Getter for the field <code>protocols</code>.</p>
     *
     * @return an array of {@link java.lang.String} objects
     */
    public String[] getProtocols() {
        return protocols;
    }

    /**
     * <p>Setter for the field <code>protocols</code>.</p>
     *
     * @param protocols an array of {@link java.lang.String} objects
     */
    public void setProtocols(String[] protocols) {
        this.protocols = protocols;
    }

    /**
     * <p>Setter for the field <code>useClientMode</code>.</p>
     *
     * @param useClientMode a boolean
     */
    public void setUseClientMode(boolean useClientMode) {
        this.useClientMode = useClientMode;
    }

    /**
     * <p>Setter for the field <code>needClientAuth</code>.</p>
     *
     * @param needClientAuth a boolean
     */
    public void setNeedClientAuth(boolean needClientAuth) {
        this.needClientAuth = needClientAuth;
    }

    /**
     * <p>Setter for the field <code>wantClientAuth</code>.</p>
     *
     * @param wantClientAuth a boolean
     */
    public void setWantClientAuth(boolean wantClientAuth) {
        this.wantClientAuth = wantClientAuth;
    }

}
