package org.red5.server.stream.consumer;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.red5.server.api.stream.IClientStream;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.stream.message.RTMPMessage;

/**
 * Verifies recording byte, duration and free space limits on the sliced file consumer, which apply to the whole file rather than to
 * each slice of queued writes (issue #462).
 */
public class SlicedFileConsumerLimitsTest {

    // bytes each tag takes in the file: tag header, body and previous tag size
    private static final int TAG = 11 + FileConsumerAppendLimitsTest.body().length + 4;

    private Path file;

    private SlicedFileConsumer consumer;

    @Before
    public void setUp() throws Exception {
        file = Files.createTempDirectory("slicedcons-limits").resolve("rec.flv");
        consumer = new SlicedFileConsumer(null, file.toFile());
        consumer.setMode(IClientStream.MODE_RECORD);
    }

    @After
    public void tearDown() throws Exception {
        consumer.destroy();
    }

    @Test
    public void testNoLimitsKeepsRecording() throws Exception {
        push(0, 20, 100);
        consumer.uninit();
        assertNull(consumer.getFailure());
        assertTrue("all tags must be written", Files.size(file) >= 20 * TAG);
    }

    @Test
    public void testByteLimitStopsRecording() throws Exception {
        long limit = 10_000;
        consumer.setMaxRecordingBytes(limit);
        push(0, 40, 100);
        consumer.uninit();
        String failure = consumer.getFailure();
        assertNotNull("byte limit must stop the recording", failure);
        assertTrue(failure, failure.contains("byte"));
        long size = Files.size(file);
        assertTrue("file must stop near the limit, size " + size, size >= limit && size <= limit + TAG + 1024);
    }

    @Test
    public void testDurationLimitStopsRecording() throws Exception {
        consumer.setMaxRecordingDurationMs(1_000);
        push(0, 40, 100);
        consumer.uninit();
        String failure = consumer.getFailure();
        assertNotNull("duration limit must stop the recording", failure);
        assertTrue(failure, failure.contains("duration"));
        // tags at 0 to 1000 ms are written
        assertTrue("file must stop at the duration limit", Files.size(file) <= 11 * TAG + 1024);
    }

    @Test
    public void testFreeSpaceLimitStopsRecording() throws Exception {
        consumer.setMinFreeDiskBytes(Long.MAX_VALUE);
        push(0, 5, 100);
        String failure = consumer.getFailure();
        assertNotNull("free space minimum must stop the recording before anything is written", failure);
        assertTrue(failure, failure.contains("usable"));
        consumer.uninit();
        assertTrue("nothing is written on a volume short of space", Files.size(file) < TAG + 1024);
    }

    @Test
    public void testByteLimitCountsAppendedFile() throws Exception {
        long limit = 6_000;
        // 8 tags already in the file
        FileConsumerAppendLimitsTest.writeExistingFile(file, 8, 100);
        consumer.setMode(IClientStream.MODE_APPEND);
        consumer.setMaxRecordingBytes(limit);
        // 8 more tags stay below the limit on their own, but not with the file they extend
        push(0, 8, 100);
        consumer.uninit();
        String failure = consumer.getFailure();
        assertNotNull("appending must stop once the file reaches the byte limit", failure);
        assertTrue(failure, failure.contains("byte"));
        long size = Files.size(file);
        assertTrue("file must keep the earlier session and stop near the limit, size " + size, size >= limit && size <= limit + TAG + 1024);
    }

    @Test
    public void testDurationLimitCountsAppendedFile() throws Exception {
        // 700 ms already in the file
        FileConsumerAppendLimitsTest.writeExistingFile(file, 8, 100);
        consumer.setMode(IClientStream.MODE_APPEND);
        consumer.setMaxRecordingDurationMs(1_000);
        // 700 ms more is below the limit on its own, but not with the file it extends
        push(0, 8, 100);
        consumer.uninit();
        String failure = consumer.getFailure();
        assertNotNull("appending must stop once the file reaches the duration limit", failure);
        assertTrue(failure, failure.contains("duration"));
    }

    @Test
    public void testAppendBelowLimitsKeepsRecording() throws Exception {
        FileConsumerAppendLimitsTest.writeExistingFile(file, 8, 100);
        consumer.setMode(IClientStream.MODE_APPEND);
        consumer.setMaxRecordingBytes(20 * TAG);
        consumer.setMaxRecordingDurationMs(2_000);
        push(0, 8, 100);
        consumer.uninit();
        assertNull(consumer.getFailure());
        assertTrue("both sessions must be in the file", Files.size(file) >= 16 * TAG);
    }

    private void push(int startTimestamp, int count, int step) throws Exception {
        for (int i = 0; i < count; i++) {
            AudioData audio = new AudioData(IoBuffer.wrap(FileConsumerAppendLimitsTest.body()));
            audio.setTimestamp(startTimestamp + i * step);
            consumer.pushMessage(null, RTMPMessage.build(audio));
        }
    }

}
