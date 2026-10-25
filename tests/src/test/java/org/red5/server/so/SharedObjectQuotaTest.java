package org.red5.server.so;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.red5.server.api.IConnection;
import org.red5.server.api.IContext;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.event.IEventListener;
import org.red5.server.api.persistence.IPersistenceStore;
import org.red5.server.api.scheduling.IScheduledJob;
import org.red5.server.api.scheduling.ISchedulingService;
import org.red5.server.api.scope.IBasicScope;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.ScopeType;
import org.red5.server.api.so.ISharedObject;
import org.red5.server.net.rtmp.RTMPMinaConnection;
import org.red5.server.persistence.FilePersistence;
import org.red5.server.scope.Scope;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Client-specific shared object limits: name depth, client persistence, per-connection creation count (issue #454), scope memory budget,
 * connected objects per connection and idle eviction of persistent objects (issue #492).
 */
public class SharedObjectQuotaTest {

    private SharedObjectService service;

    private IScope scope;

    private final Map<String, IBasicScope> children = new ConcurrentHashMap<>();

    private final Map<String, Object> scopeAttributes = new ConcurrentHashMap<>();

    // once jobs handed to the scheduling service, run by the tests
    private final List<IScheduledJob> jobs = new CopyOnWriteArrayList<>();

    private static ThreadPoolTaskScheduler scheduler;

    private File webapp;

    @BeforeClass
    public static void startScheduler() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        SharedObjectService.setScheduler(scheduler);
    }

    @AfterClass
    public static void stopScheduler() {
        scheduler.shutdown();
    }

    @Before
    public void setUp() {
        service = new SharedObjectService();
        ISchedulingService schedulingService = (ISchedulingService) Proxy.newProxyInstance(ISchedulingService.class.getClassLoader(), new Class<?>[] { ISchedulingService.class }, (proxy, method, args) -> {
            if ("addScheduledOnceJob".equals(method.getName())) {
                jobs.add((IScheduledJob) args[1]);
            }
            return method.getReturnType() == String.class ? "job" : null;
        });
        IContext context = (IContext) Proxy.newProxyInstance(IContext.class.getClassLoader(), new Class<?>[] { IContext.class }, (proxy, method, args) -> "getBean".equals(method.getName()) ? schedulingService : null);
        // the application scope also acts as the resource resolver of its persistence stores
        scope = (IScope) Proxy.newProxyInstance(IScope.class.getClassLoader(), new Class<?>[] { IScope.class, ResourcePatternResolver.class }, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getContextPath":
                    return "/app";
                case "getContext":
                    return context;
                case "getName":
                    return "app";
                case "hasChildScope":
                    return args.length == 2 && children.containsKey((String) args[1]);
                case "getBasicScopeNames":
                    return children.keySet();
                case "getBasicScope":
                    return children.get((String) args[1]);
                case "addChildScope":
                    IBasicScope child = (IBasicScope) args[0];
                    return children.putIfAbsent(child.getName(), child) == null;
                case "removeChildScope":
                    IBasicScope removed = (IBasicScope) args[0];
                    children.remove(removed.getName(), removed);
                    return null;
                case "hasAttribute":
                    return scopeAttributes.containsKey((String) args[0]);
                case "getAttribute":
                    return scopeAttributes.get((String) args[0]);
                case "setAttribute":
                    if (args[1] == null) {
                        // lookups cache a missing scope service as null
                        scopeAttributes.remove((String) args[0]);
                    } else {
                        scopeAttributes.put((String) args[0], args[1]);
                    }
                    return true;
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return "scope:app";
                default:
                    return method.getReturnType() == boolean.class ? false : null;
            }
        });
    }

    private static IConnection connection(String id) {
        Map<String, Object> attributes = new ConcurrentHashMap<>();
        return (IConnection) Proxy.newProxyInstance(IConnection.class.getClassLoader(), new Class<?>[] { IConnection.class }, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getSessionId":
                    return id;
                case "getAttribute":
                    return args[0] instanceof String ? attributes.get((String) args[0]) : null;
                case "setAttribute":
                    attributes.put((String) args[0], args[1]);
                    return true;
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return "conn:" + id;
                default:
                    return method.getReturnType() == boolean.class ? false : null;
            }
        });
    }

    private static String name(int segments) {
        StringBuilder sb = new StringBuilder("s");
        for (int i = 1; i < segments; i++) {
            sb.append("/s");
        }
        return sb.toString();
    }

    @Test
    public void testNameDepthCap() {
        // default
        assertTrue(service.createSharedObject(scope, name(SharedObjectService.DEFAULT_MAX_NAME_DEPTH), true));
        assertFalse(service.createSharedObject(scope, "x/" + name(SharedObjectService.DEFAULT_MAX_NAME_DEPTH), true));
        service.setMaxNameDepth(3);
        assertTrue(service.createSharedObject(scope, "a/b/c", false));
        assertFalse(service.createSharedObject(scope, "a/b/c/d", false));
        assertFalse(service.createClientSharedObject(connection("c1"), scope, "a/b/c/d", true));
        assertFalse(children.containsKey("a/b/c/d"));
        // 0 means unlimited
        service.setMaxNameDepth(0);
        assertTrue(service.createSharedObject(scope, name(100), true));
    }

    @Test
    public void testClientPersistentRefusedWhenDisabled() {
        IConnection conn = connection("c1");
        // allowed by default
        assertTrue(service.createClientSharedObject(conn, scope, "p1", true));
        assertTrue(children.get("p1") instanceof SharedObjectScope && ((SharedObjectScope) children.get("p1")).isPersistent());
        service.setAllowClientPersistent(false);
        assertFalse(service.createClientSharedObject(conn, scope, "p2", true));
        assertFalse(children.containsKey("p2"));
        // transient objects are still allowed
        assertTrue(service.createClientSharedObject(conn, scope, "t1", false));
        assertTrue(children.containsKey("t1"));
        // server-side code may still create persistent objects, and clients may join them
        assertTrue(service.createSharedObject(scope, "server", true));
        assertTrue(service.createClientSharedObject(conn, scope, "server", true));
        assertEquals(3, children.size());
    }

    @Test
    public void testPerConnectionCreationCap() {
        service.setMaxSharedObjectsPerConnection(2);
        IConnection first = connection("c1"), second = connection("c2");
        assertTrue(service.createClientSharedObject(first, scope, "a", false));
        // a failed creation is not counted
        assertFalse(service.createClientSharedObject(first, scope, "../bad", false));
        assertTrue(service.createClientSharedObject(first, scope, "b", true));
        assertFalse(service.createClientSharedObject(first, scope, "c", false));
        assertFalse(children.containsKey("c"));
        // joining an existing shared object is not a creation
        assertTrue(service.createClientSharedObject(first, scope, "a", false));
        // the count is per connection
        assertTrue(service.createClientSharedObject(second, scope, "c", false));
        assertTrue(service.createClientSharedObject(first, scope, "c", false));
        // server-side creation is not counted or limited
        assertTrue(service.createSharedObject(scope, "d", false));
        assertTrue(service.createClientSharedObject(null, scope, "e", false));
        assertEquals(5, children.size());
    }

    @Test
    public void testPerConnectionCapUnlimitedByDefault() {
        IConnection conn = connection("c1");
        for (int i = 0; i < 50; i++) {
            assertTrue(service.createClientSharedObject(conn, scope, "so" + i, false));
        }
        assertEquals(50, children.size());
    }

    @Test
    public void testScopeBudget() {
        // "k" and 10 characters take 1 + 13 bytes, room for two such attributes in the scope
        service.setMaxBytesPerScope(28);
        assertTrue(service.createSharedObject(scope, "a", false));
        assertTrue(service.createSharedObject(scope, "b", false));
        SharedObjectScope a = child("a"), b = child("b");
        Listener client = new Listener();
        dispatch(a, client, event(ISharedObjectEvent.Type.SERVER_CONNECT, null, null), event(ISharedObjectEvent.Type.SERVER_SET_ATTRIBUTE, "k", "0123456789"));
        dispatch(b, client, event(ISharedObjectEvent.Type.SERVER_CONNECT, null, null), event(ISharedObjectEvent.Type.SERVER_SET_ATTRIBUTE, "k", "0123456789"));
        assertEquals(28L, SharedObjectScope.getScopeAttributeBytes(scope));
        dispatch(a, client, event(ISharedObjectEvent.Type.SERVER_SET_ATTRIBUTE, "j", "0"));
        assertNull("the scope budget is exhausted", a.getAttribute("j"));
        // a write that does not grow the total is allowed
        dispatch(b, client, event(ISharedObjectEvent.Type.SERVER_SET_ATTRIBUTE, "k", "9876543210"));
        assertEquals("9876543210", b.getAttribute("k"));
        // deleting in one object frees budget for another
        dispatch(b, client, event(ISharedObjectEvent.Type.SERVER_DELETE_ATTRIBUTE, "k", null));
        assertEquals(14L, SharedObjectScope.getScopeAttributeBytes(scope));
        dispatch(a, client, event(ISharedObjectEvent.Type.SERVER_SET_ATTRIBUTE, "j", "0"));
        assertEquals("0", a.getAttribute("j"));
        assertEquals(19L, SharedObjectScope.getScopeAttributeBytes(scope));
        // a released transient object gives back its share
        dispatch(a, client, event(ISharedObjectEvent.Type.SERVER_DISCONNECT, null, null));
        a.so.get().checkRelease();
        assertEquals(0L, SharedObjectScope.getScopeAttributeBytes(scope));
    }

    @Test
    public void testReleasedTransientObjectLeavesItsStore() {
        assertTrue(service.createSharedObject(scope, "t", false));
        SharedObjectScope t = child("t");
        t.setAttribute("k", "v");
        IPersistenceStore store = (IPersistenceStore) scopeAttributes.get(SharedObjectService.SO_TRANSIENT_STORE);
        assertEquals(1, store.getObjectNames().size());
        t.so.get().checkRelease();
        assertTrue("a released object must not be stored again while it closes", store.getObjectNames().isEmpty());
    }

    @Test
    public void testConnectedPerConnectionLimit() {
        service.setMaxConnectedPerConnection(2);
        for (String name : new String[] { "a", "b", "c" }) {
            assertTrue(service.createSharedObject(scope, name, false));
        }
        Listener client = new Listener();
        dispatch(child("a"), client, event(ISharedObjectEvent.Type.SERVER_CONNECT, null, null));
        dispatch(child("b"), client, event(ISharedObjectEvent.Type.SERVER_CONNECT, null, null));
        // a third connect is refused and ends its message
        dispatch(child("c"), client, event(ISharedObjectEvent.Type.SERVER_CONNECT, null, null), event(ISharedObjectEvent.Type.SERVER_SET_ATTRIBUTE, "k", "v"));
        assertFalse(child("c").getEventListeners().contains(client));
        assertNull(child("c").getAttribute("k"));
        assertEquals(2, client.getSharedObjectCount());
        // connecting again to a connected object takes no further slot
        dispatch(child("b"), client, event(ISharedObjectEvent.Type.SERVER_CONNECT, null, null));
        assertEquals(2, client.getSharedObjectCount());
        // the count is per connection
        Listener other = new Listener();
        dispatch(child("c"), other, event(ISharedObjectEvent.Type.SERVER_CONNECT, null, null));
        assertTrue(child("c").getEventListeners().contains(other));
        // leaving an object frees its slot
        dispatch(child("a"), client, event(ISharedObjectEvent.Type.SERVER_DISCONNECT, null, null));
        assertEquals(1, client.getSharedObjectCount());
        dispatch(child("c"), client, event(ISharedObjectEvent.Type.SERVER_CONNECT, null, null));
        assertTrue(child("c").getEventListeners().contains(client));
        assertEquals(2, client.getSharedObjectCount());
        // disconnecting frees every slot
        client.joinScope();
        client.disconnect();
        assertEquals(0, client.getSharedObjectCount());
        assertFalse(child("b").getEventListeners().contains(client));
        assertFalse(child("c").getEventListeners().contains(client));
    }

    @Test
    public void testIdlePersistentObjectIsEvictedAndReloaded() throws Exception {
        FilePersistence store = fileStore();
        service.setPersistentIdleEvictSeconds(1);
        // a reload is not a client creation, so neither of these limits applies to it
        service.setAllowClientPersistent(false);
        service.setMaxSharedObjectsPerConnection(1);
        assertTrue(service.createSharedObject(scope, "room/state", true));
        SharedObjectScope so = child("room/state");
        so.setAttribute("server", "value");
        Listener client = new Listener();
        dispatch(so, client, event(ISharedObjectEvent.Type.SERVER_CONNECT, null, null), event(ISharedObjectEvent.Type.SERVER_SET_ATTRIBUTE, "count", 42.0));
        dispatch(so, client, event(ISharedObjectEvent.Type.SERVER_DISCONNECT, null, null));
        Map<String, Object> attributes = new HashMap<>(so.getAttributes());
        int version = so.getVersion();
        assertTrue(version > 1);
        // before the idle period has passed the check only schedules the next one
        runJobs();
        assertFalse(so.isEvicted());
        assertSame(so, child("room/state"));
        assertFalse("the next check is scheduled", jobs.isEmpty());
        Thread.sleep(1100L);
        // not while it is acquired
        so.acquire();
        runJobs();
        assertFalse(so.isEvicted());
        so.release();
        runJobs();
        assertTrue(so.isEvicted());
        assertNull(child("room/state"));
        assertFalse(store.getObjectNames().contains(ScopeType.SHARED_OBJECT + "/app/room/state"));
        try (Stream<Path> files = Files.walk(webapp.toPath())) {
            assertTrue("the evicted object is on disk", files.anyMatch(p -> p.endsWith(Paths.get("app", "room", "state.red5"))));
        }
        // a client connecting again reloads the object with its attributes and version
        IConnection conn = connection("c1");
        assertTrue(service.createClientSharedObject(conn, scope, "x", false));
        assertTrue(service.createClientSharedObject(conn, scope, "room/state", true));
        SharedObjectScope reloaded = child("room/state");
        assertNotNull(reloaded);
        assertNotSame(so, reloaded);
        assertEquals(attributes, reloaded.getAttributes());
        assertEquals(version, reloaded.getVersion());
        assertTrue(reloaded.isPersistent());
    }

    @Test
    public void testMessageForEvictedObjectReachesReloadedObject() throws Exception {
        fileStore();
        service.setPersistentIdleEvictSeconds(1);
        assertTrue(service.createSharedObject(scope, "p", true));
        SharedObjectScope so = child("p");
        so.setAttribute("k", "v");
        so.setIdleEvictMillis(1L);
        Thread.sleep(10L);
        assertEquals(-1L, so.checkEviction());
        assertTrue(so.isEvicted());
        // a message routed to the evicted instance before it was removed is handled by the reloaded object
        Listener client = new Listener();
        dispatch(so, client, event(ISharedObjectEvent.Type.SERVER_CONNECT, null, null), event(ISharedObjectEvent.Type.SERVER_SET_ATTRIBUTE, "c", "client"));
        SharedObjectScope reloaded = child("p");
        assertNotNull(reloaded);
        assertNotSame(so, reloaded);
        assertEquals("v", reloaded.getAttribute("k"));
        assertEquals("client", reloaded.getAttribute("c"));
        assertTrue(reloaded.getEventListeners().contains(client));
    }

    @Test
    public void testObjectInUseOrUnsavedIsNotEvicted() throws Exception {
        FilePersistence store = fileStore();
        service.setPersistentIdleEvictSeconds(1);
        assertTrue(service.createSharedObject(scope, "p", true));
        SharedObjectScope so = child("p");
        so.setIdleEvictMillis(1L);
        Listener client = new Listener();
        dispatch(so, client, event(ISharedObjectEvent.Type.SERVER_CONNECT, null, null), event(ISharedObjectEvent.Type.SERVER_SET_ATTRIBUTE, "k", "v"));
        Thread.sleep(10L);
        // a listener is connected; the last one to leave starts the idle period
        assertEquals(-1L, so.checkEviction());
        assertFalse(so.isEvicted());
        dispatch(so, client, event(ISharedObjectEvent.Type.SERVER_DISCONNECT, null, null));
        // a service handler is registered by the application
        so.registerServiceHandler(new Object());
        Thread.sleep(10L);
        assertTrue(so.checkEviction() > 0);
        assertFalse(so.isEvicted());
        so.unregisterServiceHandler("");
        // changes the store cannot write keep the object in memory
        store.setMaxBytes(1L);
        assertTrue(so.checkEviction() > 0);
        assertFalse(so.isEvicted());
        assertSame(so, child("p"));
        store.setMaxBytes(0L);
        assertEquals(-1L, so.checkEviction());
        assertTrue(so.isEvicted());
        // only objects in memory are listed, and a server-side lookup reloads the evicted one
        assertFalse(service.getSharedObjectNames(scope).contains("p"));
        ISharedObject reloaded = service.getSharedObject(scope, "p");
        assertNotNull(reloaded);
        assertNotSame(so, reloaded);
        assertEquals("v", reloaded.getAttribute("k"));
        assertTrue(service.getSharedObjectNames(scope).contains("p"));
    }

    @Test
    public void testNoBudgetsOrEvictionByDefault() throws Exception {
        fileStore();
        assertTrue(service.createSharedObject(scope, "p", true));
        SharedObjectScope so = child("p");
        Listener client = new Listener();
        dispatch(so, client, event(ISharedObjectEvent.Type.SERVER_CONNECT, null, null), event(ISharedObjectEvent.Type.SERVER_SET_ATTRIBUTE, "big", "x".repeat(100_000)));
        assertEquals(100_000, ((String) so.getAttribute("big")).length());
        assertEquals(0L, SharedObjectScope.getScopeAttributeBytes(scope));
        for (int i = 0; i < 20; i++) {
            assertTrue(service.createSharedObject(scope, "t" + i, false));
            dispatch(child("t" + i), client, event(ISharedObjectEvent.Type.SERVER_CONNECT, null, null));
        }
        assertEquals(21, client.getSharedObjectCount());
        dispatch(so, client, event(ISharedObjectEvent.Type.SERVER_DISCONNECT, null, null));
        jobs.clear();
        Thread.sleep(10L);
        // no idle eviction is configured
        assertEquals(-1L, so.checkEviction());
        assertFalse(so.isEvicted());
        assertSame(so, child("p"));
    }

    private FilePersistence fileStore() throws Exception {
        webapp = Files.createTempDirectory("red5-so-evict").toFile();
        new File(webapp, "persistence").mkdirs();
        FileSystemResourceLoader loader = new FileSystemResourceLoader() {
            @Override
            protected Resource getResourceByPath(String path) {
                return new FileSystemResource(new File(webapp, path));
            }
        };
        FilePersistence store = new FilePersistence(new PathMatchingResourcePatternResolver(loader));
        scopeAttributes.put(SharedObjectService.SO_PERSISTENCE_STORE, store);
        return store;
    }

    private SharedObjectScope child(String name) {
        return (SharedObjectScope) children.get(name);
    }

    private void runJobs() throws Exception {
        List<IScheduledJob> due = new ArrayList<>(jobs);
        jobs.clear();
        for (IScheduledJob job : due) {
            job.execute(null);
        }
    }

    private static void dispatch(SharedObjectScope target, IEventListener source, ISharedObjectEvent... events) {
        SharedObjectMessage msg = new SharedObjectMessage(source, target.getName(), 0, target.isPersistent());
        for (ISharedObjectEvent e : events) {
            msg.addEvent(e);
        }
        target.dispatchEvent(msg);
    }

    private static ISharedObjectEvent event(ISharedObjectEvent.Type type, String key, Object value) {
        return new SharedObjectEvent(type, key, value);
    }

    private static class Listener extends RTMPMinaConnection {

        @Override
        public void sendSharedObjectMessage(String name, int currentVersion, boolean persistent, Set<ISharedObjectEvent> events) {
        }

        @Override
        public void notifyEvent(IEvent event) {
        }

        // a connection must be connected to a scope for disconnect to release its shared objects
        void joinScope() {
            scope = new Scope();
        }

    }

}
