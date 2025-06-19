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
import org.red5.server.stream.message.RTMPMessage;

/**
 * Tests for connecting to Facebook live servers. This test requires a valid Facebook account and a stream key.
 * The stream key can be obtained from the Facebook live video setup page.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class FBLiveConnectTest extends PublisherTest {

    // example url: rtmps://live-api-s.facebook.com:443/rtmp/FB-4177549469201521-0-Ab2WZb797amQLgvwvyT_6nnM
    // rtmps://live-api-s.facebook.com:443/rtmp/
    // rtmps://edgetee-upload-phx.xx.fbcdn.net:443/rtmp/

    @Before
    public void setUp() throws Exception {
        super.setUp();
        log.info("Setting up FBLiveConnectTest");
        // set system properties for keystore and truststore as Facebook live requires SSL/TLS
        System.setProperty("javax.net.ssl.trustStorePassword", "password123");
        System.setProperty("javax.net.ssl.trustStore", "conf/rtmps_truststore.p12");
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
        streamKey = "FB-4179321645690970-0-Ab2VYp26L8YNem_v2i6m3Q42"; // replace with your own stream key
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
     * @throws InterruptedException
     */
    @Test
    public void testPublish() throws InterruptedException {
        log.info("\n testFBLivePublish - streamKey: {}", streamKey);
        // Facebook live ingest server is RTMPS only now
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
        final INetStreamEventHandler handler = new INetStreamEventHandler() {
            @Override
            public void onStreamEvent(Notify notify) {
                log.info("ClientStream.onStreamEvent: {}", notify);
                IServiceCall call = notify.getCall();
                if ("onStatus".equals(call.getServiceMethodName())) {
                    @SuppressWarnings("rawtypes")
                    ObjectMap status = ((ObjectMap) call.getArguments()[0]);
                    String code = (String) status.get("code");
                    switch (code) {
                        case "NetStream.Publish.Start":
                            publishing = true;
                            break;
                        case "NetStream.UnPublish.Success":
                            publishing = false;
                            break;
                        case "NetStream.Publish.Failed":

                            break;
                    }
                }
            }
        };
        client.setStreamEventHandler(handler);
        IPendingServiceCallback connectCallback = new IPendingServiceCallback() {
            @Override
            public void resultReceived(IPendingServiceCall call) {
                log.info("connectCallback");
                ObjectMap<?, ?> map = (ObjectMap<?, ?>) call.getResult();
                String code = (String) map.get("code");
                log.info("Response code: {}", code);
                if ("NetConnection.Connect.Rejected".equals(code)) {
                    System.out.printf("Rejected: %s\n", map.get("description"));
                    client.disconnect();
                    finished.set(true);
                } else if ("NetConnection.Connect.Success".equals(code)) {
                    client.createStream(new IPendingServiceCallback() {
                        @Override
                        public void resultReceived(IPendingServiceCall call) {
                            double streamId = (Double) call.getResult();
                            client.publish(streamId, streamKey, "live", handler);
                        }
                    });
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
        FBLiveConnectTest test = new FBLiveConnectTest();
        try {
            test.setUp();
            test.testPublish();
            test.tearDown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
