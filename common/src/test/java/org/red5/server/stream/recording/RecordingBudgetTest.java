package org.red5.server.stream.recording;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.red5.io.ITag;
import org.red5.io.flv.impl.FLVWriter;
import org.red5.server.api.IConnection;
import org.red5.server.api.stream.IClientStream;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.stream.consumer.FileConsumer;
import org.red5.server.stream.consumer.ImmutableTag;
import org.red5.server.stream.message.RTMPMessage;

/**
 * Verifies the per-application recording budget, the per-principal quota hook and recording attribution (issue #490).
 */
public class RecordingBudgetTest {

    // body size of each audio tag
    private static final int BODY = 500;

    // bytes each tag takes in the file: tag header, body and previous tag size
    private static final int TAG = 11 + BODY + 4;

    private Path dir;

    private RecordingBudget budget;

    private final List<FileConsumer> consumers = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("recording-budget");
        budget = new RecordingBudget();
    }

    @After
    public void tearDown() throws Exception {
        for (FileConsumer consumer : consumers) {
            consumer.uninit();
            consumer.destroy();
        }
        budget.destroy();
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted((a, b) -> b.compareTo(a)).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    public void testEverythingOffByDefault() throws Exception {
        assertEquals(0L, budget.getMaxApplicationBytes());
        assertNull(budget.getPrincipalQuota());
        assertNull(budget.getRetentionPolicy());
        assertEquals(0L, budget.getRetentionIntervalMs());
        filler("vod.flv", 100_000);
        RecordingSession session = budget.start(dir, "live", null, "rec", dir.resolve("rec.flv"), false);
        FileConsumer consumer = consumer("rec.flv", IClientStream.MODE_RECORD, session);
        push(consumer, 0, 30);
        awaitDataSize(dir.resolve("rec.flv.ser"), 30 * TAG);
        assertNull("nothing limits a recording by default", consumer.getFailure());
        finish(consumer, session);
        assertTrue("an old file is kept without a retention policy", Files.exists(dir.resolve("vod.flv")));
        // another recording is still allowed
        budget.stop(budget.start(dir, "live", null, "rec2", dir.resolve("rec2.flv"), false));
    }

    @Test
    public void testRefusesRecordWhenBudgetUsedUp() throws Exception {
        filler("vod.flv", 3000);
        filler("notes.txt", 2000);
        budget.setMaxApplicationBytes(5000);
        try {
            budget.start(dir, "live", null, "rec", dir.resolve("rec.flv"), false);
            fail("a recording must be refused while the application budget is used up");
        } catch (RecordingRefusedException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("budget"));
        }
        // one byte of room allows it
        budget.setMaxApplicationBytes(5001);
        budget.stop(budget.start(dir, "live", null, "rec", dir.resolve("rec.flv"), false));
    }

    @Test
    public void testStopsRecordingAtBudget() throws Exception {
        filler("vod.flv", 3000);
        long max = 3000 + 10 * TAG;
        budget.setMaxApplicationBytes(max);
        RecordingSession session = budget.start(dir, "live", null, "rec", dir.resolve("rec.flv"), false);
        FileConsumer consumer = consumer("rec.flv", IClientStream.MODE_RECORD, session);
        push(consumer, 0, 30);
        String failure = awaitFailure(consumer);
        assertNotNull("the recording must stop at the application budget", failure);
        assertTrue(failure, failure.contains("budget"));
        long written = Files.size(dir.resolve("rec.flv.ser"));
        assertTrue("only the tags up to the budget are written, wrote " + written, written <= 11 * TAG);
        finish(consumer, session);
        assertEquals(failure, session.getFailure());
        assertTrue("the directory is at the budget", budget.getUsedBytes(dir) >= max);
        // the next recording is refused
        try {
            budget.start(dir, "live", null, "next", dir.resolve("next.flv"), false);
            fail("a recording must be refused once the budget is reached");
        } catch (RecordingRefusedException e) {
            // expected
        }
    }

    @Test
    public void testRecordModeDoesNotCountTheFileItReplaces() throws Exception {
        writeExistingFile(dir.resolve("rec.flv"), 20);
        long size = Files.size(dir.resolve("rec.flv"));
        budget.setMaxApplicationBytes(size);
        // record mode replaces rec.flv, so its bytes are not used
        budget.stop(budget.start(dir, "live", null, "rec", dir.resolve("rec.flv"), false));
    }

    @Test
    public void testAppendCountsExistingFile() throws Exception {
        Path file = dir.resolve("rec.flv");
        writeExistingFile(file, 8);
        long size = Files.size(file);
        // the file being appended to already uses the whole budget
        budget.setMaxApplicationBytes(size);
        try {
            budget.start(dir, "live", null, "rec", file, true);
            fail("appending must be refused when the existing file uses up the budget");
        } catch (RecordingRefusedException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("budget"));
        }
        assertEquals("a refused append leaves the file alone", size, Files.size(file));
        // room for about five more tags
        budget.setMaxApplicationBytes(size + 5 * TAG);
        RecordingSession session = budget.start(dir, "live", null, "rec", file, true);
        FileConsumer consumer = consumer("rec.flv", IClientStream.MODE_APPEND, session);
        push(consumer, 0, 20);
        String failure = awaitFailure(consumer);
        assertNotNull("appending must stop at the budget, counting the existing file", failure);
        assertTrue(failure, failure.contains("budget"));
        assertTrue("only the tags up to the budget are appended", Files.size(dir.resolve("rec.flv.ser")) <= 7 * TAG);
        finish(consumer, session);
    }

    @Test
    public void testRecordingInProgressIsCountedOnce() throws Exception {
        RecordingSession session = budget.start(dir, "live", null, "rec", dir.resolve("rec.flv"), false);
        FileConsumer consumer = consumer("rec.flv", IClientStream.MODE_RECORD, session);
        push(consumer, 0, 10);
        awaitDataSize(dir.resolve("rec.flv.ser"), 10 * TAG);
        // the working files of the recording on disk are left out of the walk and its size is counted from the writer
        long used = budget.getUsedBytes(dir);
        assertEquals("a recording in progress must be counted once", session.getFileBytes(), used);
        assertTrue(used >= 10 * TAG);
        // a second recording sees the first one's bytes
        budget.setMaxApplicationBytes(used);
        try {
            budget.start(dir, "live", null, "other", dir.resolve("other.flv"), false);
            fail("the bytes of the recording in progress must count towards the budget");
        } catch (RecordingRefusedException e) {
            // expected
        }
        budget.setMaxApplicationBytes(used + 1000);
        budget.stop(budget.start(dir, "live", null, "other", dir.resolve("other.flv"), false));
        finish(consumer, session);
        // stopped, the file is measured on disk
        assertEquals(Files.size(dir.resolve("rec.flv")), budget.getUsedBytes(dir));
    }

    @Test
    public void testPrincipalQuota() throws Exception {
        TestQuota quota = new TestQuota();
        quota.maxBytes.put("alice", 10L * TAG);
        quota.maxBytes.put("carol", 1000L);
        quota.usedBytes.put("carol", 1000L);
        budget.setPrincipalQuota(quota);
        // alice records until her quota is reached
        quota.principal = "alice";
        RecordingSession session = budget.start(dir, "live", null, "a1", dir.resolve("a1.flv"), false);
        assertEquals("alice", session.getPrincipal());
        FileConsumer consumer = consumer("a1.flv", IClientStream.MODE_RECORD, session);
        push(consumer, 0, 30);
        String failure = awaitFailure(consumer);
        assertNotNull("the recording must stop at the principal's quota", failure);
        assertTrue(failure, failure.contains("quota") && failure.contains("alice"));
        finish(consumer, session);
        assertTrue(budget.getPrincipalBytes("alice") >= 10L * TAG);
        // her next recording is refused
        try {
            budget.start(dir, "live", null, "a2", dir.resolve("a2.flv"), false);
            fail("a recording must be refused once the principal's quota is used up");
        } catch (RecordingRefusedException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("alice"));
        }
        // carol used her quota before the budget started counting
        quota.principal = "carol";
        try {
            budget.start(dir, "live", null, "c1", dir.resolve("c1.flv"), false);
            fail("usage reported by the hook must count");
        } catch (RecordingRefusedException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("carol"));
        }
        // bob has no quota, and a connection without a principal is not subject to one
        quota.principal = "bob";
        budget.stop(budget.start(dir, "live", null, "b1", dir.resolve("b1.flv"), false));
        quota.principal = null;
        budget.stop(budget.start(dir, "live", null, "anon", dir.resolve("anon.flv"), false));
    }

    @Test
    public void testAttribution() throws Exception {
        TestQuota quota = new TestQuota();
        quota.principal = "alice";
        budget.setPrincipalQuota(quota);
        RecordingSession session = budget.start(dir, "live", null, "cam1", dir.resolve("cam1.flv"), false);
        assertEquals(1, quota.started.size());
        assertTrue(quota.stopped.isEmpty());
        FileConsumer consumer = consumer("cam1.flv", IClientStream.MODE_RECORD, session);
        push(consumer, 0, 10);
        awaitDataSize(dir.resolve("cam1.flv.ser"), 10 * TAG);
        finish(consumer, session);
        assertEquals(1, quota.stopped.size());
        RecordingSession stopped = quota.stopped.get(0);
        assertEquals("live", stopped.getApplication());
        assertEquals("cam1", stopped.getStreamName());
        assertEquals(dir.resolve("cam1.flv").toAbsolutePath().normalize(), stopped.getFile());
        assertEquals("alice", stopped.getPrincipal());
        assertFalse(stopped.isAppend());
        assertEquals(10L * TAG, stopped.getBytesWritten());
        assertEquals(900L, stopped.getDurationMs());
        assertNull(stopped.getFailure());
        assertTrue(stopped.isStopped());
        assertTrue(stopped.getStopTime() >= stopped.getStartTime());
        // stopping again does nothing
        budget.stop(session);
        assertEquals(1, quota.stopped.size());
    }

    @Test
    public void testRetentionRunsWhenRecordingStops() throws Exception {
        Path old1 = filler("old1.flv", 1000);
        Path old2 = filler("old2.flv", 1000);
        Path vod = filler("vod.mp4", 5000);
        age(old1, 3 * 3600_000L);
        age(old2, 2 * 3600_000L);
        age(vod, 10 * 3600_000L);
        OldestFirstRetentionPolicy policy = new OldestFirstRetentionPolicy();
        policy.setMaxTotalBytes(2000);
        budget.setRetentionPolicy(policy);
        budget.setMaxApplicationBytes(100_000);
        RecordingSession session = budget.start(dir, "live", null, "rec", dir.resolve("rec.flv"), false);
        FileConsumer consumer = consumer("rec.flv", IClientStream.MODE_RECORD, session);
        push(consumer, 0, 10);
        awaitDataSize(dir.resolve("rec.flv.ser"), 10 * TAG);
        finish(consumer, session);
        assertFalse("the oldest recordings are deleted when the total is above the maximum", Files.exists(old1));
        assertFalse(Files.exists(old2));
        assertTrue("other extensions are kept", Files.exists(vod));
        assertTrue("the recording that just stopped is kept", Files.exists(dir.resolve("rec.flv")));
        // the budget measures the directory after deleting
        assertEquals(5000 + Files.size(dir.resolve("rec.flv")), budget.getUsedBytes(dir));
    }

    @Test
    public void testScheduledRetention() throws Exception {
        Path old = filler("old.flv", 1000);
        Path recent = filler("recent.flv", 1000);
        age(old, 2 * 3600_000L);
        age(recent, 120_000L);
        OldestFirstRetentionPolicy policy = new OldestFirstRetentionPolicy();
        policy.setMaxAgeMs(3600_000L);
        budget.setRetentionPolicy(policy);
        budget.setRetentionIntervalMs(50);
        budget.setRecordDirectory(dir.toString());
        budget.init();
        for (int i = 0; i < 100 && Files.exists(old); i++) {
            Thread.sleep(50);
        }
        assertFalse("scheduled retention must delete an expired recording", Files.exists(old));
        assertTrue(Files.exists(recent));
    }

    private Path filler(String name, int size) throws IOException {
        Path file = dir.resolve(name);
        Files.write(file, new byte[size]);
        return file;
    }

    private static void age(Path file, long ageMs) throws IOException {
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() - ageMs));
    }

    private FileConsumer consumer(String name, String mode, RecordingSession session) {
        FileConsumer consumer = new FileConsumer(null, dir.resolve(name).toFile());
        consumer.setMode(mode);
        consumer.setRecordingSession(session);
        consumers.add(consumer);
        return consumer;
    }

    private void finish(FileConsumer consumer, RecordingSession session) throws Exception {
        consumer.uninit();
        consumer.destroy();
        consumers.remove(consumer);
        // as the recording listener does once the file is closed
        session.setFailure(consumer.getFailure());
        budget.stop(session);
    }

    /**
     * Writes a finished flv holding the given number of audio tags, as an earlier recording session would.
     */
    private static void writeExistingFile(Path file, int count) throws Exception {
        FLVWriter writer = new FLVWriter(file, false);
        for (int i = 0; i < count; i++) {
            assertTrue(writer.writeTag(ImmutableTag.build(ITag.TYPE_AUDIO, i * 100, body(), 0)));
        }
        writer.close();
    }

    /**
     * MP3 audio, which needs no decoder configuration before frames are written.
     */
    private static byte[] body() {
        byte[] body = new byte[BODY];
        body[0] = (byte) 0x2F;
        return body;
    }

    private static void push(FileConsumer consumer, int startTimestamp, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            AudioData audio = new AudioData(IoBuffer.wrap(body()));
            audio.setTimestamp(startTimestamp + i * 100);
            consumer.pushMessage(null, RTMPMessage.build(audio));
        }
    }

    private static void awaitDataSize(Path file, long size) throws Exception {
        for (int i = 0; i < 100 && (Files.notExists(file) || Files.size(file) < size); i++) {
            Thread.sleep(50);
        }
        assertEquals("all tags must be written", size, Files.size(file));
    }

    private static String awaitFailure(FileConsumer consumer) throws InterruptedException {
        for (int i = 0; i < 100 && consumer.getFailure() == null; i++) {
            Thread.sleep(50);
        }
        return consumer.getFailure();
    }

    /**
     * Quota hook returning the principal set by the test.
     */
    private static class TestQuota implements IRecordingPrincipalQuota {

        volatile String principal;

        final Map<String, Long> maxBytes = new HashMap<>();

        final Map<String, Long> usedBytes = new HashMap<>();

        final List<RecordingSession> started = new CopyOnWriteArrayList<>();

        final List<RecordingSession> stopped = new CopyOnWriteArrayList<>();

        @Override
        public String getPrincipal(IConnection conn) {
            return principal;
        }

        @Override
        public long getMaxBytes(String principal) {
            return maxBytes.getOrDefault(principal, 0L);
        }

        @Override
        public long getUsedBytes(String principal) {
            return usedBytes.getOrDefault(principal, 0L);
        }

        @Override
        public void recordingStarted(RecordingSession session) {
            started.add(session);
        }

        @Override
        public void recordingStopped(RecordingSession session) {
            stopped.add(session);
        }

    }

}
