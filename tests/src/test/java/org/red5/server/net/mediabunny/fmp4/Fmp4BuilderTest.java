package org.red5.server.net.mediabunny.fmp4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.junit.Test;

/**
 * Verifies the fMP4 builders used by MediaBunny. Golden values are the red5-moq-pkgr 1.3.11 builder output after the
 * post-processing MediaBunnyStreamListener used to apply (ftyp major brand set to iso6, leading styp removed), which is
 * what MediaBunny subscribers have been receiving; any change to them is a change on the wire.
 */
public class Fmp4BuilderTest {

    private static final String GOLDEN_INIT_SHA256 = "bb63ef95981fe3cc595c80a1a17a13e9bffb299f7ef78a3d3a0e33e0fd2bbf9c";

    private static final String GOLDEN_VIDEO_FRAGMENT = "000000886d6f6f66000000106d66686400000000000000070000007074726166000000107466686400020000000000010000001474666474010000000000000100000005000000447472756e01000f01000000030000009000000bb800000004020000000000177000000bb80000000301010000fffff44800000bb8000000020101000000000000000000116d646174808182838485868788";

    private static final String GOLDEN_AUDIO_FRAGMENT = "000000706d6f6f66000000106d666864000000000000000800000058747261660000001074666864000200000000000200000014746664740100000000000000000008000000002c7472756e0000070100000002000000780000040000000003020000000000040000000002020000000000000d6d646174a0a1a2a3a4";

    @Test
    public void testInitSegmentMatchesGolden() throws Exception {
        byte[] init = buildAudioVideoInit();
        assertEquals(1030, init.length);
        assertEquals(GOLDEN_INIT_SHA256, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(init)));
    }

    @Test
    public void testInitSegmentBrands() {
        byte[] init = buildAudioVideoInit();
        assertEquals("ftypiso6", new String(init, 4, 8, StandardCharsets.US_ASCII));
        assertEquals("cmfciso6", new String(init, 16, 8, StandardCharsets.US_ASCII));
    }

    @Test
    public void testFragmentsStartWithMoof() {
        assertEquals("moof", new String(buildVideoFragment(), 4, 4, StandardCharsets.US_ASCII));
        assertEquals("moof", new String(buildAudioFragment(), 4, 4, StandardCharsets.US_ASCII));
    }

    @Test
    public void testInitSegmentBoxTree() {
        List<String> boxes = new ArrayList<>();
        walk(ByteBuffer.wrap(buildAudioVideoInit()), "", boxes);
        List<String> track = List.of("tkhd", "mdia", "mdia/mdhd", "mdia/hdlr", "mdia/minf", "mdia/minf/%s", "mdia/minf/dinf", "mdia/minf/dinf/dref", "mdia/minf/stbl", "mdia/minf/stbl/stsd", "mdia/minf/stbl/stts", "mdia/minf/stbl/stsc", "mdia/minf/stbl/stsz", "mdia/minf/stbl/stco");
        List<String> expected = new ArrayList<>(List.of("ftyp", "moov", "moov/mvhd"));
        for (String mediaHeader : List.of("vmhd", "smhd")) {
            expected.add("moov/trak");
            for (String box : track) {
                expected.add("moov/trak/" + String.format(box, mediaHeader));
            }
        }
        expected.addAll(List.of("moov/mvex", "moov/mvex/trex", "moov/mvex/trex"));
        assertEquals(expected, boxes);
    }

    @Test
    public void testVideoOnlyInitNextTrackId() {
        byte[] init = new Fmp4InitSegmentBuilder().addVideoTrack(new Fmp4InitSegmentBuilder.VideoTrackConfig(3, 90000, "avc1", new byte[8], 640, 360)).build();
        ByteBuffer buffer = ByteBuffer.wrap(init);
        int mvhd = indexOf(init, "mvhd") - 4;
        // next_track_ID is the last field of the 108-byte version 0 mvhd
        assertEquals(4, buffer.getInt(mvhd + 108 - 4));
    }

    @Test(expected = IllegalStateException.class)
    public void testInitRequiresTrack() {
        new Fmp4InitSegmentBuilder().build();
    }

    @Test
    public void testVideoFragmentMatchesGolden() {
        assertEquals(GOLDEN_VIDEO_FRAGMENT, HexFormat.of().formatHex(buildVideoFragment()));
    }

    @Test
    public void testAudioFragmentMatchesGolden() {
        assertEquals(GOLDEN_AUDIO_FRAGMENT, HexFormat.of().formatHex(buildAudioFragment()));
    }

    @Test
    public void testDataOffsetPointsAtMdatPayload() {
        for (byte[] fragment : List.of(buildVideoFragment(), buildAudioFragment())) {
            ByteBuffer buffer = ByteBuffer.wrap(fragment);
            int moof = indexOf(fragment, "moof") - 4;
            int trun = indexOf(fragment, "trun") - 4;
            // trun: size, type, version/flags, sample_count, data_offset
            int dataOffset = buffer.getInt(trun + 16);
            int mdat = indexOf(fragment, "mdat") - 4;
            assertEquals(mdat + 8, moof + dataOffset);
        }
    }

    @Test
    public void testSignedCompositionOffsetsUseTrunVersion1() {
        byte[] fragment = buildVideoFragment();
        int trun = indexOf(fragment, "trun") - 4;
        assertEquals(1, fragment[trun + 8]);
        // without negative offsets the trun stays at version 0
        byte[] positive = new Fmp4FragmentBuilder().buildFragment(new Fmp4FragmentBuilder.FragmentConfig().setSequenceNumber(1).setTrackId(1).setMediaData(new byte[4]).setSamples(List.of(new Fmp4FragmentBuilder.SampleData(3000, 4, SampleFlags.createSyncSampleFlags(), 3000L))));
        assertEquals(0, positive[indexOf(positive, "trun") + 4]);
    }

    @Test
    public void testFragmentRequiresMediaAndSamples() {
        Fmp4FragmentBuilder builder = new Fmp4FragmentBuilder();
        try {
            builder.buildFragment(new Fmp4FragmentBuilder.FragmentConfig().setSamples(List.of(new Fmp4FragmentBuilder.SampleData(1, 1, SampleFlags.createSyncSampleFlags(), null))));
            fail("missing media data accepted");
        } catch (IllegalStateException expected) {
        }
        try {
            builder.buildFragment(new Fmp4FragmentBuilder.FragmentConfig().setMediaData(new byte[1]));
            fail("empty sample list accepted");
        } catch (IllegalStateException expected) {
        }
    }

    @Test
    public void testSampleFlags() {
        assertEquals(0x02000000, SampleFlags.createSyncSampleFlags().getFlags());
        assertEquals(0x01010000, SampleFlags.createNonSyncSampleFlags().getFlags());
    }

    private static byte[] buildAudioVideoInit() {
        return new Fmp4InitSegmentBuilder().addVideoTrack(new Fmp4InitSegmentBuilder.VideoTrackConfig(1, 90000, "avc1", sequence(12, 0x10), 1280, 720)).addAudioTrack(new Fmp4InitSegmentBuilder.AudioTrackConfig(2, 48000, "mp4a", sequence(6, 0x40), 2, 48000, 16)).build();
    }

    private static byte[] buildVideoFragment() {
        List<Fmp4FragmentBuilder.SampleData> samples = List.of(new Fmp4FragmentBuilder.SampleData(3000, 4, SampleFlags.createSyncSampleFlags(), 6000L), new Fmp4FragmentBuilder.SampleData(3000, 3, SampleFlags.createNonSyncSampleFlags(), -3000L), new Fmp4FragmentBuilder.SampleData(3000, 2, SampleFlags.createNonSyncSampleFlags(), 0L));
        // decode time above 32 bits exercises the version 1 tfdt
        return new Fmp4FragmentBuilder().buildFragment(new Fmp4FragmentBuilder.FragmentConfig().setSequenceNumber(7).setTrackId(1).setBaseDecodeTime(0x1_0000_0000L + 5).setMediaData(sequence(9, 0x80)).setSamples(samples));
    }

    private static byte[] buildAudioFragment() {
        List<Fmp4FragmentBuilder.SampleData> samples = List.of(new Fmp4FragmentBuilder.SampleData(1024, 3, SampleFlags.createSyncSampleFlags(), null), new Fmp4FragmentBuilder.SampleData(1024, 2, SampleFlags.createSyncSampleFlags(), null));
        return new Fmp4FragmentBuilder().buildFragment(new Fmp4FragmentBuilder.FragmentConfig().setSequenceNumber(8).setTrackId(2).setBaseDecodeTime(2048).setMediaData(sequence(5, 0xa0)).setSamples(samples));
    }

    private static byte[] sequence(int length, int start) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) (start + i);
        }
        return data;
    }

    /**
     * Walks container boxes recording each box path, and checks that every child fits its parent exactly.
     */
    private static void walk(ByteBuffer buffer, String parent, List<String> boxes) {
        while (buffer.hasRemaining()) {
            int start = buffer.position();
            int size = buffer.getInt();
            byte[] type = new byte[4];
            buffer.get(type);
            String path = parent + new String(type, StandardCharsets.US_ASCII);
            assertTrue(path + " overruns its parent", size >= 8 && start + size <= buffer.limit());
            boxes.add(path);
            if (List.of("moov", "trak", "mdia", "minf", "dinf", "stbl", "mvex").contains(path.substring(path.length() - 4))) {
                walk(buffer.slice(start + 8, size - 8), path + "/", boxes);
            }
            buffer.position(start + size);
        }
    }

    private static int indexOf(byte[] data, String type) {
        byte[] needle = type.getBytes(StandardCharsets.US_ASCII);
        outer: for (int i = 0; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        throw new AssertionError(type + " not found");
    }

}
