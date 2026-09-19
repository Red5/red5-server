package org.red5.codec;

/**
 * <p>VideoCommand class.</p>
 *
 * @author mondain
 */
public enum VideoCommand {

    /** Marks the start of a client-side seeking video frame sequence. */
    START_SEEK((byte) 0),
    /** Marks the end of a client-side seeking video frame sequence. */
    END_SEEK((byte) 0x01);

    private final byte value;

    VideoCommand(byte value) {
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
     * @return a {@link org.red5.codec.VideoCommand} object
     */
    public static VideoCommand valueOf(int value) {
        for (VideoCommand command : values()) {
            if (command.value == value) {
                return command;
            }
        }
        return null;
    }

}
