package org.red5.server.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.red5.cache.impl.NoCacheImpl;
import org.red5.io.ITag;
import org.red5.io.ITagReader;
import org.red5.io.ITagWriter;
import org.red5.io.IoConstants;
import org.red5.io.amf.Input;
import org.red5.io.amf.Output;
import org.red5.io.flv.IFLV;
import org.red5.io.flv.impl.Tag;
import org.red5.io.flv.meta.ICueType;
import org.red5.io.flv.meta.IMetaCue;
import org.red5.io.flv.meta.MetaCue;
import org.red5.io.object.Deserializer;
import org.red5.io.object.Serializer;
import org.red5.server.service.flv.IFLVService;
import org.red5.server.service.flv.impl.FLVService;

/**
 * Writes cue point metadata tags into a fresh FLV and reads them back.
 */
public class CuePointInjectionTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private IFLVService service;

    @Before
    public void setUp() {
        service = new FLVService();
    }

    private static IMetaCue cue(String name, double time) {
        IMetaCue cue = new MetaCue<Object, Object>();
        cue.setName(name);
        cue.setTime(time);
        cue.setType(ICueType.EVENT);
        return cue;
    }

    @Test
    public void testCuePointInjection() throws Exception {
        File f = tmp.newFile("test_cue1.flv");
        IFLV flv = (IFLV) service.getStreamableFile(f);
        TreeSet<IMetaCue> cues = new TreeSet<>();
        cues.add(cue("cue_2", 2.5));
        cues.add(cue("cue_1", 0.01));
        ITagWriter writer = flv.getWriter();
        try {
            for (IMetaCue cue : cues) {
                assertTrue(writer.writeTag(injectCuePoint(cue)));
            }
        } finally {
            writer.close();
        }
        assertTrue("written flv is empty", f.length() > 0);
        // read back: only the two cue tags, in time order
        IFLV readflv = (IFLV) service.getStreamableFile(f);
        readflv.setCache(NoCacheImpl.getInstance());
        ITagReader reader = readflv.getReader();
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
        // the writer prepends its own onMetaData on finalize; the two cue tags must follow in time order
        List<ITag> cueTags = new ArrayList<>();
        for (ITag tag : tags) {
            assertEquals(IoConstants.TYPE_METADATA, tag.getDataType());
            Input in = new Input(tag.getBody().duplicate());
            if ("onCuePoint".equals(Deserializer.deserialize(in, String.class))) {
                Map<String, Object> cue = Deserializer.deserialize(in, Map.class);
                assertEquals(ICueType.EVENT, cue.get("type"));
                assertEquals((int) (((Number) cue.get("time")).doubleValue() * 1000), tag.getTimestamp());
                cueTags.add(tag);
            }
        }
        assertEquals(2, cueTags.size());
        assertEquals(10, cueTags.get(0).getTimestamp());
        assertEquals(2500, cueTags.get(1).getTimestamp());
        String[] names = { "cue_1", "cue_2" };
        for (int i = 0; i < cueTags.size(); i++) {
            Input in = new Input(cueTags.get(i).getBody().duplicate());
            Deserializer.deserialize(in, String.class);
            Map<String, Object> cue = Deserializer.deserialize(in, Map.class);
            assertEquals(names[i], cue.get("name"));
        }
    }

    private static ITag injectCuePoint(IMetaCue cue) {
        Output out = new Output(IoBuffer.allocate(1000));
        Serializer.serialize(out, "onCuePoint");
        Serializer.serialize(out, cue);
        IoBuffer body = out.buf().flip();
        int timestamp = (int) (cue.getTime() * 1000.00);
        return new Tag(IoConstants.TYPE_METADATA, timestamp, body.limit(), body, 0);
    }

    @Test
    public void testCuePointOrder() {
        TreeSet<IMetaCue> cues = new TreeSet<>();
        cues.add(cue("cue_1", 0.01));
        cues.add(cue("cue_3", 2.01));
        cues.add(cue("cue_2", 1.01));
        Iterator<IMetaCue> it = cues.iterator();
        assertEquals("cue_1", it.next().getName());
        assertEquals("cue_2", it.next().getName());
        assertEquals("cue_3", it.next().getName());
        assertFalse(it.hasNext());
    }

}
