package org.red5.server.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Queue;
import java.util.stream.Stream;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.red5.server.api.scope.ScopeType;
import org.red5.server.so.SharedObject;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * FilePersistence must not create directories for objects it does not write, and must honor the free space floor (issue #454).
 */
public class FilePersistenceQuotaTest {

    private File webapp;

    private File soRoot;

    private FilePersistence persistence;

    @Before
    public void setUp() throws Exception {
        webapp = Files.createTempDirectory("red5-persist-quota").toFile();
        new File(webapp, "persistence").mkdirs();
        FileSystemResourceLoader loader = new FileSystemResourceLoader() {
            @Override
            protected Resource getResourceByPath(String path) {
                return new FileSystemResource(new File(webapp, path));
            }
        };
        persistence = new FilePersistence(new PathMatchingResourcePatternResolver(loader));
        // object directories that do not exist yet are resolved against the store's root directory
        Field rootDir = FilePersistence.class.getDeclaredField("rootDir");
        rootDir.setAccessible(true);
        soRoot = new File((String) rootDir.get(persistence), "persistence/" + ScopeType.SHARED_OBJECT);
    }

    private boolean anyNamed(String name) throws IOException {
        try (Stream<Path> files = Files.walk(webapp.toPath())) {
            return files.anyMatch(p -> p.getFileName().toString().equals(name));
        }
    }

    @Test
    public void testEmptyObjectCreatesNoDirectories() throws Exception {
        SharedObject empty = new SharedObject("a/b/c/empty", "/app", true, persistence);
        assertTrue(persistence.saveObject(empty));
        assertFalse("no directory for an object that is not written", anyNamed("app"));
        // once it has data it is written
        empty.setAttribute("k", "v");
        assertTrue(persistence.saveObject(empty));
        assertTrue(new File(soRoot, "app/a/b/c/empty.red5").isFile());
    }

    @Test
    public void testRejectedObjectCreatesNoDirectories() throws Exception {
        File outside = Files.createTempDirectory("red5-persist-outside").toFile();
        File app = new File(soRoot, "app");
        app.mkdirs();
        try {
            Files.createSymbolicLink(new File(app, "link").toPath(), outside.toPath());
        } catch (IOException | UnsupportedOperationException e) {
            Assume.assumeNoException("symbolic links not supported", e);
        }
        SharedObject escaping = new SharedObject("link/deep/nested/so", "/app", true, persistence);
        escaping.setAttribute("k", "v");
        assertFalse(persistence.saveObject(escaping));
        assertFalse("no directory may be created outside the root", new File(outside, "deep").exists());
        try (Stream<Path> files = Files.list(outside.toPath())) {
            assertEquals(0, files.count());
        }
    }

    @Test
    public void testFreeSpaceFloorSkipsWriteAndRetries() throws Exception {
        persistence.setMinFreeBytes(Long.MAX_VALUE);
        SharedObject so = new SharedObject("x/y/full", "/app", true, persistence);
        SharedObject other = new SharedObject("other", "/app", true, persistence);
        // a modification queues the object
        assertTrue(so.setAttribute("k", "v"));
        assertTrue(other.setAttribute("k", "v"));
        assertTrue(persistence.save(so));
        assertEquals(2, queue().size());
        persist();
        assertFalse("no directory while below the free space floor", anyNamed("app"));
        // the skipped objects stay queued, once each, for the next flush
        assertEquals(2, queue().size());
        persist();
        assertEquals(2, queue().size());
        // once space is available the retried write goes through
        persistence.setMinFreeBytes(1);
        persist();
        assertEquals(0, queue().size());
        assertTrue(new File(soRoot, "app/x/y/full.red5").isFile());
        assertTrue(new File(soRoot, "app/other.red5").isFile());
    }

    @Test
    public void testRemovedObjectIsNotRetried() throws Exception {
        persistence.setMinFreeBytes(Long.MAX_VALUE);
        SharedObject so = new SharedObject("gone", "/app", true, persistence);
        so.setAttribute("k", "v");
        assertTrue(persistence.save(so));
        persistence.remove(so);
        persist();
        assertEquals(0, queue().size());
    }

    private void persist() throws Exception {
        Method method = FilePersistence.class.getDeclaredMethod("persist");
        method.setAccessible(true);
        method.invoke(persistence);
    }

    private Queue<?> queue() throws Exception {
        Field field = FilePersistence.class.getDeclaredField("queue");
        field.setAccessible(true);
        return (Queue<?>) field.get(persistence);
    }

}
