package org.red5.client.net.rtmps;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to grab and save server certificates from a given host and port; useful for TLS connections to ensure
 * a server's certificate is trusted.
 *
 * Usage:
 * Call the `retrieveCertificate` method with the desired host and port to save the server's certificate in PEM format.
 *
 * @author Paul Gregoire
 */
public class CertificateGrabber {

    private static Logger log = LoggerFactory.getLogger(CertificateGrabber.class);

    /**
     * Retrieves the full certificate chain from the specified host and port.
     * This includes the server certificate and all intermediate CA certificates,
     * which are required for proper TLS validation.
     *
     * @param host the hostname to connect to
     * @param port the port to connect to
     * @throws Exception if an error occurs while retrieving certificates
     */
    public static void retrieveCertificate(String host, int port) throws Exception {
        // Create a trust manager that accepts all certificates (for retrieval only)
        TrustManager[] trustManagers = new TrustManager[] { new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        } };
        // Create SSL context
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagers, null);
        // Connect to the server
        SSLSocketFactory factory = sslContext.getSocketFactory();
        try (SSLSocket socket = (SSLSocket) factory.createSocket(host, port)) {
            socket.startHandshake();
            // Get the full certificate chain
            SSLSession session = socket.getSession();
            Certificate[] certs = session.getPeerCertificates();
            // Save the full certificate chain
            if (certs != null && certs.length > 0) {
                // check for path to store the certificate
                String truststorePath = System.getProperty("javax.net.ssl.trustStore");
                if (truststorePath == null || truststorePath.isEmpty()) {
                    throw new IllegalStateException("Truststore path is not set. Please set 'javax.net.ssl.trustStore' system property.");
                }
                String pemPath = truststorePath.substring(0, truststorePath.lastIndexOf('/'));
                log.info("CertificateGrabber - pemPath: {}, chain length: {}", pemPath, certs.length);
                // Save all certificates in the chain to a single PEM file
                saveCertificateChain(certs, String.format("%s/%s.pem", pemPath, host));
                // Log info about each certificate in the chain
                for (int i = 0; i < certs.length; i++) {
                    X509Certificate cert = (X509Certificate) certs[i];
                    log.debug("Certificate[{}] subject: {} issuer: {} serial: {} valid: {} to {}", i, cert.getSubjectX500Principal(), cert.getIssuerX500Principal(), cert.getSerialNumber(), cert.getNotBefore(), cert.getNotAfter());
                }
            }
        }
    }

    /**
     * Saves the full certificate chain to a file in PEM format.
     * All certificates are written to a single file, which can then be
     * imported into a truststore.
     *
     * @param certs the certificate chain to save
     * @param fileName name of the file to save the certificates to
     * @throws Exception if an error occurs while saving the certificates
     */
    private static void saveCertificateChain(Certificate[] certs, String fileName) throws Exception {
        // Save all certificates as PEM format in a single file
        try (FileWriter fw = new FileWriter(fileName); PrintWriter pw = new PrintWriter(fw)) {
            for (int i = 0; i < certs.length; i++) {
                X509Certificate cert = (X509Certificate) certs[i];
                pw.println("-----BEGIN CERTIFICATE-----");
                pw.println(Base64.encodeBase64String(cert.getEncoded()));
                pw.println("-----END CERTIFICATE-----");
                if (i < certs.length - 1) {
                    pw.println(); // blank line between certificates
                }
            }
        }
        log.info("Certificate chain ({} certs) saved to: {}", certs.length, fileName);
    }

    /**
     * Saves the given X509 certificate to a file in PEM format.
     *
     * @param cert the X509 certificate to save
     * @param fileName name of the file to save the certificate to
     * @throws Exception if an error occurs while saving the certificate
     */
    @SuppressWarnings("unused")
    private static void saveCertificate(X509Certificate cert, String fileName) throws Exception {
        // Save as PEM format
        try (FileWriter fw = new FileWriter(fileName); PrintWriter pw = new PrintWriter(fw)) {
            pw.println("-----BEGIN CERTIFICATE-----");
            pw.println(Base64.encodeBase64String(cert.getEncoded()));
            pw.println("-----END CERTIFICATE-----");
        }
        log.debug("Certificate saved to: {}", fileName);
    }

}
