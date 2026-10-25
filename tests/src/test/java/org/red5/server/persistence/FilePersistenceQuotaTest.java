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
 * FilePersistence must not create directories for objects it does not write, and must honor the free space floor (issue #454) and the
 * budget of persisted bytes (issue #492).
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

    private FilePersistence newStore() {
        FileSystemResourceLoader loader = new FileSystemResourceLoader() {
            @Override
            protected Resource getResourceByPath(String path) {
                return new FileSystemResource(new File(webapp, path));
            }
        };
        return new FilePersistence(new PathMatchingResourcePatternResolver(loader));
    }

    private static SharedObject so(String name, FilePersistence store, String value) {
        SharedObject so = new SharedObject(name, "/app", true, store);
        so.setAttribute("k", value);
        return so;
    }

    @Test
    public void testDiskBudgetUnlimitedByDefault() throws Exception {
        for (int i = 0; i < 20; i++) {
            assertTrue(persistence.saveObject(so("so" + i, persistence, "0123456789")));
        }
        assertEquals(20, new File(soRoot, "app").list().length);
    }

    @Test
    public void testDiskBudgetSkipsWriteAndResumesAfterDeletion() throws Exception {
        SharedObject first = so("one", persistence, "0123456789");
        assertTrue(persistence.saveObject(first));
        File firstFile = new File(soRoot, "app/one.red5");
        long size = firstFile.length();
        assertTrue(size > 0);
        // room for exactly one object of this size
        persistence.setMaxBytes(size);
        assertTrue("rewriting an object replaces its size", persistence.saveObject(first));
        assertEquals(size, persistence.getPersistedBytes());
        // the second object would exceed the budget, it stays queued and is retried each flush
        SharedObject second = so("two", persistence, "abcdefghij");
        assertTrue(persistence.save(second));
        persist();
        assertFalse(new File(soRoot, "app/two.red5").exists());
        assertEquals(1, queue().size());
        persist();
        assertEquals(1, queue().size());
        // a growing rewrite is refused as well
        first.setAttribute("k", "0123456789-longer");
        assertFalse(persistence.saveObject(first));
        assertEquals(size, firstFile.length());
        first.setAttribute("k", "0123456789");
        // deleting the first object makes room, the next flush writes the second one
        persistence.remove(first);
        assertFalse(firstFile.exists());
        assertEquals(0L, persistence.getPersistedBytes());
        persist();
        assertEquals(0, queue().size());
        assertTrue(new File(soRoot, "app/two.red5").isFile());
        assertEquals(size, persistence.getPersistedBytes());
    }

    @Test
    public void testDiskBudgetCountsFilesWrittenBefore() throws Exception {
        // written while no budget applied, as by an earlier run
        SharedObject earlier = so("earlier", persistence, "0123456789");
        assertTrue(persistence.saveObject(earlier));
        long size = new File(soRoot, "app/earlier.red5").length();
        FilePersistence store = newStore();
        store.setMaxBytes(size + 1);
        assertFalse(store.saveObject(so("later", store, "abcdefghij")));
        assertFalse(new File(soRoot, "app/later.red5").exists());
        assertEquals(size, store.getPersistedBytes());
    }

    @Test
    public void testDiskBudgetIsSharedByTheStoresOfOneApplication() throws Exception {
        // shared objects of room scopes have stores of their own on the application's persistence root
        FilePersistence room = newStore();
        persistence.setMaxBytes(1L << 20);
        room.setMaxBytes(1L << 20);
        assertTrue(persistence.saveObject(so("one", persistence, "0123456789")));
        long size = new File(soRoot, "app/one.red5").length();
        assertTrue(room.saveObject(so("two", room, "0123456789")));
        assertEquals(2 * size, persistence.getPersistedBytes());
        assertEquals(2 * size, room.getPersistedBytes());
        // the budget covers both stores
        persistence.setMaxBytes(2 * size);
        room.setMaxBytes(2 * size);
        assertFalse(room.saveObject(so("six", room, "0123456789")));
        // a removal through one store makes room for the other
        persistence.remove(ScopeType.SHARED_OBJECT + "/app/one");
        assertEquals(size, room.getPersistedBytes());
        assertTrue(room.saveObject(so("six", room, "0123456789")));
        assertEquals(2 * size, persistence.getPersistedBytes());
    }

    @Test
    public void testClearedObjectRemovesItsFile() throws Exception {
        persistence.setMaxBytes(1L << 20);
        SharedObject so = so("x/cleared", persistence, "0123456789");
        so.setAttribute("other", 42);
        assertTrue(persistence.saveObject(so));
        File file = new File(soRoot, "app/x/cleared.red5");
        assertTrue(file.isFile());
        assertTrue(persistence.getPersistedBytes() > 0);
        so.removeAttributes();
        assertTrue(persistence.saveObject(so));
        assertFalse("the earlier state must not come back on load", file.exists());
        assertEquals(0L, persistence.getPersistedBytes());
    }

    @Test
    public void testEvictWritesPendingChangesFirst() throws Exception {
        SharedObject so = so("evicted", persistence, "0123456789");
        assertTrue(persistence.save(so));
        String id = ScopeType.SHARED_OBJECT + "/app/evicted";
        assertTrue(persistence.load(id) == so);
        // a pending change that cannot be written keeps the object in memory
        persistence.setMaxBytes(1);
        assertFalse(persistence.evict(so));
        assertTrue(persistence.load(id) == so);
        assertEquals(1, queue().size());
        persistence.setMaxBytes(0);
        assertTrue(persistence.evict(so));
        assertEquals(0, queue().size());
        // the next load reads the object back from its file
        SharedObject loaded = (SharedObject) persistence.load(id);
        assertTrue(loaded != null && loaded != so);
        assertEquals("0123456789", loaded.getAttribute("k"));
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
