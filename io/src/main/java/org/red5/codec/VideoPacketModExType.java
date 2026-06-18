package org.red5.codec;

/**
 * Sub-types for the VideoPacketType.ModEx modifier/extension signal.
 * Per E-RTMP v2 spec, the upper nibble of the byte following ModEx data
 * identifies the modifier type.
 *
 * @author Paul Gregoire
 */
public enum VideoPacketModExType {

    /**
     * Nanosecond timestamp offset. The modExData contains a UI24 value
     * representing nanoseconds within the current millisecond (max 999999).
     */
    TimestampOffsetNano((byte) 0);

    private final byte value;

    VideoPacketModExType(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }

    public static VideoPacketModExType valueOf(int value) {
        for (VideoPacketModExType type : values()) {
            if (type.getValue() == value) {
                return type;
            }
        }
        return null;
    }
}
