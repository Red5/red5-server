package org.red5.codec;

/**
 * <p>AvMultitrackType class.</p>
 *
 * @author mondain
 */
public enum AvMultitrackType {

    // Used by audio and video pipeline
    OneTrack((byte) 0), ManyTracks((byte) 0x01), ManyTracksManyCodecs((byte) 0x02);

    private final byte multitrackType;

    AvMultitrackType(byte multitrackType) {
        this.multitrackType = multitrackType;
    }

    /**
     * <p>Getter for the field <code>multitrackType</code>.</p>
     *
     * @return a byte
     */
    public byte getMultitrackType() {
        return multitrackType;
    }

    /**
     * <p>valueOf.</p>
     *
     * @param multitrackType a int
     * @return a {@link org.red5.codec.AvMultitrackType} object
     */
    public static AvMultitrackType valueOf(int multitrackType) {
        for (AvMultitrackType type : values()) {
            if (type.getMultitrackType() == multitrackType) {
                return type;
            }
        }
        return null;
    }

}
