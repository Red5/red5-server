package org.red5.codec;

/**
 * Mask used to indicate which channels are present in the stream.
 *
 * @link <https://en.wikipedia.org/wiki/Surround_sound#Standard_speaker_channels>
 */
public enum AudioChannelMask {

    FrontLeft(0x000001), FrontRight(0x000002), FrontCenter(0x000004), LowFrequency1(0x000008), BackLeft(0x000010), BackRight(0x000020), FrontLeftCenter(0x000040), FrontRightCenter(0x000080), BackCenter(0x000100), SideLeft(0x000200), SideRight(0x000400), TopCenter(0x000800), TopFrontLeft(0x001000), TopFrontCenter(0x002000), TopFrontRight(0x004000), TopBackLeft(0x008000), TopBackCenter(0x010000), TopBackRight(
            0x020000),

    // Completes 22.2 multichannel audio), as standardized in SMPTE ST2036-2-2008
    // see - <https://en.wikipedia.org/wiki/22.2_surround_sound>
    LowFrequency2(0x040000), TopSideLeft(0x080000), TopSideRight(0x100000), BottomFrontCenter(0x200000), BottomFrontLeft(0x400000), BottomFrontRight(0x800000);

    private int mask;

    AudioChannelMask(int mask) {
        this.mask = mask;
    }

    public int getMask() {
        return mask;
    }

}