package org.red5.io.obu;

public enum OBPOBUType {
    OBP_OBU_SEQUENCE_HEADER(1), OBP_OBU_TEMPORAL_DELIMITER(2), OBP_OBU_FRAME_HEADER(3), OBP_OBU_TILE_GROUP(4), OBP_OBU_METADATA(5), OBP_OBU_FRAME(6), OBP_OBU_REDUNDANT_FRAME_HEADER(7), OBP_OBU_TILE_LIST(8), OBP_OBU_PADDING(15);

    private final int value;

    OBPOBUType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}