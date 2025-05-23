package org.red5.io.obu;

/**
 * <p>OBPMetadataType class.</p>
 *
 * @author mondain
 */
public enum OBPMetadataType {

    HDR_CLL(1), HDR_MDCV(2), SCALABILITY(3), ITUT_T35(4), TIMECODE(5);

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
