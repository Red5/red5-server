package org.red5.server.tomcat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.net.InetAddress;
import java.nio.file.Files;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies that {@link TomcatConnector} configures TLS from a PEM certificate and private key.
 */
public class TomcatConnectorPemTest {

    private File dir;

    private File certFile;

    private File keyFile;

    private Connector connector;

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("tomcat-pem").toFile();
        certFile = new File(dir, "cert.pem");
        keyFile = new File(dir, "key.pem");
        Process p = new ProcessBuilder("openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1", "-subj", "/CN=localhost", "-keyout", keyFile.getAbsolutePath(), "-out", certFile.getAbsolutePath()).redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        assumeTrue("openssl not available to generate a test certificate", p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0);
    }

    @After
    public void tearDown() throws Exception {
        if (connector != null) {
            connector.stop();
            connector.destroy();
        }
        for (File f : dir.listFiles()) {
            f.delete();
        }
        dir.delete();
    }

    private TomcatConnector newConnector(Map<String, String> props) {
        TomcatConnector tc = new TomcatConnector();
        tc.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
        tc.setAddress("127.0.0.1:0");
        tc.setSecure(true);
        tc.setConnectionProperties(props);
        return tc;
    }

    private Map<String, String> pemProps() {
        Map<String, String> props = new HashMap<>();
        props.put("certificateFile", certFile.getAbsolutePath());
        props.put("certificateKeyFile", keyFile.getAbsolutePath());
        props.put("protocols", "TLSv1.2,TLSv1.3");
        return props;
    }

    @Test
    public void testPemConfiguresCertificate() {
        connector = newConnector(pemProps()).getConnector();
        SSLHostConfig[] configs = connector.getProtocolHandler().findSslHostConfigs();
        assertEquals(1, configs.length);
        SSLHostConfigCertificate cert = configs[0].getCertificates().iterator().next();
        assertEquals(certFile.getAbsolutePath(), cert.getCertificateFile());
        assertEquals(keyFile.getAbsolutePath(), cert.getCertificateKeyFile());
    }

    @Test
    public void testPemMissingKeyFileIsRejected() {
        Map<String, String> props = pemProps();
        props.remove("certificateKeyFile");
        connector = newConnector(props).getConnector();
        // init failure is logged, so no ssl host config may be registered
        assertEquals(0, connector.getProtocolHandler().findSslHostConfigs().length);
    }

    @Test
    public void testPemHandshake() throws Exception {
        connector = newConnector(pemProps()).getConnector();
        assertNotNull(connector);
        connector.init();
        connector.start();
        int port = connector.getLocalPort();
        assertTrue("connector did not bind", port > 0);
        // trust anything, the check is that the server presents the PEM certificate
        TrustManager[] trustAll = new TrustManager[] { new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        } };
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, trustAll, null);
        try (SSLSocket socket = (SSLSocket) ctx.getSocketFactory().createSocket(InetAddress.getByName("127.0.0.1"), port)) {
            socket.setSoTimeout(10000);
            socket.startHandshake();
            X509Certificate served = (X509Certificate) socket.getSession().getPeerCertificates()[0];
            assertTrue(served.getSubjectX500Principal().getName().contains("CN=localhost"));
        }
    }

}
