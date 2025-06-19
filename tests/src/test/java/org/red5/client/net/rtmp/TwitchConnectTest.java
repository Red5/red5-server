package org.red5.client.net.rtmp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.red5.client.net.rtmps.RTMPSClient;
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
        // set system properties for keystore and truststore as Twitch
        System.setProperty("javax.net.ssl.trustStorePassword", "password123");
        System.setProperty("javax.net.ssl.trustStore", "conf/rtmps_truststore.p12");
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
        host = "ingest.global-contribute.live-video.net";
        port = 1935;
        app = "app";
        streamKey = "live_107484810_EdWk5R6WYyT8XIfL7JLBi2RY86ZMyc"; // replace with your own stream key
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
     * @throws InterruptedException if the thread is interrupted during execution
     */
    @Test
    public void testPublish() throws InterruptedException {
        log.info("\ntestPublish");
        client = new RTMPSClient();
        client.setConnectionClosedHandler(() -> {
            log.info("Test - exit");
        });
        client.setExceptionHandler(new ClientExceptionHandler() {
            @Override
            public void handleException(Throwable throwable) {
                throwable.printStackTrace();
            }
        });
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
                        publishing = true;
                        executor.submit(() -> {
                            client.invoke("FCPublish", new Object[] { streamKey }, new IPendingServiceCallback() {
                                @Override
                                public void resultReceived(IPendingServiceCall call) {
                                    Object result = call.getResult();
                                    log.info("FCPublish result: {}", result);
                                }
                            });
                        });
                        break;
                    case StatusCodes.NS_UNPUBLISHED_SUCCESS:
                        log.info("Publishing stopped, stream: {}", streamKey);
                        publishing = false;
                        executor.submit(() -> {
                            client.invoke("FCUnpublish", new Object[] { streamKey }, new IPendingServiceCallback() {
                                @Override
                                public void resultReceived(IPendingServiceCall call) {
                                    Object result = call.getResult();
                                    log.info("FCUnpublish result: {}", result);
                                }
                            });
                        });
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
                log.info("connectCallback");
                ObjectMap<?, ?> map = (ObjectMap<?, ?>) call.getResult();
                String code = (String) map.get("code");
                log.info("Response code: {}", code);
                if (StatusCodes.NC_CONNECT_SUCCESS.equals(code)) {
                    log.info("Connected to Twitch server");
                    executor.submit(() -> {
                        // release the stream before publishing
                        client.releaseStream(new IPendingServiceCallback() {
                            @Override
                            public void resultReceived(IPendingServiceCall call) {
                                log.info("Stream released: {}", call.getResult());
                                client.createStream(new IPendingServiceCallback() {
                                    @Override
                                    public void resultReceived(IPendingServiceCall call) {
                                        streamId = (Double) call.getResult();
                                        log.info("Stream created: {}", streamId);
                                        client.publish(streamId, streamKey, "live", netStreamEventHandler);
                                    }
                                });
                            }
                        }, new Object[] { streamKey });
                    });
                } else if (StatusCodes.NC_CONNECT_REJECTED.equals(code)) {
                    System.out.printf("Rejected: %s\n", map.get("description"));
                    client.disconnect();
                    finished.set(true);
                } else {
                    log.warn("Unexpected response code: {}", code);
                    finished.set(true);
                }
            }
        };
        // connect
        client.connect(host, port, app, connectCallback);
        executor.submit(() -> {
            while (!publishing) {
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
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
                    log.warn("streaming error {}", e1);
                }
            } while (!que.isEmpty());
            client.unpublish(streamId);
        });
        Thread.currentThread().join(30000L);
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
        }
    }

}
