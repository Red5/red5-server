package org.red5.io.obu;

public enum OBPTransferCharacteristics {
    OBP_TC_RESERVED_0(0), OBP_TC_BT_709(1), OBP_TC_UNSPECIFIED(2), OBP_TC_RESERVED_3(3), OBP_TC_BT_470_M(4), OBP_TC_BT_470_B_G(5), OBP_TC_BT_601(6), OBP_TC_SMPTE_240(7), OBP_TC_LINEAR(8), OBP_TC_LOG_100(9), OBP_TC_LOG_100_SQRT10(10), OBP_TC_IEC_61966(11), OBP_TC_BT_1361(12), OBP_TC_SRGB(13), OBP_TC_BT_2020_10_BIT(14), OBP_TC_BT_2020_12_BIT(15), OBP_TC_SMPTE_2084(16), OBP_TC_SMPTE_428(17), OBP_TC_HLG(18);

    private final int value;

    OBPTransferCharacteristics(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
