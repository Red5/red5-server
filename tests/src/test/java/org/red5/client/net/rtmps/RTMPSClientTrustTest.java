package org.red5.client.net.rtmps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Verifies RTMPS client server-identity checks against a local TLS server whose certificate is only valid for "localhost" (issue
 * #465).
 */
public class RTMPSClientTrustTest {

    private static final String PASSWORD = "changeit";

    private static Path dir;

    private static Path serverKeystore;

    private static Path serverPem;

    private SSLServerSocket serverSocket;

    private CompletableFuture<Boolean> handshake;

    @BeforeClass
    public static void createCertificate() throws Exception {
        dir = Files.createTempDirectory("rtmps-trust");
        serverKeystore = dir.resolve("server.p12");
        serverPem = dir.resolve("server-cert.pem");
        keytool("-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-keystore", serverKeystore.toString(), "-storetype", "PKCS12", "-storepass", PASSWORD, "-keypass", PASSWORD);
        keytool("-exportcert", "-rfc", "-alias", "server", "-keystore", serverKeystore.toString(), "-storetype", "PKCS12", "-storepass", PASSWORD, "-file", serverPem.toString());
    }

    @Before
    public void startServer() throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(serverKeystore.toFile())) {
            ks.load(in, PASSWORD.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, PASSWORD.toCharArray());
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), null, null);
        serverSocket = (SSLServerSocket) context.getServerSocketFactory().createServerSocket(0, 1, InetAddress.getLoopbackAddress());
        handshake = CompletableFuture.supplyAsync(() -> {
            try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                socket.setSoTimeout(5000);
                socket.startHandshake();
                // TLS 1.3 clients report certificate failures after the server finishes, so wait for the client's next move
                socket.getInputStream().read();
                return true;
            } catch (Exception e) {
                return false;
            }
        });
    }

    @After
    public void stopServer() throws Exception {
        serverSocket.close();
    }

    @Test
    public void testUntrustedServerIsRejected() throws Exception {
        TestClient client = new TestClient(dir.resolve("empty-truststore.p12"));
        client.connect("localhost", serverSocket.getLocalPort());
        assertFalse("handshake with an untrusted certificate must fail", handshake.get(10, TimeUnit.SECONDS));
        assertFalse("no certificate may be enrolled without trust-on-first-use", Files.exists(dir.resolve("localhost.pem")));
    }

    @Test
    public void testTrustedServerIsAccepted() throws Exception {
        Path truststore = dir.resolve("trusted.p12");
        P12StoreManager.buildTrustStore(truststore.toString(), PASSWORD.toCharArray(), serverPem.toString());
        TestClient client = new TestClient(truststore);
        client.connect("localhost", serverSocket.getLocalPort());
        assertTrue("handshake with a trusted certificate must succeed", handshake.get(10, TimeUnit.SECONDS));
    }

    @Test
    public void testWrongHostIsRejected() throws Exception {
        Path truststore = dir.resolve("trusted-wronghost.p12");
        P12StoreManager.buildTrustStore(truststore.toString(), PASSWORD.toCharArray(), serverPem.toString());
        TestClient client = new TestClient(truststore);
        // the certificate names only "localhost"
        client.connect("127.0.0.1", serverSocket.getLocalPort());
        assertFalse("handshake with a certificate for another host must fail", handshake.get(10, TimeUnit.SECONDS));
    }

    @Test
    public void testExplicitTrustOnFirstUseEnrolls() throws Exception {
        Path tofuDir = Files.createDirectories(dir.resolve("tofu"));
        Path truststore = tofuDir.resolve("truststore.p12");
        TestClient client = new TestClient(truststore);
        client.setTrustOnFirstUse(true);
        // the enrollment connection consumes the first accept; restart the server for the real connection
        CompletableFuture<Void> enrollment = CompletableFuture.runAsync(() -> client.connect("localhost", serverSocket.getLocalPort()));
        handshake.get(10, TimeUnit.SECONDS);
        int port = serverSocket.getLocalPort();
        serverSocket.close();
        enrollment.get(20, TimeUnit.SECONDS);
        assertTrue(Files.exists(tofuDir.resolve("localhost.pem")));
        assertTrue(Files.exists(truststore));
        // a second client trusts the enrolled chain without enrolling again
        startServer();
        TestClient second = new TestClient(truststore);
        second.connect("localhost", serverSocket.getLocalPort());
        assertTrue("enrolled certificate must be trusted", handshake.get(10, TimeUnit.SECONDS));
        assertEquals(1, Files.list(tofuDir).filter(p -> p.toString().endsWith(".pem")).count());
        assertTrue(port > 0);
    }

    private static void keytool(String... args) throws Exception {
        String[] command = new String[args.length + 1];
        command[0] = System.getProperty("java.home") + File.separator + "bin" + File.separator + "keytool";
        System.arraycopy(args, 0, command, 1, args.length);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals("keytool failed: " + output, 0, process.waitFor());
    }

    private static class TestClient extends RTMPSClient {

        TestClient(Path truststore) throws Exception {
            super("PKCS12", PASSWORD, null, PASSWORD, truststore.toString());
        }

        void connect(String host, int port) {
            startConnector(host, port);
        }

    }

}
