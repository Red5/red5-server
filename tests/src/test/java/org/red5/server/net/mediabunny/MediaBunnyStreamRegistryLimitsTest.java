package org.red5.server.net.mediabunny;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.lang.reflect.Proxy;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.red5.server.api.scope.IBroadcastScope;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IClientBroadcastStream;
import org.red5.server.net.mediabunny.MediaBunnyStreamRegistry.StreamSubscription;
import org.red5.server.net.mediabunny.MediaBunnyStreamRegistry.SubscriberLimitException;

/**
 * Verifies MediaBunny subscriber caps and slow-consumer handling (issue #459).
 */
public class MediaBunnyStreamRegistryLimitsTest {

    private MediaBunnyStreamRegistry registry;

    private IScope scope;

    @Before
    public void setUp() {
        registry = new MediaBunnyStreamRegistry();
        IClientBroadcastStream stream = proxy(IClientBroadcastStream.class, null);
        IBroadcastScope broadcastScope = proxy(IBroadcastScope.class, stream);
        scope = (IScope) Proxy.newProxyInstance(IScope.class.getClassLoader(), new Class<?>[] { IScope.class }, (p, method, args) -> {
            switch (method.getName()) {
                case "getName":
                    return "live";
                case "getBroadcastScope":
                    return broadcastScope;
                case "hashCode":
                    return System.identityHashCode(p);
                case "equals":
                    return p == args[0];
                default:
                    return method.getReturnType() == boolean.class ? false : null;
            }
        });
    }

    @Test
    public void testSlowConsumerIsDisconnectedWithBoundedBacklog() throws Exception {
        registry.setMaxQueuedBytes(1000);
        StreamSubscription slow = registry.subscribe(scope, "stream1");
        StreamSubscription fast = registry.subscribe(scope, "stream1");
        for (int i = 0; i < 100; i++) {
            registry.onFragment("live:stream1", new byte[600]);
            // the fast reader keeps up
            assertEquals(600, fast.poll(1, TimeUnit.SECONDS).length);
        }
        // the slow reader never read: its backlog was discarded and it was ended
        assertEquals(0, slow.getQueuedBytes());
        assertArrayEquals(new byte[0], slow.poll(1, TimeUnit.SECONDS));
        slow.close();
        fast.close();
        assertEquals(0, registry.getSubscriberCount());
    }

    @Test
    public void testGlobalSubscriberLimit() throws Exception {
        registry.setMaxSubscribers(2);
        StreamSubscription a = registry.subscribe(scope, "stream1");
        registry.subscribe(scope, "stream2");
        try {
            registry.subscribe(scope, "stream3");
            fail("Expected SubscriberLimitException");
        } catch (SubscriberLimitException expected) {
        }
        assertEquals(2, registry.getSubscriberCount());
        a.close();
        // closing twice releases the slot once
        a.close();
        assertEquals(1, registry.getSubscriberCount());
        registry.subscribe(scope, "stream3");
        assertEquals(2, registry.getSubscriberCount());
    }

    @Test
    public void testPerStreamSubscriberLimit() throws Exception {
        registry.setMaxSubscribersPerStream(1);
        registry.subscribe(scope, "stream1");
        try {
            registry.subscribe(scope, "stream1");
            fail("Expected SubscriberLimitException");
        } catch (SubscriberLimitException expected) {
        }
        assertEquals(1, registry.getSubscriberCount());
        registry.subscribe(scope, "stream2");
        assertEquals(2, registry.getSubscriberCount());
    }

    @Test
    public void testIdlePollReturnsNull() throws Exception {
        StreamSubscription sub = registry.subscribe(scope, "stream1");
        assertNull(sub.poll(10, TimeUnit.MILLISECONDS));
        sub.close();
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Object stream) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (p, method, args) -> {
            switch (method.getName()) {
                case "getClientBroadcastStream":
                    return stream;
                case "hashCode":
                    return System.identityHashCode(p);
                case "equals":
                    return p == args[0];
                default:
                    return method.getReturnType() == boolean.class ? false : null;
            }
        });
    }

}
