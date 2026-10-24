package org.red5.server.net.mediabunny;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.lang.reflect.Proxy;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

    private MediaBunnySubscriberLimits limits;

    private IScope scope;

    @Before
    public void setUp() {
        registry = new MediaBunnyStreamRegistry();
        limits = new MediaBunnySubscriberLimits();
        scope = scope("live");
    }

    @Test
    public void testSlowConsumerIsDisconnectedWithBoundedBacklog() throws Exception {
        limits.setMaxQueuedBytes(1000);
        StreamSubscription slow = registry.subscribe(scope, "stream1", limits, null);
        StreamSubscription fast = registry.subscribe(scope, "stream1", limits, null);
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
        assertEquals(0, limits.getSubscriberCount());
    }

    @Test
    public void testGlobalSubscriberLimit() throws Exception {
        limits.setMaxSubscribers(2);
        StreamSubscription a = registry.subscribe(scope, "stream1", limits, null);
        registry.subscribe(scope, "stream2", limits, null);
        try {
            registry.subscribe(scope, "stream3", limits, null);
            fail("Expected SubscriberLimitException");
        } catch (SubscriberLimitException expected) {
        }
        assertEquals(2, limits.getSubscriberCount());
        a.close();
        // closing twice releases the slot once
        a.close();
        assertEquals(1, limits.getSubscriberCount());
        registry.subscribe(scope, "stream3", limits, null);
        assertEquals(2, limits.getSubscriberCount());
    }

    @Test
    public void testPerStreamSubscriberLimit() throws Exception {
        limits.setMaxSubscribersPerStream(1);
        registry.subscribe(scope, "stream1", limits, null);
        try {
            registry.subscribe(scope, "stream1", limits, null);
            fail("Expected SubscriberLimitException");
        } catch (SubscriberLimitException expected) {
        }
        assertEquals(1, limits.getSubscriberCount());
        assertEquals(1, registry.getAttachedSubscriberCount("live:stream1"));
        registry.subscribe(scope, "stream2", limits, null);
        assertEquals(2, limits.getSubscriberCount());
    }

    @Test
    public void testPerAddressSubscriberLimit() throws Exception {
        limits.setMaxSubscribersPerAddress(2);
        StreamSubscription a = registry.subscribe(scope, "stream1", limits, "10.0.0.1");
        registry.subscribe(scope, "stream2", limits, "10.0.0.1");
        try {
            registry.subscribe(scope, "stream3", limits, "10.0.0.1");
            fail("Expected SubscriberLimitException");
        } catch (SubscriberLimitException expected) {
        }
        // a refused subscription takes no slot of any kind
        assertEquals(2, limits.getSubscriberCount());
        assertEquals(0, limits.getStreamSubscriberCount("live:stream3"));
        assertEquals(0, registry.getAttachedSubscriberCount("live:stream3"));
        // another address is unaffected
        registry.subscribe(scope, "stream3", limits, "10.0.0.2");
        a.close();
        assertEquals(1, limits.getAddressSubscriberCount("10.0.0.1"));
        registry.subscribe(scope, "stream3", limits, "10.0.0.1");
        assertEquals(2, limits.getAddressSubscriberCount("10.0.0.1"));
    }

    @Test
    public void testLimitsAreIndependentPerEndpoint() throws Exception {
        MediaBunnySubscriberLimits other = new MediaBunnySubscriberLimits();
        limits.setMaxSubscribers(1);
        other.setMaxSubscribers(3);
        registry.subscribe(scope, "stream1", limits, null);
        try {
            registry.subscribe(scope, "stream1", limits, null);
            fail("Expected SubscriberLimitException");
        } catch (SubscriberLimitException expected) {
        }
        // the other endpoint shares the stream but not the budget
        for (int i = 0; i < 3; i++) {
            registry.subscribe(scope, "stream1", other, null);
        }
        assertEquals(1, limits.getSubscriberCount());
        assertEquals(3, other.getSubscriberCount());
        assertEquals(4, registry.getAttachedSubscriberCount("live:stream1"));
    }

    @Test
    public void testMissingStreamReleasesSlot() throws Exception {
        IScope empty = (IScope) Proxy.newProxyInstance(IScope.class.getClassLoader(), new Class<?>[] { IScope.class }, (p, method, args) -> "getName".equals(method.getName()) ? "live" : (method.getReturnType() == boolean.class ? false : null));
        try {
            registry.subscribe(empty, "missing", limits, "10.0.0.1");
            fail("Expected IllegalStateException");
        } catch (SubscriberLimitException e) {
            fail("Unexpected limit exception");
        } catch (IllegalStateException expected) {
        }
        assertEquals(0, limits.getSubscriberCount());
        assertEquals(0, limits.getAddressSubscriberCount("10.0.0.1"));
    }

    @Test
    public void testIdlePollReturnsNull() throws Exception {
        StreamSubscription sub = registry.subscribe(scope, "stream1", limits, null);
        assertNull(sub.poll(10, TimeUnit.MILLISECONDS));
        sub.close();
    }

    /**
     * A subscriber added while the stream's last subscriber leaves must stay attached. Before the fix, removal could drop the state as
     * empty just as a concurrent subscribe reused it, leaving the new subscriber on a detached state until its idle timeout.
     */
    @Test
    public void testSubscribeRacingLastUnsubscribeStaysAttached() throws Exception {
        int orphans = 0;
        for (int i = 0; i < 10000; i++) {
            StreamSubscription first = registry.subscribe(scope, "race", limits, null);
            CyclicBarrier barrier = new CyclicBarrier(2);
            AtomicReference<StreamSubscription> second = new AtomicReference<>();
            Thread t = new Thread(() -> {
                try {
                    barrier.await();
                    second.set(registry.subscribe(scope, "race", limits, null));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            t.start();
            barrier.await();
            first.close();
            t.join();
            assertNotNull(second.get());
            registry.onFragment("live:race", new byte[10]);
            if (second.get().poll(0, TimeUnit.MILLISECONDS) == null) {
                orphans++;
            }
            second.get().close();
        }
        assertEquals("subscribers orphaned on a detached stream state", 0, orphans);
        assertEquals(0, limits.getSubscriberCount());
        assertEquals(0, registry.getAttachedSubscriberCount("live:race"));
    }

    static IScope scope(String name) {
        IClientBroadcastStream stream = proxy(IClientBroadcastStream.class, null);
        IBroadcastScope broadcastScope = proxy(IBroadcastScope.class, stream);
        return (IScope) Proxy.newProxyInstance(IScope.class.getClassLoader(), new Class<?>[] { IScope.class }, (p, method, args) -> {
            switch (method.getName()) {
                case "getName":
                    return name;
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
