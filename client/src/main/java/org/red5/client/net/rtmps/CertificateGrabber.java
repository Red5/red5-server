package org.red5.client.net.rtmps;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.security.MessageDigest;
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
     * Retrieves the full certificate chain from the specified host and port and saves it next to the truststore named by the
     * javax.net.ssl.trustStore system property. The chain is NOT verified; see {@link #retrieveCertificate(String, int, String)}.
     *
     * @param host the hostname to connect to
     * @param port the port to connect to
     * @throws Exception if an error occurs while retrieving certificates
     */
    public static void retrieveCertificate(String host, int port) throws Exception {
        String truststorePath = System.getProperty("javax.net.ssl.trustStore");
        if (truststorePath == null || truststorePath.isEmpty()) {
            throw new IllegalStateException("Truststore path is not set. Please set 'javax.net.ssl.trustStore' system property.");
        }
        String pemPath = truststorePath.substring(0, truststorePath.lastIndexOf('/'));
        retrieveCertificate(host, port, String.format("%s/%s.pem", pemPath, host));
    }

    /**
     * Retrieves the full certificate chain from the specified host and port, including intermediate CA certificates, and saves it
     * to the given PEM file.
     * <p>
     * The chain is accepted WITHOUT verification, so whoever answers on the network at that moment is the one recorded. Only use
     * this for explicit trust-on-first-use enrollment, and compare the logged SHA-256 fingerprint with one obtained out of band.
     *
     * @param host the hostname to connect to
     * @param port the port to connect to
     * @param pemFile file to write the PEM encoded chain to
     * @return the retrieved chain, server certificate first
     * @throws Exception if an error occurs while retrieving certificates
     */
    public static X509Certificate[] retrieveCertificate(String host, int port, String pemFile) throws Exception {
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
            if (certs == null || certs.length == 0) {
                throw new IllegalStateException("No certificates presented by " + host + ":" + port);
            }
            X509Certificate[] chain = new X509Certificate[certs.length];
            for (int i = 0; i < certs.length; i++) {
                chain[i] = (X509Certificate) certs[i];
                log.debug("Certificate[{}] subject: {} issuer: {} serial: {} valid: {} to {}", i, chain[i].getSubjectX500Principal(), chain[i].getIssuerX500Principal(), chain[i].getSerialNumber(), chain[i].getNotBefore(), chain[i].getNotAfter());
            }
            // Save all certificates in the chain to a single PEM file
            saveCertificateChain(certs, pemFile);
            log.warn("Trusting unverified certificate for {}:{} subject: {} SHA-256: {}", host, port, chain[0].getSubjectX500Principal(), fingerprint(chain[0]));
            return chain;
        }
    }

    /**
     * Returns the SHA-256 fingerprint of a certificate as colon separated upper-case hex.
     *
     * @param cert certificate
     * @return fingerprint
     * @throws Exception if the certificate cannot be encoded
     */
    public static String fingerprint(X509Certificate cert) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
        StringBuilder sb = new StringBuilder(digest.length * 3);
        for (byte b : digest) {
            if (sb.length() > 0) {
                sb.append(':');
            }
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
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
