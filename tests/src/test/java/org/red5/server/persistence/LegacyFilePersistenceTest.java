package org.red5.server.persistence;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.red5.server.so.SharedObject;
import org.red5.test.LegacyBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.w3c.dom.Document;

/**
 * Shared objects persisted by Red5 before the AMF0 specification fixes must still load.
 * <p>
 * The files under src/test/resources/persistence/legacy were written by FilePersistence at a04119fb, the commit before the AMF0/AMF3
 * changes: a worktree of a04119fb was built offline with {@code mvn -o -pl server -am -Dmaven.test.skip=true package} and a small program
 * run against its classes saved {@code new SharedObject(values, "legacy", "/legacyapp", true)} holding the values built by
 * {@link #legacyValues()} (with {@link LegacyBean} compiled from this source tree; it is not in an org.red5.server package because those
 * are on the deserialization black-list), and a second shared object "legacyxml" holding an XML document and a string. legacy.red5 is
 * stored gzipped because its long strings make it 136 KB. The program is kept next to the files as LegacyGenerator.java.txt.
 * <p>
 * The old writer already wrote AMF0 references with the indices of the specification, so the new reader resolves them. The old reader
 * did not: it gave dates an index and indexed an object after its members, so with the old code mixed[2] and mixed[3] loaded as dates
 * instead of the "shared" object and nested.inner loaded as the "mixed" list.
 * <p>
 * Known incompatibility: before the fix the AMF0 writer gave XML documents a U16 length, the specification and the new reader use a U32
 * length, so legacyxml.red5 does not load. The old reader could not load it either (it threw a NullPointerException), so no shared object
 * that loaded before is lost.
 */
public class LegacyFilePersistenceTest {

    private static final long DATE_MS = 1700000000123L;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File webapp;

    private FilePersistence persistence;

    @Before
    public void setUp() throws Exception {
        webapp = tmp.newFolder("webapp");
        File dir = new File(webapp, "persistence/SHARED_OBJECT/legacyapp");
        assertTrue(dir.mkdirs());
        try (InputStream in = new GZIPInputStream(getClass().getResourceAsStream("/persistence/legacy/legacy.red5.gz"))) {
            Files.copy(in, new File(dir, "legacy.red5").toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        try (InputStream in = getClass().getResourceAsStream("/persistence/legacy/legacyxml.red5")) {
            Files.copy(in, new File(dir, "legacyxml.red5").toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        FileSystemResourceLoader loader = new FileSystemResourceLoader() {
            @Override
            protected Resource getResourceByPath(String path) {
                return new FileSystemResource(new File(webapp, path));
            }
        };
        persistence = new FilePersistence(new PathMatchingResourcePatternResolver(loader));
    }

    /**
     * The values the legacy files were written with. A HashMap, as in the generator, so the attributes are written in the same order.
     */
    static Map<String, Object> legacyValues() {
        Date date = new Date(DATE_MS);
        Map<String, Object> shared = new LinkedHashMap<>();
        shared.put("id", 7);
        shared.put("label", "shared");
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("x", 1.5d);
        inner.put("label", "inner");
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("inner", inner);
        nested.put("list", Arrays.asList(1, 2, 3));
        nested.put("when", date);
        nested.put("again", shared);
        // dates before an object reference: the old reader gave each date a reference index
        List<Object> mixed = Arrays.asList(date, date, shared, shared, inner);
        char[] longChars = new char[70000];
        Arrays.fill(longChars, 'L');
        char[] maxChars = new char[65535];
        Arrays.fill(maxChars, 'M');
        Map<String, Object> values = new HashMap<>();
        values.put("date", date);
        values.put("shared", shared);
        values.put("nested", nested);
        values.put("mixed", mixed);
        values.put("bean", new LegacyBean("bean", 42, date));
        values.put("longString", new String(longChars));
        values.put("maxString", new String(maxChars));
        values.put("unicode", "héllo 世界 😀");
        values.put("number", 3.25d);
        values.put("integer", 12);
        values.put("flag", Boolean.TRUE);
        values.put("nothing", null);
        return values;
    }

    private SharedObject load(String name) {
        // not persistent yet, otherwise FilePersistence treats it as already loaded
        SharedObject so = new SharedObject(name, "/legacyapp", false);
        assertTrue(name + " must load", persistence.load(so));
        return so;
    }

    @Test
    public void testLegacySharedObjectLoads() {
        SharedObject so = load("legacy");
        assertEquals("legacy", so.getName());
        assertTrue(so.isPersistent());
        assertLegacyAttributes(so.getAttributes());
    }

    @SuppressWarnings("unchecked")
    private static void assertLegacyAttributes(Map<String, Object> attrs) {
        Date date = new Date(DATE_MS);
        assertEquals(date, attrs.get("date"));
        assertEquals(3.25d, attrs.get("number"));
        assertEquals(12.0d, ((Number) attrs.get("integer")).doubleValue(), 0d);
        assertEquals(Boolean.TRUE, attrs.get("flag"));
        // "nothing" is stored as an AMF0 null; the attribute store does not keep null attributes
        assertFalse(attrs.containsKey("nothing"));
        assertEquals("héllo 世界 😀", attrs.get("unicode"));
        assertEquals(legacyValues().get("longString"), attrs.get("longString"));
        assertEquals(legacyValues().get("maxString"), attrs.get("maxString"));
        Map<String, Object> shared = (Map<String, Object>) attrs.get("shared");
        assertEquals(7.0d, ((Number) shared.get("id")).doubleValue(), 0d);
        assertEquals("shared", shared.get("label"));
        Map<String, Object> nested = (Map<String, Object>) attrs.get("nested");
        Map<String, Object> inner = (Map<String, Object>) nested.get("inner");
        assertEquals(1.5d, inner.get("x"));
        assertEquals("inner", inner.get("label"));
        assertArrayEquals(new Object[] { 1.0d, 2.0d, 3.0d }, ((List<Object>) nested.get("list")).stream().map(n -> ((Number) n).doubleValue()).toArray());
        assertEquals(date, nested.get("when"));
        // the references the old writer wrote resolve to the objects they were written for
        assertSame(shared, nested.get("again"));
        List<Object> mixed = (List<Object>) attrs.get("mixed");
        assertEquals(5, mixed.size());
        assertEquals(date, mixed.get(0));
        assertEquals(date, mixed.get(1));
        assertSame(shared, mixed.get(2));
        assertSame(shared, mixed.get(3));
        assertSame(inner, mixed.get(4));
        Object bean = attrs.get("bean");
        assertTrue(String.valueOf(bean), bean instanceof LegacyBean);
        assertEquals("bean", ((LegacyBean) bean).getName());
        assertEquals(42, ((LegacyBean) bean).getCount());
        assertEquals(date, ((LegacyBean) bean).getCreated());
        assertEquals(11, attrs.size());
    }

    /**
     * Saving the loaded shared object with the new code and loading it again gives the same attributes.
     */
    @Test
    public void testLegacySharedObjectSurvivesResave() {
        SharedObject so = load("legacy");
        File file = new File(webapp, "persistence/SHARED_OBJECT/legacyapp/legacy.red5");
        assertTrue(file.delete());
        assertTrue(persistence.save(so));
        persistence.notifyClose();
        assertTrue(file.isFile());
        FileSystemResourceLoader loader = new FileSystemResourceLoader() {
            @Override
            protected Resource getResourceByPath(String path) {
                return new FileSystemResource(new File(webapp, path));
            }
        };
        persistence = new FilePersistence(new PathMatchingResourcePatternResolver(loader));
        assertLegacyAttributes(load("legacy").getAttributes());
    }

    /**
     * The new code writes the same values as a shared object the new code loads, so the expected attributes are not only those of the
     * old files.
     */
    @Test
    public void testNewSharedObjectRoundTrip() {
        SharedObject so = new SharedObject(legacyValues(), "fresh", "/legacyapp", true);
        assertTrue(persistence.save(so));
        persistence.notifyClose();
        SharedObject loaded = new SharedObject("fresh", "/legacyapp", false);
        assertTrue(persistence.load(loaded));
        assertLegacyAttributes(loaded.getAttributes());
    }

    /**
     * XML written by the old writer carries a U16 length; it cannot be read as the U32 length the specification defines. The old reader
     * could not read it either.
     */
    @Test
    public void testLegacyXmlDoesNotLoad() {
        SharedObject so = new SharedObject("legacyxml", "/legacyapp", false);
        boolean loaded;
        try {
            loaded = persistence.load(so);
        } catch (RuntimeException e) {
            // the U16 length and the first text bytes read as a U32 length run past the end of the file
            loaded = false;
        }
        assertFalse(loaded && so.getAttributes().get("xml") instanceof Document);
    }

    /**
     * XML written by the new code loads.
     */
    @Test
    public void testNewXmlRoundTrip() throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        doc.appendChild(doc.createElement("root")).appendChild(doc.createElement("child")).setTextContent("text");
        Map<String, Object> values = new HashMap<>();
        values.put("xml", doc);
        values.put("after", "after xml");
        assertTrue(persistence.save(new SharedObject(values, "freshxml", "/legacyapp", true)));
        persistence.notifyClose();
        SharedObject loaded = new SharedObject("freshxml", "/legacyapp", false);
        assertTrue(persistence.load(loaded));
        Object xml = loaded.getAttributes().get("xml");
        assertNotNull(xml);
        assertTrue(xml instanceof Document);
        assertEquals("text", ((Document) xml).getDocumentElement().getFirstChild().getTextContent());
        assertEquals("after xml", loaded.getAttributes().get("after"));
    }

}
