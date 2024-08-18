package org.red5.io.obu;

public enum OBPMetadataType {

    HDR_CLL(1), HDR_MDCV(2), SCALABILITY(3), ITUT_T35(4), TIMECODE(5);

    private final int value;

    OBPMetadataType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static OBPMetadataType fromValue(int value) {
        for (OBPMetadataType type : OBPMetadataType.values()) {
            if (type.getValue() == value) {
                return type;
            }
        }
        return null;
    }

}
