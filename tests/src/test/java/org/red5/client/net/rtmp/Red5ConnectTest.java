package org.red5.client.net.rtmp;

import org.junit.After;
import org.junit.Before;

/**
 * Tests for connecting to red5 servers.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class Red5ConnectTest extends PublisherTest {

    // XXX(paul) verified working on 2025-07-27

    @Before
    public void setUp() throws Exception {
        super.setUp();
        log.info("Setting up Red5ConnectTest");
        // set system properties for the RTMP handshake
        //System.setProperty("use.fp9.handshake", "false"); // false to use the older handshake
        // set system properties for keystore and truststore as Twitch
        System.setProperty("javax.net.ssl.trustStorePassword", "password123");
        System.setProperty("javax.net.ssl.trustStore", "tests/conf/rtmps_truststore.p12");
        // set up debug logging for SSL
        System.setProperty("javax.net.debug", "ssl");
        // debug
        if (log.isDebugEnabled()) {
            //System.setProperty("javax.net.debug", "ssl,handshake,verbose,data,keymanager,record"); // keyStore focused
            //System.setProperty("javax.net.debug", "ssl,handshake,verbose,data,trustmanager,record"); // truststore focused
        }
        if (log.isTraceEnabled()) {
            System.setProperty("javax.net.debug", "ssl,handshake,verbose,data,keymanager,trustmanager,record,plaintext");
        }
        // Red5 server ingest server details
        host = "10.0.0.35"; // replace with your Red5 server IP or hostname
        port = 1935;
        app = "live";
        streamKey = "rtmpClientStream1"; // replace with your own stream key
        log.info("Stream key: {}", streamKey);
    }

    @After
    public void tearDown() {
        log.info("Tearing down Red5ConnectTest");
        try {
            super.tearDown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Red5ConnectTest test = new Red5ConnectTest();
        test.setSecure(false);
        try {
            test.setUp();
            test.testPublish();
            test.tearDown();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.exit(0);
        }
    }

}
