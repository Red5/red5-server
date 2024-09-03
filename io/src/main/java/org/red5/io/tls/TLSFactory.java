package org.red5.io.tls;

import java.io.FileInputStream;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.UnrecoverableKeyException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TLSFactory {

    private static final Logger log = LoggerFactory.getLogger(TLSFactory.class);

    private static final boolean isDebug = log.isDebugEnabled();

    private static final SecureRandom RANDOM = new SecureRandom();

    public static final int MAX_HANDSHAKE_LOOPS = 200;

    public static final int MAX_APP_READ_LOOPS = 60;

    public static final int SOCKET_TIMEOUT = Integer.getInteger("socket.timeout", 3 * 1000); // in millis

    public static final int BUFFER_SIZE = 1024 * 4; // 4KB enough?

    public static final int MAXIMUM_PACKET_SIZE = 1180; // use this for PMTU for now

    // protocol version
    public static final String PROTOCOL_VERSION = "TLSv1.2";

    /*
     * The following is to set up the keystores.
     */
    private static String storeType = "PKCS12"; // JKS or PKCS12

    private static String keyStoreFile = String.format("server.%s", "PKCS12".equals(storeType) ? "p12" : "jks");

    private static String trustStoreFile = String.format("truststore.%s", "PKCS12".equals(storeType) ? "p12" : "jks");

    private static String passwd = "password123";

    private static String keyFilename = Paths.get(System.getProperty("user.dir"), "conf", keyStoreFile).toString();

    private static String trustFilename = Paths.get(System.getProperty("user.dir"), "conf", trustStoreFile).toString();

    static {
        if (isDebug) {
            //System.setProperty("javax.net.debug", "all");
            System.setProperty("javax.net.debug", "SSL,handshake,verbose,trustmanager,keymanager,record,plaintext");
        }
        // set unlimited crypto policy
        Security.setProperty("crypto.policy", "unlimited");
        // set extensions
        System.setProperty("jdk.tls.useExtendedMasterSecret", "true"); // https://bugs.openjdk.org/browse/JDK-8192045 not for DTLS 1.3
        System.setProperty("jdk.tls.allowLegacyMasterSecret", "false"); // allows rejection if session hash and master secret are not supported
        System.setProperty("jdk.tls.acknowledgeCloseNotify", "true");
        // https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/JSSERefGuide.html
        // https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/ReadDebug.html
        // check max key size
        int maxKeySize;
        try {
            maxKeySize = javax.crypto.Cipher.getMaxAllowedKeyLength("AES");
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to get max key size for AES", e);
            maxKeySize = 128;
        }
        log.info("Max key size for AES: {}", (maxKeySize == Integer.MAX_VALUE ? "unlimited" : maxKeySize));
    }

    public static SSLContext getTLSContext() throws Exception {
        log.info("Creating SSL context with keystore: {} and truststore: {} using {}", keyFilename, trustFilename, storeType);
        KeyStore ks = KeyStore.getInstance(storeType);
        KeyStore ts = KeyStore.getInstance(storeType);
        char[] passphrase = passwd.toCharArray();
        try (FileInputStream fis = new FileInputStream(keyFilename)) {
            ks.load(fis, passphrase);
        } catch (Exception e) {
            log.error("Failed to load keystore: {}", keyFilename, e);
            throw e;
        }
        try (FileInputStream fis = new FileInputStream(trustFilename)) {
            ts.load(fis, passphrase);
        } catch (Exception e) {
            log.error("Failed to load truststore: {}", trustFilename, e);
            throw e;
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        try {
            kmf.init(ks, passphrase);
        } catch (UnrecoverableKeyException e) {
            log.error("Failed to initialize KeyManagerFactory with keystore: {}", keyFilename, e);
            throw e;
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ts);
        SSLContext sslCtx = SSLContext.getInstance(PROTOCOL_VERSION);
        sslCtx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), RANDOM);
        return sslCtx;
    }

    public static SSLContext getTLSContext(String storeType, char[] passphrase) throws Exception {
        log.info("Creating SSL context with keystore: {} and truststore: {} using {}", keyFilename, trustFilename, storeType);
        log.debug("Keystore - file name: {} password: {}", keyFilename, passphrase);
        log.debug("Truststore - file name: {} password: {}", trustFilename, passphrase);        
        KeyStore ks = KeyStore.getInstance(storeType);
        KeyStore ts = KeyStore.getInstance(storeType);
        try (FileInputStream fis = new FileInputStream(keyFilename)) {
            ks.load(fis, passphrase);
        } catch (Exception e) {
            log.error("Failed to load keystore: {}", keyFilename, e);
            throw e;
        }
        try (FileInputStream fis = new FileInputStream(trustFilename)) {
            ts.load(fis, passphrase);
        } catch (Exception e) {
            log.error("Failed to load truststore: {}", trustFilename, e);
            throw e;
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        try {
            PrivateKey privateKey = (PrivateKey) ks.getKey("privatekey", passphrase);
            log.debug("Private key: {}", privateKey);
            kmf.init(ks, passphrase);
        } catch (UnrecoverableKeyException e) {
            log.error("Failed to initialize KeyManagerFactory with keystore: {}", keyFilename, e);
            throw e;
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ts);
        SSLContext sslCtx = SSLContext.getInstance(PROTOCOL_VERSION);
        sslCtx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), RANDOM);
        return sslCtx;
    }

    public static SSLContext getTLSContext(String storeType, String keystorePassword, String keyFilename, String truststorePassword, String trustFilename) throws Exception {
        log.info("Creating SSL context with keystore: {} and truststore: {} using {}", keyFilename, trustFilename, storeType);
        log.debug("Keystore - file name: {} password: {}", keyFilename, keystorePassword);
        log.debug("Truststore - file name: {} password: {}", trustFilename, truststorePassword);
        KeyStore ks = KeyStore.getInstance(storeType);
        KeyStore ts = KeyStore.getInstance(storeType);
        char[] keyStrorePassphrase = keystorePassword.toCharArray();
        char[] trustStorePassphrase = truststorePassword.toCharArray();
        try (FileInputStream fis = new FileInputStream(keyFilename)) {
            ks.load(fis, keyStrorePassphrase);
        }
        try (FileInputStream fis = new FileInputStream(trustFilename)) {
            ts.load(fis, trustStorePassphrase);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, keyStrorePassphrase);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ts);
        SSLContext sslCtx = SSLContext.getInstance(PROTOCOL_VERSION);
        sslCtx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), RANDOM);
        return sslCtx;
    }

    public static SSLEngine createSSLEngine(boolean isClient) throws Exception {
        SSLContext context = getTLSContext();
        SSLEngine engine = context.createSSLEngine();
        SSLParameters paras = engine.getSSLParameters();
        paras.setMaximumPacketSize(MAXIMUM_PACKET_SIZE);
        engine.setUseClientMode(isClient);
        engine.setSSLParameters(paras);
        return engine;
    }

    public static String getStoreType() {
        return storeType;
    }

    public static void setStoreType(String storeType) {
        TLSFactory.storeType = storeType;
    }

    public static String getKeyStoreFile() {
        return keyStoreFile;
    }

    public static void setKeyStoreFile(String keyStoreFile) {
        TLSFactory.keyStoreFile = keyStoreFile;
    }

    public static String getTrustStoreFile() {
        return trustStoreFile;
    }

    public static void setTrustStoreFile(String trustStoreFile) {
        TLSFactory.trustStoreFile = trustStoreFile;
    }

    public static String getPasswd() {
        return passwd;
    }

    public static void setPasswd(String passwd) {
        TLSFactory.passwd = passwd;
    }

    public static String getKeyFilename() {
        return keyFilename;
    }

    public static void setKeyFilename(String keyFilename) {
        TLSFactory.keyFilename = keyFilename;
    }

    public static String getTrustFilename() {
        return trustFilename;
    }

    public static void setTrustFilename(String trustFilename) {
        TLSFactory.trustFilename = trustFilename;
    }

}