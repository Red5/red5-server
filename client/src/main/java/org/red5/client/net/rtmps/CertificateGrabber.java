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
     * Retrieves the server certificate from the specified host and port.
     *
     * @param host
     * @param port
     * @throws Exception
     */
    public static void retrieveCertificate(String host, int port) throws Exception {
        // Create a trust manager that captures certificates
        final X509Certificate[] serverCert = new X509Certificate[1];
        TrustManager[] trustManagers = new TrustManager[] { new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                // Capture the server certificate
                if (chain != null && chain.length > 0) {
                    serverCert[0] = chain[0];
                }
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
            // Get the certificate chain
            SSLSession session = socket.getSession();
            Certificate[] certs = session.getPeerCertificates();
            // Save the certificate
            if (certs != null && certs.length > 0) {
                X509Certificate cert = (X509Certificate) certs[0];
                saveCertificate(cert, String.format("%s.pem", host));
                log.debug("Certificate subject: {} issuer: {}\n serial number: {}\n valid from: {} to: {}", cert.getSubjectX500Principal(), cert.getIssuerX500Principal(), cert.getSerialNumber(), cert.getNotBefore(), cert.getNotAfter());
            }
        }
    }

    /**
     * Saves the given X509 certificate to a file in PEM format.
     *
     * @param cert the X509 certificate to save
     * @param fileName name of the file to save the certificate to
     * @throws Exception if an error occurs while saving the certificate
     */
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
