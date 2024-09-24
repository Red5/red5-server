package org.red5.client.net.rtmp;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.red5.io.utils.ObjectMap;
import org.red5.server.api.Red5;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.event.IEventDispatcher;
import org.red5.server.api.service.IPendingServiceCall;
import org.red5.server.api.service.IPendingServiceCallback;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.event.Notify;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for connecting to YouTube servers.
 * <pre>
 * rtmpdump -V -z -r "rtmp://a.rtmp.youtube.com/live2" -a "live2" -y "<your stream name here>" -v -f "WIN 11,2,202,235"
 * </pre>
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class YouTubeConnectTest {

    private Logger log = LoggerFactory.getLogger(YouTubeConnectTest.class);

    private AtomicBoolean finished = new AtomicBoolean(false);

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testYouTubePublish() throws InterruptedException {
        log.info("\ntestYouTubePublish");
        String youtubeHost = "a.rtmp.youtube.com";
        int youtubePort = 1935;
        String youtubeApp = "live2";
        final String youtubePublishName = "dybx-y3ph-uqzx-30vx"; //System.getProperty("youtube.streamname");
        log.info("youtubePublishName: {}", youtubePublishName);
        //        if (youtubePublishName == null) {
        //            log.info("You forgot to set a 'youtube.streamname' system property");
        //            return;
        //        }

        final RTMPClient client = new RTMPClient();
        client.setConnectionClosedHandler(new Runnable() {
            @Override
            public void run() {
                log.info("Test - exit");
            }
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
                if ("NetConnection.Connect.Rejected".equals(code)) {
                    System.out.printf("Rejected: %s\n", map.get("description"));
                    client.disconnect();
                    finished.set(true);
                } else if ("NetConnection.Connect.Success".equals(code)) {
                    client.createStream(new IPendingServiceCallback() {
                        @Override
                        public void resultReceived(IPendingServiceCall call) {
                            double streamId = (Double) call.getResult();
                            // live buffer 0.5s
                            @SuppressWarnings("unused")
                            RTMPConnection conn = (RTMPConnection) Red5.getConnectionLocal();
                            //conn.ping(new Ping(Ping.CLIENT_BUFFER, streamId, 500));
                            //client.play(streamId, youtubePublishName, -1, -1);
                            client.publish(streamId, youtubePublishName, "live", netStreamEventHandler);
                        }
                    });
                    // push data out for the publish
                    //test();
                }
            }
        };
        // connect
        client.connect(youtubeHost, youtubePort, youtubeApp, connectCallback);

        Thread.currentThread().join(30000L);
        client.disconnect();
        log.info("Test - end");
    }

    //@Test
    public void testLocalhostRed5Publish() throws InterruptedException {
        log.info("\ntestLocalhostRed5Publish");
        String host = "localhost";
        int port = 1935;
        // check to see if a server is listening on 1935 before proceeding

        String app = "live";
        final String publishName = "test";
        final RTMPClient client = new RTMPClient();
        client.setConnectionClosedHandler(new Runnable() {
            @Override
            public void run() {
                log.info("Test - exit");
            }
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
                log.info("ClientStream.dispachEvent() {}", event);
            }
        });
        final INetStreamEventHandler netStreamEventHandler = new INetStreamEventHandler() {
            @Override
            public void onStreamEvent(Notify notify) {
                log.info("ClientStream.dispachEvent() {}", notify);
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
                if ("NetConnection.Connect.Rejected".equals(code)) {
                    System.out.printf("Rejected: %s\n", map.get("description"));
                    client.disconnect();
                    finished.set(true);
                } else if ("NetConnection.Connect.Success".equals(code)) {
                    client.createStream(new IPendingServiceCallback() {
                        @Override
                        public void resultReceived(IPendingServiceCall call) {
                            int streamId = (Integer) call.getResult();
                            // live buffer 0.5s
                            @SuppressWarnings("unused")
                            RTMPConnection conn = (RTMPConnection) Red5.getConnectionLocal();
                            //conn.ping(new Ping(Ping.CLIENT_BUFFER, streamId, 500));
                            //client.play(streamId, youtubePublishName, -1, -1);
                            client.publish(streamId, publishName, "live", netStreamEventHandler);
                        }
                    });
                    // push data out for the publish
                    //test();
                }
            }
        };
        // connect
        client.connect(host, port, app, connectCallback);

        Thread.currentThread().join(30000L);
        client.disconnect();
        log.info("Test - end");
    }

}
