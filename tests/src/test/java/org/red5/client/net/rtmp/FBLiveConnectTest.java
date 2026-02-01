package org.red5.client.net.rtmp;

import org.junit.After;
import org.junit.Before;
import org.junit.experimental.categories.Category;
import org.red5.test.IntegrationTest;

/**
 * Tests for connecting to Facebook live servers. This test requires a valid Facebook account and a stream key.
 * The stream key can be obtained from the Facebook live video setup page.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
@Category(IntegrationTest.class)
public class FBLiveConnectTest extends PublisherTest {

    // rtmps://live-api-s.facebook.com:443/rtmp/
    // XXX(paul) verified working on 2025-07-27

    @Before
    public void setUp() throws Exception {
        super.setUp();
        log.info("Setting up FBLiveConnectTest");
        // set system properties for keystore and truststore as Facebook live requires SSL/TLS
        System.setProperty("javax.net.ssl.trustStorePassword", "password123");
        System.setProperty("javax.net.ssl.trustStore", "tests/conf/rtmps_truststore.p12");
        // set up debugging logging for SSL
        System.setProperty("javax.net.debug", "ssl");
        // debug
        if (log.isDebugEnabled()) {
            //System.setProperty("javax.net.debug", "ssl,handshake,verbose,data,keymanager,record"); // keyStore focused
            //System.setProperty("javax.net.debug", "ssl,handshake,verbose,data,trustmanager,record"); // truststore focused
        }
        if (log.isTraceEnabled()) {
            System.setProperty("javax.net.debug", "ssl,handshake,verbose,data,keymanager,trustmanager,record,plaintext");
        }
        // Facebook live ingest server details
        host = "live-api-s.facebook.com";
        port = 443;
        app = "rtmp";
        streamKey = "FB-4215850858704715-0-nope"; // replace with your own stream key
        log.info("Stream key: {}", streamKey);
    }

    @After
    public void tearDown() {
        log.info("Tearing down FBLiveConnectTest");
        try {
            super.tearDown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Test for publishing a stream to Facebook live.
     * This test connects to the Facebook live ingest server and publishes a stream using the provided stream key.
     * It uses RTMPSClient to handle the RTMPS connection and stream publishing.
     * The test will run for a limited time and then disconnect.
     *
     * Note: This test requires a valid Facebook account and a stream key.
     * The stream key can be obtained from the Facebook live video setup page.
     *
     */
    public static void main(String[] args) {
        FBLiveConnectTest test = new FBLiveConnectTest();
        test.setSecure(true);
        try {
            test.setUp();
            test.testPublish();
            test.tearDown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
