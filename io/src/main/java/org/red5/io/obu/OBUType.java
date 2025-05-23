package org.red5.io.obu;

/**
 * <p>OBUType class.</p>
 *
 * @author mondain
 */
public enum OBUType {

    SEQUENCE_HEADER(1), TEMPORAL_DELIMITER(2), FRAME_HEADER(3), TILE_GROUP(4), METADATA(5), FRAME(6), REDUNDANT_FRAME_HEADER(7), TILE_LIST(8), PADDING(15);

    private final int value;

    OBUType(int value) {
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
     * @return a {@link org.red5.io.obu.OBUType} object
     */
    public static OBUType fromValue(int value) {
        for (OBUType type : OBUType.values()) {
            if (type.getValue() == value) {
                return type;
            }
        }
        return null;
    }

}
