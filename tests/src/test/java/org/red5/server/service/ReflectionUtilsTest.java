package org.red5.server.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.red5.server.api.IConnection;
import org.red5.server.api.service.IServiceCall;
import org.red5.server.net.rtmp.RTMPMinaConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReflectionUtilsTest {

    private static final Logger log = LoggerFactory.getLogger(ReflectionUtilsTest.class);

    @Test
    public void testReflectionUtils() {
        IConnection conn = new DummyConnection();
        TestService service = new TestService();
        String methodName = "doTest";
        IServiceCall call = new PendingCall("TestService.doTest", new Object[] { "test" });
        Object[] result = ReflectionUtils.findMethod(conn, call, service, methodName);
        if (result[0] == null) {
            log.info("Result is null");
            fail("Result is null, method not found");
        } else {
            log.info("Result with String: {}", Arrays.asList(result));
        }
        // call with two parameters string and int
        call = new PendingCall("TestService.doTest", new Object[] { "test", 42 });
        result = ReflectionUtils.findMethod(conn, call, service, methodName);
        if (result[0] == null) {
            log.info("Result is null");
            fail("Result is null, method not found");
        } else {
            log.info("Result with string and int: {}", Arrays.asList(result));
        }

        // no arg method
        call = new PendingCall("TestService.doTest", new Object[0]);
        result = ReflectionUtils.findMethod(conn, call, service, methodName);
        if (result[0] == null) {
            log.info("Result is null");
            fail("Result is null, method not found");
        } else {
            log.info("Result with no-args: {}", Arrays.asList(result));
        }

        // call with two parameters string and int but expecting Connection as first parameter to hit on 2nd call
        methodName = "doTest";
        call = new PendingCall("TestService.doTest", new Object[] { "test", 42 });
        result = ReflectionUtils.findMethod(conn, call, service, methodName);
        if (result[0] == null) {
            log.info("Result is null");
            fail("Result is null, method not found");
        } else {
            log.info("Result with connection: {}", Arrays.asList(result));
        }

        // call with two parameters string and int in a list
        List<String> list = List.of("test");
        methodName = "doTest";
        call = new PendingCall("TestService.doTest", new Object[] { list });
        result = ReflectionUtils.findMethod(conn, call, service, methodName);
        if (result[0] == null) {
            log.info("Result is null");
            fail("Result is null, method not found");
        } else {
            log.info("Result with list: {}", Arrays.asList(result));
        }

        // call with two parameters string and int
        call = new PendingCall("TestService.doTestObjectArray", new Object[] { "test", 42 });
        methodName = "doTestObjectArray";
        result = ReflectionUtils.findMethod(conn, call, service, methodName);
        if (result[0] == null) {
            log.info("Result is null");
            fail("Result is null, method not found");
        } else {
            log.info("Result with Object array of string and int: {}", Arrays.asList(result));
        }

        // call with two parameters string and int
        call = new PendingCall("TestService.doTestObjectArray", new Object[] { new Object[] { "test", 42 }, new Object[] { "blllllaaahhh" } });
        methodName = "doTestObjectArray";
        result = ReflectionUtils.findMethod(conn, call, service, methodName);
        if (result[0] == null) {
            log.info("Result is null");
            fail("Result is null, method not found");
        } else {
            log.info("Result with Object array of string and int: {}", Arrays.asList(result));
        }
    }

    private class DummyConnection extends RTMPMinaConnection {

    }

    /**
     * Overloads reachable through argument conversion (Boolean converts to String) must be selected by the actual
     * argument type; this is what routes a client's publish(false) unpublish to publish(Boolean) rather than
     * publish(String) with the name "false".
     */
    @Test
    public void testOverloadPrefersAssignableParameter() {
        IConnection conn = new DummyConnection();
        TestService service = new TestService();
        // repeat since candidate ordering comes from reflection and is not stable
        for (int i = 0; i < 25; i++) {
            IServiceCall call = new PendingCall("TestService.doTest", new Object[] { Boolean.FALSE });
            Object[] result = ReflectionUtils.findMethod(conn, call, service, "doTest");
            assertNotNull("method not found", result[0]);
            assertEquals(Boolean.class, ((Method) result[0]).getParameterTypes()[0]);
            call = new PendingCall("TestService.doTest", new Object[] { "test" });
            result = ReflectionUtils.findMethod(conn, call, service, "doTest");
            assertNotNull("method not found", result[0]);
            assertEquals(String.class, ((Method) result[0]).getParameterTypes()[0]);
            // list variant without a connection
            result = ReflectionUtils.findMethod(service, "doTest", List.of(Boolean.FALSE));
            assertNotNull("method not found", result[0]);
            assertEquals(Boolean.class, ((Method) result[0]).getParameterTypes()[0]);
        }
    }

    public class TestService {

        public void doTest() {
            log.info("doTest - no params");
        }

        public void doTest(List<?> param) {
            log.info("doTest - List: {}", param);
        }

        public void doTest(Boolean param) {
            log.info("doTest Boolean: {}", param);
        }

        public void doTest(String param) {
            log.info("doTest - String: {}", param);
        }

        public void doTest(String param0, Integer param1) {
            log.info("doTest - String and Integer: {} {}", param0, param1);
        }

        // method with int as second parameter isn't found (Integer is ok)
        public void doTest(String param0, int param1) {
            log.info("doTest - String and int: {} {}", param0, param1);
        }

        // method with int as second parameter isn't found (Integer is ok)
        public void doTest(IConnection conn, String param0, int param1) {
            log.info("doTest - Conn, String, and int: {} {}", conn, param0, param1);
        }

        public void doTestWithConn(IConnection conn, String param0, Integer param1) {
            log.info("doTestWithConn - Connection, String, and Integer: {} {} {}", conn, param0, param1);
        }

        // simple method generically taking an object array
        public void doTestObjectArray(Object[] param) {
            log.info("doTestObjectArray - Object array: {}", Arrays.asList(param));
        }

        // simple method generically taking an object array
        public void doTestObjectArray(Object[] param0, Object[] param1) {
            log.info("doTestObjectArray - Object array(s): {} {}", Arrays.asList(param0), Arrays.asList(param1));
        }

    }

}
