package org.red5.codec;

/**
 * Sub-types for the AudioPacketType.ModEx modifier/extension signal.
 * Per E-RTMP v2 spec, the upper nibble of the byte following ModEx data
 * identifies the modifier type.
 *
 * @author Paul Gregoire
 */
public enum AudioPacketModExType {

    /**
     * Nanosecond timestamp offset. The modExData contains a UI24 value
     * representing nanoseconds within the current millisecond (max 999999).
     */
    TimestampOffsetNano((byte) 0);

    private final byte value;

    AudioPacketModExType(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }

    public static AudioPacketModExType valueOf(int value) {
        for (AudioPacketModExType type : values()) {
            if (type.getValue() == value) {
                return type;
            }
        }
        return null;
    }
}
