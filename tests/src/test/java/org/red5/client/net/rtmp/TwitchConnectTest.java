package org.red5.client.net.rtmp;

import org.junit.After;
import org.junit.Before;
import org.red5.io.utils.ObjectMap;

/**
 * Tests for connecting to Twitch servers.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class TwitchConnectTest extends PublisherTest {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        log.info("Setting up TwitchConnectTest");
        // set system properties for the RTMP handshake
        System.setProperty("use.fp9.handshake", "false"); // false to use the older handshake
        // set system properties for keystore and truststore as Twitch
        System.setProperty("javax.net.ssl.trustStorePassword", "password123");
        System.setProperty("javax.net.ssl.trustStore", "tests/conf/rtmps_truststore.p12");
        // set up debug logging for SSL
        //System.setProperty("javax.net.debug", "ssl");
        // debug
        if (log.isDebugEnabled()) {
            //System.setProperty("javax.net.debug", "ssl,handshake,verbose,data,keymanager,record"); // keyStore focused
            //System.setProperty("javax.net.debug", "ssl,handshake,verbose,data,trustmanager,record"); // truststore focused
        }
        if (log.isTraceEnabled()) {
            System.setProperty("javax.net.debug", "ssl,handshake,verbose,data,keymanager,trustmanager,record,plaintext");
        }
        // Twitch ingest server details
        //host = "ingest.global-contribute.live-video.net";
        host = "lax.contribute.live-video.net"; // Los Angeles region
        port = 1935;
        app = "app"; // Standard RTMP application name for Twitch
        streamKey = "live_107484810_QSnKJKfaSjFigTqRQ1o0Y38ggnope"; // Stream key for publish command
        log.info("Stream key: {}", streamKey);
        // NOTE: This test requires a valid/active Twitch stream key to work properly
        // Twitch-specific configuration: Use standard workflow without FC commands
        // Twitch workflow: connect -> releaseStream (optional) -> createStream -> publish
        setStartWithReleaseStream(true); // Try releaseStream first (ignore if it fails)
        setUseFCCommands(true);
        // Set up connection parameters for Twitch
        ObjectMap<String, Object> params = new ObjectMap<>();
        params.put("app", app);
        params.put("type", "nonprivate");
        params.put("flashVer", "FMLE/3.0 (compatible; FMSc/1.0)"); // OBS-like user agent
        params.put("tcUrl", "rtmp://" + host + ":" + port + "/" + app);
        params.put("objectEncoding", Integer.valueOf(0)); // AMF0 encoding
        params.put("fpad", Boolean.FALSE);
        params.put("audioCodecs", Integer.valueOf(3575)); // Standard OBS audio codecs
        params.put("videoCodecs", Integer.valueOf(252)); // Standard OBS video codecs
        params.put("videoFunction", Integer.valueOf(1));
        params.put("capabilities", Integer.valueOf(15));
        // Add swfUrl which some servers expect
        params.put("swfUrl", params.get("tcUrl") + "/swf/Red5Publisher.swf");
        params.put("pageUrl", "");
        params.put("streamKey", streamKey); // Use the Twitch stream key for publishing
        // Set the connection parameters in the test instance
        setConnectionParams(params);
    }

    @After
    public void tearDown() {
        log.info("Tearing down TwitchConnectTest");
        try {
            super.tearDown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Test for publishing a stream to Twitch.
     * This method connects to the Twitch RTMP server, creates a stream, and publishes it.
     * It handles various stream events such as start, stop, and errors.
     *
     * Note: This test requires a valid Twitch account and a stream key.
     *
     * A working order for Twitch streaming is as follows:
     * 1. Connect to the Twitch RTMP server - connect
     * 2. Release the stream before publishing - releaseStream
     * 3. Prepare the stream for publishing - FCPublish
     * 4. Create the stream - createStream
     * 5. Publish the stream - publish
     * Once the stream is published, it can be used to send video/audio data. When done, it should be unpublished using
     * FCUnpublish.
     *
     * Lastly, it seems best to use the unversioned RTMP connection handshake, instead of newer styles.
     *
     */

    public static void main(String[] args) {
        TwitchConnectTest test = new TwitchConnectTest();
        // Twitch allows both RTMP and RTMPS connections
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
