package org.red5.io.obu;

public class OBPFrameHeader {

    public boolean showExistingFrame;

    public byte frameToShowMapIdx;

    public TemporalPointInfo temporalPointInfo;

    public long displayFrameId;

    public OBPFrameType frameType;

    public boolean showFrame;

    public boolean showableFrame;

    public boolean errorResilientMode;

    public boolean disableCdfUpdate;

    public boolean allowScreenContentTools;

    public boolean forceIntegerMv;

    public int currentFrameId;

    public boolean frameSizeOverrideFlag;

    public byte orderHint;

    public byte primaryRefFrame;

    public boolean bufferRemovalTimePresentFlag;

    public long[] bufferRemovalTime = new long[32];

    public byte refreshFrameFlags;

    public byte[] refOrderHint = new byte[8];

    public int frameWidthMinus1;

    public int frameHeightMinus1;

    public int frameWidth;

    public int frameHeight;

    public int upscaledWidth;

    public boolean renderAndFrameSizeDifferent;

    public int renderWidthMinus1;

    public int renderHeightMinus1;

    public int renderWidth;

    public int renderHeight;

    public SuperresParams superresParams = new SuperresParams();

    public boolean allowIntrabc;

    public boolean frameRefsShortSignaling;

    public byte lastFrameIdx;

    public byte goldFrameIdx;

    public byte[] refFrameIdx = new byte[OBPConstants.REFS_PER_FRAME];

    public byte[] deltaFrameIdMinus1 = new byte[7];

    public boolean foundRef;

    public boolean allowHighPrecisionMv;

    public InterpolationFilter interpolationFilter = new InterpolationFilter();

    public boolean isMotionModeSwitchable;

    public boolean useRefFrameMvs;

    public boolean disableFrameEndUpdateCdf;

    public TileInfo tileInfo;

    public QuantizationParams quantizationParams;

    public SegmentationParams segmentationParams;

    public DeltaQParams deltaQParams;

    public DeltaLfParams deltaLfParams;

    public LoopFilterParams loopFilterParams;

    public CdefParams cdefParams;

    public LrParams lrParams;

    public boolean skipModePresent;

    public boolean referenceSelect;

    public boolean allowWarpedMotion;

    public boolean reducedTxSet;

    public GlobalMotionParams globalMotionParams;

    public OBPFilmGrainParameters filmGrainParams;

    public int miCols, miRows;

    public boolean codedLossless, allLossless;

    public boolean referenceSelectInter;

    public boolean txModeSelect;

    public OBPTxMode txMode;

    public static class TileInfo {
        public boolean uniformTileSpacingFlag;

        public int tileCols;

        public int tileRows;

        public int tileColsLog2;

        public int tileRowsLog2;

        public int contextUpdateTileId;

        public int tileSizeBytesMinus1;
    }

    public static class TemporalPointInfo {
        public long framePresentationTime;
    }

    public static class SuperresParams {
        public boolean useSuperres;

        public byte codedDenom;

        public int superresDenom;
    }

    public static class InterpolationFilter {

        public boolean isFilterSwitchable;

        public OBPInterpolationFilter interpolationFilter;
    }

    public static class QuantizationParams {
        public int baseQIdx;

        public int deltaQYDc;

        public boolean diffUvDelta;

        public int deltaQUDc;

        public int deltaQUAc;

        public int deltaQVDc;

        public int deltaQVAc;

        public boolean usingQmatrix;

        public int qmY;

        public int qmU;

        public int qmV;
    }

    public static class SegmentationParams {
        public boolean segmentationEnabled;

        public boolean segmentationUpdateMap;

        public boolean segmentationTemporalUpdate;

        public boolean segmentationUpdateData;

        public boolean[][] featureEnabled;

        public short[][] featureData;

        public SegmentationParams() {
            featureEnabled = new boolean[8][8];
            featureData = new short[8][8];
        }
    }

    public static class DeltaQParams {
        public boolean deltaQPresent;

        public byte deltaQRes;
    }

    public static class DeltaLfParams {
        public boolean deltaLfPresent;

        public byte deltaLfRes;

        public boolean deltaLfMulti;
    }

    public static class LoopFilterParams {
        public byte[] loopFilterLevel = new byte[4];

        public byte loopFilterSharpness;

        public boolean loopFilterDeltaEnabled;

        public boolean loopFilterDeltaUpdate;

        public boolean[] updateRefDelta = new boolean[8];

        public byte[] loopFilterRefDeltas = new byte[8];

        public boolean[] updateModeDelta = new boolean[8];

        public byte[] loopFilterModeDeltas = new byte[8];
    }

    public static class CdefParams {
        public byte cdefDampingMinus3;

        public byte cdefBits;

        public byte[] cdefYPriStrength = new byte[8];

        public byte[] cdefYSecStrength = new byte[8];

        public byte[] cdefUvPriStrength = new byte[8];

        public byte[] cdefUvSecStrength = new byte[8];
    }

    public static class LrParams {
        public byte[] lrType = new byte[3];

        public byte lrUnitShift;

        public boolean lrUvShift;
    }

    public static class GlobalMotionParams {
        public byte[] gmType = new byte[8];

        public int[][] gmParams = new int[8][6];

        public int[][] prevGmParams = new int[8][6];
    }

}
