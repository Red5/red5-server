package org.red5.io.obu;

/**
 * <p>OBPFrameType class.</p>
 *
 * @author mondain
 */
public enum OBPFrameType {

    KEYFRAME(0), INTERFRAME(1), INTRA_ONLY_FRAME(2), SWITCH_FRAME(3);

    private final int value;

    OBPFrameType(int value) {
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
     * @return a {@link org.red5.io.obu.OBPFrameType} object
     */
    public static OBPFrameType fromValue(int value) {
        for (OBPFrameType type : OBPFrameType.values()) {
            if (type.getValue() == value) {
                return type;
            }
        }
        return null;
    }
}
