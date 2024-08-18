package org.red5.codec;

public enum AvMultitrackType {

    // Used by audio and video pipeline
    OneTrack((byte) 0), ManyTracks((byte) 0x01), ManyTracksManyCodecs((byte) 0x02);

    private final byte multitrackType;

    AvMultitrackType(byte multitrackType) {
        this.multitrackType = multitrackType;
    }

    public byte getMultitrackType() {
        return multitrackType;
    }

    public static AvMultitrackType valueOf(int multitrackType) {
        for (AvMultitrackType type : values()) {
            if (type.getMultitrackType() == multitrackType) {
                return type;
            }
        }
        return null;
    }

}
