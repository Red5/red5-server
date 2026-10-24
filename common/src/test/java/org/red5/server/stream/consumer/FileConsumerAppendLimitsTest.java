package org.red5.server.stream.consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.red5.io.ITag;
import org.red5.io.flv.impl.FLVWriter;
import org.red5.server.api.stream.IClientStream;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.stream.message.RTMPMessage;

/**
 * Verifies that recording byte and duration limits count the existing file when appending, so repeated append sessions cannot grow
 * one file without bound (issue #462).
 */
public class FileConsumerAppendLimitsTest {

    // body size of each audio tag
    private static final int BODY = 500;

    // bytes each tag takes in the file: tag header, body and previous tag size
    private static final int TAG = 11 + BODY + 4;

    private Path dir;

    private Path file;

    private FileConsumer consumer;

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("filecons-append");
        file = dir.resolve("rec.flv");
    }

    @After
    public void tearDown() throws Exception {
        if (consumer != null) {
            consumer.uninit();
            consumer.destroy();
        }
    }

    @Test
    public void testByteLimitCountsAppendedSessions() throws Exception {
        long limit = 10_000;
        // 8 tags already in the file
        writeExistingFile(file, 8, 100);
        // second session adds 8 tags, 16 in all stay below the limit
        startAppend(limit, 0);
        push(0, 8, 100);
        awaitDataSize(8 * TAG);
        assertNull("16 tags are below the byte limit", consumer.getFailure());
        finish();
        // third session would stay below the limit on its own, the file it extends does not
        startAppend(limit, 0);
        push(0, 10, 100);
        String failure = awaitFailure();
        assertNotNull("appending must stop once the file reaches the byte limit", failure);
        assertTrue(failure, failure.contains("byte"));
        long written = Files.size(dataFile());
        assertTrue("only the tags up to the limit are written, wrote " + written, written < 10 * TAG);
        assertTrue("16 tags plus what was written must reach the limit", 16 * TAG + written >= limit);
        finish();
        // the file holds the earlier sessions and is bounded by the limit plus the tag that crossed it, header and metadata
        long size = Files.size(file);
        assertTrue("file must keep the earlier sessions, size " + size, size >= 16 * TAG);
        assertTrue("file must stop near the limit, size " + size, size <= limit + TAG + 1024);
    }

    @Test
    public void testDurationLimitCountsAppendedSessions() throws Exception {
        long limit = 2_000;
        // 700 ms already in the file
        writeExistingFile(file, 8, 100);
        // second session adds 700 ms, 1400 ms in all is below the limit
        startAppend(0, limit);
        push(0, 8, 100);
        awaitDataSize(8 * TAG);
        assertNull("1400 ms is below the duration limit", consumer.getFailure());
        finish();
        // third session lasts 900 ms, which is below the limit on its own but not with the 1400 ms it extends
        startAppend(0, limit);
        push(0, 10, 100);
        String failure = awaitFailure();
        assertNotNull("appending must stop once the file reaches the duration limit", failure);
        assertTrue(failure, failure.contains("duration"));
        assertTrue("only the tags up to the limit are written", Files.size(dataFile()) < 10 * TAG);
    }

    @Test
    public void testAppendToFileAlreadyAtLimitWritesNothing() throws Exception {
        writeExistingFile(file, 20, 100);
        long before = Files.size(file);
        startAppend(20 * TAG, 0);
        push(0, 5, 100);
        String failure = awaitFailure();
        assertNotNull("a file already at the byte limit must not be appended to", failure);
        Thread.sleep(200);
        assertEquals("nothing is written once the file is at the limit", 0, Files.size(dataFile()));
        finish();
        // the existing recording is kept
        assertTrue("existing content must be kept", Files.size(file) >= before - 64);
    }

    @Test
    public void testAppendBelowLimitsKeepsRecording() throws Exception {
        writeExistingFile(file, 8, 100);
        startAppend(100_000, 60_000);
        push(0, 20, 100);
        awaitDataSize(20 * TAG);
        assertNull(consumer.getFailure());
    }

    /**
     * Writes a finished flv holding the given number of audio tags, as an earlier recording session would.
     */
    static void writeExistingFile(Path file, int count, int step) throws Exception {
        FLVWriter writer = new FLVWriter(file, false);
        for (int i = 0; i < count; i++) {
            assertTrue(writer.writeTag(ImmutableTag.build(ITag.TYPE_AUDIO, i * step, body(), 0)));
        }
        writer.close();
    }

    /**
     * MP3 audio, which needs no decoder configuration before frames are written.
     */
    static byte[] body() {
        byte[] body = new byte[BODY];
        body[0] = (byte) 0x2F;
        return body;
    }

    private void startAppend(long maxBytes, long maxDurationMs) {
        consumer = new FileConsumer(null, file.toFile());
        consumer.setMode(IClientStream.MODE_APPEND);
        consumer.setMaxRecordingBytes(maxBytes);
        consumer.setMaxRecordingDurationMs(maxDurationMs);
    }

    private void finish() throws Exception {
        consumer.uninit();
        consumer.destroy();
        consumer = null;
    }

    private Path dataFile() {
        return dir.resolve("rec.flv.ser");
    }

    private void push(int startTimestamp, int count, int step) throws Exception {
        for (int i = 0; i < count; i++) {
            AudioData audio = new AudioData(IoBuffer.wrap(body()));
            audio.setTimestamp(startTimestamp + i * step);
            consumer.pushMessage(null, RTMPMessage.build(audio));
        }
    }

    private void awaitDataSize(long size) throws Exception {
        for (int i = 0; i < 100 && (Files.notExists(dataFile()) || Files.size(dataFile()) < size); i++) {
            Thread.sleep(50);
        }
        assertEquals("all tags must be written", size, Files.size(dataFile()));
    }

    private String awaitFailure() throws InterruptedException {
        for (int i = 0; i < 100 && consumer.getFailure() == null; i++) {
            Thread.sleep(50);
        }
        return consumer.getFailure();
    }

}
