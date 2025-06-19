package org.red5.client.net.rtmp;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.red5.io.ITag;
import org.red5.io.flv.impl.FLVReader;
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

    static {
        System.setProperty("red5.deployment.type", "junit");
    }

    public void setUp() throws Exception {
        String userDir = System.getProperty("user.dir");
        log.info("User dir: {}", userDir);
        if (userDir.contains("/tests")) {
            reader = new FLVReader(new File(userDir + "/src/test/resources/fixtures", "bbb_720p30.flv"));
        } else {
            reader = new FLVReader(new File(userDir + "/tests/src/test/resources/fixtures", "bbb_720p30.flv"));
        }
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

    public void tearDown() throws Exception {
        if (reader != null) {
            log.info("Closing reader");
            reader.close();
        }
        que.clear();
        ExecutorServiceUtil.shutdown(executor);
    }

}
