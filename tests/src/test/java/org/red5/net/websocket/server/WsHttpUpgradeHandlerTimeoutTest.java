package org.red5.net.websocket.server;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tomcat.websocket.WsSession;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sun.misc.Unsafe;

/**
 * Regression tests for {@link WsHttpUpgradeHandler#timeoutAsync(long)}.
 *
 * Issue #436: Tomcat keeps calling {@code timeoutAsync} once per second after the underlying
 * {@link WsSession} has been closed; every call threw {@code IllegalStateException} from
 * {@code wsSession.getUserProperties()} and the handler never released its reference, so the
 * same stack flooded {@code red5.log} forever.
 */
public class WsHttpUpgradeHandlerTimeoutTest {

    private static final Logger log = LoggerFactory.getLogger(WsHttpUpgradeHandlerTimeoutTest.class);

    /**
     * With no session attached, {@code timeoutAsync} must be a silent no-op rather than NPE.
     * This guards the pre-existing null check that the fix relies on.
     */
    @Test
    public void timeoutAsync_nullSession_isNoOp() {
        WsHttpUpgradeHandler handler = new WsHttpUpgradeHandler();
        // first call - no field has been set, nothing to do
        handler.timeoutAsync(System.currentTimeMillis());
        // and again, for good measure
        handler.timeoutAsync(System.currentTimeMillis() + 1000);
    }

    /**
     * When the attached {@link WsSession} reports {@code isClosed() == true}, {@code timeoutAsync}
     * must release its reference and return immediately. Without the fix the method would call
     * {@code wsSession.getUserProperties()} and re-throw {@code IllegalStateException} on every
     * scheduler tick.
     */
    @Test
    public void timeoutAsync_closedSession_releasesRefAndBreaksLoop() throws Exception {
        WsHttpUpgradeHandler handler = new WsHttpUpgradeHandler();
        WsSession closedSession = allocateClosedWsSession();
        setPrivateField(handler, "wsSession", closedSession);

        // simulate the per-second Tomcat scheduler tick a few times
        for (int i = 0; i < 5; i++) {
            try {
                handler.timeoutAsync(System.currentTimeMillis() + i * 1000L);
            } catch (Throwable t) {
                fail("timeoutAsync must not propagate exceptions on closed sessions, got: " + t);
            }
        }
        // the fix must null the field so future ticks short-circuit on the existing null guard
        assertNull("wsSession ref should be cleared once detected as closed", getPrivateField(handler, "wsSession"));
    }

    // --- helpers -------------------------------------------------------------------------------

    /**
     * Builds a {@link WsSession} instance whose {@code isClosed()} returns true, without invoking
     * its full constructor (which requires a configured Tomcat container). Uses {@link Unsafe} to
     * allocate the object, then flips its private state to CLOSED.
     */
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
            // allocateInstance leaves final fields null; initialise it ourselves
            state = new AtomicReference<>();
            stateField.set(session, state);
        }
        Class<?> stateEnumClass = Class.forName("org.apache.tomcat.websocket.WsSession$State");
        Object closed = enumValue(stateEnumClass, "CLOSED");
        state.set(closed);
        log.debug("allocated WsSession with state CLOSED for test");
        return session;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Object enumValue(Class<?> enumClass, String name) {
        return Enum.valueOf((Class<Enum>) enumClass.asSubclass(Enum.class), name);
    }

    private static void setPrivateField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Object getPrivateField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
}