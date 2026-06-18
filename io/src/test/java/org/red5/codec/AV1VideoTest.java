package org.red5.codec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;
import org.red5.io.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for AV1Video codec.
 *
 * Per E-RTMP v2 spec:
 * - AV1 uses OBUs (Open Bitstream Units), not NALUs
 * - AV1 does NOT use compositionTimeOffset (SI24) in CodedFrames
 * - CodedFramesX is not defined for AV1 in the spec (both carry raw OBU data)
 * - AV1 is the only codec that supports MPEG2TSSequenceStart
 * - AV1 is NOT in the compositionTime set
 */
public class AV1VideoTest {

    private static Logger log = LoggerFactory.getLogger(AV1VideoTest.class);

    // AV1 FourCC = "av01"
    private static final int AV1_FOURCC = IOUtils.makeFourcc("av01");

    // HEVC FourCC = "hvc1" (wrong codec, used for negative tests)
    private static final int HEVC_FOURCC = IOUtils.makeFourcc("hvc1");

    // Enhanced byte bit layout:
    //   bit 7 = enhanced flag
    //   bits 6-4 = frame type (3 bits)
    //   bits 3-0 = packet type (4 bits)
    //
    // Frame types: KEYFRAME=1, INTERFRAME=2
    // Packet types: SequenceStart=0, CodedFrames=1, SequenceEnd=2, CodedFramesX=3,
    //               MPEG2TSSequenceStart=5
    //
    // Enhanced keyframe + SequenceStart         = 1_001_0000 = 0x90
    // Enhanced keyframe + CodedFrames           = 1_001_0001 = 0x91
    // Enhanced keyframe + CodedFramesX          = 1_001_0011 = 0x93
    // Enhanced interframe + CodedFrames         = 1_010_0001 = 0xA1
    // Enhanced keyframe + MPEG2TSSequenceStart  = 1_001_0101 = 0x95

    @Test
    public void testCodecEnum() {
        log.info("testCodecEnum");
        VideoCodec av1 = VideoCodec.AV1;
        assertNotNull(av1);
        assertEquals((byte) 0x0d, av1.getId());
        assertEquals("av01", av1.getMimeType());
        assertEquals(AV1_FOURCC, av1.getFourcc());
    }

    @Test
    public void testAV1NotInCompositionTimeSet() {
        log.info("testAV1NotInCompositionTimeSet");
        // Per E-RTMP v2 spec, AV1 does NOT use compositionTimeOffset
        assertFalse("AV1 must NOT be in compositionTime set", VideoCodec.getCompositionTime().contains(VideoCodec.AV1));
        // AVC and HEVC should still be there
        assertTrue(VideoCodec.getCompositionTime().contains(VideoCodec.AVC));
        assertTrue(VideoCodec.getCompositionTime().contains(VideoCodec.HEVC));
    }

    @Test
    public void testCanHandleData() {
        log.info("testCanHandleData");
        IoBuffer data = IoBuffer.allocate(16);
        data.put((byte) 0x90); // enhanced + keyframe + SequenceStart
        data.putInt(AV1_FOURCC);
        data.put(new byte[] { 0x01, 0x02, 0x03, 0x04 });
        data.flip();

        AV1Video av1 = new AV1Video();
        assertTrue(av1.canHandleData(data));
        assertTrue(av1.isEnhanced());
    }

    @Test
    public void testCanHandleDataWrongFourcc() {
        log.info("testCanHandleDataWrongFourcc");
        IoBuffer data = IoBuffer.allocate(16);
        data.put((byte) 0x90);
        data.putInt(HEVC_FOURCC);
        data.put(new byte[] { 0x01, 0x02, 0x03, 0x04 });
        data.flip();

        AV1Video av1 = new AV1Video();
        assertFalse(av1.canHandleData(data));
    }

    /**
     * SequenceStart stores AV1CodecConfigurationRecord and exposes it via getDecoderConfiguration().
     */
    @Test
    public void testEnhancedSequenceStart() {
        log.info("testEnhancedSequenceStart");
        IoBuffer data = IoBuffer.allocate(32);
        data.put((byte) 0x90); // enhanced + keyframe + SequenceStart
        data.putInt(AV1_FOURCC);
        // Fake AV1CodecConfigurationRecord
        data.put(new byte[] { (byte) 0x81, 0x00, 0x0C, 0x00, 0x0A, 0x0F, 0x00, 0x00 });
        data.flip();

        AV1Video av1 = new AV1Video();
        assertTrue(av1.addData(data, 0));
        // Now that AV1Video implements IEnhancedRTMPVideoCodec and overrides getDecoderConfiguration(),
        // the config should be accessible
        assertNotNull("getDecoderConfiguration must return config after SequenceStart", av1.getDecoderConfiguration());
    }

    /**
     * CodedFrames for AV1: NO compositionTimeOffset is read -- body is raw OBU data.
     * This is the key behavioral difference from AVC/HEVC.
     */
    @Test
    public void testEnhancedCodedFramesNoCompTimeOffset() {
        log.info("testEnhancedCodedFramesNoCompTimeOffset");
        IoBuffer data = IoBuffer.allocate(32);
        data.put((byte) 0x91); // enhanced + keyframe + CodedFrames
        data.putInt(AV1_FOURCC);
        // Raw OBU data (NO SI24 composition time offset before this)
        data.put(new byte[] { 0x12, 0x00, 0x0A, 0x0E, 0x00, 0x00 });
        data.flip();

        AV1Video av1 = new AV1Video();
        assertTrue(av1.addData(data, 1000));
        assertEquals(VideoPacketType.CodedFrames, av1.getPacketType());
        assertEquals(VideoFrameType.KEYFRAME, av1.getFrameType());
        assertTrue(av1.getKeyframes().length > 0);
    }

    /**
     * CodedFramesX also carries raw OBU data (treated identically to CodedFrames for AV1).
     * The spec doesn't list AV1 under CodedFramesX, but we handle it for robustness.
     */
    @Test
    public void testEnhancedCodedFramesXKeyframe() {
        log.info("testEnhancedCodedFramesXKeyframe");
        IoBuffer data = IoBuffer.allocate(32);
        data.put((byte) 0x93); // enhanced + keyframe + CodedFramesX
        data.putInt(AV1_FOURCC);
        data.put(new byte[] { 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F });
        data.flip();

        AV1Video av1 = new AV1Video();
        assertTrue(av1.addData(data, 1000));
        assertTrue(av1.getKeyframes().length > 0);
    }

    /**
     * Interframe buffering works for AV1.
     */
    @Test
    public void testEnhancedCodedFramesInterframe() {
        log.info("testEnhancedCodedFramesInterframe");
        AV1Video av1 = new AV1Video();
        av1.setBufferInterframes(true);

        // Send a keyframe first
        IoBuffer keyframe = IoBuffer.allocate(32);
        keyframe.put((byte) 0x91); // keyframe + CodedFrames
        keyframe.putInt(AV1_FOURCC);
        keyframe.put(new byte[] { 0x01, 0x02, 0x03, 0x04 });
        keyframe.flip();
        assertTrue(av1.addData(keyframe, 1000));

        // Send an interframe
        IoBuffer interframe = IoBuffer.allocate(32);
        interframe.put((byte) 0xA1); // interframe + CodedFrames = 1_010_0001
        interframe.putInt(AV1_FOURCC);
        interframe.put(new byte[] { 0x11, 0x12, 0x13, 0x14 });
        interframe.flip();

        assertTrue(av1.addData(interframe, 1033));
        assertEquals(VideoFrameType.INTERFRAME, av1.getFrameType());
        assertTrue(av1.getNumInterframes() > 0);
    }

    /**
     * MPEG2TSSequenceStart stores AV1VideoDescriptor. AV1 is the only codec that supports this.
     */
    @Test
    public void testMPEG2TSSequenceStart() {
        log.info("testMPEG2TSSequenceStart");
        IoBuffer data = IoBuffer.allocate(32);
        data.put((byte) 0x95); // enhanced + keyframe + MPEG2TSSequenceStart(5) = 1_001_0101
        data.putInt(AV1_FOURCC);
        // Fake AV1VideoDescriptor
        data.put(new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05 });
        data.flip();

        AV1Video av1 = new AV1Video();
        assertTrue(av1.addData(data, 0));
        assertEquals(VideoPacketType.MPEG2TSSequenceStart, av1.getPacketType());
    }

    @Test
    public void testFourCcLookup() {
        log.info("testFourCcLookup");
        VideoCodec found = VideoCodec.valueOfByFourCc(AV1_FOURCC);
        assertNotNull(found);
        assertEquals(VideoCodec.AV1, found);
    }

    /**
     * Non-enhanced data: AV1 has no legacy RTMP codec ID, so non-enhanced is a no-op.
     */
    @Test
    public void testNonEnhanced() {
        log.info("testNonEnhanced");
        IoBuffer data = IoBuffer.allocate(16);
        data.put((byte) 0x1D); // non-enhanced, codec id 0x0D (AV1's byte id)
        data.put(new byte[] { 0x00, 0x01, 0x02, 0x03 });
        data.flip();

        AV1Video av1 = new AV1Video();
        assertTrue(av1.addData(data, 0));
        assertFalse(av1.isEnhanced());
        assertEquals(0, av1.getKeyframes().length);
    }

    @Test
    public void testCanDropFrames() {
        AV1Video av1 = new AV1Video();
        assertTrue(av1.canDropFrames());
    }

    @Test
    public void testImplementsEnhancedInterface() {
        AV1Video av1 = new AV1Video();
        assertTrue("AV1Video must implement IEnhancedRTMPVideoCodec", av1 instanceof IEnhancedRTMPVideoCodec);
    }
}
