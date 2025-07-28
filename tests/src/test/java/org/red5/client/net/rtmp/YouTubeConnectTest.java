package org.red5.client.net.rtmp;

import org.junit.After;
import org.junit.Before;

/**
 * Tests for connecting to YouTube servers.
 * <pre>
 * rtmpdump -V -z -r "rtmp://a.rtmp.youtube.com/live2" -a "live2" -y "<your stream name here>" -v -f "WIN 11,2,202,235"
 * </pre>
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class YouTubeConnectTest extends PublisherTest {

    // XXX(paul) verified working on 2025-07-27

    @Before
    public void setUp() throws Exception {
        super.setUp();
        log.info("Setting up YouTubeConnectTest");
        // set system properties for the RTMP handshake
        System.setProperty("use.fp9.handshake", "false"); // false to use the older handshake
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
        host = "a.rtmp.youtube.com";
        port = 1935;
        app = "live2";
        streamKey = "cxqs-ec5x-m2qh-67pa-nope"; // replace with your own stream key
        log.info("Stream key: {}", streamKey);
        // Skip releaseStream step for YouTube or it will fail
        setStartWithReleaseStream(false);
        // YouTube doesn't use FCPublish and FCUnpublish commands
        setUseFCCommands(false);
    }

    @After
    public void tearDown() {
        log.info("Tearing down YouTubeConnectTest");
        try {
            super.tearDown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        YouTubeConnectTest test = new YouTubeConnectTest();
        // YouTube isn't using RTMPS connections
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
