package org.red5.io.obu;

public enum OBPMatrixCoefficients {
    OBP_MC_IDENTITY(0), OBP_MC_BT_709(1), OBP_MC_UNSPECIFIED(2), OBP_MC_RESERVED_3(3), OBP_MC_FCC(4), OBP_MC_BT_470_B_G(5), OBP_MC_BT_601(6), OBP_MC_SMPTE_240(7), OBP_MC_SMPTE_YCGCO(8), OBP_MC_BT_2020_NCL(9), OBP_MC_BT_2020_CL(10), OBP_MC_SMPTE_2085(11), OBP_MC_CHROMAT_NCL(12), OBP_MC_CHROMAT_CL(13), OBP_MC_ICTCP(14);

    private final int value;

    OBPMatrixCoefficients(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
