package org.red5.codec;

public enum VideoCommand {

    START_SEEK((byte) 0), END_SEEK((byte) 0x01);

    private final byte value;

    VideoCommand(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }

}