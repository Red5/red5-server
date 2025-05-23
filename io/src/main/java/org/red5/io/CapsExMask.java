package org.red5.io;

import java.util.EnumSet;

/**
 * Extended capability flags define specific functionalities, such as the ability to reconnect or multitrack.
 *
 * @author Paul Gregoire
 */
public enum CapsExMask {

    Reconnect((byte) 0x01), Multitrack((byte) 0x02);

    private final byte mask;

    CapsExMask(byte b) {
        mask = b;
    }

    /**
     * <p>Getter for the field <code>mask</code>.</p>
     *
     * @return a byte
     */
    public byte getMask() {
        return mask;
    }

    /**
     * <p>fromMask.</p>
     *
     * @param b a byte
     * @return a {@link java.util.EnumSet} object
     */
    public static EnumSet<CapsExMask> fromMask(byte b) {
        EnumSet<CapsExMask> result = EnumSet.noneOf(CapsExMask.class);
        for (CapsExMask mask : values()) {
            if ((b & mask.getMask()) == mask.getMask()) {
                result.add(mask);
            }
        }
        return result;
    }

    /**
     * <p>toMask.</p>
     *
     * @param set a {@link java.util.EnumSet} object
     * @return a byte
     */
    public static byte toMask(EnumSet<CapsExMask> set) {
        byte result = 0;
        for (CapsExMask mask : set) {
            result |= mask.getMask();
        }
        return result;
    }

    /**
     * <p>all.</p>
     *
     * @return a {@link java.util.EnumSet} object
     */
    public static EnumSet<CapsExMask> all() {
        return EnumSet.allOf(CapsExMask.class);
    }

}
