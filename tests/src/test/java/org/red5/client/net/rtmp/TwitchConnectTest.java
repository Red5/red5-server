package org.red5.client.net.rtmp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.red5.io.utils.ObjectMap;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.event.IEventDispatcher;
import org.red5.server.api.service.IPendingServiceCall;
import org.red5.server.api.service.IPendingServiceCallback;
import org.red5.server.api.service.IServiceCall;
import org.red5.server.net.rtmp.event.Notify;
import org.red5.server.net.rtmp.status.StatusCodes;
import org.red5.server.stream.message.RTMPMessage;

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
        System.setProperty("javax.net.debug", "ssl");
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
        app = "app";
        // validate against the local Red5 server
        //host = "localhost";
        //app = "live";
        streamKey = "live_107484810_wnHKDftISXEIATEbGlCL2vmV5Xxxxnope"; // replace with your own stream key
        log.info("Stream key: {}", streamKey);
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
     * @throws InterruptedException if the thread is interrupted during execution
     */
    @Test
    public void testPublish() throws InterruptedException {
        log.info("\ntestPublish");
        client = new RTMPClient();
        client.setConnectionClosedHandler(() -> {
            log.info("Test - exit");
        });
        ClientExceptionHandler clientExceptionHandler = new ClientExceptionHandler() {
            @Override
            public void handleException(Throwable throwable) {
                log.warn("Exception in handleException", throwable);
            }
        };
        client.setExceptionHandler(clientExceptionHandler);
        client.setStreamEventDispatcher(new IEventDispatcher() {
            @Override
            public void dispatchEvent(IEvent event) {
                log.info("ClientStream.dispachEvent: {}", event);
            }
        });
        final INetStreamEventHandler netStreamEventHandler = new INetStreamEventHandler() {
            @Override
            public void onStreamEvent(Notify notify) {
                log.info("ClientStream.onStreamEvent: {}", notify);
                IServiceCall call = notify.getCall();
                String methodName = call.getServiceMethodName();
                log.debug("Method: {}", methodName);
                ObjectMap<?, ?> map = (ObjectMap<?, ?>) call.getArguments()[0];
                String code = (String) map.get("code");
                log.debug("Code: {}", code);
                switch (code) {
                    case StatusCodes.NS_PUBLISH_START:
                        log.info("Publishing stream: {}", streamKey);
                        break;
                    case StatusCodes.NS_UNPUBLISHED_SUCCESS:
                        log.info("Publishing stopped, stream: {}", streamKey);
                        break;
                    case StatusCodes.NC_CALL_FAILED:
                        log.warn("Failed to publish stream: {}", map.get("description"));
                        finished.set(true);
                        break;
                    case StatusCodes.NS_PUBLISH_BADNAME:
                        log.warn("Bad name for stream: {}", map.get("description"));
                        finished.set(true);
                        break;
                    default:
                        log.warn("Unhandled code: {} for method: {}", code, methodName);
                }
            }
        };
        client.setStreamEventHandler(netStreamEventHandler);
        IPendingServiceCallback connectCallback = new IPendingServiceCallback() {
            @Override
            public void resultReceived(IPendingServiceCall call) {
                String method = call.getServiceMethodName();
                log.debug("resultReceived: {}", method);
                // use self for this callback for reusability
                final IPendingServiceCallback self = this;
                switch (method) {
                    case "connect":
                        log.info("Connected to Twitch server");
                        // set the source type attribute on our connection
                        //client.getConnection().setAttribute("sourceType", "RTMP");
                        // Thread.ofVirtual().uncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                        //     @Override
                        //     public void uncaughtException(Thread t, Throwable e) {
                        //         log.error("Uncaught exception in thread: {}", t.getName(), e);
                        //         clientExceptionHandler.handleException(e);
                        //     }
                        // }).start(() -> {
                        //     // check for onBWDone
                        //     log.debug("Bandwidth check done: {}", client.isBandwidthCheckDone());
                        //     // initiate the stream creation
                        //     client.createStream(self);
                        // });
                        // release the stream before publishing
                        Thread.ofVirtual().start(() -> {
                            log.info("Releasing stream: {}", streamKey);
                            client.invoke("releaseStream", new Object[] { streamKey }, self);
                        });
                        //client.releaseStream(self, new Object[] { streamKey });
                        // prepare the stream for publishing, skip releaseStream
                        //client.invoke("FCPublish", new Object[] { streamKey }, self);
                        break;
                    case "releaseStream":
                        log.info("Stream released: {}", streamKey);
                        Thread.ofVirtual().start(() -> {
                            client.invoke("FCPublish", new Object[] { streamKey }, self);
                        });
                        break;
                    case "FCPublish":
                        log.info("FCPublish called for stream: {}", streamKey);
                        // now we can create the stream
                        Thread.ofVirtual().start(() -> {
                            client.createStream(self);
                        });
                        break;
                    case "createStream":
                        log.info("Created stream for: {}", streamKey);
                        streamId = (Double) call.getResult();
                        log.info("Stream created with ID: {}", streamId);
                        // now we can publish
                        Thread.ofVirtual().start(() -> {
                            client.publish(streamId, streamKey, "live", netStreamEventHandler);
                        });
                        break;
                    case "publish":
                        log.info("Stream published: {}", streamKey);
                        publishing = true;
                        break;
                    case "FCUnpublish":
                        log.info("Stream unpublished: {}", streamKey);
                        publishing = false;
                        break;
                    default:
                        log.warn("Unexpected result: {}", method, call.getArguments());
                        //client.invoke("FCUnpublish", new Object[] { streamKey }, self);
                        client.disconnect();
                        finished.set(true);
                }
            }
        };
        // connect
        log.info("Connecting to Twitch server at {}:{}", host, port);
        // connect to the Twitch RTMP server
        client.connect(host, port, app, connectCallback);
        log.info("Preparing to publish stream: {}", streamKey);
        executor.submit(() -> {
            while (!publishing) {
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    log.warn("Interrupted while waiting for publishing to start", e);
                }
            }
            do {
                try {
                    RTMPMessage message = que.poll();
                    if (message != null && client != null) {
                        log.debug("Publishing message: {}", message);
                        client.publishStreamData(streamId, message);
                    } else {
                        Thread.sleep(3L);
                    }
                } catch (Exception e1) {
                    log.warn("Streaming error {}", e1);
                }
            } while (!que.isEmpty());
            client.unpublish(streamId);
        });
        log.info("Waiting for publish to complete");
        Thread.currentThread().join(30000L);
        log.info("Publish complete, disconnecting client");
        client.disconnect();
        log.info("Test - end");
    }

    public static void main(String[] args) {
        TwitchConnectTest test = new TwitchConnectTest();
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
