package org.red5.server.so;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.red5.io.amf3.ByteArray;
import org.red5.server.net.rtmp.codec.RTMPProtocolDecoder;
import org.red5.server.net.rtmp.codec.RTMPProtocolEncoder;
import org.red5.server.persistence.FilePersistence;
import org.red5.server.so.ISharedObjectEvent.Type;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Values an AMF3 client can store in a shared object that AMF0 cannot hold, vectors and byte arrays, reach AMF0 subscribers and the AMF0
 * persistence file as AMF3 values after the avmplus-object marker (AMF0 3.1).
 */
public class SharedObjectSwitchedValuesTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static Map<String, Object> values() {
        Vector<Integer> ints = new Vector<>(List.of(1, -2));
        ByteArray bytes = new ByteArray();
        bytes.writeBytes(new byte[] { 0x7F, (byte) 0x80 });
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("ints", ints);
        values.put("again", ints);
        values.put("numbers", new Vector<>(List.of(1.5d, -2.25d)));
        values.put("objects", new Vector<Object>(List.of("s", 1.5d)));
        values.put("bytes", bytes);
        values.put("text", "after");
        return values;
    }

    private static void assertValues(Map<String, Object> attrs) {
        assertTrue(String.valueOf(attrs.get("ints")), attrs.get("ints") instanceof Vector);
        assertEquals(List.of(1, -2), attrs.get("ints"));
        assertEquals(List.of(1.5d, -2.25d), attrs.get("numbers"));
        assertEquals(List.of("s", 1.5d), attrs.get("objects"));
        ByteArray bytes = (ByteArray) attrs.get("bytes");
        assertNotNull(bytes);
        assertEquals(2, bytes.length());
        assertEquals("after", attrs.get("text"));
    }

    /**
     * An update to an AMF0 subscriber, as SharedObject sends it when an AMF3 client set the values, is written by the server encoder and
     * read back by the decoder; the vector set twice is one value, its second occurrence an AMF3 reference.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testUpdateToAmf0Subscriber() {
        SharedObjectMessage msg = new SharedObjectMessage(null, "so", 1, false);
        msg.addEvent(Type.CLIENT_UPDATE_DATA, null, values());
        // without a connection the encoder writes AMF0
        IoBuffer out = new RTMPProtocolEncoder().encodeSharedObject(msg).flip();
        ISharedObjectMessage decoded = new RTMPProtocolDecoder().decodeSharedObject(out);
        assertFalse("the whole body must be read", out.hasRemaining());
        List<ISharedObjectEvent> events = new ArrayList<>(decoded.getEvents());
        Map<String, Object> attrs = new LinkedHashMap<>();
        for (ISharedObjectEvent event : events) {
            // the decoder gives every update event a map of the attributes it holds
            assertEquals(Type.CLIENT_UPDATE_DATA, event.getType());
            attrs.putAll((Map<String, Object>) event.getValue());
        }
        assertEquals(6, attrs.size());
        assertValues(attrs);
        assertSame(attrs.get("ints"), attrs.get("again"));
    }

    /**
     * FilePersistence writes shared objects with the AMF0 encoder and reads them with the AMF0 input.
     */
    @Test
    public void testPersistence() throws Exception {
        File webapp = tmp.newFolder("webapp");
        assertTrue(new File(webapp, "persistence/SHARED_OBJECT/app").mkdirs());
        FileSystemResourceLoader loader = new FileSystemResourceLoader() {
            @Override
            protected Resource getResourceByPath(String path) {
                return new FileSystemResource(new File(webapp, path));
            }
        };
        FilePersistence persistence = new FilePersistence(new PathMatchingResourcePatternResolver(loader));
        assertTrue(persistence.save(new SharedObject(values(), "switched", "/app", true)));
        persistence.notifyClose();
        SharedObject loaded = new SharedObject("switched", "/app", false);
        assertTrue(persistence.load(loaded));
        Map<String, Object> attrs = loaded.getAttributes();
        assertValues(attrs);
        assertSame(attrs.get("ints"), attrs.get("again"));
    }

}
