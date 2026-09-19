package org.red5.server.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.red5.cache.impl.NoCacheImpl;
import org.red5.io.ITag;
import org.red5.io.ITagReader;
import org.red5.io.IoConstants;
import org.red5.io.amf.Input;
import org.red5.io.flv.IFLV;
import org.red5.io.flv.meta.ICueType;
import org.red5.io.flv.meta.IMetaCue;
import org.red5.io.flv.meta.MetaCue;
import org.red5.io.flv.meta.MetaData;
import org.red5.io.flv.meta.MetaService;
import org.red5.io.object.Deserializer;
import org.red5.server.service.flv.impl.FLVService;

/**
 * Writes metadata and cue points into a copy of an FLV via the meta service and verifies the result on disk.
 */
public class MetaServiceTest {

    private static final File SOURCE = new File("target/test-classes/fixtures/test.flv");

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private FLVService service;

    private MetaService metaService;

    @Before
    public void setUp() {
        service = new FLVService();
        metaService = new MetaService();
        assertTrue("Missing fixture: " + SOURCE, SOURCE.isFile());
    }

    private List<ITag> readAll(File file) throws Exception {
        IFLV flv = (IFLV) service.getStreamableFile(file);
        flv.setCache(NoCacheImpl.getInstance());
        ITagReader reader = flv.getReader();
        List<ITag> tags = new ArrayList<>();
        try {
            while (reader.hasMoreTags()) {
                ITag tag = reader.readTag();
                assertNotNull(tag);
                tags.add(tag);
            }
        } finally {
            reader.close();
        }
        return tags;
    }

    private static long count(List<ITag> tags, byte type) {
        return tags.stream().filter(t -> t.getDataType() == type).count();
    }

    @Test
    public void testWrite() throws Exception {
        File f = tmp.newFile("test.flv");
        Files.copy(SOURCE.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        List<ITag> before = readAll(f);
        long videoBefore = count(before, IoConstants.TYPE_VIDEO);
        long audioBefore = count(before, IoConstants.TYPE_AUDIO);
        assertTrue("fixture has no video tags", videoBefore > 0);
        assertEquals("source fixture must be pristine (one onMetaData, no cue points); an earlier test may have written into it", 1, count(before, IoConstants.TYPE_METADATA));
        MetaData<?, ?> meta = createMeta();
        IFLV flv = (IFLV) service.getStreamableFile(f);
        flv.setCache(NoCacheImpl.getInstance());
        flv.setMetaService(metaService);
        flv.setMetaData(meta);
        // the service fills in what it learns from the file
        assertTrue("duration should be taken from the file", meta.getDuration() > 0);
        List<ITag> after = readAll(f);
        assertEquals("media tags must survive the rewrite", videoBefore, count(after, IoConstants.TYPE_VIDEO));
        assertEquals("media tags must survive the rewrite", audioBefore, count(after, IoConstants.TYPE_AUDIO));
        // The writer prepends its own onMetaData when it finalizes, so the injected tag is not necessarily first.
        // AMF hands numbers back as Double, so inspect the raw map rather than the MetaData getters.
        Map<String, Object> injected = null;
        List<String> cueNames = new ArrayList<>();
        List<Integer> cueTimestamps = new ArrayList<>();
        int lastTimestamp = -1;
        for (ITag tag : after) {
            assertTrue("timestamps went backwards", tag.getTimestamp() >= lastTimestamp);
            lastTimestamp = tag.getTimestamp();
            if (tag.getDataType() != IoConstants.TYPE_METADATA) {
                continue;
            }
            Input in = new Input(tag.getBody().duplicate());
            String handler = Deserializer.deserialize(in, String.class);
            Map<String, Object> map = Deserializer.deserialize(in, Map.class);
            if ("onMetaData".equals(handler) && map.containsKey("width")) {
                assertEquals("only one injected onMetaData expected", null, injected);
                assertEquals(0, tag.getTimestamp());
                injected = map;
            } else if ("onCuePoint".equals(handler)) {
                cueNames.add((String) map.get("name"));
                cueTimestamps.add(tag.getTimestamp());
            }
        }
        assertNotNull("injected onMetaData not found", injected);
        assertEquals(300.0, ((Number) injected.get("width")).doubleValue(), 0);
        assertEquals(400.0, ((Number) injected.get("height")).doubleValue(), 0);
        assertEquals(15.0, ((Number) injected.get("framerate")).doubleValue(), 0);
        assertEquals(Boolean.TRUE, injected.get("canSeekToEnd"));
        assertEquals(meta.getDuration(), ((Number) injected.get("duration")).doubleValue(), 0.001);
        assertNotNull("cue points missing from injected metadata", injected.get("cuePoints"));
        // the two cue points are written as their own tags, in time order
        assertEquals(List.of("cue_1", "cue_2"), cueNames);
        assertEquals(List.of(10, 30), cueTimestamps);
    }

    private static MetaData<?, ?> createMeta() {
        IMetaCue cp = new MetaCue<Object, Object>();
        cp.setName("cue_1");
        cp.setTime(0.01);
        cp.setType(ICueType.EVENT);
        IMetaCue cp1 = new MetaCue<Object, Object>();
        cp1.setName("cue_2");
        cp1.setTime(0.03);
        cp1.setType(ICueType.EVENT);
        MetaData<?, ?> meta = new MetaData<Object, Object>();
        meta.setMetaCue(new IMetaCue[] { cp, cp1 });
        meta.setCanSeekToEnd(true);
        meta.setDuration(300);
        meta.setFrameRate(15);
        meta.setHeight(400);
        meta.setWidth(300);
        return meta;
    }

}
