package org.red5.server.stream.consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.red5.server.api.stream.IClientStream;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.net.rtmp.event.VideoData;
import org.red5.server.stream.message.RTMPMessage;

/**
 * Verifies the sliced file consumer keeps writing slices while recording instead of holding everything after the first slice in
 * memory until uninit (issue #491).
 */
public class SlicedFileConsumerQueueTest {

    private static final String THREAD_PREFIX = "FileConsumerExecutor-";

    private Path file;

    private SlicedFileConsumer consumer;

    @Before
    public void setUp() throws Exception {
        file = Files.createTempDirectory("slicedcons-queue").resolve("rec.flv");
        consumer = new SlicedFileConsumer(null, file.toFile());
        consumer.setMode(IClientStream.MODE_RECORD);
    }

    @After
    public void tearDown() throws Exception {
        consumer.uninit();
        consumer.destroy();
    }

    @Test
    public void testAudioOnlyQueueStaysBounded() throws Exception {
        int maxQueued = 0;
        for (int i = 0; i < 2000; i++) {
            pushAudio(i * 20);
            maxQueued = Math.max(maxQueued, consumer.getQueueSize());
        }
        assertTrue("queue must stay within the default threshold, max " + maxQueued, maxQueued <= SlicedFileConsumer.DEFAULT_QUEUE_THRESHOLD);
        assertTrue("data must reach the file while recording", awaitDataSize(100 * 15));
    }

    @Test
    public void testVideoWritesSlicesWhileRecording() throws Exception {
        int maxQueued = 0;
        for (int i = 0; i < 1000; i++) {
            pushAudio(i * 20);
            pushVideo(i * 20, i % 50 == 0);
            maxQueued = Math.max(maxQueued, consumer.getQueueSize());
        }
        assertTrue("each video message writes what is queued up to it, max " + maxQueued, maxQueued <= 2);
        assertTrue("data must reach the file while recording", awaitDataSize(1000 * 15));
    }

    @Test
    public void testByteLimitStopsWhileRecording() throws Exception {
        consumer.setMaxRecordingBytes(10_000);
        for (int i = 0; i < 2000 && consumer.getFailure() == null; i++) {
            pushAudio(i * 20);
        }
        // the limit is reached before the recording is stopped, not when uninit flushes the queue
        for (int i = 0; i < 100 && consumer.getFailure() == null; i++) {
            Thread.sleep(20);
        }
        String failure = consumer.getFailure();
        assertNotNull("byte limit must stop the recording while it is in progress", failure);
        assertTrue(failure, failure.contains("byte"));
    }

    @Test
    public void testNotRecordingQueuesNothing() throws Exception {
        consumer.setMode("none");
        for (int i = 0; i < 500; i++) {
            pushAudio(i * 20);
        }
        assertEquals("nothing is queued when no file is written", 0, consumer.getQueueSize());
    }

    @Test
    public void testUninitReleasesWriterThread() throws Exception {
        long before = writerThreads();
        for (int i = 0; i < 300; i++) {
            pushVideo(i * 20, i == 0);
            pushAudio(i * 20);
        }
        assertTrue("a writer thread must have been started", writerThreads() > before);
        consumer.uninit();
        for (int i = 0; i < 100 && writerThreads() > before; i++) {
            Thread.sleep(20);
        }
        assertEquals("the writer thread must exit once the recording is stopped", before, writerThreads());
    }

    private static long writerThreads() {
        return Thread.getAllStackTraces().keySet().stream().filter(t -> t.isAlive() && t.getName().startsWith(THREAD_PREFIX)).count();
    }

    private boolean awaitDataSize(long size) throws Exception {
        // until the writer is closed the tags are in the data file next to the flv
        Path data = file.resolveSibling(file.getFileName() + ".ser");
        for (int i = 0; i < 100; i++) {
            if (Files.exists(data) && Files.size(data) >= size) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

    private void pushAudio(int timestamp) throws Exception {
        AudioData audio = new AudioData(IoBuffer.wrap(FileConsumerAppendLimitsTest.body()));
        audio.setTimestamp(timestamp);
        consumer.pushMessage(null, RTMPMessage.build(audio));
    }

    private void pushVideo(int timestamp, boolean keyframe) throws Exception {
        // Sorenson H.263, which needs no decoder configuration: frame type in the high nibble, codec id 2 in the low nibble
        byte[] body = new byte[64];
        body[0] = (byte) ((keyframe ? 0x10 : 0x20) | 0x02);
        VideoData video = new VideoData(IoBuffer.wrap(body));
        video.setTimestamp(timestamp);
        consumer.pushMessage(null, RTMPMessage.build(video));
    }

}
