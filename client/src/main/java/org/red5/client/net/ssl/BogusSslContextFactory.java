package org.red5.client.net.ssl;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Security;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * Factory to create a bougus SSLContext.
 *
 * @author The Apache MINA Project (dev@mina.apache.org)
 */
public class BogusSslContextFactory {

    /**
     * Protocol to use.
     */
    private static final String PROTOCOL = "TLS";

    private static final String KEY_MANAGER_FACTORY_ALGORITHM;

    static {
        String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
        if (algorithm == null) {
            algorithm = "SunX509";
        }
        KEY_MANAGER_FACTORY_ALGORITHM = algorithm;
    }

    /**
     * Bougus Server certificate keystore file name.
     */
    private static final String BOGUS_KEYSTORE = "bogus.cert";

    // NOTE: The keystore was generated using keytool:
    // keytool -genkey -alias bogus -keysize 512 -validity 3650 -keyalg RSA -dname "CN=bogus.com, OU=XXX CA, O=Bogus Inc, L=Stockholm, S=Stockholm, C=SE" -keypass boguspw -storepass boguspw -keystore bogus.cert
    /**
     * Bougus keystore password.
     */
    private static final char[] BOGUS_PW = { 'b', 'o', 'g', 'u', 's', 'p', 'w' };

    private static SSLContext serverInstance = null;

    private static SSLContext clientInstance = null;

    /**
     * Get SSLContext singleton.
     *
     * @param server whether server or client
     * @return SSLContext
     * @throws java.security.GeneralSecurityException thrown
     */
    public static SSLContext getInstance(boolean server) throws GeneralSecurityException {
        SSLContext retInstance = null;
        if (server) {
            if (serverInstance == null) {
                synchronized (BogusSslContextFactory.class) {
                    if (serverInstance == null) {
                        try {
                            serverInstance = createBougusServerSslContext();
                        } catch (Exception ioe) {
                            throw new GeneralSecurityException("Can't create Server SSLContext:" + ioe);
                        }
                    }
                }
            }
            retInstance = serverInstance;
        } else {
            if (clientInstance == null) {
                synchronized (BogusSslContextFactory.class) {
                    if (clientInstance == null) {
                        clientInstance = createBougusClientSslContext();
                    }
                }
            }
            retInstance = clientInstance;
        }
        return retInstance;
    }

    private static SSLContext createBougusServerSslContext() throws GeneralSecurityException, IOException {
        // Create keystore
        KeyStore ks = KeyStore.getInstance("JKS");
        InputStream in = null;
        try {
            in = BogusSslContextFactory.class.getResourceAsStream(BOGUS_KEYSTORE);
            ks.load(in, BOGUS_PW);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
        // Set up key manager factory to use our key store
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KEY_MANAGER_FACTORY_ALGORITHM);
        kmf.init(ks, BOGUS_PW);
        // Initialize the SSLContext to work with our key managers.
        SSLContext sslContext = SSLContext.getInstance(PROTOCOL);
        sslContext.init(kmf.getKeyManagers(), BogusTrustManagerFactory.X509_MANAGERS, null);
        return sslContext;
    }

    private static SSLContext createBougusClientSslContext() throws GeneralSecurityException {
        SSLContext context = SSLContext.getInstance(PROTOCOL);
        context.init(null, BogusTrustManagerFactory.X509_MANAGERS, null);
        return context;
    }
}