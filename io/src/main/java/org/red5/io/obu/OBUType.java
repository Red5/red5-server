package org.red5.io.obu;

public enum OBUType {

    SEQUENCE_HEADER(1), TEMPORAL_DELIMITER(2), FRAME_HEADER(3), TILE_GROUP(4), METADATA(5), FRAME(6), REDUNDANT_FRAME_HEADER(7), TILE_LIST(8), PADDING(15);

    private final int value;

    OBUType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static OBUType fromValue(int value) {
        for (OBUType type : OBUType.values()) {
            if (type.getValue() == value) {
                return type;
            }
        }
        return null;
    }

}