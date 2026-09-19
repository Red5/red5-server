package org.red5.io.obu;

/**
 * <p>OBUType class.</p>
 *
 * @author mondain
 */
public enum OBUType {

    /** AV1 {@code OBU_SEQUENCE_HEADER} (obu_type 1): carries the sequence header for the coded video. */
    SEQUENCE_HEADER(1),
    /** AV1 {@code OBU_TEMPORAL_DELIMITER} (obu_type 2): marks the start of a temporal unit. */
    TEMPORAL_DELIMITER(2),
    /** AV1 {@code OBU_FRAME_HEADER} (obu_type 3): carries a frame header without frame data. */
    FRAME_HEADER(3),
    /** AV1 {@code OBU_TILE_GROUP} (obu_type 4): carries one or more coded tiles. */
    TILE_GROUP(4),
    /** AV1 {@code OBU_METADATA} (obu_type 5): carries metadata associated with the coded video. */
    METADATA(5),
    /** AV1 {@code OBU_FRAME} (obu_type 6): carries a frame header immediately followed by tile group data. */
    FRAME(6),
    /** AV1 {@code OBU_REDUNDANT_FRAME_HEADER} (obu_type 7): carries a repeated copy of a frame header for error resilience. */
    REDUNDANT_FRAME_HEADER(7),
    /** AV1 {@code OBU_TILE_LIST} (obu_type 8): carries a list of tiles for large-scale tile decoding. */
    TILE_LIST(8),
    /** AV1 {@code OBU_PADDING} (obu_type 15): carries padding bits with no semantic meaning. */
    PADDING(15);

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
