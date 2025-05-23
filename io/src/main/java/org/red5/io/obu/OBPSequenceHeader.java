package org.red5.io.obu;

/**
 * <p>OBPSequenceHeader class.</p>
 *
 * @author mondain
 */
public class OBPSequenceHeader {
    public byte seqProfile;

    public boolean stillPicture;

    public boolean reducedStillPictureHeader;

    public boolean timingInfoPresentFlag;

    public TimingInfo timingInfo;

    public boolean decoderModelInfoPresentFlag;

    public DecoderModelInfo decoderModelInfo;

    public boolean initialDisplayDelayPresentFlag;

    public byte operatingPointsCntMinus1;

    public byte[] operatingPointIdc = new byte[32];

    public byte[] seqLevelIdx = new byte[32];

    public byte[] seqTier = new byte[32];

    public boolean[] decoderModelPresentForThisOp = new boolean[32];

    public OperatingParametersInfo[] operatingParametersInfo = new OperatingParametersInfo[32];

    public boolean[] initialDisplayDelayPresentForThisOp = new boolean[32];

    public byte[] initialDisplayDelayMinus1 = new byte[32];

    public byte frameWidthBitsMinus1;

    public byte frameHeightBitsMinus1;

    public int maxFrameWidthMinus1;

    public int maxFrameHeightMinus1;

    public boolean frameIdNumbersPresentFlag;

    public byte deltaFrameIdLengthMinus2;

    public byte additionalFrameIdLengthMinus1;

    public boolean use128x128Superblock;

    public boolean enableFilterIntra;

    public boolean enableIntraEdgeFilter;

    public boolean enableInterintraCompound;

    public boolean enableMaskedCompound;

    public boolean enableWarpedMotion;

    public boolean enableDualFilter;

    public boolean enableOrderHint;

    public boolean enableJntComp;

    public boolean enableRefFrameMvs;

    public boolean seqChooseScreenContentTools;

    public int seqForceScreenContentTools;

    public boolean seqChooseIntegerMv;

    public int seqForceIntegerMv;

    public byte orderHintBitsMinus1;

    public byte OrderHintBits;

    public boolean enableSuperres;

    public boolean enableCdef;

    public boolean enableRestoration;

    public ColorConfig colorConfig;

    public boolean filmGrainParamsPresent;

    public static class TimingInfo {
        public long numUnitsInDisplayTick;

        public long timeScale;

        public boolean equalPictureInterval;

        public long numTicksPerPictureMinus1;
    }

    public static class DecoderModelInfo {
        public byte bufferDelayLengthMinus1;

        public long numUnitsInDecodingTick;

        public byte bufferRemovalTimeLengthMinus1;

        public byte framePresentationTimeLengthMinus1;
    }

    public static class OperatingParametersInfo {
        public long decoderBufferDelay;

        public long encoderBufferDelay;

        public boolean lowDelayModeFlag;
    }

    public static class ColorConfig {
        public boolean highBitdepth;

        public boolean twelveBit;

        public byte BitDepth;

        public boolean monoChrome;

        public byte NumPlanes;

        public boolean colorDescriptionPresentFlag;

        public OBPColorPrimaries colorPrimaries;

        public OBPTransferCharacteristics transferCharacteristics;

        public OBPMatrixCoefficients matrixCoefficients;

        public boolean colorRange;

        public boolean subsamplingX;

        public boolean subsamplingY;

        public OBPChromaSamplePosition chromaSamplePosition;

        public boolean separateUvDeltaQ;
    }
}
