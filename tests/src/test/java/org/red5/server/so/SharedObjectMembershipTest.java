package org.red5.server.so;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.red5.server.api.IAttributeStore;
import org.red5.server.api.IContext;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.event.IEventListener;
import org.red5.server.api.scheduling.ISchedulingService;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.so.ISharedObject;
import org.red5.server.api.so.ISharedObjectBase;
import org.red5.server.api.so.ISharedObjectListener;
import org.red5.server.api.so.ISharedObjectSecurity;
import org.red5.server.net.rtmp.RTMPMinaConnection;
import org.red5.server.persistence.RamPersistence;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Verifies that shared-object mutations and sends require an admitted, current membership (issue #477).
 */
public class SharedObjectMembershipTest {

    private SharedObjectScope so;

    private boolean allowConnect;

    private final List<String> sent = new ArrayList<>();

    private static ThreadPoolTaskScheduler scheduler;

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
        // scheduling service that accepts, but never runs, the linger job created on disconnect
        ISchedulingService schedulingService = (ISchedulingService) Proxy.newProxyInstance(ISchedulingService.class.getClassLoader(), new Class<?>[] { ISchedulingService.class }, (proxy, method, args) -> method.getReturnType() == String.class ? "job" : null);
        IContext context = (IContext) Proxy.newProxyInstance(IContext.class.getClassLoader(), new Class<?>[] { IContext.class }, (proxy, method, args) -> "getBean".equals(method.getName()) ? schedulingService : null);
        IScope parent = (IScope) Proxy.newProxyInstance(IScope.class.getClassLoader(), new Class<?>[] { IScope.class }, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getContextPath":
                    return "/app";
                case "getContext":
                    return context;
                case "getName":
                    return "app";
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
        so = new SharedObjectScope(parent, "members", false, new RamPersistence((ResourcePatternResolver) null));
        allowConnect = true;
        so.registerSharedObjectSecurity(new ISharedObjectSecurity() {

            public boolean isCreationAllowed(IScope scope, String name, boolean persistent) {
                return true;
            }

            public boolean isConnectionAllowed(ISharedObject object) {
                return allowConnect;
            }

            public boolean isWriteAllowed(ISharedObject object, String key, Object value) {
                return true;
            }

            public boolean isDeleteAllowed(ISharedObject object, String key) {
                return true;
            }

            public boolean isSendAllowed(ISharedObject object, String message, List<?> arguments) {
                return true;
            }
        });
        so.addSharedObjectListener(new ISharedObjectListener() {

            public void onSharedObjectConnect(ISharedObjectBase so) {
            }

            public void onSharedObjectDisconnect(ISharedObjectBase so) {
            }

            public void onSharedObjectUpdate(ISharedObjectBase so, String key, Object value) {
            }

            public void onSharedObjectUpdate(ISharedObjectBase so, IAttributeStore values) {
            }

            public void onSharedObjectUpdate(ISharedObjectBase so, Map<String, Object> values) {
            }

            public void onSharedObjectDelete(ISharedObjectBase so, String key) {
            }

            public void onSharedObjectClear(ISharedObjectBase so) {
            }

            public void onSharedObjectSend(ISharedObjectBase so, String method, List<?> params) {
                sent.add(method);
            }
        });
    }

    @Test
    public void testNeverConnectedClientCannotMutate() {
        IEventListener client = new Listener();
        so.setAttribute("keep", "original");
        dispatch(client, event(ISharedObjectEvent.Type.SERVER_SET_ATTRIBUTE, "keep", "changed"), event(ISharedObjectEvent.Type.SERVER_DELETE_ATTRIBUTE, "keep", null), event(ISharedObjectEvent.Type.SERVER_SET_ATTRIBUTE, "new", "value"), event(ISharedObjectEvent.Type.SERVER_SEND_MESSAGE, "handler", new ArrayList<>()));
        assertEquals("original", so.getAttribute("keep"));
        assertNull(so.getAttribute("new"));
        assertTrue(sent.isEmpty());
    }

    @Test
    public void testDeniedConnectStopsTheMessage() {
        allowConnect = false;
        IEventListener client = new Listener();
        dispatch(client, event(ISharedObjectEvent.Type.SERVER_CONNECT, null, null), event(ISharedObjectEvent.Type.SERVER_SET_ATTRIBUTE, "k", "v"), event(ISharedObjectEvent.Type.SERVER_SEND_MESSAGE, "handler", new ArrayList<>()));
        assertNull(so.getAttribute("k"));
        assertTrue(sent.isEmpty());
        assertFalse(so.getEventListeners().contains(client));
        // later messages from the denied client are rejected too
        dispatch(client, event(ISharedObjectEvent.Type.SERVER_SET_ATTRIBUTE, "k", "v"));
        assertNull(so.getAttribute("k"));
    }

    @Test
    public void testAdmittedMemberCanMutateUntilDisconnect() {
        IEventListener client = new Listener();
        dispatch(client, event(ISharedObjectEvent.Type.SERVER_CONNECT, null, null), event(ISharedObjectEvent.Type.SERVER_SET_ATTRIBUTE, "k", "v1"), event(ISharedObjectEvent.Type.SERVER_SEND_MESSAGE, "handler", new ArrayList<>()));
        assertEquals("v1", so.getAttribute("k"));
        assertEquals(1, sent.size());
        dispatch(client, event(ISharedObjectEvent.Type.SERVER_DISCONNECT, null, null));
        dispatch(client, event(ISharedObjectEvent.Type.SERVER_SET_ATTRIBUTE, "k", "v2"), event(ISharedObjectEvent.Type.SERVER_DELETE_ATTRIBUTE, "k", null));
        assertEquals("v1", so.getAttribute("k"));
    }

    @Test
    public void testClientAttributeLimit() {
        so.setMaxClientAttributes(2);
        IEventListener client = new Listener();
        dispatch(client, event(ISharedObjectEvent.Type.SERVER_CONNECT, null, null), event(ISharedObjectEvent.Type.SERVER_SET_ATTRIBUTE, "a", 1), event(ISharedObjectEvent.Type.SERVER_SET_ATTRIBUTE, "b", 2), event(ISharedObjectEvent.Type.SERVER_SET_ATTRIBUTE, "c", 3));
        assertEquals(2, so.getAttributeNames().size());
        assertNull(so.getAttribute("c"));
        // updating an existing attribute is still allowed
        dispatch(client, event(ISharedObjectEvent.Type.SERVER_SET_ATTRIBUTE, "a", 10));
        assertEquals(10, so.getAttribute("a"));
        // server-side code is not limited
        so.setAttribute("server", "x");
        assertEquals(3, so.getAttributeNames().size());
    }

    @Test
    public void testServerSideEventsNeedNoMembership() {
        SharedObjectMessage msg = new SharedObjectMessage("members", 0, false);
        msg.addEvent(event(ISharedObjectEvent.Type.SERVER_SET_ATTRIBUTE, "k", "server"));
        so.dispatchEvent(msg);
        assertEquals("server", so.getAttribute("k"));
    }

    private void dispatch(IEventListener source, ISharedObjectEvent... events) {
        SharedObjectMessage msg = new SharedObjectMessage(source, "members", 0, false);
        for (ISharedObjectEvent e : events) {
            msg.addEvent(e);
        }
        so.dispatchEvent(msg);
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

    }

}
