package org.red5.server.service;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.junit.Test;
import org.red5.server.api.IConnection;
import org.red5.server.api.service.IServiceCall;
import org.red5.server.net.rtmp.RTMPMinaConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReflectionUtilsTest {

    private static final Logger log = LoggerFactory.getLogger(ReflectionUtilsTest.class);

    private static final Object[] NULL_RETURN = new Object[] { null, null };

    @Test
    public void testReflectionUtils() {
        IConnection conn = new DummyConnection();
        TestService service = new TestService();
        String methodName = "doTest";
        IServiceCall call = new PendingCall("TestService.doTest", new Object[] { "test" });
        Object[] result = ReflectionUtils.findMethod(conn, call, service, methodName);
        if (result == null) {
            log.info("Result is null");
            fail("Result is null, method not found");
        } else {
            log.info("Result: {}", Arrays.asList(result));
        }
        assertNotEquals(NULL_RETURN, result);
        // call with two parameters string and int
        call = new PendingCall("TestService.doTest", new Object[] { "test", 42 });
        result = ReflectionUtils.findMethod(conn, call, service, methodName);
        if (result == null) {
            log.info("Result is null");
            fail("Result is null, method not found");
        } else {
            log.info("Result 2: {}", Arrays.asList(result));
        }
        assertNotEquals(NULL_RETURN, result);
        // call with two parameters string and int but expecting Connection as first parameter to hit on 2nd call
        methodName = "doTestWithConn";
        call = new PendingCall("TestService.doTestWithConn", new Object[] { "test", 42 });
        result = ReflectionUtils.findMethod(conn, call, service, methodName);
        if (result == null) {
            log.info("Result is null");
            fail("Result is null, method not found");
        } else {
            log.info("Result 2: {}", Arrays.asList(result));
        }
        assertNotEquals(NULL_RETURN, result);

        
    }

    private class DummyConnection extends RTMPMinaConnection {
            
    }

    public class TestService {

        public void doTest(String param) {
            log.info("doTest: {}", param);
        }
        
        public void doTestWithConn(IConnection conn, String param0, Integer param1) {
            log.info("doTestWithConn: {} {} {}", conn, param0, param1);
        }

        public void doTest(String param0, Integer param1) {
            log.info("doTest: {} {}", param0, param1);
        }

        // method with int as second parameter isn't found (Integer is ok)
        public void doTest(String param0, int param1) {
            log.info("doTest: {} {}", param0, param1);
        }

    }

}
