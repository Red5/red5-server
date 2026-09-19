package org.red5.io.obu;

/**
 * <p>OBPMetadataType class.</p>
 *
 * @author mondain
 */
public enum OBPMetadataType {

    /** Content Light Level metadata (HDR CLL: MaxCLL / MaxFALL). */
    HDR_CLL(1),
    /** Mastering Display Color Volume metadata (HDR MDCV). */
    HDR_MDCV(2),
    /** Scalability structure metadata describing spatial/temporal layers. */
    SCALABILITY(3),
    /** ITU-T T.35 registered user data metadata. */
    ITUT_T35(4),
    /** Timecode metadata (SMPTE-style time-of-day/frame count). */
    TIMECODE(5);

    private final int value;

    OBPMetadataType(int value) {
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
     * @return a {@link org.red5.io.obu.OBPMetadataType} object
     */
    public static OBPMetadataType fromValue(int value) {
        for (OBPMetadataType type : OBPMetadataType.values()) {
            if (type.getValue() == value) {
                return type;
            }
        }
        return null;
    }

}
