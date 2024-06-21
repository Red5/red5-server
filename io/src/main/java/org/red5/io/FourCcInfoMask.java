package org.red5.io;

import java.util.EnumSet;

/**
 * Capability flags define specific functionalities, such as the ability to decode, encode, or forward.
 *
 * @author Paul Gregoire
 */
public enum FourCcInfoMask {

    CanDecode((byte) 0x01), // Can decode
    CanEncode((byte) 0x02), // Can encode
    CanForward((byte) 0x04); // Can forward any codec

    private final byte mask;

    FourCcInfoMask(byte b) {
        mask = b;
    }

    public byte getMask() {
        return mask;
    }

    public static EnumSet<FourCcInfoMask> fromMask(byte b) {
        EnumSet<FourCcInfoMask> result = EnumSet.noneOf(FourCcInfoMask.class);
        for (FourCcInfoMask mask : values()) {
            if ((b & mask.getMask()) == mask.getMask()) {
                result.add(mask);
            }
        }
        return result;
    }

    public static byte toMask(EnumSet<FourCcInfoMask> set) {
        byte result = 0;
        for (FourCcInfoMask mask : set) {
            result |= mask.getMask();
        }
        return result;
    }

    public static EnumSet<FourCcInfoMask> all() {
        return EnumSet.allOf(FourCcInfoMask.class);
    }

}