package org.red5.client.net.rtmp;

import java.io.File;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.red5.io.ITag;
import org.red5.io.flv.impl.FLVReader;
import org.red5.io.utils.ObjectMap;
import org.red5.server.api.Red5;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.event.IEventDispatcher;
import org.red5.server.api.service.IPendingServiceCall;
import org.red5.server.api.service.IPendingServiceCallback;
import org.red5.server.api.service.IServiceCall;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.net.rtmp.event.IRTMPEvent;
import org.red5.server.net.rtmp.event.Invoke;
import org.red5.server.net.rtmp.event.Notify;
import org.red5.server.net.rtmp.event.Unknown;
import org.red5.server.net.rtmp.event.VideoData;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.stream.message.RTMPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.core.util.ExecutorServiceUtil;

/**
 * Tests for connecting to Facebook live servers.
 *
 * <pre>
 * rtmpdump -V -z -r "rtmp://a.rtmp.youtube.com/live2" -a "live2" -y "<your stream name here>" -v -f "WIN 11,2,202,235"
 * </pre>
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class FBLiveConnectTest {

    private Logger log = LoggerFactory.getLogger(FBLiveConnectTest.class);

    private AtomicBoolean finished = new AtomicBoolean(false);

    private ExecutorService executor;

    private FLVReader reader;

    private ConcurrentLinkedQueue<RTMPMessage> que = new ConcurrentLinkedQueue<>();

    private boolean publishing;

    private double streamId = 1.0d;

    private String host = "localhost"; //"rtmp-api.facebook.com";

    private int port = 1935; // 80;

    private String app = "live"; //"rtmp";

    private String publishName = "stream1"; //"1567066673326082?ds=1&s_l=1&a=ATiBCGoo4bLDTa4c";

    static {
        System.setProperty("red5.deployment.type", "junit");
    }

    @Before
    public void setUp() throws Exception {
        executor = Executors.newCachedThreadPool();
        reader = new FLVReader(new File(System.getProperty("user.dir") + "/src/test/resources/fixtures", "rotations.flv"));
        executor.submit(() -> {
            while (reader.hasMoreTags()) {
                ITag tag = reader.readTag();
                if (tag != null) {
                    IRTMPEvent msg;
                    switch (tag.getDataType()) {
                        case Constants.TYPE_AUDIO_DATA:
                            msg = new AudioData(tag.getBody());
                            break;
                        case Constants.TYPE_VIDEO_DATA:
                            msg = new VideoData(tag.getBody());
                            break;
                        case Constants.TYPE_INVOKE:
                            msg = new Invoke(tag.getBody());
                            break;
                        case Constants.TYPE_NOTIFY:
                            msg = new Notify(tag.getBody());
                            break;
                        default:
                            log.warn("Unexpected type? {}", tag.getDataType());
                            msg = new Unknown(tag.getDataType(), tag.getBody());
                            break;
                    }
                    msg.setTimestamp(tag.getTimestamp());
                    que.add(RTMPMessage.build(msg));
                } else {
                    break;
                }
            }
            log.info("Queue fill completed: {}", que.size());
        });
    }

    @After
    public void tearDown() throws Exception {
        reader.close();
        que.clear();
        ExecutorServiceUtil.shutdown(executor);
    }

    @Test
    public void testFBLivePublish() throws InterruptedException {
        log.info("\n testFBLivePublish");
        log.info("PublishName: {}", publishName);
        final RTMPClient client = new RTMPClient();
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
                            // live buffer 0.5s
                            @SuppressWarnings("unused")
                            RTMPConnection conn = (RTMPConnection) Red5.getConnectionLocal();
                            //conn.ping(new Ping(Ping.CLIENT_BUFFER, streamId, 500));
                            //client.play(streamId, youtubePublishName, -1, -1);
                            client.publish(streamId, publishName, "live", handler);
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
            test.testFBLivePublish();
            test.tearDown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
