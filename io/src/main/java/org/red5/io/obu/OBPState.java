package org.red5.io.obu;

/**
 * <p>OBPState class.</p>
 *
 * @author mondain
 */
public class OBPState {

    /** The most recently decoded AV1 frame header, used as the source for delta-coded parameters of subsequent frames. */
    public OBPFrameHeader prev;

    /** Whether {@link #prev} has been populated with a decoded frame header yet. */
    public boolean prevFilled;

    /** Byte offset within the OBU at which parsing of the uncompressed frame header ended, marking the start of the tile group data. */
    public int frameHeaderEndPos;

    /** AV1 {@code RefFrameType}: the frame type (key, inter, intra-only or switch) of the frame stored in each of the 8 reference frame slots. */
    public OBPFrameType[] refFrameType = new OBPFrameType[8];

    /** AV1 {@code RefValid}: whether each of the 8 reference frame slots currently holds a valid decoded frame. */
    public byte[] refValid = new byte[8];

    /** AV1 {@code RefOrderHint}: the order hint recorded for the frame stored in each of the 8 reference frame slots. */
    public byte[] refOrderHint = new byte[8];

    /** AV1 {@code OrderHint} values saved per reference frame slot, used to compute relative frame ordering distances. */
    public byte[] orderHint = new byte[8];

    /** AV1 {@code RefFrameId}: the {@code current_frame_id} of the frame stored in each of the {@link OBPConstants#NUM_REF_FRAMES} reference frame slots. */
    public int[] refFrameId = new int[OBPConstants.NUM_REF_FRAMES];

    /** AV1 {@code RefUpscaledWidth}: the upscaled reconstructed frame width, in pixels, stored in each of the 8 reference frame slots. */
    public long[] refUpscaledWidth = new long[8];

    /** AV1 {@code RefFrameHeight}: the reconstructed frame height, in pixels, stored in each of the 8 reference frame slots. */
    public long[] refFrameHeight = new long[8];

    /** AV1 {@code RefRenderWidth}: the render (display) width, in pixels, stored in each of the 8 reference frame slots. */
    public long[] refRenderWidth = new long[8];

    /** AV1 {@code RefRenderHeight}: the render (display) height, in pixels, stored in each of the 8 reference frame slots. */
    public long[] refRenderHeight = new long[8];

    /** AV1 {@code RefFrameSignBias}: the sign bias used for compound prediction, per reference frame slot. */
    public int[] refFrameSignBias = new int[8];

    /** Film grain synthesis parameters saved per reference frame slot, for frames that reuse a previous frame's film grain parameters. */
    public OBPFilmGrainParameters[] refGrainParams = new OBPFilmGrainParameters[8];

    /** AV1 {@code SavedGmParams}: global motion parameters saved per reference frame slot, indexed by reference and parameter index. */
    public int[][][] savedGmParams;

    /** AV1 {@code SavedLoopFilterRefDeltas}: loop filter reference deltas saved per reference frame slot. */
    public byte[][] savedLoopFilterRefDeltas;

    /** AV1 {@code SavedLoopFilterModeDeltas}: loop filter mode deltas saved per reference frame slot. */
    public byte[][] savedLoopFilterModeDeltas;

    /** AV1 {@code SavedSegmentationParams}: segmentation parameters saved per reference frame slot. */
    public OBPFrameHeader.SegmentationParams[] savedSegmentationParams;

    /** AV1 {@code SavedFeatureEnabled}: per reference frame slot, per segment, per feature flags indicating whether a segmentation feature is enabled. */
    public boolean[][][] savedFeatureEnabled;

    /** AV1 {@code SavedFeatureData}: per reference frame slot, per segment, per feature data values for enabled segmentation features. */
    public short[][][] savedFeatureData;

}
