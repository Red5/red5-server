package org.red5.client.test;

import java.io.File;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

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
 * Load tests for rapidly adding publishers via RTMP.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class PublisherConnectLoadTest {

    private static Logger log = LoggerFactory.getLogger(PublisherConnectLoadTest.class);

    private static Random rnd = new Random();

    private static ExecutorService executor = Executors.newCachedThreadPool();

    public static int publishers = 30;

    private static CountDownLatch latch = new CountDownLatch(publishers);

    private static CopyOnWriteArrayList<RTMPClient> publisherList = new CopyOnWriteArrayList<>();

    private static AtomicIntegerFieldUpdater<PublisherConnectLoadTest> AtomicPublishCounter = AtomicIntegerFieldUpdater.newUpdater(PublisherConnectLoadTest.class, "publishCount");

    // updated atomically as a counter since the publish list is weakly consistent in terms of size
    private volatile int publishCount;

    private ITagReader reader;

    private ConcurrentLinkedQueue<RTMPMessage> que = new ConcurrentLinkedQueue<>();

    private String host = "localhost";

    private int port = 1935;

    private String app = "live";

    static {
        System.setProperty("red5.deployment.type", "junit");
    }

    public void setUp() throws Exception {
        reader = new FLVReader(new File("/media/mondain/terrorbyte/Videos", "BladeRunner2049.flv"));
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
    }

    public void testLivePublish(int i) throws InterruptedException {
        final String publishName = String.format("stream%d", i);
        log.info("Publisher load test: {}", publishName);
        final RTMPClient client = new RTMPClient();
        client.setConnectionClosedHandler(() -> {
            log.info("Connection closed: {}", publishName);
        });
        client.setExceptionHandler(new ClientExceptionHandler() {
            @Override
            public void handleException(Throwable throwable) {
                log.info("Exception caught: {}", publishName);
                throwable.printStackTrace();
                disconnect(client);
            }
        });
        client.setStreamEventDispatcher(new IEventDispatcher() {
            @Override
            public void dispatchEvent(IEvent event) {
                log.info("Client: {} dispach event: {}", publishName, event);
            }
        });
        final INetStreamEventHandler handler = new INetStreamEventHandler() {
            @Override
            public void onStreamEvent(Notify notify) {
                log.info("Client: {} onStreamEvent: {}", publishName, notify);
                IServiceCall call = notify.getCall();
                if ("onStatus".equals(call.getServiceMethodName())) {
                    @SuppressWarnings("rawtypes")
                    ObjectMap status = ((ObjectMap) call.getArguments()[0]);
                    String code = (String) status.get("code");
                    switch (code) {
                        case "NetStream.Publish.Start":
                            log.info("Publish success: {}", publishName);
                            // do publishing
                            startPublish(client, publishName);
                            // randomly decide if a publisher should be killed
                            maybeKillPublisher();
                            break;
                        case "NetStream.UnPublish.Success":
                            log.info("Unpublish success: {}", publishName);
                        case "NetStream.Publish.Failed":
                            disconnect(client);
                            break;
                    }
                }
            }

        };
        // set the handler
        client.setStreamEventHandler(handler);
        // connect
        executor.submit(() -> {
            client.connect(host, port, app, new IPendingServiceCallback() {
                @Override
                public void resultReceived(IPendingServiceCall call) {
                    ObjectMap<?, ?> map = (ObjectMap<?, ?>) call.getResult();
                    String code = (String) map.get("code");
                    log.info("Response code: {} for {}", code, publishName);
                    if ("NetConnection.Connect.Rejected".equals(code)) {
                        log.warn("Rejected: {} detail: {}", publishName, map.get("description"));
                        disconnect(client);
                    } else if ("NetConnection.Connect.Success".equals(code)) {
                        client.createStream(new IPendingServiceCallback() {
                            @Override
                            public void resultReceived(IPendingServiceCall call) {
                                Number streamId = (Number) call.getResult();
                                log.info("Create for publish: {} with stream id: {}", publishName, streamId);
                                client.publish(streamId, publishName, "live", handler);
                            }
                        });
                    }
                }
            });
        });
    }

    public void startPublish(RTMPClient client, String publishName) {
        log.info("Start publish: {} name: {}", client, publishName);
        // add to list
        if (publisherList.add(client)) {
            // increment the counter
            AtomicPublishCounter.incrementAndGet(this);
        }
        // publishing thread
        executor.submit(() -> {
            // get the underlying connection
            final RTMPConnection conn = client.getConnection();
            final Number streamId = conn.getStreamId() == null ? 1.0d : conn.getStreamId();
            log.info("Publishing: {} stream id: {}", publishName, streamId);
            AtomicInteger messageCounter = new AtomicInteger();
            // publish stream data
            que.spliterator().forEachRemaining(msg -> {
                if (msg != null) {
                    log.trace("Publishing: {}", msg);
                    client.publishStreamData(streamId, msg);
                    messageCounter.incrementAndGet();
                } else {
                    log.warn("Null message for: {}", publishName);
                }
                try {
                    Thread.sleep(13L);
                } catch (InterruptedException e) {
                }
                // TODO(paul) looking to why its always disconnected
                /*
                // check for disconnect
                if (conn.isDisconnected()) {
                    log.warn("Connection is disconnected for: {} while publishing", publishName);
                    return;
                }
                */
            });
            // unpublish
            client.unpublish(streamId);
            disconnect(client);
            log.info("Publishing completed: {} with {} messages published", publishName, messageCounter.get());
        });
    }

    public void maybeKillPublisher() {
        // our current publisher count of those with publish-success
        log.info("Publisher count: {}", publishCount);
        // for every few publishers, kill one off randomly
        if (publishCount > (publishers / 3)) {
            int index = rnd.nextInt(publishCount);
            if (index % 3 == 0) {
                log.info("Killing publisher at index: {} of {}", index, publishCount);
                RTMPClient client = publisherList.get(index);
                if (client != null) {
                    Number streamId = client.getConnection().getStreamId();
                    log.info("Unpublishing: {} stream id: {}", client, streamId);
                    client.unpublish(streamId);
                }
            }
        }
    }

    public void disconnect(RTMPClient client) {
        log.info("Disconnecting: {}", client);
        // ensure the client is removed from the list
        if (publisherList.remove(client)) {
            AtomicPublishCounter.decrementAndGet(this);
        } else {
            log.info("Publisher already removed or was not publishing: {}", client);
        }
        client.disconnect();
        latch.countDown();
    }

    public void tearDown() throws Exception {
        reader.close();
        que.clear();
        ExecutorServiceUtil.shutdown(executor);
        publisherList.clear();
    }

    public int getPublishCount() {
        return publishCount;
    }

    public static void main(String[] args) {
        PublisherConnectLoadTest test = new PublisherConnectLoadTest();
        try {
            // set up
            test.setUp();
            // launch publishers
            for (int i = 0; i < publishers; i++) {
                // launch a publisher test
                test.testLivePublish(i);
            }
            // wait for all to finish
            latch.await();
            // tear down
            test.tearDown();
        } catch (Exception e) {
            log.warn("Exception", e);
        } finally {
            log.info("Done");
        }
    }

}
