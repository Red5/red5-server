package org.red5.io.obu;

/**
 * <p>OBPMatrixCoefficients class.</p>
 *
 * @author mondain
 */
public enum OBPMatrixCoefficients {
    /** AV1 {@code matrix_coefficients} value 0: the identity matrix, used with the {@code GBR} color format. */
    MC_IDENTITY(0),
    /** AV1 {@code matrix_coefficients} value 1: BT.709 matrix coefficients. */
    MC_BT_709(1),
    /** AV1 {@code matrix_coefficients} value 2: unspecified matrix coefficients. */
    MC_UNSPECIFIED(2),
    /** AV1 {@code matrix_coefficients} value 3: reserved for future use. */
    MC_RESERVED_3(3),
    /** AV1 {@code matrix_coefficients} value 4: US FCC Title 47 matrix coefficients. */
    MC_FCC(4),
    /** AV1 {@code matrix_coefficients} value 5: BT.470 System B, G (historical) matrix coefficients. */
    MC_BT_470_B_G(5),
    /** AV1 {@code matrix_coefficients} value 6: BT.601 matrix coefficients. */
    MC_BT_601(6),
    /** AV1 {@code matrix_coefficients} value 7: SMPTE 240M matrix coefficients. */
    MC_SMPTE_240(7),
    /** AV1 {@code matrix_coefficients} value 8: YCgCo matrix coefficients. */
    MC_SMPTE_YCGCO(8),
    /** AV1 {@code matrix_coefficients} value 9: BT.2020 non-constant luminance matrix coefficients. */
    MC_BT_2020_NCL(9),
    /** AV1 {@code matrix_coefficients} value 10: BT.2020 constant luminance matrix coefficients. */
    MC_BT_2020_CL(10),
    /** AV1 {@code matrix_coefficients} value 11: SMPTE ST 2085 (Y'D'zD'x) matrix coefficients. */
    MC_SMPTE_2085(11),
    /** AV1 {@code matrix_coefficients} value 12: chromaticity-derived non-constant luminance matrix coefficients. */
    MC_CHROMAT_NCL(12),
    /** AV1 {@code matrix_coefficients} value 13: chromaticity-derived constant luminance matrix coefficients. */
    MC_CHROMAT_CL(13),
    /** AV1 {@code matrix_coefficients} value 14: ICtCp matrix coefficients (BT.2100). */
    MC_ICTCP(14);

    private final int value;

    OBPMatrixCoefficients(int value) {
        this.value = value;
    }

    /**
     * <p>Getter for the field <code>value</code>.</p>
     *
     * @return a int
     */
    public int getValue() {
        return value;
    }

    /**
     * <p>fromValue.</p>
     *
     * @param value a int
     * @return a {@link org.red5.io.obu.OBPMatrixCoefficients} object
     */
    public static OBPMatrixCoefficients fromValue(int value) {
        for (OBPMatrixCoefficients type : OBPMatrixCoefficients.values()) {
            if (type.getValue() == value) {
                return type;
            }
        }
        return null;
    }
}
