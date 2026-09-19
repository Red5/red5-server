package org.red5.io.obu;

/**
 * Parsed contents of an AV1 metadata OBU (metadata_obu), as defined by the AV1 bitstream
 * specification. Only the fields corresponding to {@link #metadataType} are populated.
 *
 * @author mondain
 */
public class OBPMetadata {
    /** Identifies which of the metadata payload fields below is populated. */
    public OBPMetadataType metadataType;

    /** Populated when {@link #metadataType} is {@link OBPMetadataType#ITUT_T35}. */
    public MetadataItutT35 metadataItutT35;

    /** Populated when {@link #metadataType} is {@link OBPMetadataType#HDR_CLL}. */
    public MetadataHdrCll metadataHdrCll;

    /** Populated when {@link #metadataType} is {@link OBPMetadataType#HDR_MDCV}. */
    public MetadataHdrMdcv metadataHdrMdcv;

    /** Populated when {@link #metadataType} is {@link OBPMetadataType#SCALABILITY}. */
    public MetadataScalability metadataScalability;

    /** Populated when {@link #metadataType} is {@link OBPMetadataType#TIMECODE}. */
    public MetadataTimecode metadataTimecode;

    /** Populated when {@link #metadataType} does not correspond to a known/registered type. */
    public Unregistered unregistered;

    /**
     * ITU-T T.35 registered user data metadata (itut_t35 syntax).
     */
    public static class MetadataItutT35 {
        /** ITU-T T.35 terminal provider (country) code, itu_t_t35_country_code. */
        public byte ituTT35CountryCode;

        /** ITU-T T.35 country code extension byte, present when the country code is 0xFF. */
        public byte ituTT35CountryCodeExtensionByte;

        /** Raw payload bytes following the country code(s). */
        public byte[] ituTT35PayloadBytes;

        /** Number of valid bytes in {@link #ituTT35PayloadBytes}. */
        public long ituTT35PayloadBytesSize;
    }

    /**
     * HDR Content Light Level metadata (metadata_hdr_cl, as defined by CTA-861.3).
     */
    public static class MetadataHdrCll {
        /** Maximum Content Light Level, in candelas per square meter. */
        public short maxCll;

        /** Maximum Frame-Average Light Level, in candelas per square meter. */
        public short maxFall;
    }

    /**
     * HDR Mastering Display Color Volume metadata (metadata_hdr_mdcv, as defined by CTA-861.3).
     */
    public static class MetadataHdrMdcv {
        /** Mastering display primaries' x chromaticity coordinates, one per color primary (in 0.00002 units). */
        public short[] primaryChromaticityX = new short[3];

        /** Mastering display primaries' y chromaticity coordinates, one per color primary (in 0.00002 units). */
        public short[] primaryChromaticityY = new short[3];

        /** Mastering display white point x chromaticity coordinate (in 0.00002 units). */
        public short whitePointChromaticityX;

        /** Mastering display white point y chromaticity coordinate (in 0.00002 units). */
        public short whitePointChromaticityY;

        /** Nominal maximum display luminance of the mastering display, in units of 0.0001 candelas per square meter. */
        public long luminanceMax;

        /** Nominal minimum display luminance of the mastering display, in units of 0.0001 candelas per square meter. */
        public long luminanceMin;
    }

    /**
     * Scalability metadata describing the spatial/temporal layer structure of the bitstream
     * (metadata_scalability).
     */
    public static class MetadataScalability {
        /** Scalability mode identifier, scalability_mode_idc. */
        public byte scalabilityModeIdc;

        /** Detailed layer structure, present when scalabilityModeIdc indicates SCALABILITY_SS. */
        public ScalabilityStructure scalabilityStructure;

        /**
         * Detailed spatial/temporal scalability structure (scalability_structure).
         */
        public static class ScalabilityStructure {
            /** Number of spatial layers minus 1. */
            public byte spatialLayersCntMinus1;

            /** Whether per-layer maximum width/height dimensions are present. */
            public boolean spatialLayerDimensionsPresentFlag;

            /** Whether per-layer reference id descriptions are present. */
            public boolean spatialLayerDescriptionPresentFlag;

            /** Whether the temporal group description is present. */
            public boolean temporalGroupDescriptionPresentFlag;

            /** Reserved bits following the flags above, must be ignored by decoders. */
            public byte scalabilityStructureReserved3bits;

            /** Maximum frame width of each spatial layer, in luma samples, indexed by layer. */
            public short[] spatialLayerMaxWidth = new short[3];

            /** Maximum frame height of each spatial layer, in luma samples, indexed by layer. */
            public short[] spatialLayerMaxHeight = new short[3];

            /** Reference id assigned to each spatial layer. */
            public byte[] spatialLayerRefId = new byte[3];

            /** Number of pictures in the temporal group. */
            public byte temporalGroupSize;

            /** Temporal id of each picture in the temporal group. */
            public byte[] temporalGroupTemporalId = new byte[256];

            /** Whether each picture in the temporal group is a temporal switching-up point. */
            public boolean[] temporalGroupTemporalSwitchingUpPointFlag = new boolean[256];

            /** Whether each picture in the temporal group is a spatial switching-up point. */
            public boolean[] temporalGroupSpatialSwitchingUpPointFlag = new boolean[256];

            /** Number of reference pictures used by each picture in the temporal group. */
            public byte[] temporalGroupRefCnt = new byte[256];

            /** Difference between the picture index and the index of each referenced picture, per picture and reference. */
            public byte[][] temporalGroupRefPicDiff = new byte[256][8];
        }
    }

    /**
     * Timecode metadata describing the presentation time of day for a frame (metadata_timecode).
     */
    public static class MetadataTimecode {
        /** Frame counting method used, counting_type. */
        public byte countingType;

        /** Whether all of the hours, minutes and seconds fields are present. */
        public boolean fullTimestampFlag;

        /** Whether there is a discontinuity in the timecode relative to the previous frame. */
        public boolean discontinuityFlag;

        /** Whether frame numbers have been dropped to maintain synchronization (drop-frame timecode). */
        public boolean cntDroppedFlag;

        /** Frame number within the current second, n_frames. */
        public short nFrames;

        /** Seconds value of the timecode, 0-59. */
        public byte secondsValue;

        /** Minutes value of the timecode, 0-59. */
        public byte minutesValue;

        /** Hours value of the timecode, 0-23. */
        public byte hoursValue;

        /** Whether {@link #secondsValue} is present, used when fullTimestampFlag is false. */
        public boolean secondsFlag;

        /** Whether {@link #minutesValue} is present, used when fullTimestampFlag is false. */
        public boolean minutesFlag;

        /** Whether {@link #hoursValue} is present, used when fullTimestampFlag is false. */
        public boolean hoursFlag;

        /** Number of bits used to encode {@link #timeOffsetValue}. */
        public byte timeOffsetLength;

        /** Time offset value, in units defined by the timing info, used for sub-frame timing precision. */
        public long timeOffsetValue;
    }

    /**
     * Payload for an unregistered/unrecognized metadata type.
     */
    public static class Unregistered {
        /** Raw, unparsed metadata payload bytes. */
        public byte[] buf;

        /** Number of valid bytes in {@link #buf}. */
        public long bufSize;
    }
}
