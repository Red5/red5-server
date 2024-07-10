package org.red5.io.obu;

public class OBPState {

    public OBPFrameHeader prev;

    public boolean prevFilled;

    public int frameHeaderEndPos;

    public OBPFrameType[] refFrameType = new OBPFrameType[8];

    public byte[] refValid = new byte[8];

    public byte[] refOrderHint = new byte[8];

    public byte[] orderHint = new byte[8];

    public int[] refFrameId = new int[OBPConstants.NUM_REF_FRAMES];

    public long[] refUpscaledWidth = new long[8];

    public long[] refFrameHeight = new long[8];

    public long[] refRenderWidth = new long[8];

    public long[] refRenderHeight = new long[8];

    public int[] refFrameSignBias = new int[8];

    public OBPFilmGrainParameters[] refGrainParams = new OBPFilmGrainParameters[8];

    public int[][][] savedGmParams;

    public byte[][] savedLoopFilterRefDeltas;

    public byte[][] savedLoopFilterModeDeltas;

    public OBPFrameHeader.SegmentationParams[] savedSegmentationParams;

    public boolean[][][] savedFeatureEnabled;

    public short[][][] savedFeatureData;

}