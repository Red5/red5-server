package org.red5.io.obu;

public enum OBPColorPrimaries {
    OBP_CP_BT_709(1), OBP_CP_UNSPECIFIED(2), OBP_CP_BT_470_M(4), OBP_CP_BT_470_B_G(5), OBP_CP_BT_601(6), OBP_CP_SMPTE_240(7), OBP_CP_GENERIC_FILM(8), OBP_CP_BT_2020(9), OBP_CP_XYZ(10), OBP_CP_SMPTE_431(11), OBP_CP_SMPTE_432(12), OBP_CP_EBU_3213(22);

    private final int value;

    OBPColorPrimaries(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}