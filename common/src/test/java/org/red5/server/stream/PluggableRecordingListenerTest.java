package org.red5.server.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.red5.server.api.IConnection;
import org.red5.server.api.IContext;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.api.stream.IStreamCapableConnection;
import org.red5.server.api.stream.IStreamPacket;
import org.red5.server.stream.consumer.FileConsumer;
import org.red5.server.stream.recording.RecordingRefusedException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.support.GenericApplicationContext;

/**
 * Verifies that an application can supply its own recording listener as a prototype bean (issue #490).
 */
public class PluggableRecordingListenerTest {

    private static final List<TestRecordingListener> created = new CopyOnWriteArrayList<>();

    private GenericApplicationContext appCtx;

    private IScope scope;

    private IStreamCapableConnection conn;

    @Before
    public void setUp() {
        created.clear();
        appCtx = new GenericApplicationContext();
        IContext context = proxy(IContext.class, (method, args) -> "getApplicationContext".equals(method) ? appCtx : null);
        scope = proxy(IScope.class, (method, args) -> "getContext".equals(method) ? context : null);
        conn = proxy(IStreamCapableConnection.class, (method, args) -> "getScope".equals(method) ? scope : null);
    }

    @After
    public void tearDown() {
        appCtx.close();
    }

    @Test
    public void testApplicationListenerBeanIsUsed() throws Exception {
        appCtx.registerBean(IRecordingListener.BEAN_NAME, TestRecordingListener.class, bd -> bd.setScope(BeanDefinition.SCOPE_PROTOTYPE));
        appCtx.refresh();
        ClientBroadcastStream stream = stream();
        stream.saveAs("cam1", false);
        assertEquals("the application's listener must be created for the recording", 1, created.size());
        TestRecordingListener listener = created.get(0);
        assertEquals("cam1", listener.name);
        assertSame(conn, listener.conn);
        assertTrue(listener.started);
        assertTrue(stream.isRecording());
        stream.stopRecording();
        assertTrue(listener.stopped);
        // each recording gets its own listener
        stream.saveAs("cam2", false);
        assertEquals(2, created.size());
        assertNotSame(listener, created.get(1));
        stream.stopRecording();
    }

    @Test
    public void testRefusedRecordingFailsSaveAs() throws Exception {
        appCtx.registerBean(IRecordingListener.BEAN_NAME, TestRecordingListener.class, bd -> bd.setScope(BeanDefinition.SCOPE_PROTOTYPE));
        appCtx.refresh();
        ClientBroadcastStream stream = stream();
        try {
            stream.saveAs("refuse", false);
            fail("a refused recording must fail saveAs");
        } catch (IOException e) {
            assertTrue(e.getCause() instanceof RecordingRefusedException);
            assertEquals("Application recording budget used up", e.getMessage());
        }
        assertTrue(!stream.isRecording());
    }

    @Test
    public void testDefaultListenerWithoutBean() {
        appCtx.refresh();
        assertTrue(RecordingListener.createRecordingListener(scope) instanceof RecordingListener);
        assertTrue(created.isEmpty());
    }

    @Test
    public void testSingletonBeanIsNotShared() {
        // a singleton would be shared by every recording, so it is not used
        appCtx.registerBean(IRecordingListener.BEAN_NAME, TestRecordingListener.class);
        appCtx.refresh();
        created.clear();
        assertTrue(RecordingListener.createRecordingListener(scope) instanceof RecordingListener);
    }

    private ClientBroadcastStream stream() {
        ClientBroadcastStream stream = new ClientBroadcastStream();
        stream.setConnection(conn);
        stream.setPublishedName("cam1");
        return stream;
    }

    interface Answer {
        Object answer(String method, Object[] args);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Answer answer) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
            switch (method.getName()) {
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return type.getSimpleName() + " stub";
                default:
                    Object result = answer.answer(method.getName(), args);
                    if (result == null && method.getReturnType() == boolean.class) {
                        return false;
                    }
                    return result;
            }
        });
    }

    /**
     * Application listener; refuses a stream named "refuse" as a recording budget would.
     */
    public static class TestRecordingListener implements IRecordingListener {

        String name;

        IConnection conn;

        boolean started, stopped, recording;

        public TestRecordingListener() {
            created.add(this);
        }

        @Override
        public boolean init(IConnection conn, String name, boolean isAppend) {
            if ("refuse".equals(name)) {
                throw new RecordingRefusedException("Application recording budget used up");
            }
            this.conn = conn;
            this.name = name;
            recording = true;
            return true;
        }

        @Override
        public boolean init(IScope scope, String name, boolean isAppend) {
            this.name = name;
            recording = true;
            return true;
        }

        @Override
        public void start() {
            started = true;
        }

        @Override
        public void stop() {
            stopped = true;
            recording = false;
        }

        @Override
        public void packetReceived(IBroadcastStream stream, IStreamPacket packet) {
        }

        @Override
        public boolean isRecording() {
            return recording;
        }

        @Override
        public boolean isAppending() {
            return false;
        }

        @Override
        public FileConsumer getFileConsumer() {
            return null;
        }

        @Override
        public void setFileConsumer(FileConsumer recordingConsumer) {
        }

        @Override
        public String getFileName() {
            return name;
        }

        @Override
        public void setFileName(String fileName) {
        }

    }

}
