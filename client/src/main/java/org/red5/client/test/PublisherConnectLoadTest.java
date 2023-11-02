package org.red5.client.test;

import java.io.File;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.red5.client.net.rtmp.ClientExceptionHandler;
import org.red5.client.net.rtmp.INetStreamEventHandler;
import org.red5.client.net.rtmp.RTMPClient;
import org.red5.io.ITag;
import org.red5.io.ITagReader;
import org.red5.io.flv.impl.FLVReader;
import org.red5.io.utils.ObjectMap;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.event.IEventDispatcher;
import org.red5.server.api.service.IPendingServiceCall;
import org.red5.server.api.service.IPendingServiceCallback;
import org.red5.server.api.service.IServiceCall;
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
 * Load tests for rapidly adding publishers via RTMP.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class PublisherConnectLoadTest {

    private static Logger log = LoggerFactory.getLogger(PublisherConnectLoadTest.class);

    private ExecutorService executor;

    private ITagReader reader;

    private ConcurrentLinkedQueue<RTMPMessage> que = new ConcurrentLinkedQueue<>();

    private String host = "localhost";

    private int port = 1935;

    private String app = "live";

    public static int publishers = 100;

    private static CountDownLatch latch = new CountDownLatch(publishers);

    static {
        System.setProperty("red5.deployment.type", "junit");
    }

    public void setUp() throws Exception {
        executor = Executors.newCachedThreadPool();
        reader = new FLVReader(new File("/media/mondain/terrorbyte/Videos", "BladeRunner2049.flv"));
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

    public void testLivePublish() throws InterruptedException {
        final String publishName = String.format("stream%d", System.nanoTime());
        log.info("Publisher load test: {}", publishName);
        final RTMPClient client = new RTMPClient();
        client.setConnectionClosedHandler(() -> {
            log.info("Test: {} - exit", publishName);
        });
        client.setExceptionHandler(new ClientExceptionHandler() {
            @Override
            public void handleException(Throwable throwable) {
                throwable.printStackTrace();
                disconnect(client);
            }
        });
        client.setStreamEventDispatcher(new IEventDispatcher() {
            @Override
            public void dispatchEvent(IEvent event) {
                log.info("ClientStream: {} dispachEvent: {}", publishName, event);
            }
        });
        final INetStreamEventHandler handler = new INetStreamEventHandler() {
            @Override
            public void onStreamEvent(Notify notify) {
                log.info("ClientStream: {} onStreamEvent: {}", publishName, notify);
                IServiceCall call = notify.getCall();
                if ("onStatus".equals(call.getServiceMethodName())) {
                    @SuppressWarnings("rawtypes")
                    ObjectMap status = ((ObjectMap) call.getArguments()[0]);
                    String code = (String) status.get("code");
                    switch (code) {
                        case "NetStream.Publish.Success":
                            log.info("Publish success: {}", publishName);
                            break;
                        case "NetStream.UnPublish.Success":
                        case "NetStream.Publish.Failed":
                            disconnect(client);
                            break;
                    }
                }
            }
        };
        executor.submit(() -> {
            // set the handler
            client.setStreamEventHandler(handler);
            // connect
            client.connect(host, port, app, new IPendingServiceCallback() {
                @Override
                public void resultReceived(IPendingServiceCall call) {
                    ObjectMap<?, ?> map = (ObjectMap<?, ?>) call.getResult();
                    String code = (String) map.get("code");
                    log.info("Response code: {} for {}", code, publishName);
                    if ("NetConnection.Connect.Rejected".equals(code)) {
                        log.warn("Rejected: {}", map.get("description"));
                        disconnect(client);
                    } else if ("NetConnection.Connect.Success".equals(code)) {
                        client.createStream(new IPendingServiceCallback() {
                            @Override
                            public void resultReceived(IPendingServiceCall call) {
                                double streamId = (Double) call.getResult();
                                // live buffer 0.5s
                                client.publish(streamId, publishName, "live", handler);
                                // publish stream data
                                que.forEach(message -> {
                                    if (message != null) {
                                        log.info("Publishing: {}", message);
                                        client.publishStreamData(streamId, message);
                                    }
                                });
                                client.unpublish(streamId);
                                disconnect(client);
                            }
                        });
                    }
                }
            });
            log.info("test - end: {}", publishName);
        });
    }

    public static void disconnect(RTMPClient client) {
        client.disconnect();
        latch.countDown();
    }

    public void tearDown() throws Exception {
        reader.close();
        que.clear();
        ExecutorServiceUtil.shutdown(executor);
    }

    public static void main(String[] args) {
        PublisherConnectLoadTest test = new PublisherConnectLoadTest();
        try {
            // set up
            test.setUp();
            // launch publishers
            for (int i = 0; i < publishers; i++) {
                test.testLivePublish();
            }
            // wait for all to finish
            latch.await();
            // tear down
            test.tearDown();
        } catch (Exception e) {
            log.warn("Exception", e);
        }
    }

}
