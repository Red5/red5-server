package org.red5.io.object;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Map;
import java.util.Timer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.After;
import org.junit.Test;
import org.red5.io.TestJavaBean;

/**
 * Verifies that typed AMF objects only instantiate classes that pass the deserializer class filter (issue #467).
 */
public class TypedObjectFilterTest {

    @After
    public void tearDown() {
        Deserializer.setAllowList(null);
    }

    @Test
    public void testBundledBlackListIsLoaded() {
        for (String name : new String[] { "java.util.Timer", "javax.management.timer.Timer", "org.springframework.jmx.support.JmxUtils", "org.apache.commons.beanutils.BeanUtils", "org.red5.server.scope.Scope" }) {
            assertFalse(String.format("Class creation should NOT be allowed: '%s'", name), Deserializer.classAllowed(name));
        }
        for (String name : new String[] { "org.red5.io.TestJavaBean", "org.red5.compatibility.flex.messaging.io.ArrayCollection", "com.example.dto.User" }) {
            assertTrue(String.format("Class creation should be allowed: '%s'", name), Deserializer.classAllowed(name));
        }
        assertFalse(Deserializer.classAllowed(null));
    }

    @Test
    public void testAllowList() {
        Deserializer.setAllowList(Collections.singleton("com.example.dto."));
        assertTrue(Deserializer.classAllowed("com.example.dto.User"));
        assertFalse(Deserializer.classAllowed("org.red5.io.TestJavaBean"));
        // denied prefixes still win over the allow-list
        Deserializer.setAllowList(Collections.singleton("java.util."));
        assertFalse(Deserializer.classAllowed("java.util.Timer"));
    }

    @Test
    public void testThreadOwningJdkClassDecodesAsMapAmf0() {
        assertTimerNotInstantiated(buf -> new org.red5.io.amf.Output(buf), buf -> new org.red5.io.amf.Input(buf));
    }

    @Test
    public void testThreadOwningJdkClassDecodesAsMapAmf3() {
        assertTimerNotInstantiated(buf -> new org.red5.io.amf3.Output(buf), buf -> new org.red5.io.amf3.Input(buf));
    }

    @Test
    public void testRegisteredBeanDecodesAsBeanAmf0() {
        Object result = roundTrip(new TestJavaBean(), buf -> new org.red5.io.amf.Output(buf), buf -> new org.red5.io.amf.Input(buf));
        assertEquals(TestJavaBean.class, result.getClass());
    }

    @Test
    public void testRegisteredBeanDecodesAsBeanAmf3() {
        Object result = roundTrip(new TestJavaBean(), buf -> new org.red5.io.amf3.Output(buf), buf -> new org.red5.io.amf3.Input(buf));
        assertEquals(TestJavaBean.class, result.getClass());
    }

    @Test
    public void testBeanOutsideAllowListDecodesAsMap() {
        Deserializer.setAllowList(Collections.singleton("com.example.dto."));
        Object amf0 = roundTrip(new TestJavaBean(), buf -> new org.red5.io.amf.Output(buf), buf -> new org.red5.io.amf.Input(buf));
        assertTrue(amf0 instanceof Map);
        Object amf3 = roundTrip(new TestJavaBean(), buf -> new org.red5.io.amf3.Output(buf), buf -> new org.red5.io.amf3.Input(buf));
        assertTrue(amf3 instanceof Map);
    }

    private void assertTimerNotInstantiated(Function<IoBuffer, Output> outputFactory, Function<IoBuffer, Input> inputFactory) {
        IoBuffer buf = encode(() -> {
            Timer timer = new Timer("encode-only");
            timer.cancel();
            return timer;
        }, outputFactory);
        int before = timerThreads();
        Object result = null;
        for (int i = 0; i < 20; i++) {
            buf.rewind();
            result = Deserializer.deserialize(inputFactory.apply(buf), Object.class);
        }
        assertTrue("Expected a map but got " + result, result instanceof Map);
        assertEquals("Decoding must not start Timer threads", before, timerThreads());
    }

    private static Object roundTrip(Object value, Function<IoBuffer, Output> outputFactory, Function<IoBuffer, Input> inputFactory) {
        IoBuffer buf = encode(() -> value, outputFactory);
        return Deserializer.deserialize(inputFactory.apply(buf), Object.class);
    }

    private static IoBuffer encode(Supplier<Object> value, Function<IoBuffer, Output> outputFactory) {
        IoBuffer buf = IoBuffer.allocate(512).setAutoExpand(true);
        Serializer.serialize(outputFactory.apply(buf), value.get());
        buf.flip();
        return buf;
    }

    private static int timerThreads() {
        return (int) Thread.getAllStackTraces().keySet().stream().filter(t -> t.getName().startsWith("Timer-")).count();
    }

}
