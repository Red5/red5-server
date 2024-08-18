package org.red5.io.obu;

public enum OBPChromaSamplePosition {
    CSP_UNKNOWN(0), CSP_VERTICAL(1), CSP_COLOCATED(2);

    private final int value;

    OBPChromaSamplePosition(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static OBPChromaSamplePosition fromValue(int value) {
        for (OBPChromaSamplePosition type : OBPChromaSamplePosition.values()) {
            if (type.getValue() == value) {
                return type;
            }
        }
        return null;
    }
}
