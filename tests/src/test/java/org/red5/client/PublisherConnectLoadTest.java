package org.red5.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.red5.client.net.rtmp.INetStreamEventHandler;
import org.red5.client.net.rtmp.RTMPClient;
import org.red5.io.ITag;
import org.red5.io.ITagReader;
import org.red5.io.flv.impl.FLVReader;
import org.red5.io.utils.ObjectMap;
import org.red5.server.api.service.IPendingServiceCall;
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
import org.red5.test.IntegrationTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connects a number of RTMP publishers to a running Red5 server, publishes a bundled FLV to each and verifies every
 * publisher connects, receives publish success and pushes the expected number of messages. Every third publisher
 * unpublishes early to exercise the unpublish and disconnect paths while others are still streaming.
 * <p>
 * Requires a Red5 server; see tests/src/test/resources/scripts/publisher_load_compose_e2e_test.sh which runs it
 * against a docker compose stack. Configuration via system properties:
 * <ul>
 * <li>red5.host (localhost), red5.port (1935), red5.app (live)</li>
 * <li>red5.load.publishers (5) number of concurrent publishers</li>
 * <li>red5.load.messages (200) messages published per publisher</li>
 * <li>red5.load.flv (target/test-classes/fixtures/rotations.flv) source stream</li>
 * <li>red5.load.timeout (120) seconds to wait for all publishers to finish</li>
 * </ul>
 */
@Category(IntegrationTest.class)
public class PublisherConnectLoadTest {

    private static Logger log = LoggerFactory.getLogger(PublisherConnectLoadTest.class);

    /** Pacing between published messages, in ms. */
    private static final long PACING_MS = 13L;

    static {
        System.setProperty("red5.deployment.type", "junit");
    }

    private final String host = System.getProperty("red5.host", "localhost");

    private final int port = Integer.getInteger("red5.port", 1935);

    private final String app = System.getProperty("red5.app", "live");

    private final int publishers = Integer.getInteger("red5.load.publishers", 5);

    private final int messagesPerPublisher = Integer.getInteger("red5.load.messages", 200);

    private final String flvPath = System.getProperty("red5.load.flv", "target/test-classes/fixtures/rotations.flv");

    private final long timeoutSeconds = Long.getLong("red5.load.timeout", 120L);

    private ExecutorService executor;

    /** Source tags captured once; each publisher gets its own message instances built from these. */
    private final List<SourceTag> source = new ArrayList<>();

    private CountDownLatch latch;

    private final ConcurrentHashMap<String, PublisherState> states = new ConcurrentHashMap<>();

    private static final class SourceTag {

        final byte dataType;

        final int timestamp;

        final byte[] body;

        SourceTag(byte dataType, int timestamp, byte[] body) {
            this.dataType = dataType;
            this.timestamp = timestamp;
            this.body = body;
        }

        IRTMPEvent toEvent() {
            IoBuffer buf = IoBuffer.wrap(body.clone());
            IRTMPEvent msg;
            switch (dataType) {
                case Constants.TYPE_AUDIO_DATA:
                    msg = new AudioData(buf);
                    break;
                case Constants.TYPE_VIDEO_DATA:
                    msg = new VideoData(buf);
                    break;
                case Constants.TYPE_INVOKE:
                    msg = new Invoke(buf);
                    break;
                case Constants.TYPE_NOTIFY:
                    msg = new Notify(buf);
                    break;
                default:
                    msg = new Unknown(dataType, buf);
                    break;
            }
            msg.setTimestamp(timestamp);
            return msg;
        }
    }

    private static final class PublisherState {

        final String name;

        final int expectedMessages;

        final RTMPClient client;

        volatile String connectCode;

        volatile boolean publishStarted;

        volatile boolean unpublished;

        final AtomicInteger published = new AtomicInteger();

        final List<Throwable> errors = new ArrayList<>();

        PublisherState(String name, int expectedMessages, RTMPClient client) {
            this.name = name;
            this.expectedMessages = expectedMessages;
            this.client = client;
        }

        @Override
        public String toString() {
            return String.format("%s[connect=%s publishStarted=%s published=%d/%d unpublished=%s errors=%d]", name, connectCode, publishStarted, published.get(), expectedMessages, unpublished, errors.size());
        }
    }

    @Before
    public void setUp() throws Exception {
        File flv = new File(flvPath);
        assertTrue("Missing source flv: " + flv.getAbsolutePath(), flv.isFile());
        ITagReader reader = new FLVReader(flv, false);
        try {
            while (reader.hasMoreTags() && source.size() < messagesPerPublisher) {
                ITag tag = reader.readTag();
                if (tag == null) {
                    break;
                }
                IoBuffer body = tag.getBody();
                byte[] bytes = new byte[body.remaining()];
                body.get(bytes);
                source.add(new SourceTag(tag.getDataType(), tag.getTimestamp(), bytes));
            }
        } finally {
            reader.close();
        }
        assertEquals("source flv is too short for red5.load.messages", messagesPerPublisher, source.size());
        log.info("Loaded {} source tags from {}", source.size(), flv.getName());
        executor = Executors.newCachedThreadPool();
        latch = new CountDownLatch(publishers);
    }

    @After
    public void tearDown() {
        states.values().forEach(s -> s.client.disconnect());
        states.clear();
        source.clear();
        executor.shutdownNow();
    }

    @Test
    public void testPublishers() throws Exception {
        log.info("Publisher load test: {} publishers x {} messages against rtmp://{}:{}/{}", publishers, messagesPerPublisher, host, port, app);
        for (int i = 0; i < publishers; i++) {
            launchPublisher(i);
        }
        boolean finished = latch.await(timeoutSeconds, TimeUnit.SECONDS);
        // all publishers done, verify each one
        List<String> failures = new ArrayList<>();
        if (!finished) {
            failures.add("timed out after " + timeoutSeconds + "s with " + latch.getCount() + " publisher(s) unfinished");
        }
        for (PublisherState state : states.values()) {
            log.info("Result: {}", state);
            if (!"NetConnection.Connect.Success".equals(state.connectCode)) {
                failures.add(state.name + " connect result: " + state.connectCode);
            }
            if (!state.publishStarted) {
                failures.add(state.name + " never received NetStream.Publish.Start");
            }
            if (state.published.get() != state.expectedMessages) {
                failures.add(state.name + " published " + state.published.get() + " of " + state.expectedMessages);
            }
            if (!state.unpublished) {
                failures.add(state.name + " never unpublished");
            }
            if (!state.errors.isEmpty()) {
                failures.add(state.name + " errors: " + state.errors);
            }
        }
        assertEquals(publishers, states.size());
        assertTrue("Publisher failures:\n" + String.join("\n", failures), failures.isEmpty());
    }

    private void launchPublisher(int index) {
        final String publishName = String.format("loadstream%d", index);
        // every third publisher stops half way through
        final int expected = index % 3 == 0 ? messagesPerPublisher / 2 : messagesPerPublisher;
        final RTMPClient client = new RTMPClient();
        final PublisherState state = new PublisherState(publishName, expected, client);
        states.put(publishName, state);
        client.setConnectionClosedHandler(() -> log.info("Connection closed: {}", publishName));
        client.setExceptionHandler(throwable -> {
            log.warn("Exception for {}", publishName, throwable);
            synchronized (state.errors) {
                state.errors.add(throwable);
            }
            finish(state);
        });
        client.setStreamEventDispatcher(event -> log.debug("Client: {} dispatch event: {}", publishName, event));
        final INetStreamEventHandler handler = new INetStreamEventHandler() {
            @Override
            public void onStreamEvent(Notify notify) {
                IServiceCall call = notify.getCall();
                if (!"onStatus".equals(call.getServiceMethodName())) {
                    return;
                }
                ObjectMap<?, ?> status = (ObjectMap<?, ?>) call.getArguments()[0];
                String code = (String) status.get("code");
                log.info("Client: {} status: {}", publishName, code);
                switch (code) {
                    case "NetStream.Publish.Start":
                        state.publishStarted = true;
                        startPublish(state);
                        break;
                    case "NetStream.Unpublish.Success":
                        state.unpublished = true;
                        finish(state);
                        break;
                    case "NetStream.Publish.Failed":
                    case "NetStream.Publish.BadName":
                        synchronized (state.errors) {
                            state.errors.add(new IllegalStateException(code));
                        }
                        finish(state);
                        break;
                    default:
                        break;
                }
            }
        };
        client.setStreamEventHandler(handler);
        executor.submit(() -> client.connect(host, port, app, (IPendingServiceCall call) -> {
            ObjectMap<?, ?> map = (ObjectMap<?, ?>) call.getResult();
            String code = (String) map.get("code");
            state.connectCode = code;
            log.info("Connect result: {} for {}", code, publishName);
            if ("NetConnection.Connect.Success".equals(code)) {
                client.createStream((IPendingServiceCall create) -> {
                    Number streamId = (Number) create.getResult();
                    log.info("Created stream id {} for {}", streamId, publishName);
                    client.publish(streamId, publishName, "live", handler);
                });
            } else {
                log.warn("Connect failed for {}: {}", publishName, map.get("description"));
                finish(state);
            }
        }));
    }

    private void startPublish(PublisherState state) {
        executor.submit(() -> {
            RTMPClient client = state.client;
            RTMPConnection conn = client.getConnection();
            Number streamId = conn.getStreamId() == null ? 1.0d : conn.getStreamId();
            log.info("Publishing {} messages on {} stream id {}", state.expectedMessages, state.name, streamId);
            for (int i = 0; i < state.expectedMessages; i++) {
                if (conn.isDisconnected()) {
                    log.warn("Connection dropped for {} after {} messages", state.name, state.published.get());
                    break;
                }
                client.publishStreamData(streamId, RTMPMessage.build(source.get(i).toEvent()));
                state.published.incrementAndGet();
                try {
                    Thread.sleep(PACING_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            log.info("Publishing loop done for {} with {} messages, unpublishing", state.name, state.published.get());
            client.unpublish(streamId);
        });
    }

    private void finish(PublisherState state) {
        // count each publisher down once, then drop the connection
        synchronized (state) {
            if (state.client.getConnection() != null && !state.client.getConnection().isDisconnected()) {
                state.client.disconnect();
            }
        }
        latch.countDown();
    }

}
