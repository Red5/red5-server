package org.red5.client.net.rtmp;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.red5.client.net.rtmps.RTMPSClient;
import org.red5.io.ITag;
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
import org.red5.server.net.rtmp.status.StatusCodes;
import org.red5.server.service.Call;
import org.red5.server.stream.message.RTMPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.core.util.ExecutorServiceUtil;

/**
 * Base class for publisher tests.
 * Provides common setup and utilities for tests that involve publishing RTMP streams.
 */
abstract class PublisherTest {

    protected Logger log = LoggerFactory.getLogger(getClass());

    protected AtomicBoolean finished = new AtomicBoolean(false);

    protected ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    protected FLVReader reader;

    protected LinkedTransferQueue<RTMPMessage> que = new LinkedTransferQueue<>();

    protected volatile boolean publishing;

    protected double streamId = 1.0d;

    protected String host, app, streamKey;

    protected int port = 1935;

    protected RTMPClient client;

    protected boolean secure;

    /**
     * Whether to start with releaseStream before publishing.
     * Set to false to skip the releaseStream step.
     */
    protected boolean startWithReleaseStream = true;

    /**
     * Use FCPublish and FCUnpublish commands by default.
     * Set to false to use createStream and publish instead.
     */
    protected boolean useFCCommands = true; // Use FCPublish and FCUnpublish commands by default

    protected ObjectMap<String, Object> connectionParams;

    static {
        System.setProperty("red5.deployment.type", "junit");
    }

    public void setUp() throws Exception {
        String userDir = System.getProperty("user.dir");
        log.info("User dir: {}", userDir);
        String sourceMediaFile = System.getProperty("source.media.file", "bbb_480p25.flv");
        if (userDir.contains("/tests")) {
            reader = new FLVReader(new File(userDir + "/src/test/resources/fixtures", sourceMediaFile));
        } else {
            // Try different paths for H.264 content
            File h264File = new File(userDir + "/tests/src/test/resources/fixtures", sourceMediaFile);
            if (!h264File.exists()) {
                h264File = new File(userDir + "/io/src/test/resources/fixtures", sourceMediaFile);
            }
            reader = new FLVReader(h264File);
        }
        // Read FLV synchronously to ensure queue is populated before streaming starts
        log.info("Reading FLV file and populating queue...");
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
        log.info("Queue populated with {} messages", que.size());
    }

    public void tearDown() throws Exception {
        if (reader != null) {
            log.info("Closing reader");
            reader.close();
        }
        que.clear();
        ExecutorServiceUtil.shutdown(executor);
    }

    /**
     * Test for publishing a RTMP based stream to servers that support them.
     * This method connects to the RTMP server, creates a stream, and publishes it.
     * It handles various stream events such as start, stop, and errors.
     *
     * A working order for streaming is as follows:
     * 1. Connect to the Red5 RTMP server - connect
     * 2. Release the stream before publishing - releaseStream
     * 3. Prepare the stream for publishing - FCPublish
     * 4. Create the stream - createStream
     * 5. Publish the stream - publish
     * Once the stream is published, it can be used to send video/audio data. When done, it should be unpublished using
     * FCUnpublish.
     *
     * @throws InterruptedException if the thread is interrupted during execution
     */
    @Test
    public void testPublish() throws InterruptedException {
        log.info("\ntestPublish");
        client = secure ? new RTMPSClient() : new RTMPClient();
        client.setConnectionClosedHandler(() -> {
            log.warn("Connection closed unexpectedly - Test exit");
        });
        ClientExceptionHandler clientExceptionHandler = new ClientExceptionHandler() {
            @Override
            public void handleException(Throwable throwable) {
                log.error("RTMP Client Exception occurred", throwable);
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
                        publishing = true;
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
                log.info("resultReceived: {} - Status: {} - Result: {}", method, call.getStatus(), call.getResult());
                // use self for this callback for reusability
                final IPendingServiceCallback self = this;
                switch (method) {
                    case "connect":
                        if (call.getStatus() == Call.STATUS_SUCCESS_RESULT) {
                            log.info("Successfully connected to the server");
                            // release the stream before publishing
                            Thread.ofVirtual().start(() -> {
                                if (startWithReleaseStream) {
                                    log.info("Releasing stream: {}", streamKey);
                                    client.invoke("releaseStream", new Object[] { streamKey }, self);
                                } else {
                                    // Skip releaseStream step
                                    log.info("Skipping releaseStream for stream: {}", streamKey);
                                    client.createStream(self);
                                }
                            });
                        } else {
                            log.error("Connection failed - Status: {} Exception: {}", call.getStatus(), call.getException());
                            finished.set(true);
                        }
                        break;
                    case "releaseStream":
                        // Some servers (like Twitch) may return error status for releaseStream but we should continue anyway
                        if (call.getStatus() == Call.STATUS_SUCCESS_RESULT) {
                            log.info("Stream released successfully: {}", streamKey);
                        } else {
                            log.warn("Stream release returned status {} but continuing anyway: {}", call.getStatus(), streamKey);
                        }
                        Thread.ofVirtual().start(() -> {
                            if (useFCCommands) {
                                log.info("Calling FCPublish for stream: {}", streamKey);
                                client.invoke("FCPublish", new Object[] { streamKey }, self);
                            } else {
                                log.info("Calling createStream for stream: {}", streamKey);
                                client.createStream(self);
                            }
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
                        Object result = call.getResult();
                        if (result != null) {
                            log.info("Created stream for: {}", streamKey);
                            streamId = (Double) result;
                            log.info("Stream created with ID: {}", streamId);
                            // now we can publish
                            Thread.ofVirtual().start(() -> {
                                client.publish(streamId, streamKey, "live", netStreamEventHandler);
                            });
                        } else {
                            log.error("createStream failed - result is null. Status: {}, Exception: {}", call.getStatus(), call.getException());
                            log.error("This usually indicates authentication failure or invalid stream key");
                            finished.set(true);
                        }
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
                        if (useFCCommands) {
                            client.invoke("FCUnpublish", new Object[] { streamKey }, self);
                        }
                        client.disconnect();
                        finished.set(true);
                }
            }
        };
        // connect to the RTMP server
        log.info("Connecting to server at {}:{}", host, port);
        if (connectionParams != null) {
            log.info("Using custom connection parameters: {}", connectionParams);
            client.connect(host, port, connectionParams, connectCallback);
        } else {
            client.connect(host, port, app, connectCallback);
        }
        log.info("Preparing to publish stream: {}", streamKey);
        executor.submit(() -> {
            log.info("Streaming thread started, waiting for publishing to begin...");
            while (!publishing) {
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    log.warn("Interrupted while waiting for publishing to start", e);
                    return;
                }
            }
            log.info("Publishing started, beginning A/V data streaming. Queue size: {}", que.size());
            int messageCount = 0;
            long streamingStartTime = System.currentTimeMillis();
            long previousTimestamp = 0;
            log.info("Starting to publish messages...");
            while (!que.isEmpty()) {
                try {
                    RTMPMessage message = que.poll();
                    if (message != null && client != null) {
                        long currentTimestamp = message.getBody().getTimestamp();
                        // Calculate sleep time based on timestamp difference
                        if (messageCount > 0 && currentTimestamp > previousTimestamp) {
                            long timestampDelta = currentTimestamp - previousTimestamp;
                            // Limit sleep time to reasonable values (max 100ms per message)
                            long sleepTime = Math.min(timestampDelta, 100L);
                            if (sleepTime > 0) {
                                log.trace("Sleeping {}ms based on timestamp delta (current: {}, previous: {})", sleepTime, currentTimestamp, previousTimestamp);
                                Thread.sleep(sleepTime);
                            }
                        }
                        log.debug("Publishing message #{} with timestamp {}: {}", ++messageCount, currentTimestamp, message);
                        client.publishStreamData(streamId, message);
                        previousTimestamp = currentTimestamp;
                    } else if (message == null) {
                        // Queue is empty, break out of loop
                        break;
                    }
                } catch (Exception e1) {
                    log.warn("Streaming error on message #{}: {}", messageCount, e1.getMessage(), e1);
                }
            }
            long duration = System.currentTimeMillis() - streamingStartTime;
            log.info("Finished streaming {} messages in {}ms", messageCount, duration);
            // Unpublish the stream after all messages are sent
            try {
                client.unpublish(streamId);
                log.info("Stream unpublished successfully");
            } catch (Exception e) {
                log.warn("Error unpublishing stream: {}", e.getMessage(), e);
            }
        });
        log.info("Waiting for publish to complete");
        // Wait for connection and publishing to be established, then wait for streaming to finish
        long startTime = System.currentTimeMillis();
        // First wait for publishing to start
        while (!publishing && !finished.get() && (System.currentTimeMillis() - startTime) < 30000) {
            try {
                Thread.sleep(500L);
                log.debug("Waiting for publishing to start... publishing={} finished={}", publishing, finished.get());
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for publishing to start", e);
                break;
            }
        }
        if (finished.get()) {
            log.error("Connection failed before publishing could start");
        } else if (!publishing) {
            log.warn("Publishing did not start within timeout");
        } else {
            log.info("Publishing started, waiting for streaming to complete");
            // Now wait for streaming to finish
            while (publishing && !que.isEmpty()) {
                try {
                    Thread.sleep(1000L);
                    log.debug("Still publishing... Queue size: {}", que.size());
                } catch (InterruptedException e) {
                    log.warn("Interrupted while waiting for streaming to complete", e);
                    break;
                }
            }
        }
        log.info("Publish complete, disconnecting client");
        client.disconnect();
        log.info("Test - end");
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public void setConnectionParams(ObjectMap<String, Object> params) {
        this.connectionParams = params;
    }

    public void setStartWithReleaseStream(boolean startWithReleaseStream) {
        this.startWithReleaseStream = startWithReleaseStream;
    }

    public void setUseFCCommands(boolean useFCCommands) {
        this.useFCCommands = useFCCommands;
    }

}
