package org.red5.io.flv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.red5.io.ITag;
import org.red5.io.IoConstants;
import org.red5.io.amf.Input;
import org.red5.io.flv.impl.FLVReader;
import org.red5.io.flv.impl.FLVWriter;
import org.red5.io.flv.meta.MetaData;
import org.red5.io.flv.meta.MetaService;
import org.red5.io.object.Deserializer;

/**
 * FLV onMetaData between Red5 and an independent AMF0 implementation, FFmpeg: Red5 reads the metadata FFmpeg writes, and ffprobe reads the
 * metadata Red5 writes when it records (FLVWriter) and when it injects metadata (MetaService).
 * <p>
 * fixtures/ffmpeg_h264_aac.flv (30 KB) was made with FFmpeg N-121974 by: {@code ffmpeg -f lavfi -i testsrc=d=2:size=160x120:rate=10 -f
 * lavfi -i sine=frequency=440:d=2 -c:v libx264 -preset ultrafast -g 10 -pix_fmt yuv420p -c:a aac -b:a 32k -ar 22050 -ac 1 -fflags
 * +bitexact -flags:v +bitexact -flags:a +bitexact -metadata title=red5test ffmpeg_h264_aac.flv}. The ffprobe tests are skipped when
 * ffprobe is not installed.
 */
public class FLVMetaDataInteropTest {

    private static final File FIXTURE = new File("target/test-classes/fixtures/ffmpeg_h264_aac.flv");

    private static final Date DATE = new Date(1700000000123L);

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static List<ITag> readAll(File file) throws IOException {
        List<ITag> tags = new ArrayList<>();
        FLVReader reader = new FLVReader(file);
        try {
            while (reader.hasMoreTags()) {
                tags.add(reader.readTag());
            }
        } finally {
            reader.close();
        }
        return tags;
    }

    private static Map<String, Object> metaData(ITag tag) {
        assertEquals(IoConstants.TYPE_METADATA, tag.getDataType());
        Input input = new Input(tag.getBody().duplicate());
        assertEquals("onMetaData", Deserializer.deserialize(input, String.class));
        return Deserializer.deserialize(input, Map.class);
    }

    /**
     * FFmpeg writes onMetaData as an ECMA array of numbers, a boolean and strings.
     */
    @Test
    public void testRed5ReadsFFmpegMetaData() throws Exception {
        assertTrue("Missing fixture: " + FIXTURE, FIXTURE.isFile());
        ITag first = readAll(FIXTURE).get(0);
        MetaData<?, ?> meta = new MetaService().readMetaData(first.getBody().duplicate());
        assertEquals(String.valueOf(meta), 13, meta.size());
        assertEquals(2.046d, meta.getDuration(), 0.0005d);
        assertEquals(160, meta.getWidth());
        assertEquals(120, meta.getHeight());
        assertEquals(10d, meta.getFrameRate(), 0d);
        assertEquals(0, meta.getVideoDataRate());
        assertEquals(7, meta.getVideoCodecId());
        assertEquals(10, meta.getAudioCodecId());
        assertEquals(31.25d, ((Number) meta.get("audiodatarate")).doubleValue(), 0d);
        assertEquals(22050d, ((Number) meta.get("audiosamplerate")).doubleValue(), 0d);
        assertEquals(16d, ((Number) meta.get("audiosamplesize")).doubleValue(), 0d);
        assertEquals(Boolean.FALSE, meta.get("stereo"));
        assertEquals("red5test", meta.get("title"));
        assertEquals((double) FIXTURE.length(), ((Number) meta.get("filesize")).doubleValue(), 0d);
    }

    private static String ffprobe() {
        for (String candidate : new String[] { "/usr/local/bin/ffprobe", "/usr/bin/ffprobe" }) {
            if (new File(candidate).canExecute()) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Runs ffprobe on the file; errors are printed at level error, so a clean parse leaves stderr empty.
     *
     * @return format and stream fields, keyed as ffprobe prints them (TAG:key for metadata)
     */
    private static Map<String, String> probe(File file) throws Exception {
        String ffprobe = ffprobe();
        assumeTrue("ffprobe is not installed", ffprobe != null);
        File err = File.createTempFile("ffprobe", ".err");
        try {
            Process process = new ProcessBuilder(ffprobe, "-v", "error", "-show_format", "-show_streams", file.getAbsolutePath()).redirectError(err).start();
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue("ffprobe did not finish", process.waitFor(30, TimeUnit.SECONDS));
            assertEquals(0, process.exitValue());
            String errors = Files.readString(err.toPath());
            assertEquals("ffprobe reported errors", "", errors);
            Map<String, String> fields = new LinkedHashMap<>();
            String section = "";
            for (String line : out.split("\n")) {
                if (line.startsWith("[") && !line.startsWith("[/")) {
                    section = line.substring(1, line.length() - 1);
                } else if (line.contains("=")) {
                    int eq = line.indexOf('=');
                    String key = line.substring(0, eq);
                    String value = line.substring(eq + 1);
                    if ("STREAM".equals(section) && "codec_name".equals(key)) {
                        fields.merge("codecs", value, (a, b) -> a + "," + b);
                    } else if ("FORMAT".equals(section)) {
                        fields.put(key, value);
                    }
                }
            }
            return fields;
        } finally {
            err.delete();
        }
    }

    /**
     * A recording: FLVWriter writes the tags and, when it is closed, an onMetaData of its own ahead of them. FFmpeg's metadata tag is written
     * through as a data tag.
     */
    @Test
    public void testFFprobeReadsRecordedMetaData() throws Exception {
        File out = tmp.newFile("recorded.flv");
        out.delete();
        FLVWriter writer = new FLVWriter(out, false);
        for (ITag tag : readAll(FIXTURE)) {
            assertTrue(writer.writeTag(tag));
        }
        writer.close();
        Map<String, Object> written = metaData(readAll(out).get(0));
        assertEquals("Red5", written.get("server"));
        assertNotNull(written.get("recordeddate"));
        assertEquals(Boolean.TRUE, written.get("canSeekToEnd"));
        double duration = ((Number) written.get("duration")).doubleValue();
        Map<String, String> probed = probe(out);
        assertEquals("h264,aac", probed.get("codecs"));
        assertEquals("Red5", probed.get("TAG:server"));
        assertEquals("true", probed.get("TAG:canSeekToEnd"));
        assertEquals(written.get("recordeddate"), probed.get("TAG:recordeddate"));
        assertEquals(duration, Double.parseDouble(probed.get("duration")), 0.1d);
    }

    /**
     * MetaService injects an onMetaData built from MetaData, without cue points: custom keys including a date, which FFmpeg turns into a UTC
     * time string, and a string after the date, which is only read if the date had the right size.
     */
    @Test
    public void testFFprobeReadsInjectedMetaData() throws Exception {
        File file = tmp.newFile("injected.flv");
        Files.copy(FIXTURE.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        MetaData<?, ?> meta = new MetaData<Object, Object>();
        meta.setWidth(160);
        meta.setHeight(120);
        meta.setFrameRate(10);
        meta.setCanSeekToEnd(true);
        meta.put("red5date", DATE);
        meta.put("red5string", "héllo");
        meta.put("red5number", 42d);
        meta.put("red5flag", Boolean.TRUE);
        Map<String, Object> nested = new HashMap<>();
        nested.put("inner", "value");
        meta.put("red5object", nested);
        MetaService service = new MetaService(file);
        service.write(meta);
        Map<String, String> probed = probe(file);
        assertEquals("h264,aac", probed.get("codecs"));
        // FFmpeg prints the date as UTC; the milliseconds show the double was read as written
        assertTrue(probed.get("TAG:red5date"), probed.get("TAG:red5date").startsWith("2023-11-14T22:13:20.123"));
        assertEquals("héllo", probed.get("TAG:red5string"));
        // FFmpeg prints numbers without decimals
        assertEquals("42", probed.get("TAG:red5number"));
        assertEquals("true", probed.get("TAG:red5flag"));
        assertEquals(meta.getDuration(), Double.parseDouble(probed.get("duration")), 0.1d);
        // and Red5 reads back what it injected
        Map<String, Object> injected = null;
        for (ITag tag : readAll(file)) {
            if (tag.getDataType() == IoConstants.TYPE_METADATA) {
                Map<String, Object> map = metaData(tag);
                if (map.containsKey("red5date")) {
                    injected = map;
                }
            }
        }
        assertNotNull(injected);
        assertEquals(DATE, injected.get("red5date"));
        assertEquals(nested, injected.get("red5object"));
    }

}
