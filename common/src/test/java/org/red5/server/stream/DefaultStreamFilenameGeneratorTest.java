package org.red5.server.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.lang.reflect.Proxy;

import org.junit.Test;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.ScopeType;
import org.red5.server.api.stream.IStreamFilenameGenerator.GenerationType;

/**
 * Stream names must stay confined beneath the application's stream root (issue #463).
 */
public class DefaultStreamFilenameGeneratorTest {

    private static IScope appScope() {
        return (IScope) Proxy.newProxyInstance(IScope.class.getClassLoader(), new Class<?>[] { IScope.class }, (proxy, method, args) -> {
            switch (method.getName()) {
                case "hasParent":
                    return false;
                case "getType":
                    return ScopeType.APPLICATION;
                case "getName":
                    return "app";
                case "getParent":
                    return null;
                default:
                    Class<?> rt = method.getReturnType();
                    if (rt == boolean.class) {
                        return false;
                    }
                    if (rt == int.class) {
                        return 0;
                    }
                    return null;
            }
        });
    }

    private final DefaultStreamFilenameGenerator generator = new DefaultStreamFilenameGenerator();

    private void assertRejected(String name) {
        for (GenerationType type : GenerationType.values()) {
            try {
                String result = generator.generateFilename(appScope(), name, ".flv", type);
                fail("name '" + name + "' must be rejected for " + type + " but produced " + result);
            } catch (IllegalArgumentException expected) {
            }
        }
    }

    @Test
    public void testParentTraversalIsRejected() {
        assertRejected("../../etc/passwd");
        assertRejected("foo/../../bar");
        assertRejected("..");
    }

    @Test
    public void testAbsolutePathsAreRejected() {
        assertRejected("/etc/passwd");
        assertRejected("\\\\server\\share\\x");
    }

    @Test
    public void testWindowsFormsAreRejected() {
        assertRejected("..\\..\\win.ini");
        assertRejected("C:\\boot.ini");
        assertRejected("c:/boot.ini");
    }

    @Test
    public void testEmptyAndControlCharactersAreRejected() {
        assertRejected("");
        assertRejected("bad\u0000name");
    }

    @Test
    public void testPlainAndNestedNamesAreAccepted() {
        assertEquals("streams/hello.flv", generator.generateFilename(appScope(), "hello", ".flv", GenerationType.RECORD));
        assertEquals("streams/sub/dir/hello", generator.generateFilename(appScope(), "sub/dir/hello", GenerationType.PLAYBACK));
        assertEquals("streams/my.stream_1-2", generator.generateFilename(appScope(), "my.stream_1-2", GenerationType.PLAYBACK));
    }
}
