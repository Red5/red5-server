package org.red5.server.stream.consumer;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
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
 * Verifies recording byte, duration and free space limits (issue #462).
 */
public class FileConsumerLimitsTest {

    private Path dir;

    private FileConsumer consumer;

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("filecons-limits");
        consumer = new FileConsumer(null, new File(dir.toFile(), "rec.flv"));
        consumer.setMode(IClientStream.MODE_RECORD);
    }

    @After
    public void tearDown() {
        consumer.uninit();
    }

    @Test
    public void testByteLimitStopsRecording() throws Exception {
        consumer.setMaxRecordingBytes(10_000);
        push(0, 200, 1000, 20);
        String f = awaitFailure();
        Files.list(dir).forEach(p -> {
            try {
                System.out.println("DBG " + p + " " + Files.size(p));
            } catch (Exception e) {
            }
        });
        assertNotNull("byte limit must stop the recording", f);
        long size = Files.size(dir.resolve("rec.flv"));
        // later data is dropped
        push(20_000, 20, 1000, 20);
        Thread.sleep(200);
        assertTrue("file must stop growing near the limit, size " + size, Files.size(dir.resolve("rec.flv")) <= 10_000 + 2 * 1024);
    }

    @Test
    public void testDurationLimitStopsRecording() throws Exception {
        consumer.setMaxRecordingDurationMs(1_000);
        push(0, 100, 100, 20);
        String failure = awaitFailure();
        assertNotNull("duration limit must stop the recording", failure);
        assertTrue(failure.contains("duration"));
    }

    @Test
    public void testFreeSpaceLimitStopsRecording() throws Exception {
        consumer.setMinFreeDiskBytes(Long.MAX_VALUE);
        push(0, 300, 100, 20);
        String failure = awaitFailure();
        assertNotNull("free space minimum must stop the recording", failure);
        assertTrue(failure.contains("usable"));
    }

    @Test
    public void testNoLimitsKeepsRecording() throws Exception {
        push(0, 300, 100, 20);
        Thread.sleep(300);
        assertNull(consumer.getFailure());
    }

    private void push(int startTimestamp, int count, int size, int step) throws Exception {
        for (int i = 0; i < count; i++) {
            IoBuffer data = IoBuffer.allocate(size);
            // MP3 audio header, which needs no decoder configuration before frames are written
            data.put((byte) 0x2F);
            data.position(size);
            data.flip();
            AudioData audio = new AudioData(data);
            audio.setTimestamp(startTimestamp + i * step);
            consumer.pushMessage(null, RTMPMessage.build(audio));
        }
    }

    private String awaitFailure() throws InterruptedException {
        for (int i = 0; i < 100 && consumer.getFailure() == null; i++) {
            Thread.sleep(50);
        }
        return consumer.getFailure();
    }

}
