package org.red5.io.object;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;

public class InputTest {
    @BeforeClass
    public static void setup() throws IOException {
        Deserializer.loadBlackList();
    }

    @Test
    public void testClassFilter() {
        for (String name : new String[] { "java.util.Calendar", "java.sql.ResultSet" }) {
            assertTrue(String.format("Class creation should be allowed: '%s'", name), BaseInput.classAllowed(name));
        }
        for (String name : new String[] { "org.springframework.jmx.support.JmxUtils" }) {
            assertFalse(String.format("Class creation should NOT be allowed: '%s'", name), BaseInput.classAllowed(name));
        }
    }
}
