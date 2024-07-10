package org.red5.io.obu;

public enum OBPChromaSamplePosition {
    OBP_CSP_UNKNOWN(0), OBP_CSP_VERTICAL(1), OBP_CSP_COLOCATED(2);

    private final int value;

    OBPChromaSamplePosition(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
