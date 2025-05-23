package org.red5.io.obu;

/**
 * <p>OBPMatrixCoefficients class.</p>
 *
 * @author mondain
 */
public enum OBPMatrixCoefficients {
    MC_IDENTITY(0), MC_BT_709(1), MC_UNSPECIFIED(2), MC_RESERVED_3(3), MC_FCC(4), MC_BT_470_B_G(5), MC_BT_601(6), MC_SMPTE_240(7), MC_SMPTE_YCGCO(8), MC_BT_2020_NCL(9), MC_BT_2020_CL(10), MC_SMPTE_2085(11), MC_CHROMAT_NCL(12), MC_CHROMAT_CL(13), MC_ICTCP(14);

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
