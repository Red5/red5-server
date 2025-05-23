package org.red5.io.obu;

/**
 * <p>OBPTransferCharacteristics class.</p>
 *
 * @author mondain
 */
public enum OBPTransferCharacteristics {

    TC_RESERVED_0(0), TC_BT_709(1), TC_UNSPECIFIED(2), TC_RESERVED_3(3), TC_BT_470_M(4), TC_BT_470_B_G(5), TC_BT_601(6), TC_SMPTE_240(7), TC_LINEAR(8), TC_LOG_100(9), TC_LOG_100_SQRT10(10), TC_IEC_61966(11), TC_BT_1361(12), TC_SRGB(13), TC_BT_2020_10_BIT(14), TC_BT_2020_12_BIT(15), TC_SMPTE_2084(16), TC_SMPTE_428(17), TC_HLG(18);

    private final int value;

    OBPTransferCharacteristics(int value) {
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
}
