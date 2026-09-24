package org.red5.server.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Queue;
import java.util.stream.Stream;

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
 * A frequently modified persistent object must only be queued once per flush (issue #454).
 */
public class FilePersistenceQueueTest {

    @Test
    public void testRepeatedSavesQueueOnce() throws Exception {
        File webapp = Files.createTempDirectory("red5-persist-queue").toFile();
        new File(webapp, "persistence/SharedObject").mkdirs();
        FileSystemResourceLoader loader = new FileSystemResourceLoader() {
            @Override
            protected Resource getResourceByPath(String path) {
                return new FileSystemResource(new File(webapp, path));
            }
        };
        FilePersistence persistence = new FilePersistence(new PathMatchingResourcePatternResolver(loader));
        Persistable hot = new Persistable("hot"), other = new Persistable("other");
        for (int i = 0; i < 10_000; i++) {
            assertTrue(persistence.save(hot));
        }
        assertTrue(persistence.save(other));
        assertEquals(2, queue(persistence).size());
        // after a flush the object may be queued again
        persistence.notifyClose();
        assertEquals(0, queue(persistence).size());
        try (Stream<Path> files = Files.walk(webapp.toPath())) {
            assertTrue("flushed object must be written", files.anyMatch(p -> p.getFileName().toString().equals("hot.red5")));
        }
    }

    private static Queue<?> queue(FilePersistence persistence) throws Exception {
        Field field = FilePersistence.class.getDeclaredField("queue");
        field.setAccessible(true);
        return (Queue<?>) field.get(persistence);
    }

    private static class Persistable implements IPersistable {

        private String name;

        private String path = "/app";

        Persistable(String name) {
            this.name = name;
        }

        public boolean isPersistent() {
            return true;
        }

        public void setPersistent(boolean persistent) {
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

}
