package org.red5.io.obu;

/**
 * <p>OBPTransferCharacteristics class.</p>
 *
 * @author mondain
 */
public enum OBPTransferCharacteristics {

    /** Reserved transfer characteristics value 0. */
    TC_RESERVED_0(0),
    /** ITU-R BT.709 transfer characteristics. */
    TC_BT_709(1),
    /** Unspecified transfer characteristics. */
    TC_UNSPECIFIED(2),
    /** Reserved transfer characteristics value 3. */
    TC_RESERVED_3(3),
    /** ITU-R BT.470 System M (historical) transfer characteristics. */
    TC_BT_470_M(4),
    /** ITU-R BT.470 System B, G (historical) transfer characteristics. */
    TC_BT_470_B_G(5),
    /** ITU-R BT.601 transfer characteristics. */
    TC_BT_601(6),
    /** SMPTE ST 240 transfer characteristics. */
    TC_SMPTE_240(7),
    /** Linear transfer characteristics. */
    TC_LINEAR(8),
    /** Logarithmic transfer characteristics with a 100:1 range. */
    TC_LOG_100(9),
    /** Logarithmic transfer characteristics with a 100*sqrt(10):1 range. */
    TC_LOG_100_SQRT10(10),
    /** IEC 61966-2-4 transfer characteristics. */
    TC_IEC_61966(11),
    /** ITU-R BT.1361 extended colour gamut transfer characteristics. */
    TC_BT_1361(12),
    /** IEC 61966-2-1 sRGB or sYCC transfer characteristics. */
    TC_SRGB(13),
    /** ITU-R BT.2020 10-bit transfer characteristics. */
    TC_BT_2020_10_BIT(14),
    /** ITU-R BT.2020 12-bit transfer characteristics. */
    TC_BT_2020_12_BIT(15),
    /** SMPTE ST 2084 (PQ) transfer characteristics. */
    TC_SMPTE_2084(16),
    /** SMPTE ST 428-1 transfer characteristics. */
    TC_SMPTE_428(17),
    /** Hybrid Log-Gamma (HLG, ARIB STD-B67) transfer characteristics. */
    TC_HLG(18);

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
