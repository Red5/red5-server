package org.red5.io.obu;

/**
 * <p>OBPSequenceHeader class.</p>
 *
 * @author mondain
 */
public class OBPSequenceHeader {
    /** AV1 {@code seq_profile}: specifies the features that can be used in the coded video sequence. */
    public byte seqProfile;

    /** AV1 {@code still_picture}: equal to 1 specifies that the coded video sequence contains only one coded frame. */
    public boolean stillPicture;

    /** AV1 {@code reduced_still_picture_header}: equal to 1 specifies that the syntax elements not needed for a still picture are omitted from the sequence header. */
    public boolean reducedStillPictureHeader;

    /** AV1 {@code timing_info_present_flag}: equal to 1 indicates that {@code timing_info} is present in the sequence header. */
    public boolean timingInfoPresentFlag;

    /** The {@code timing_info()} structure, present when {@link #timingInfoPresentFlag} is set. */
    public TimingInfo timingInfo;

    /** AV1 {@code decoder_model_info_present_flag}: equal to 1 indicates that {@code decoder_model_info} is present in the sequence header. */
    public boolean decoderModelInfoPresentFlag;

    /** The {@code decoder_model_info()} structure, present when {@link #decoderModelInfoPresentFlag} is set. */
    public DecoderModelInfo decoderModelInfo;

    /** AV1 {@code initial_display_delay_present_flag}: equal to 1 indicates that initial display delay information is present for each operating point. */
    public boolean initialDisplayDelayPresentFlag;

    /** AV1 {@code operating_points_cnt_minus_1}: plus 1 specifies the number of operating points present in this sequence header. */
    public byte operatingPointsCntMinus1;

    /** AV1 {@code operating_point_idc[i]}: bitmask indicating which spatial and temporal layers should be decoded for operating point {@code i}. */
    public byte[] operatingPointIdc = new byte[32];

    /** AV1 {@code seq_level_idx[i]}: specifies the level that the coded video sequence conforms to for operating point {@code i}. */
    public byte[] seqLevelIdx = new byte[32];

    /** AV1 {@code seq_tier[i]}: specifies the tier that the coded video sequence conforms to for operating point {@code i}. */
    public byte[] seqTier = new byte[32];

    /** AV1 {@code decoder_model_present_for_this_op[i]}: equal to 1 indicates decoder model information is present for operating point {@code i}. */
    public boolean[] decoderModelPresentForThisOp = new boolean[32];

    /** AV1 {@code operating_parameters_info()} structures, one per operating point. */
    public OperatingParametersInfo[] operatingParametersInfo = new OperatingParametersInfo[32];

    /** AV1 {@code initial_display_delay_present_for_this_op[i]}: equal to 1 indicates an initial display delay is present for operating point {@code i}. */
    public boolean[] initialDisplayDelayPresentForThisOp = new boolean[32];

    /** AV1 {@code initial_display_delay_minus_1[i]}: plus 1 specifies the number of decoded frames that must be buffered before display for operating point {@code i}. */
    public byte[] initialDisplayDelayMinus1 = new byte[32];

    /** AV1 {@code frame_width_bits_minus_1}: plus 1 specifies the number of bits used for {@link #maxFrameWidthMinus1}. */
    public byte frameWidthBitsMinus1;

    /** AV1 {@code frame_height_bits_minus_1}: plus 1 specifies the number of bits used for {@link #maxFrameHeightMinus1}. */
    public byte frameHeightBitsMinus1;

    /** AV1 {@code max_frame_width_minus_1}: plus 1 specifies the maximum frame width, in luma samples, for the coded video sequence. */
    public int maxFrameWidthMinus1;

    /** AV1 {@code max_frame_height_minus_1}: plus 1 specifies the maximum frame height, in luma samples, for the coded video sequence. */
    public int maxFrameHeightMinus1;

    /** AV1 {@code frame_id_numbers_present_flag}: equal to 1 indicates that frame id numbers are present in the coded video sequence. */
    public boolean frameIdNumbersPresentFlag;

    /** AV1 {@code delta_frame_id_length_minus_2}: used, together with {@link #additionalFrameIdLengthMinus1}, to compute the number of bits used to encode {@code delta_frame_id}. */
    public byte deltaFrameIdLengthMinus2;

    /** AV1 {@code additional_frame_id_length_minus_1}: plus 1, together with the delta frame id length, specifies the number of bits used for the {@code idLen} frame id field. */
    public byte additionalFrameIdLengthMinus1;

    /** AV1 {@code use_128x128_superblock}: equal to 1 indicates superblocks are 128x128 pixels, 0 indicates they are 64x64 pixels. */
    public boolean use128x128Superblock;

    /** AV1 {@code enable_filter_intra}: equal to 1 specifies that the {@code use_filter_intra} syntax element may be present. */
    public boolean enableFilterIntra;

    /** AV1 {@code enable_intra_edge_filter}: equal to 1 specifies that the intra edge filtering process may be used. */
    public boolean enableIntraEdgeFilter;

    /** AV1 {@code enable_interintra_compound}: equal to 1 specifies that the inter-intra compound prediction mode may be used. */
    public boolean enableInterintraCompound;

    /** AV1 {@code enable_masked_compound}: equal to 1 specifies that mask-based compound prediction may be used. */
    public boolean enableMaskedCompound;

    /** AV1 {@code enable_warped_motion}: equal to 1 indicates that warped motion compensation may be used. */
    public boolean enableWarpedMotion;

    /** AV1 {@code enable_dual_filter}: equal to 1 indicates that the inter prediction filter type may be specified independently in the horizontal and vertical directions. */
    public boolean enableDualFilter;

    /** AV1 {@code enable_order_hint}: equal to 1 indicates that tools that use the order hints may be used, including joint compound mode and motion field motion vector prediction. */
    public boolean enableOrderHint;

    /** AV1 {@code enable_jnt_comp}: equal to 1 indicates that distance weights process may be used for compound prediction. */
    public boolean enableJntComp;

    /** AV1 {@code enable_ref_frame_mvs}: equal to 1 indicates that the use of motion field estimation may be enabled at the frame level. */
    public boolean enableRefFrameMvs;

    /** AV1 {@code seq_choose_screen_content_tools}: equal to 1 indicates that {@code SelectScreenContentTools} should be derived rather than read from {@link #seqForceScreenContentTools}. */
    public boolean seqChooseScreenContentTools;

    /** AV1 {@code seq_force_screen_content_tools}: value used to derive whether {@code allow_screen_content_tools} is present in the frame header when it is not chosen automatically. */
    public int seqForceScreenContentTools;

    /** AV1 {@code seq_choose_integer_mv}: equal to 1 indicates that {@code SelectIntegerMv} should be derived rather than read from {@link #seqForceIntegerMv}. */
    public boolean seqChooseIntegerMv;

    /** AV1 {@code seq_force_integer_mv}: value used to derive whether {@code force_integer_mv} is present in the frame header when it is not chosen automatically. */
    public int seqForceIntegerMv;

    /** AV1 {@code order_hint_bits_minus_1}: plus 1 specifies the number of bits used for the {@code order_hint} field of each frame header. */
    public byte orderHintBitsMinus1;

    /** Derived variable {@code OrderHintBits}: number of bits used to code order hints, or 0 if {@link #enableOrderHint} is false. */
    public byte OrderHintBits;

    /** AV1 {@code enable_superres}: equal to 1 specifies that the use_superres syntax element may be present, allowing superresolution upscaling. */
    public boolean enableSuperres;

    /** AV1 {@code enable_cdef}: equal to 1 indicates that constrained directional enhancement filtering may be enabled. */
    public boolean enableCdef;

    /** AV1 {@code enable_restoration}: equal to 1 indicates that loop restoration filtering may be enabled. */
    public boolean enableRestoration;

    /** The {@code color_config()} structure describing the bit depth, chroma subsampling and color characteristics of the decoded pictures. */
    public ColorConfig colorConfig;

    /** AV1 {@code film_grain_params_present}: equal to 1 specifies that film grain parameters may be present in frames in the coded video sequence. */
    public boolean filmGrainParamsPresent;

    /** AV1 {@code timing_info()} structure describing the clock used to time the display of decoded pictures. */
    public static class TimingInfo {
        /** AV1 {@code num_units_in_display_tick}: number of time units of a clock operating at {@link #timeScale} Hz corresponding to one increment of a clock tick counter. */
        public long numUnitsInDisplayTick;

        /** AV1 {@code time_scale}: number of time units that pass in one second. */
        public long timeScale;

        /** AV1 {@code equal_picture_interval}: equal to 1 indicates that pictures should be displayed according to a constant tick interval. */
        public boolean equalPictureInterval;

        /** AV1 {@code num_ticks_per_picture_minus_1}: plus 1 specifies the number of clock ticks corresponding to time elapsed between two consecutive pictures. */
        public long numTicksPerPictureMinus1;
    }

    /** AV1 {@code decoder_model_info()} structure providing information about the operation of the resource availability mode. */
    public static class DecoderModelInfo {
        /** AV1 {@code buffer_delay_length_minus_1}: plus 1 specifies the bit length used to encode {@code decoder_buffer_delay} and {@code encoder_buffer_delay}. */
        public byte bufferDelayLengthMinus1;

        /** AV1 {@code num_units_in_decoding_tick}: number of time units of a decoding clock operating at the frequency used to measure buffer removal and presentation times. */
        public long numUnitsInDecodingTick;

        /** AV1 {@code buffer_removal_time_length_minus_1}: plus 1 specifies the bit length used to encode {@code buffer_removal_time}. */
        public byte bufferRemovalTimeLengthMinus1;

        /** AV1 {@code frame_presentation_time_length_minus_1}: plus 1 specifies the bit length used to encode {@code frame_presentation_time}. */
        public byte framePresentationTimeLengthMinus1;
    }

    /** AV1 {@code operating_parameters_info()} structure describing the smoothing buffer behaviour for one operating point. */
    public static class OperatingParametersInfo {
        /** AV1 {@code decoder_buffer_delay[op]}: used to compute the initial buffer removal time for operating point {@code op}, in units of the decoding clock. */
        public long decoderBufferDelay;

        /** AV1 {@code encoder_buffer_delay[op]}: used to compute the initial buffer arrival time for operating point {@code op}, in units of the decoding clock. */
        public long encoderBufferDelay;

        /** AV1 {@code low_delay_mode_flag[op]}: equal to 1 signals that the smoothing buffer for operating point {@code op} operates in low delay mode. */
        public boolean lowDelayModeFlag;
    }

    /** AV1 {@code color_config()} structure describing the bit depth, chroma subsampling and color characteristics of the decoded pictures. */
    public static class ColorConfig {
        /** AV1 {@code high_bitdepth}: together with {@link #twelveBit} and the sequence profile, determines {@link #BitDepth}. */
        public boolean highBitdepth;

        /** AV1 {@code twelve_bit}: equal to 1 indicates, for profile 2 with {@link #highBitdepth} set, that {@link #BitDepth} is 12. */
        public boolean twelveBit;

        /** Derived variable {@code BitDepth}: bit depth used for the luma and chroma samples. */
        public byte BitDepth;

        /** AV1 {@code mono_chrome}: equal to 1 indicates that the video does not contain chroma sampling (luma only). */
        public boolean monoChrome;

        /** Derived variable {@code NumPlanes}: number of color planes, 1 when {@link #monoChrome} is set, otherwise 3. */
        public byte NumPlanes;

        /** AV1 {@code color_description_present_flag}: equal to 1 indicates that {@link #colorPrimaries}, {@link #transferCharacteristics} and {@link #matrixCoefficients} are present. */
        public boolean colorDescriptionPresentFlag;

        /** AV1 {@code color_primaries}: chromaticity coordinates of the source color primaries. */
        public OBPColorPrimaries colorPrimaries;

        /** AV1 {@code transfer_characteristics}: opto-electronic transfer characteristic of the source picture. */
        public OBPTransferCharacteristics transferCharacteristics;

        /** AV1 {@code matrix_coefficients}: matrix coefficients used in deriving luma and chroma signals from the color primaries. */
        public OBPMatrixCoefficients matrixCoefficients;

        /** AV1 {@code color_range}: 0 indicates studio swing representation, 1 indicates full swing representation. */
        public boolean colorRange;

        /** AV1 {@code subsampling_x}: horizontal chroma subsampling flag. */
        public boolean subsamplingX;

        /** AV1 {@code subsampling_y}: vertical chroma subsampling flag. */
        public boolean subsamplingY;

        /** AV1 {@code chroma_sample_position}: sample position for subsampled chroma when {@link #subsamplingX} and {@link #subsamplingY} are both set. */
        public OBPChromaSamplePosition chromaSamplePosition;

        /** AV1 {@code separate_uv_delta_q}: equal to 1 indicates that the U and V planes may use separate delta quantizer values. */
        public boolean separateUvDeltaQ;
    }
}
