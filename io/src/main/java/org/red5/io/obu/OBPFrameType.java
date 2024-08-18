package org.red5.io.obu;

public enum OBPFrameType {

    KEYFRAME(0), INTERFRAME(1), INTRA_ONLY_FRAME(2), SWITCH_FRAME(3);

    private final int value;

    OBPFrameType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static OBPFrameType fromValue(int value) {
        for (OBPFrameType type : OBPFrameType.values()) {
            if (type.getValue() == value) {
                return type;
            }
        }
        return null;
    }
}
