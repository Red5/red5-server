package org.red5.client.net.rtmps;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages a PKCS#12 (P12) truststore for TLS connections.
 */
public class P12StoreManager {

    private static Logger log = LoggerFactory.getLogger(P12StoreManager.class);

    @SuppressWarnings("unchecked")
    public static KeyStore buildTrustStore(String truststorePath, char[] truststorePassword, String certPath) {
        KeyStore trustStore = null;
        try {
            // load / create the store (P12 format)
            trustStore = KeyStore.getInstance("PKCS12");
            try (InputStream is = new FileInputStream(truststorePath)) {
                trustStore.load(is, truststorePassword);
                log.info("Truststore loaded successfully from {}", truststorePath);
            } catch (FileNotFoundException e) {
                // store doesn't exist, create a new one
                trustStore.load(null, truststorePassword);
                log.info("New truststore created successfully at {}", truststorePath);
            }
            // load the certs
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            // if we have a single PEM file with the full chain
            try (InputStream certIs = new FileInputStream(certPath)) {
                // read all certificates from the stream (useful for chain)
                for (X509Certificate cert : (Collection<X509Certificate>) certFactory.generateCertificates(certIs)) {
                    // generate an alias based on the certificate's subject name
                    // using hashCode to ensure uniqueness
                    String alias = "cert_" + Integer.toHexString(cert.getSubjectX500Principal().getName().hashCode());
                    log.info("Processing certificate with alias: {}", alias);
                    if (trustStore.containsAlias(alias)) {
                        log.warn("Certificate with alias '{}' already exists, skipping", alias);
                    } else {
                        trustStore.setCertificateEntry(alias, cert);
                        log.info("Imported certificate: {}", cert.getSubjectX500Principal().getName());
                    }
                }
            }
            // save the KeyStore
            try (FileOutputStream fos = new FileOutputStream(truststorePath)) {
                trustStore.store(fos, truststorePassword);
                log.info("Truststore saved successfully to {}", truststorePath);
            }
        } catch (Exception e) {
            log.error("Error managing truststore", e);
        }
        return trustStore;
    }
}