package org.red5.io.obu;

/**
 * <p>OBPColorPrimaries class.</p>
 *
 * @author mondain
 */
public enum OBPColorPrimaries {
    /** BT.709 color primaries. */
    CP_BT_709(1),
    /** Color primaries are unspecified and must be determined by external means. */
    CP_UNSPECIFIED(2),
    /** BT.470 System M (historical) color primaries. */
    CP_BT_470_M(4),
    /** BT.470 System B, G (historical) color primaries. */
    CP_BT_470_B_G(5),
    /** BT.601 color primaries. */
    CP_BT_601(6),
    /** SMPTE 240M color primaries. */
    CP_SMPTE_240(7),
    /** Generic film color primaries. */
    CP_GENERIC_FILM(8),
    /** BT.2020 / BT.2100 wide color gamut primaries. */
    CP_BT_2020(9),
    /** SMPTE ST 428-1 CIE 1931 XYZ color primaries. */
    CP_XYZ(10),
    /** SMPTE RP 431-2 (DCI-P3) color primaries. */
    CP_SMPTE_431(11),
    /** SMPTE EG 432-1 (Display P3) color primaries. */
    CP_SMPTE_432(12),
    /** EBU Tech. 3213-E color primaries. */
    CP_EBU_3213(22);

    private final int value;

    OBPColorPrimaries(int value) {
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
     * @return a {@link org.red5.io.obu.OBPColorPrimaries} object
     */
    public static OBPColorPrimaries fromValue(int value) {
        for (OBPColorPrimaries type : OBPColorPrimaries.values()) {
            if (type.getValue() == value) {
                return type;
            }
        }
        return null;
    }
}
