package org.red5.io.obu;

/**
 * <p>OBPFrameType class.</p>
 *
 * @author mondain
 */
public enum OBPFrameType {

    /** AV1 {@code frame_type} value 0: KEY_FRAME, a frame that can be decoded without reference to any other frame. */
    KEYFRAME(0),
    /** AV1 {@code frame_type} value 1: INTER_FRAME, a frame predicted using one or more reference frames. */
    INTERFRAME(1),
    /** AV1 {@code frame_type} value 2: INTRA_ONLY_FRAME, a non-key frame coded entirely with intra prediction. */
    INTRA_ONLY_FRAME(2),
    /** AV1 {@code frame_type} value 3: SWITCH_FRAME, a frame enabling switching between coded streams. */
    SWITCH_FRAME(3);

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
