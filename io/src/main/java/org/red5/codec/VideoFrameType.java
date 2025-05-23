package org.red5.codec;

/**
 * <p>VideoFrameType class.</p>
 *
 * @author mondain
 */
public enum VideoFrameType {

    RESERVED((byte) 0x00), // Reserved
    KEYFRAME((byte) 0x01), // Keyframe
    INTERFRAME((byte) 0x02), // Interframe
    DISPOSABLE((byte) 0x03), // Disposable interframe (H.263 only)
    GENERATED_KEYFRAME((byte) 0x04), // Generated keyframe
    /**
     * Command frame type flag
     *
     * If videoFrameType is not ignored and is set to VideoFrameType.Command, the payload will not contain video data.
     * Instead, (Ex)VideoTagHeader will be followed by a UI8, representing the following meanings:
     *  0 = Start of client-side seeking video frame sequence
     *  1 = End of client-side seeking video frame sequence
     *
     * frameType is ignored if videoPacketType is VideoPacketType.MetaData
     */
    COMMAND_FRAME((byte) 0x05), // Command or informational frame
    DELTA((byte) 0x0f); // Delta frame (only used internally for AV1 handling)

    private final byte value;

    VideoFrameType(byte value) {
        this.value = value;
    }

    /**
     * <p>Getter for the field <code>value</code>.</p>
     *
     * @return a byte
     */
    public byte getValue() {
        return value;
    }

    /**
     * <p>valueOf.</p>
     *
     * @param value a int
     * @return a {@link org.red5.codec.VideoFrameType} object
     */
    public static VideoFrameType valueOf(int value) {
        for (VideoFrameType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return null;
    }

}
