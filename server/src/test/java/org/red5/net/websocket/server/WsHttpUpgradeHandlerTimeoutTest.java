package org.red5.net.websocket.server;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tomcat.websocket.WsSession;
import org.junit.Test;

import sun.misc.Unsafe;

public class WsHttpUpgradeHandlerTimeoutTest {

    @Test
    public void timeoutAsyncNullSessionIsNoOp() {
        WsHttpUpgradeHandler handler = new WsHttpUpgradeHandler();

        handler.timeoutAsync(System.currentTimeMillis());
        handler.timeoutAsync(System.currentTimeMillis() + 1000);
    }

    @Test
    public void timeoutAsyncClosedSessionReleasesReference() throws Exception {
        WsHttpUpgradeHandler handler = new WsHttpUpgradeHandler();
        setPrivateField(handler, "wsSession", allocateClosedWsSession());

        for (int i = 0; i < 5; i++) {
            try {
                handler.timeoutAsync(System.currentTimeMillis() + i * 1000L);
            } catch (Throwable t) {
                fail("timeoutAsync must not propagate exceptions on closed sessions, got: " + t);
            }
        }
        assertNull("wsSession ref should be cleared once detected as closed", getPrivateField(handler, "wsSession"));
    }

    private static WsSession allocateClosedWsSession() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        WsSession session = (WsSession) unsafe.allocateInstance(WsSession.class);
        Field stateField = WsSession.class.getDeclaredField("state");
        stateField.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<Object> state = (AtomicReference<Object>) stateField.get(session);
        if (state == null) {
            state = new AtomicReference<>();
            stateField.set(session, state);
        }
        Class<?> stateEnumClass = Class.forName("org.apache.tomcat.websocket.WsSession$State");
        state.set(enumValue(stateEnumClass, "CLOSED"));
        return session;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Object enumValue(Class<?> enumClass, String name) {
        return Enum.valueOf((Class<Enum>) enumClass.asSubclass(Enum.class), name);
    }

    private static void setPrivateField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getPrivateField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
