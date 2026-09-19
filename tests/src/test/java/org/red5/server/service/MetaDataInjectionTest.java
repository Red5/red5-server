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
 * Copies an FLV tag by tag into a new file while injecting cue point metadata at the requested times.
 */
public class MetaDataInjectionTest {

    private static final File SOURCE = new File("target/test-classes/fixtures/test.flv");

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private IFLVService service;

    @Before
    public void setUp() {
        service = new FLVService();
        assertTrue("Missing fixture: " + SOURCE, SOURCE.isFile());
    }

    private static IMetaCue cue(String name, double time) {
        IMetaCue cue = new MetaCue<Object, Object>();
        cue.setName(name);
        cue.setTime(time);
        cue.setType(ICueType.EVENT);
        return cue;
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
    public void testMetaDataInjection() throws Exception {
        File source = tmp.newFile("source.flv");
        Files.copy(SOURCE.toPath(), source.toPath(), StandardCopyOption.REPLACE_EXISTING);
        File target = tmp.newFile("injected.flv");
        List<ITag> before = readAll(source);
        assertTrue("source fixture has no tags", before.size() > 2);
        assertEquals("source fixture must be pristine (one onMetaData, no cue points); an earlier test may have written into it", 1, count(before, IoConstants.TYPE_METADATA));
        // the fixture is under a second long, so both cues fall inside it
        TreeSet<IMetaCue> cues = new TreeSet<>();
        cues.add(cue("cue_1", 0.01));
        cues.add(cue("cue_2", 0.5));
        IFLV readflv = (IFLV) service.getStreamableFile(source);
        readflv.setCache(NoCacheImpl.getInstance());
        ITagReader reader = readflv.getReader();
        ITagWriter writer = ((IFLV) service.getStreamableFile(target)).getWriter();
        try {
            writeTagsWithInjection(reader, writer, cues);
        } finally {
            reader.close();
            writer.close();
        }
        List<ITag> after = readAll(target);
        // the writer adds its own onMetaData on finalize, so compare media tags rather than totals
        assertEquals(count(before, IoConstants.TYPE_VIDEO), count(after, IoConstants.TYPE_VIDEO));
        assertEquals(count(before, IoConstants.TYPE_AUDIO), count(after, IoConstants.TYPE_AUDIO));
        // timestamps must remain monotonic and both cues must appear in order with their payloads intact
        int lastTimestamp = -1;
        List<String> cueNames = new ArrayList<>();
        for (ITag tag : after) {
            assertTrue("timestamps went backwards", tag.getTimestamp() >= lastTimestamp);
            lastTimestamp = tag.getTimestamp();
            if (tag.getDataType() == IoConstants.TYPE_METADATA) {
                Input in = new Input(tag.getBody().duplicate());
                String handler = Deserializer.deserialize(in, String.class);
                if ("onCuePoint".equals(handler)) {
                    Map<String, Object> cue = Deserializer.deserialize(in, Map.class);
                    cueNames.add((String) cue.get("name"));
                    assertEquals((int) (((Number) cue.get("time")).doubleValue() * 1000), tag.getTimestamp());
                }
            }
        }
        assertEquals(List.of("cue_1", "cue_2"), cueNames);
    }

    private void writeTagsWithInjection(ITagReader reader, ITagWriter writer, TreeSet<IMetaCue> cues) throws IOException {
        int cuePointTimeStamp = getTimeInMilliseconds(cues.first());
        while (reader.hasMoreTags()) {
            ITag tag = reader.readTag();
            assertNotNull(tag);
            if (!cues.isEmpty()) {
                while (tag.getTimestamp() > cuePointTimeStamp) {
                    ITag injectedTag = injectMetaData(cues.first(), tag);
                    assertTrue(writer.writeTag(injectedTag));
                    tag.setPreviousTagSize((injectedTag.getBodySize() + 11));
                    cues.remove(cues.first());
                    if (cues.isEmpty()) {
                        break;
                    }
                    cuePointTimeStamp = getTimeInMilliseconds(cues.first());
                }
            }
            assertTrue(writer.writeTag(tag));
        }
        assertTrue("not every cue was injected: " + cues, cues.isEmpty());
    }

    private static ITag injectMetaData(IMetaCue cue, ITag tag) {
        Output out = new Output(IoBuffer.allocate(1000));
        Serializer.serialize(out, "onCuePoint");
        Serializer.serialize(out, cue);
        IoBuffer body = out.buf().flip();
        return new Tag(IoConstants.TYPE_METADATA, getTimeInMilliseconds(cue), body.limit(), body, tag.getPreviousTagSize());
    }

    private static int getTimeInMilliseconds(IMetaCue cue) {
        return (int) (cue.getTime() * 1000.00);
    }

}
