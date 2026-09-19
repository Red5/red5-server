package org.red5.io.obu;

/**
 * <p>OBPFrameHeader class.</p>
 *
 * @author mondain
 */
public class OBPFrameHeader {

    /** show_existing_frame: when true the frame header only asks to output a previously decoded frame from the reference slot given by {@link #frameToShowMapIdx}. */
    public boolean showExistingFrame;

    /** frame_to_show_map_idx: index (0-7) of the reference frame buffer to output when {@link #showExistingFrame} is set. */
    public byte frameToShowMapIdx;

    /** temporal_point_info(): presentation timing parsed when the sequence header enables the decoder model with equal picture intervals disabled. */
    public TemporalPointInfo temporalPointInfo;

    /** display_frame_id: frame id expected in the shown existing frame when frame id numbers are present. */
    public long displayFrameId;

    /** frame_type: KEY, INTER, INTRA_ONLY or SWITCH frame type of this frame. */
    public OBPFrameType frameType;

    /** show_frame: whether this frame is output immediately after decoding. */
    public boolean showFrame;

    /** showable_frame: whether the frame may be output later via show_existing_frame. */
    public boolean showableFrame;

    /** error_resilient_mode: when true the frame can be decoded without state from prior frames (forced on key and switch frames). */
    public boolean errorResilientMode;

    /** disable_cdf_update: when true the CDF (symbol probability) tables are not adapted during decoding. */
    public boolean disableCdfUpdate;

    /** allow_screen_content_tools: whether palette and intra block copy screen content coding tools may be used. */
    public boolean allowScreenContentTools;

    /** force_integer_mv: whether motion vectors are constrained to whole-pel precision. */
    public boolean forceIntegerMv;

    /** current_frame_id: frame id of this frame, present when frame_id_numbers_present_flag is set in the sequence header. */
    public int currentFrameId;

    /** frame_size_override_flag: whether frame_width_minus_1 / frame_height_minus_1 are coded explicitly instead of taken from the sequence header. */
    public boolean frameSizeOverrideFlag;

    /** order_hint: OrderHintBits-wide value giving the frame's position in output order. */
    public byte orderHint;

    /** primary_ref_frame: reference slot (0-6) supplying CDFs and loop filter deltas, or 7 (PRIMARY_REF_NONE) for defaults. */
    public byte primaryRefFrame;

    /** buffer_removal_time_present_flag: whether buffer_removal_time values are coded for the operating points. */
    public boolean bufferRemovalTimePresentFlag;

    /** buffer_removal_time[opNum]: decoder buffer removal time per operating point, in clock ticks. */
    public long[] bufferRemovalTime = new long[32];

    /** refresh_frame_flags: 8-bit mask of reference frame slots that this frame overwrites once decoded. */
    public byte refreshFrameFlags;

    /** ref_order_hint[i]: order hint signalled for each of the 8 reference slots in error resilient mode. */
    public byte[] refOrderHint = new byte[8];

    /** frame_width_minus_1: coded frame width minus one, in luma samples. */
    public int frameWidthMinus1;

    /** frame_height_minus_1: coded frame height minus one, in luma samples. */
    public int frameHeightMinus1;

    /** FrameWidth: downscaled coded frame width in luma samples (after superres). */
    public int frameWidth;

    /** FrameHeight: coded frame height in luma samples. */
    public int frameHeight;

    /** UpscaledWidth: frame width in luma samples before superres downscaling. */
    public int upscaledWidth;

    /** render_and_frame_size_different: whether explicit render dimensions follow. */
    public boolean renderAndFrameSizeDifferent;

    /** render_width_minus_1: intended render width minus one, in luma samples. */
    public int renderWidthMinus1;

    /** render_height_minus_1: intended render height minus one, in luma samples. */
    public int renderHeightMinus1;

    /** RenderWidth: intended display width of the frame in luma samples. */
    public int renderWidth;

    /** RenderHeight: intended display height of the frame in luma samples. */
    public int renderHeight;

    /** superres_params(): horizontal super-resolution scaling parameters. */
    public SuperresParams superresParams = new SuperresParams();

    /** allow_intrabc: whether intra block copy may be used in this frame. */
    public boolean allowIntrabc;

    /** frame_refs_short_signaling: whether the reference slots are derived from last_frame_idx and gold_frame_idx rather than coded individually. */
    public boolean frameRefsShortSignaling;

    /** last_frame_idx: reference slot (0-7) used for the LAST reference under short signaling. */
    public byte lastFrameIdx;

    /** gold_frame_idx: reference slot (0-7) used for the GOLDEN reference under short signaling. */
    public byte goldFrameIdx;

    /** ref_frame_idx[i]: reference slot (0-7) assigned to each of the REFS_PER_FRAME references. */
    public byte[] refFrameIdx = new byte[OBPConstants.REFS_PER_FRAME];

    /** delta_frame_id_minus_1[i]: distance minus one between the current frame id and each reference's expected frame id. */
    public byte[] deltaFrameIdMinus1 = new byte[7];

    /** found_ref: whether a reference frame's size was adopted via frame_size_with_refs(). */
    public boolean foundRef;

    /** allow_high_precision_mv: whether motion vectors use 1/8-pel precision rather than 1/4-pel. */
    public boolean allowHighPrecisionMv;

    /** interpolation_filter(): the inter prediction interpolation filter selection. */
    public InterpolationFilter interpolationFilter = new InterpolationFilter();

    /** is_motion_mode_switchable: whether motion mode (simple, OBMC, warped) may be chosen per block. */
    public boolean isMotionModeSwitchable;

    /** use_ref_frame_mvs: whether motion vectors from reference frames are used as motion field projections. */
    public boolean useRefFrameMvs;

    /** disable_frame_end_update_cdf: whether the CDFs are left unchanged at the end of the frame instead of being saved from the largest tile. */
    public boolean disableFrameEndUpdateCdf;

    /** tile_info(): tile layout of the frame. */
    public TileInfo tileInfo;

    /** quantization_params(): base and delta quantizer indices and quantizer matrix selection. */
    public QuantizationParams quantizationParams;

    /** segmentation_params(): segmentation map and per-segment feature data. */
    public SegmentationParams segmentationParams;

    /** delta_q_params(): block-level quantizer index delta signalling. */
    public DeltaQParams deltaQParams;

    /** delta_lf_params(): block-level loop filter delta signalling. */
    public DeltaLfParams deltaLfParams;

    /** loop_filter_params(): deblocking loop filter levels and deltas. */
    public LoopFilterParams loopFilterParams;

    /** cdef_params(): constrained directional enhancement filter strengths. */
    public CdefParams cdefParams;

    /** lr_params(): loop restoration types and unit sizes. */
    public LrParams lrParams;

    /** skip_mode_present: whether skip mode (compound prediction from two derived references) is available. */
    public boolean skipModePresent;

    /** reference_select: whether blocks may use compound (two reference) prediction. */
    public boolean referenceSelect;

    /** allow_warped_motion: whether the local warped motion mode may be used. */
    public boolean allowWarpedMotion;

    /** reduced_tx_set: whether the reduced set of transform types is used. */
    public boolean reducedTxSet;

    /** global_motion_params(): per-reference global motion models. */
    public GlobalMotionParams globalMotionParams;

    /** film_grain_params(): film grain synthesis parameters, when show_frame or showable_frame is set and film grain is enabled. */
    public OBPFilmGrainParameters filmGrainParams;

    /** MiCols: frame width in 4x4 mode-info units, computed as {@code 2 * ((FrameWidth + 7) >> 3)}. */
    public int miCols, miRows;

    /** CodedLossless: true when every segment has a zero quantizer index (lossless coding); AllLossless additionally requires superres to be off. */
    public boolean codedLossless, allLossless;

    /** reference_select as read from the bitstream for inter frames (false for intra frames); drives skip mode and compound reference selection. */
    public boolean referenceSelectInter;

    /** tx_mode_select: whether the transform size is chosen per block (TX_MODE_SELECT) rather than always the largest. */
    public boolean txModeSelect;

    /** TxMode: the resolved transform mode (ONLY_4X4 when coded lossless, otherwise LARGEST or SELECT). */
    public OBPTxMode txMode;

    /** Tile layout parsed from tile_info() in the frame header. */
    public static class TileInfo {
        /** uniform_tile_spacing_flag: whether tiles are uniformly sized (log2 counts coded) or explicitly sized. */
        public boolean uniformTileSpacingFlag;

        /** TileCols: number of tile columns in the frame. */
        public int tileCols;

        /** TileRows: number of tile rows in the frame. */
        public int tileRows;

        /** TileColsLog2: base-2 logarithm of the tile column count. */
        public int tileColsLog2;

        /** TileRowsLog2: base-2 logarithm of the tile row count. */
        public int tileRowsLog2;

        /** context_update_tile_id: tile whose final CDFs are saved for use by later frames (present when more than one tile). */
        public int contextUpdateTileId;

        /** tile_size_bytes_minus_1: number of bytes minus one used to code each tile size in the tile group. */
        public int tileSizeBytesMinus1;
    }

    /** Presentation timing parsed from temporal_point_info(). */
    public static class TemporalPointInfo {
        /** frame_presentation_time: presentation time of the frame, in clock ticks, coded with frame_presentation_time_length_minus_1 + 1 bits. */
        public long framePresentationTime;
    }

    /** Horizontal super-resolution parameters parsed from superres_params(). */
    public static class SuperresParams {
        /** use_superres: whether the frame is coded at reduced width and upscaled horizontally. */
        public boolean useSuperres;

        /** coded_denom: 3-bit coded value from which the superres denominator is derived (SuperresDenom = coded_denom + 9). */
        public byte codedDenom;

        /** SuperresDenom: denominator of the superres scale factor (8 when superres is off, 9-16 otherwise). */
        public int superresDenom;
    }

    /** Interpolation filter selection parsed from read_interpolation_filter(). */
    public static class InterpolationFilter {

        /** is_filter_switchable: whether the interpolation filter is chosen per block instead of frame-wide. */
        public boolean isFilterSwitchable;

        /** interpolation_filter: frame-level interpolation filter used when the filter is not switchable. */
        public OBPInterpolationFilter interpolationFilter;
    }

    /** Quantizer parameters parsed from quantization_params(). */
    public static class QuantizationParams {
        /** base_q_idx: 8-bit base quantizer index (0-255) for the frame. */
        public int baseQIdx;

        /** DeltaQYDc: signed delta applied to the luma DC quantizer index. */
        public int deltaQYDc;

        /** diff_uv_delta: whether the V plane has its own DC/AC deltas rather than sharing the U plane values. */
        public boolean diffUvDelta;

        /** DeltaQUDc: signed delta applied to the U plane DC quantizer index. */
        public int deltaQUDc;

        /** DeltaQUAc: signed delta applied to the U plane AC quantizer index. */
        public int deltaQUAc;

        /** DeltaQVDc: signed delta applied to the V plane DC quantizer index. */
        public int deltaQVDc;

        /** DeltaQVAc: signed delta applied to the V plane AC quantizer index. */
        public int deltaQVAc;

        /** using_qmatrix: whether quantizer matrices are applied. */
        public boolean usingQmatrix;

        /** qm_y: quantizer matrix level (0-15) for the luma plane. */
        public int qmY;

        /** qm_u: quantizer matrix level (0-15) for the U plane. */
        public int qmU;

        /** qm_v: quantizer matrix level (0-15) for the V plane. */
        public int qmV;
    }

    /** Segmentation parameters parsed from segmentation_params(). */
    public static class SegmentationParams {
        /** segmentation_enabled: whether the frame uses segmentation. */
        public boolean segmentationEnabled;

        /** segmentation_update_map: whether the segmentation map is coded for this frame. */
        public boolean segmentationUpdateMap;

        /** segmentation_temporal_update: whether segment ids are predicted from the previous frame's map. */
        public boolean segmentationTemporalUpdate;

        /** segmentation_update_data: whether per-segment feature data is coded in this frame. */
        public boolean segmentationUpdateData;

        /** FeatureEnabled[segment][feature]: whether each of the 8 features is enabled for each of the 8 segments. */
        public boolean[][] featureEnabled;

        /** FeatureData[segment][feature]: signed feature value for each enabled segment feature, clipped to the feature's allowed range. */
        public short[][] featureData;

        /** Creates the parameter holder with the 8x8 feature enabled and feature data tables allocated. */
        public SegmentationParams() {
            featureEnabled = new boolean[8][8];
            featureData = new short[8][8];
        }
    }

    /** Quantizer index delta parameters parsed from delta_q_params(). */
    public static class DeltaQParams {
        /** delta_q_present: whether per-superblock quantizer index deltas are coded. */
        public boolean deltaQPresent;

        /** delta_q_res: 2-bit log2 resolution of the quantizer index deltas. */
        public byte deltaQRes;
    }

    /** Loop filter delta parameters parsed from delta_lf_params(). */
    public static class DeltaLfParams {
        /** delta_lf_present: whether per-superblock loop filter level deltas are coded. */
        public boolean deltaLfPresent;

        /** delta_lf_res: 2-bit log2 resolution of the loop filter deltas. */
        public byte deltaLfRes;

        /** delta_lf_multi: whether separate loop filter deltas are coded for each filter (vertical/horizontal luma, U, V). */
        public boolean deltaLfMulti;
    }

    /** Deblocking loop filter parameters parsed from loop_filter_params(). */
    public static class LoopFilterParams {
        /** loop_filter_level[i]: 6-bit filter levels for luma vertical, luma horizontal, U and V edges (0-63). */
        public byte[] loopFilterLevel = new byte[4];

        /** loop_filter_sharpness: 3-bit sharpness level (0-7) controlling the filter thresholds. */
        public byte loopFilterSharpness;

        /** loop_filter_delta_enabled: whether reference frame and mode based filter level deltas are applied. */
        public boolean loopFilterDeltaEnabled;

        /** loop_filter_delta_update: whether new delta values are coded in this frame header. */
        public boolean loopFilterDeltaUpdate;

        /** update_ref_delta[i]: whether a new loop_filter_ref_deltas value is coded for each of the 8 reference frame types. */
        public boolean[] updateRefDelta = new boolean[8];

        /** loop_filter_ref_deltas[i]: signed filter level delta per reference frame type (INTRA_FRAME through ALTREF_FRAME). */
        public byte[] loopFilterRefDeltas = new byte[8];

        /** update_mode_delta[i]: whether a new loop_filter_mode_deltas value is coded for each of the 2 mode types (only the first two entries are used). */
        public boolean[] updateModeDelta = new boolean[8];

        /** loop_filter_mode_deltas[i]: signed filter level delta per prediction mode class (only the first two entries are used). */
        public byte[] loopFilterModeDeltas = new byte[8];
    }

    /** Constrained directional enhancement filter parameters parsed from cdef_params(). */
    public static class CdefParams {
        /** cdef_damping_minus_3: damping strength minus three (CdefDamping = cdef_damping_minus_3 + 3, range 3-6). */
        public byte cdefDampingMinus3;

        /** cdef_bits: number of bits (0-3) used to signal the CDEF strength index per 64x64 block; {@code 1 << cdef_bits} strength pairs follow. */
        public byte cdefBits;

        /** cdef_y_pri_strength[i]: 4-bit luma primary filter strength for each of the coded strength pairs. */
        public byte[] cdefYPriStrength = new byte[8];

        /** cdef_y_sec_strength[i]: 2-bit luma secondary filter strength for each strength pair (a coded value of 3 is remapped to 4). */
        public byte[] cdefYSecStrength = new byte[8];

        /** cdef_uv_pri_strength[i]: 4-bit chroma primary filter strength for each strength pair (absent for monochrome). */
        public byte[] cdefUvPriStrength = new byte[8];

        /** cdef_uv_sec_strength[i]: 2-bit chroma secondary filter strength for each strength pair (a coded value of 3 is remapped to 4). */
        public byte[] cdefUvSecStrength = new byte[8];
    }

    /** Loop restoration parameters parsed from lr_params(). */
    public static class LrParams {
        /** lr_type[plane]: 2-bit restoration type per plane (Y, U, V); 0 means no restoration for that plane. */
        public byte[] lrType = new byte[3];

        /** lr_unit_shift: log2 of the luma restoration unit size relative to 64 samples (0-2). */
        public byte lrUnitShift;

        /** lr_uv_shift: whether chroma restoration units are half the luma unit size (only for subsampled chroma). */
        public boolean lrUvShift;
    }

    /** Global motion parameters parsed from global_motion_params(). */
    public static class GlobalMotionParams {
        /** GmType[ref]: global motion model per reference frame: 0 IDENTITY, 1 TRANSLATION, 2 ROTZOOM, 3 AFFINE. */
        public byte[] gmType = new byte[8];

        /** gm_params[ref][idx]: the 6 warp model parameters for each reference frame, in WARPEDMODEL_PREC_BITS fixed point. */
        public int[][] gmParams = new int[8][6];

        /** PrevGmParams[ref][idx]: warp parameters inherited from the primary reference frame (or defaults), used to predict gm_params. */
        public int[][] prevGmParams = new int[8][6];
    }

}
