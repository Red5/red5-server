package org.red5.codec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.red5.io.ITag;
import org.red5.io.IoConstants;
import org.red5.io.flv.impl.FLVReader;

/**
 * Enhanced RTMP video metadata (VideoPacketType.Metadata) written by an independent AMF0 encoder, FFmpeg's FLV muxer, read by
 * AbstractVideo.
 * <p>
 * fixtures/ffmpeg_hevc_hdr.flv (4.5 KB) was made with FFmpeg N-121974 by: {@code ffmpeg -f lavfi -i testsrc=d=0.5:size=64x64:rate=10 -c:v
 * libx265 -preset ultrafast -x265-params log-level=error:keyint=5 -pix_fmt yuv420p10le -color_primaries bt2020 -color_trc smpte2084
 * -colorspace bt2020nc -fflags +bitexact -flags:v +bitexact ffmpeg_hevc_hdr.flv}. Its muxer writes a metadata packet holding
 * ["colorInfo", {colorConfig: {matrixCoefficients: 9}}].
 */
public class VideoMetadataInteropTest {

    private static final File FIXTURE = new File("target/test-classes/fixtures/ffmpeg_hevc_hdr.flv");

    @Test
    @SuppressWarnings("unchecked")
    public void testColorInfoFromFFmpeg() throws Exception {
        assertTrue("Missing fixture: " + FIXTURE, FIXTURE.isFile());
        List<Map<String, Object>> received = new ArrayList<>();
        HEVCVideo codec = new HEVCVideo() {
            @Override
            public void onVideoMetadata(Map<String, Object> metainfos) {
                received.add(metainfos);
            }
        };
        FLVReader reader = new FLVReader(FIXTURE);
        try {
            while (reader.hasMoreTags()) {
                ITag tag = reader.readTag();
                // the reader returns null for tags it holds back, such as frames before a codec configuration
                if (tag != null && tag.getDataType() == IoConstants.TYPE_VIDEO) {
                    codec.addData(tag.getBody().duplicate(), tag.getTimestamp());
                }
            }
        } finally {
            reader.close();
        }
        assertEquals(String.valueOf(received), 1, received.size());
        Map<String, Object> colorInfo = (Map<String, Object>) received.get(0).get("colorInfo");
        assertNotNull(String.valueOf(received), colorInfo);
        Map<String, Object> colorConfig = (Map<String, Object>) colorInfo.get("colorConfig");
        assertEquals(9.0d, ((Number) colorConfig.get("matrixCoefficients")).doubleValue(), 0d);
    }

}
