package org.red5.server.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.red5.server.api.IConnection;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IStreamCapableConnection;
import org.red5.server.api.stream.IStreamFilenameGenerator;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.scope.WebScope;
import org.red5.server.stream.recording.IRecordingPrincipalQuota;
import org.red5.server.stream.recording.RecordingBudget;
import org.red5.server.stream.recording.RecordingRefusedException;
import org.red5.server.stream.recording.RecordingSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

/**
 * Verifies the application recording budget and principal quota through a broadcast stream's recording, with the budget bean found in
 * the application context (issue #490).
 */
@ContextConfiguration("file:src/test/resources/org/red5/server/stream/RecordingBudgetIntegrationTest.xml")
public class RecordingBudgetIntegrationTest extends AbstractJUnit4SpringContextTests {

    // body size of each audio tag
    private static final int BODY = 500;

    // bytes each tag takes in the file: tag header, body and previous tag size
    private static final int TAG = 11 + BODY + 4;

    // record directory used by the filename generator
    private static volatile Path recordDir;

    static {
        System.setProperty("red5.deployment.type", "junit");
    }

    private WebScope appScope;

    private RecordingBudget budget;

    private IStreamCapableConnection conn;

    private ClientBroadcastStream stream;

    @Before
    public void setUp() throws Exception {
        recordDir = Files.createTempDirectory("recording-budget-it");
        appScope = (WebScope) applicationContext.getBean("web.scope");
        budget = (RecordingBudget) applicationContext.getBean(RecordingBudget.BEAN_NAME);
        budget.setMaxApplicationBytes(0);
        budget.setPrincipalQuota(null);
        conn = connection(appScope, "conn-1", "192.0.2.1");
        stream = new ClientBroadcastStream();
        stream.setScope(appScope);
        stream.setConnection(conn);
        stream.setPublishedName("cam");
        // the publish start notifications need a full connection, they are not what is tested
        stream.sendStartNotification = false;
    }

    @After
    public void tearDown() throws Exception {
        stream.stopRecording();
        try (Stream<Path> files = Files.walk(recordDir)) {
            files.sorted((a, b) -> b.compareTo(a)).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    public void testRecordPublishRefusedWhenBudgetUsedUp() throws Exception {
        Path previous = recordDir.resolve("cam.flv");
        Files.write(previous, new byte[4000]);
        Files.write(recordDir.resolve("vod.flv"), new byte[10_000]);
        budget.setMaxApplicationBytes(10_000);
        try {
            stream.saveAs("cam", false);
            fail("a record publish must be refused while the application budget is used up");
        } catch (IOException e) {
            assertTrue("the refusal is reported as such", e.getCause() instanceof RecordingRefusedException);
            assertTrue(e.getMessage(), e.getMessage().contains("budget"));
        }
        assertFalse(stream.isRecording());
        assertEquals("a refused recording must not replace the previous one", 4000, Files.size(previous));
        // with room in the budget the record publish replaces it
        budget.setMaxApplicationBytes(10_001);
        stream.saveAs("cam", false);
        assertTrue(stream.isRecording());
        stream.stopRecording();
        assertTrue(Files.size(previous) < 4000);
    }

    @Test
    public void testRecordingStopsAtBudgetWithAttribution() throws Exception {
        TestQuota quota = new TestQuota();
        budget.setPrincipalQuota(quota);
        budget.setMaxApplicationBytes(20 * TAG);
        stream.saveAs("cam", false);
        assertTrue(stream.isRecording());
        IRecordingListener listener = stream.recordingListener.get();
        assertEquals(1, quota.started.size());
        RecordingSession started = quota.started.get(0);
        assertEquals("junit", started.getApplication());
        assertEquals("cam", started.getStreamName());
        assertEquals("conn-1", started.getConnectionId());
        assertEquals("192.0.2.1", started.getRemoteAddress());
        assertEquals("alice", started.getPrincipal());
        // publish more than the budget allows; the listener writes in the background
        for (int i = 0; i < 40; i++) {
            dispatch(i * 100);
        }
        String failure = null;
        for (int i = 0; i < 300 && (failure = listener.getFileConsumer().getFailure()) == null; i++) {
            Thread.sleep(50);
        }
        assertNotNull("the recording must stop at the application budget", failure);
        assertTrue(failure, failure.contains("budget"));
        // the next packet reports the failure, which stops the recording while the publish continues
        dispatch(4000);
        assertFalse("the recording must be stopped", stream.isRecording());
        assertEquals(1, quota.stopped.size());
        RecordingSession stopped = quota.stopped.get(0);
        assertEquals(failure, stopped.getFailure());
        assertEquals("alice", stopped.getPrincipal());
        assertTrue("bytes written must be attributed", stopped.getBytesWritten() >= 19 * TAG);
        assertTrue(stopped.getDurationMs() > 0);
        assertTrue(budget.getPrincipalBytes("alice") >= 19 * TAG);
        // the budget is now used up for the next recording
        ClientBroadcastStream next = new ClientBroadcastStream();
        next.setScope(appScope);
        next.setConnection(conn);
        next.setPublishedName("cam2");
        try {
            next.saveAs("cam2", false);
            fail("a record publish must be refused once the budget is reached");
        } catch (IOException e) {
            assertTrue(e.getCause() instanceof RecordingRefusedException);
        }
    }

    private void dispatch(int timestamp) {
        byte[] body = new byte[BODY];
        // MP3 audio, which needs no decoder configuration before frames are written
        body[0] = (byte) 0x2F;
        AudioData audio = new AudioData(IoBuffer.wrap(body));
        audio.setTimestamp(timestamp);
        stream.dispatchEvent(audio);
    }

    private static IStreamCapableConnection connection(IScope scope, String sessionId, String remoteAddress) {
        return (IStreamCapableConnection) Proxy.newProxyInstance(IStreamCapableConnection.class.getClassLoader(), new Class<?>[] { IStreamCapableConnection.class }, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getScope":
                    return scope;
                case "getSessionId":
                    return sessionId;
                case "getRemoteAddress":
                    return remoteAddress;
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return "connection " + sessionId;
                default:
                    return method.getReturnType() == boolean.class ? false : null;
            }
        });
    }

    /**
     * Records into the test's directory.
     */
    public static class TestFilenameGenerator implements IStreamFilenameGenerator {

        @Override
        public String generateFilename(IScope scope, String name, GenerationType type) {
            return generateFilename(scope, name, null, type);
        }

        @Override
        public String generateFilename(IScope scope, String name, String extension, GenerationType type) {
            return recordDir.resolve(extension != null ? name + extension : name).toString();
        }

        @Override
        public boolean resolvesToAbsolutePath() {
            return true;
        }

    }

    /**
     * Identifies every connection as alice, without a quota.
     */
    private static class TestQuota implements IRecordingPrincipalQuota {

        final List<RecordingSession> started = new CopyOnWriteArrayList<>();

        final List<RecordingSession> stopped = new CopyOnWriteArrayList<>();

        @Override
        public String getPrincipal(IConnection conn) {
            return conn != null ? "alice" : null;
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
