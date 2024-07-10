package org.red5.io.obu;

public enum OBPFrameType {
    OBP_KEY_FRAME(0), OBP_INTER_FRAME(1), OBP_INTRA_ONLY_FRAME(2), OBP_SWITCH_FRAME(3);

    private final int value;

    OBPFrameType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
