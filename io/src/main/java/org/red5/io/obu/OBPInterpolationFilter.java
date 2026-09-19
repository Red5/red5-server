package org.red5.io.obu;

/**
 * <p>OBPInterpolationFilter class.</p>
 *
 * @author mondain
 */
public enum OBPInterpolationFilter {
    /** Regular eight-tap sub-pixel interpolation filter, per the AV1 bitstream specification. */
    EIGHTTAP,
    /** Eight-tap sub-pixel interpolation filter with a smooth frequency response, per the AV1 bitstream specification. */
    EIGHTTAP_SMOOTH,
    /** Eight-tap sub-pixel interpolation filter with a sharp frequency response, per the AV1 bitstream specification. */
    EIGHTTAP_SHARP,
    /** Bilinear sub-pixel interpolation filter, per the AV1 bitstream specification. */
    BILINEAR,
    /** Indicates the interpolation filter is signaled per block rather than fixed for the frame, per the AV1 bitstream specification. */
    SWITCHABLE
}
