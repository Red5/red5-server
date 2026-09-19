package org.red5.server.persistence;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Before;
import org.junit.Test;
import org.red5.io.object.Input;
import org.red5.io.object.Output;
import org.red5.server.api.persistence.IPersistable;
import org.red5.server.api.persistence.IPersistenceStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Persistent shared-object names must stay confined beneath the persistence root (issue #453).
 */
public class FilePersistenceTraversalTest {

    private File tmp;

    private File webapp;

    private File victim;

    private FilePersistence persistence;

    /** Minimal persistable standing in for a shared object with a network-supplied name. */
    private static class Persistable implements IPersistable {

        private String name;

        private String path = "";

        private boolean persistent;

        public Persistable() {
        }

        Persistable(String name) {
            this.name = name;
        }

        public boolean isPersistent() {
            return persistent;
        }

        public void setPersistent(boolean persistent) {
            this.persistent = persistent;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return "SharedObject";
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public long getLastModified() {
            return 0;
        }

        public IPersistenceStore getStore() {
            return null;
        }

        public void setStore(IPersistenceStore store) {
        }

        public void serialize(Output output) {
            output.writeString("payload");
        }

        public void deserialize(Input input) {
            input.readString();
        }
    }

    @Before
    public void setUp() throws Exception {
        tmp = Files.createTempDirectory("red5-persist").toFile();
        // layout mirrors a deployed webapp: <webapp>/persistence/<type>/<name>.red5
        webapp = new File(tmp, "webapp");
        new File(webapp, "persistence/SharedObject").mkdirs();
        FileSystemResourceLoader loader = new FileSystemResourceLoader() {
            @Override
            protected Resource getResourceByPath(String path) {
                return new FileSystemResource(new File(webapp, path));
            }
        };
        persistence = new FilePersistence(new PathMatchingResourcePatternResolver(loader));
        // a real persisted object placed outside the persistence root
        assertTrue(persistence.saveObject(new Persistable("victim")));
        victim = new File(tmp, "victim.red5");
        Files.copy(new File(webapp, "persistence/SharedObject/victim.red5").toPath(), victim.toPath());
    }

    private static boolean existsAnywhere(File root, String name) throws Exception {
        try (java.util.stream.Stream<java.nio.file.Path> s = Files.walk(root.toPath())) {
            return s.anyMatch(p -> p.getFileName().toString().equals(name));
        }
    }

    @Test
    public void testSaveWithTraversalNameStaysInsidePersistenceRoot() throws Exception {
        assertFalse(persistence.saveObject(new Persistable("../../../../evil")));
        assertFalse("object escaped the persistence root", existsAnywhere(tmp, "evil.red5"));
    }

    @Test
    public void testSaveWithShallowTraversalNameIsRejected() throws Exception {
        assertFalse(persistence.saveObject(new Persistable("../evil2")));
        assertFalse(existsAnywhere(tmp, "evil2.red5"));
    }

    @Test
    public void testRemoveWithTraversalNameDoesNotTouchOutsideFiles() {
        persistence.remove("../../victim");
        persistence.remove("SharedObject/../../../victim");
        assertTrue("file outside persistence root was deleted", victim.exists());
    }

    @Test
    public void testLoadWithTraversalNameReturnsNull() {
        assertNull(persistence.load("../../victim"));
    }

    @Test
    public void testPlainNameIsSavedUnderPersistenceRoot() throws Exception {
        assertTrue(persistence.saveObject(new Persistable("room1")));
        assertTrue(new File(webapp, "persistence/SharedObject/room1.red5").exists());
    }
}
