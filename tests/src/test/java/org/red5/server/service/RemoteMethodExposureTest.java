package org.red5.server.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;
import org.red5.annotations.DeclarePrivate;
import org.red5.annotations.DeclareProtected;
import org.red5.server.adapter.MultiThreadedApplicationAdapter;
import org.red5.server.api.service.IServiceCall;

/**
 * Verifies that only application methods and the RTMP client command set are remotely callable (issue #457).
 */
public class RemoteMethodExposureTest {

    @Test
    public void testAdapterPolicySettersAreNotCallable() {
        TestApplication app = new TestApplication();
        for (String name : new String[] { "setCanConnect", "setCanCallService", "setCanStart", "setAttribute", "removeAttributes", "setClientTTL", "clearSharedObjects", "registerStreamPlaybackSecurity", "removeScheduledJob", "getConnections", "stop" }) {
            Object[] args = name.startsWith("setCan") ? new Object[] { Boolean.FALSE } : new Object[0];
            IServiceCall call = new PendingCall(name, args);
            assertNull(name + " should not be remotely callable", ReflectionUtils.findMethod(null, call, app, name)[0]);
            assertEquals(Call.STATUS_METHOD_NOT_FOUND, call.getStatus());
        }
    }

    @Test
    public void testObjectMethodsAreNotCallable() {
        TestApplication app = new TestApplication();
        for (String name : new String[] { "getClass", "wait", "notify", "notifyAll", "hashCode" }) {
            assertNull(name + " should not be remotely callable", ReflectionUtils.findMethod(null, new PendingCall(name, new Object[0]), app, name)[0]);
        }
        assertNull(ReflectionUtils.findMethod(app, "notifyAll", Collections.emptyList())[0]);
    }

    @Test
    public void testDeclarePrivateIsNotCallable() {
        TestApplication app = new TestApplication();
        assertNull(ReflectionUtils.findMethod(null, new PendingCall("secret", new Object[0]), app, "secret")[0]);
        assertNull(ReflectionUtils.findMethod(app, "secret", Collections.emptyList())[0]);
    }

    @Test
    public void testApplicationAndCommandMethodsAreCallable() {
        TestApplication app = new TestApplication();
        assertNotNull(ReflectionUtils.findMethod(null, new PendingCall("hello", new Object[] { "x" }), app, "hello")[0]);
        assertNotNull(ReflectionUtils.findMethod(app, "hello", Collections.singletonList("x"))[0]);
        assertNotNull(ReflectionUtils.findMethod(null, new PendingCall("FCPublish", new Object[] { "stream1" }), app, "FCPublish")[0]);
        assertNotNull(ReflectionUtils.findMethod(null, new PendingCall("FCUnpublish", new Object[] { "stream1" }), app, "FCUnpublish")[0]);
        assertNotNull(ReflectionUtils.findMethod(null, new PendingCall("getStreamLength", new Object[] { "stream1" }), app, "getStreamLength")[0]);
        // an application override of an adapter method is declared by the application and stays callable
        assertNotNull(ReflectionUtils.findMethod(null, new PendingCall("getClientTTL", new Object[0]), app, "getClientTTL")[0]);
    }

    @Test
    public void testServiceInvokerRejectsAdapterSetter() {
        TestApplication app = new TestApplication();
        ServiceInvoker invoker = new ServiceInvoker();
        IServiceCall call = new PendingCall("setCanCallService", new Object[] { Boolean.FALSE });
        assertFalse(invoker.invoke(call, app));
        assertTrue(app.serviceCall(null, call));
    }

    @Test
    public void testDeclareProtectedRequiresPermission() {
        TestApplication app = new TestApplication();
        ServiceInvoker invoker = new ServiceInvoker();
        IServiceCall call = new PendingCall("admin", new Object[0]);
        assertFalse(invoker.invoke(call, app));
        assertEquals(Call.STATUS_ACCESS_DENIED, call.getStatus());
        assertFalse(app.adminCalled);
    }

    public static class TestApplication extends MultiThreadedApplicationAdapter {

        boolean adminCalled;

        public String hello(String name) {
            return "hello " + name;
        }

        @DeclarePrivate
        public void secret() {
        }

        @DeclareProtected(permission = "admin")
        public void admin() {
            adminCalled = true;
        }

        @Override
        public long getClientTTL() {
            return super.getClientTTL();
        }

    }

}
