/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.tomcat;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.http11.Http11Nio2Protocol;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

/**
 * Model object to contain a connector, socket address, and connection properties for a Tomcat connection.
 *
 * @author Paul Gregoire
 */
public class TomcatConnector {

    private static Logger log = Red5LoggerFactory.getLogger(TomcatConnector.class);

    private Connector connector;

    private Map<String, String> connectorProperties;

    private Map<String, String> connectionProperties;

    private String protocol = "org.apache.coyote.http11.Http11NioProtocol";

    private InetSocketAddress address;

    private int redirectPort = 443;

    private boolean useIPVHosts = true;

    private String URIEncoding = "UTF-8";

    private boolean secure;

    private boolean initialized;

    /**
     * <p>init.</p>
     */
    public void init() {
        try {
            // create a connector
            connector = new Connector(protocol);
            connector.setRedirectPort(redirectPort);
            connector.setUseIPVHosts(useIPVHosts);
            connector.setURIEncoding(URIEncoding);
            // set the bind address to local if we dont have an address property
            if (address == null) {
                address = bindLocal(connector.getPort());
            }
            // set port
            connector.setPort(address.getPort());
            // set any additional connector properties
            if (connectorProperties != null) {
                for (Entry<String, String> e : connectorProperties.entrySet()) {
                    IntrospectionUtils.setProperty(connector, e.getKey(), e.getValue());
                }
            }
            // set connection properties
            if (connectionProperties != null) {
                for (String key : connectionProperties.keySet()) {
                    // skip ssl related properties
                    if (key.startsWith("keystore") || key.startsWith("truststore") || key.startsWith("certificate") || key.equals("clientAuth") || key.equals("allowUnsafeLegacyRenegotiation")) {
                        continue;
                    }
                    connector.setProperty(key, connectionProperties.get(key));
                }
            }
            // turn off native apr support
            //AprLifecycleListener listener = new AprLifecycleListener();
            //listener.setSSLEngine("off");
            //connector.addLifecycleListener(listener);
            // apply the bind address to the handler
            ProtocolHandler handler = connector.getProtocolHandler();
            if (handler instanceof Http11Nio2Protocol) {
                ((Http11Nio2Protocol) handler).setAddress(address.getAddress());
            } else if (handler instanceof Http11NioProtocol) {
                ((Http11NioProtocol) handler).setAddress(address.getAddress());
            }
            // Reference https://tomcat.apache.org/tomcat-11.0-doc/ssl-howto.html#SSL_and_Tomcat
            // determine if https support is requested
            if (secure) {
                // set connection properties
                connector.setSecure(true);
                connector.setScheme("https");
                connector.setProperty("SSLEnabled", "true");
                // create a new ssl host config
                SSLHostConfig sslHostConfig = new SSLHostConfig();
                /*
                    <entry key="sslProtocol" value="TLS" />
                    <entry key="keystoreFile" value="${rtmps.keystorefile}" />
                    <entry key="keystorePass" value="${rtmps.keystorepass}" />
                    <entry key="truststoreFile" value="${rtmps.truststorefile}" />
                    <entry key="truststorePass" value="${rtmps.truststorepass}" />
                    <entry key="clientAuth" value="false" />
                    <entry key="allowUnsafeLegacyRenegotiation" value="true" />
                 */
                sslHostConfig.setSslProtocol("TLS");
                sslHostConfig.setTruststoreFile(connectionProperties.get("truststoreFile"));
                sslHostConfig.setTruststorePassword(connectionProperties.get("truststorePass"));
                if (connectionProperties.containsKey("truststoreType")) {
                    sslHostConfig.setTruststoreType(connectionProperties.get("truststoreType"));
                } else {
                    sslHostConfig.setTruststoreType("JKS");
                }
                // set the protocols
                if (connectionProperties.containsKey("protocols")) {
                    String[] protocols = connectionProperties.get("protocols").split(",");
                    //sslHostConfig.setProtocols(protocols);
                    sslHostConfig.setEnabledProtocols(protocols);
                } else {
                    sslHostConfig.setProtocols("TLSv1.2");
                    sslHostConfig.setEnabledProtocols(new String[] { "TLSv1.2" });
                }
                // set the ciphers
                if (connectionProperties.containsKey("ciphers")) {
                    String[] ciphers = connectionProperties.get("ciphers").split(",");
                    //sslHostConfig.setCiphers(ciphers);
                    sslHostConfig.setEnabledCiphers(ciphers);
                } else {
                    //sslHostConfig.setCiphers("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384");
                }
                // dont allow unsafe renegotiation
                sslHostConfig.setInsecureRenegotiation(!secure);
                // create a new ssl host config certificate
                SSLHostConfigCertificate sslHostConfigCert = new SSLHostConfigCertificate(sslHostConfig, SSLHostConfigCertificate.Type.RSA);
                sslHostConfigCert.setCertificateKeystoreFile(connectionProperties.get("keystoreFile"));
                sslHostConfigCert.setCertificateKeystorePassword(connectionProperties.get("keystorePass"));
                if (connectionProperties.containsKey("keystoreType")) {
                    sslHostConfigCert.setCertificateKeystoreType(connectionProperties.get("keystoreType"));
                } else {
                    sslHostConfigCert.setCertificateKeystoreType("JKS");
                }
                // set the certificate key alias
                if (connectionProperties.containsKey("certificateKeyAlias")) {
                    sslHostConfigCert.setCertificateKeyAlias(connectionProperties.get("certificateKeyAlias"));
                } else {
                    //sslHostConfigCert.setCertificateKeyAlias("red5");
                }
                // add the ssl host config certificate to the ssl host config
                sslHostConfig.addCertificate(sslHostConfigCert);
                // add the ssl host config to the handler
                handler.addSslHostConfig(sslHostConfig);
            }
            // set initialized flag
            initialized = true;
        } catch (Throwable t) {
            log.error("Exception during connector creation", t);
        }
    }

    /**
     * Returns a local address and port.
     *
     * @param port
     * @return InetSocketAddress
     */
    private InetSocketAddress bindLocal(int port) throws Exception {
        return new InetSocketAddress("127.0.0.1", port);
    }

    /**
     * <p>Getter for the field <code>connector</code>.</p>
     *
     * @return the connector
     */
    public Connector getConnector() {
        if (!initialized) {
            init();
        }
        return connector;
    }

    /**
     * Set connection properties for the connector.
     *
     * @param props
     *            connection properties to set
     */
    public void setConnectionProperties(Map<String, String> props) {
        if (connectionProperties == null) {
            this.connectionProperties = new HashMap<String, String>();
        }
        this.connectionProperties.putAll(props);
    }

    /**
     * <p>Getter for the field <code>connectionProperties</code>.</p>
     *
     * @return the connectionProperties
     */
    public Map<String, String> getConnectionProperties() {
        return connectionProperties;
    }

    /**
     * Set connection properties for the connector.
     *
     * @param props
     *            connection properties to set
     */
    public void setConnectorProperties(Map<String, String> props) {
        if (connectorProperties == null) {
            this.connectorProperties = new HashMap<>();
        }
        this.connectorProperties.putAll(props);
    }

    /**
     * <p>Getter for the field <code>connectorProperties</code>.</p>
     *
     * @return the connectionProperties
     */
    public Map<String, String> getConnectorProperties() {
        return connectorProperties;
    }

    /**
     * <p>Setter for the field <code>protocol</code>.</p>
     *
     * @param protocol
     *            the protocol to set
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * <p>Setter for the field <code>useIPVHosts</code>.</p>
     *
     * @param useIPVHosts
     *            the useIPVHosts to set
     */
    public void setUseIPVHosts(boolean useIPVHosts) {
        this.useIPVHosts = useIPVHosts;
    }

    /**
     * <p>setURIEncoding.</p>
     *
     * @param uRIEncoding
     *            the uRIEncoding to set
     */
    public void setURIEncoding(String uRIEncoding) {
        URIEncoding = uRIEncoding;
    }

    /**
     * The address and port to which we will bind the connector. If the port is not supplied the default of 5080 will be used. The address and port are to be separated by a colon ':'.
     *
     * @param addressAndPort a {@link java.lang.String} object
     */
    public void setAddress(String addressAndPort) {
        try {
            String addr = "0.0.0.0";
            int port = 5080;
            if (addressAndPort != null && addressAndPort.indexOf(':') != -1) {
                String[] parts = addressAndPort.split(":");
                addr = parts[0];
                port = Integer.valueOf(parts[1]);
            }
            this.address = new InetSocketAddress(addr, port);
        } catch (Exception e) {
            log.warn("Exception configuring address", e);
        }
    }

    /**
     * <p>Getter for the field <code>address</code>.</p>
     *
     * @return the socket address as string
     */
    public String getAddress() {
        return String.format("%s:%d", address.getHostName(), address.getPort());
    }

    /**
     * <p>getSocketAddress.</p>
     *
     * @return the socket address
     */
    public InetSocketAddress getSocketAddress() {
        return address;
    }

    /**
     * <p>Setter for the field <code>redirectPort</code>.</p>
     *
     * @param redirectPort
     *            the redirectPort to set
     */
    public void setRedirectPort(int redirectPort) {
        this.redirectPort = redirectPort;
    }

    /**
     * <p>isSecure.</p>
     *
     * @return the secure
     */
    public boolean isSecure() {
        return secure;
    }

    /**
     * <p>Setter for the field <code>secure</code>.</p>
     *
     * @param secure
     *            the secure to set
     */
    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "TomcatConnector [connector=" + connector + ", connectionProperties=" + connectionProperties + ", address=" + address + "]";
    }

}
