package org.red5.io.obu;

/**
 * <p>OBPChromaSamplePosition class.</p>
 *
 * @author mondain
 */
public enum OBPChromaSamplePosition {
    CSP_UNKNOWN(0), CSP_VERTICAL(1), CSP_COLOCATED(2);

    private final int value;

    OBPChromaSamplePosition(int value) {
        this.value = value;
    }

    /**
     * <p>Getter for the field <code>value</code>.</p>
     *
     * @return a int
     */
    public int getValue() {
        return value;
    }

    /**
     * <p>fromValue.</p>
     *
     * @param value a int
     * @return a {@link org.red5.io.obu.OBPChromaSamplePosition} object
     */
    public static OBPChromaSamplePosition fromValue(int value) {
        for (OBPChromaSamplePosition type : OBPChromaSamplePosition.values()) {
            if (type.getValue() == value) {
                return type;
            }
        }
        return null;
    }
}
