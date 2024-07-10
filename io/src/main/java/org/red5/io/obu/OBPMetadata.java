package org.red5.io.obu;

public class OBPMetadata {
    public OBPMetadataType metadataType;

    public MetadataItutT35 metadataItutT35;

    public MetadataHdrCll metadataHdrCll;

    public MetadataHdrMdcv metadataHdrMdcv;

    public MetadataScalability metadataScalability;

    public MetadataTimecode metadataTimecode;

    public Unregistered unregistered;

    public static class MetadataItutT35 {
        public byte ituTT35CountryCode;

        public byte ituTT35CountryCodeExtensionByte;

        public byte[] ituTT35PayloadBytes;

        public long ituTT35PayloadBytesSize;
    }

    public static class MetadataHdrCll {
        public short maxCll;

        public short maxFall;
    }

    public static class MetadataHdrMdcv {
        public short[] primaryChromaticityX = new short[3];

        public short[] primaryChromaticityY = new short[3];

        public short whitePointChromaticityX;

        public short whitePointChromaticityY;

        public long luminanceMax;

        public long luminanceMin;
    }

    public static class MetadataScalability {
        public byte scalabilityModeIdc;

        public ScalabilityStructure scalabilityStructure;

        public static class ScalabilityStructure {
            public byte spatialLayersCntMinus1;

            public boolean spatialLayerDimensionsPresentFlag;

            public boolean spatialLayerDescriptionPresentFlag;

            public boolean temporalGroupDescriptionPresentFlag;

            public byte scalabilityStructureReserved3bits;

            public short[] spatialLayerMaxWidth = new short[3];

            public short[] spatialLayerMaxHeight = new short[3];

            public byte[] spatialLayerRefId = new byte[3];

            public byte temporalGroupSize;

            public byte[] temporalGroupTemporalId = new byte[256];

            public boolean[] temporalGroupTemporalSwitchingUpPointFlag = new boolean[256];

            public boolean[] temporalGroupSpatialSwitchingUpPointFlag = new boolean[256];

            public byte[] temporalGroupRefCnt = new byte[256];

            public byte[][] temporalGroupRefPicDiff = new byte[256][8];
        }
    }

    public static class MetadataTimecode {
        public byte countingType;

        public boolean fullTimestampFlag;

        public boolean discontinuityFlag;

        public boolean cntDroppedFlag;

        public short nFrames;

        public byte secondsValue;

        public byte minutesValue;

        public byte hoursValue;

        public boolean secondsFlag;

        public boolean minutesFlag;

        public boolean hoursFlag;

        public byte timeOffsetLength;

        public long timeOffsetValue;
    }

    public static class Unregistered {
        public byte[] buf;

        public long bufSize;
    }
}
