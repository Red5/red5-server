package org.red5.io.obu;

/**
 * <p>OBPFilmGrainParameters class.</p>
 *
 * @author mondain
 */
public class OBPFilmGrainParameters {
    /** AV1 {@code apply_grain}: whether film grain synthesis should be applied to this frame. */
    public boolean applyGrain;

    /** AV1 {@code grain_seed}: 16-bit seed used to initialize the film grain random number generator. */
    public short grainSeed;

    /** AV1 {@code update_grain}: whether new grain parameters are provided, as opposed to reusing a reference set. */
    public boolean updateGrain;

    /** AV1 {@code film_grain_params_ref_idx}: index of the reference frame whose film grain parameters should be reused. */
    public byte filmGrainParamsRefIdx;

    /** AV1 {@code num_y_points}: number of points in the luma (Y) scaling function, 0-14. */
    public byte numYPoints;

    /** AV1 {@code point_y_value}: x-coordinates (pixel values) of the luma scaling function points. */
    public byte[] pointYValue = new byte[16];

    /** AV1 {@code point_y_scaling}: scaling values corresponding to each {@link #pointYValue} entry. */
    public byte[] pointYScaling = new byte[16];

    /** AV1 {@code chroma_scaling_from_luma}: whether the chroma scaling functions are derived from the luma one. */
    public boolean chromaScalingFromLuma;

    /** AV1 {@code num_cb_points}: number of points in the Cb chroma scaling function, 0-10. */
    public byte numCbPoints;

    /** AV1 {@code point_cb_value}: x-coordinates (pixel values) of the Cb chroma scaling function points. */
    public byte[] pointCbValue = new byte[16];

    /** AV1 {@code point_cb_scaling}: scaling values corresponding to each {@link #pointCbValue} entry. */
    public byte[] pointCbScaling = new byte[16];

    /** AV1 {@code num_cr_points}: number of points in the Cr chroma scaling function, 0-10. */
    public byte numCrPoints;

    /** AV1 {@code point_cr_value}: x-coordinates (pixel values) of the Cr chroma scaling function points. */
    public byte[] pointCrValue = new byte[16];

    /** AV1 {@code point_cr_scaling}: scaling values corresponding to each {@link #pointCrValue} entry. */
    public byte[] pointCrScaling = new byte[16];

    /** AV1 {@code grain_scaling_minus_8}: precision of the scaling functions, minus 8. */
    public byte grainScalingMinus8;

    /** AV1 {@code ar_coeff_lag}: number of auto-regressive coefficients used for luma and chroma. */
    public byte arCoeffLag;

    /** AV1 {@code ar_coeffs_y_plus_128}: luma auto-regressive coefficients, each biased by +128. */
    public byte[] arCoeffsYPlus128 = new byte[24];

    /** AV1 {@code ar_coeffs_cb_plus_128}: Cb chroma auto-regressive coefficients, each biased by +128. */
    public byte[] arCoeffsCbPlus128 = new byte[25];

    /** AV1 {@code ar_coeffs_cr_plus_128}: Cr chroma auto-regressive coefficients, each biased by +128. */
    public byte[] arCoeffsCrPlus128 = new byte[25];

    /** AV1 {@code ar_coeff_shift_minus_6}: right shift applied to the auto-regressive coefficients, minus 6. */
    public byte arCoeffShiftMinus6;

    /** AV1 {@code grain_scale_shift}: downscaling factor applied to the generated grain before it is added to the image. */
    public byte grainScaleShift;

    /** AV1 {@code cb_mult}: multiplier for the Cb component when generating chroma noise. */
    public byte cbMult;

    /** AV1 {@code cb_luma_mult}: multiplier for the luma component when generating Cb chroma noise. */
    public byte cbLumaMult;

    /** AV1 {@code cb_offset}: offset used when generating Cb chroma noise. */
    public short cbOffset;

    /** AV1 {@code cr_mult}: multiplier for the Cr component when generating chroma noise. */
    public byte crMult;

    /** AV1 {@code cr_luma_mult}: multiplier for the luma component when generating Cr chroma noise. */
    public byte crLumaMult;

    /** AV1 {@code cr_offset}: offset used when generating Cr chroma noise. */
    public short crOffset;

    /** AV1 {@code overlap_flag}: whether overlap blending should be applied between film-grain blocks. */
    public boolean overlapFlag;

    /** AV1 {@code clip_to_restricted_range}: whether output samples should be clipped to studio swing (restricted) range. */
    public boolean clipToRestrictedRange;
}
