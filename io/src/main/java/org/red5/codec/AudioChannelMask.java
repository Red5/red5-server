package org.red5.codec;

/**
 * Mask used to indicate which channels are present in the stream.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Surround_sound#Standard_speaker_channels">Standard_speaker_channels</a>
 * @author mondain@gmail.com
 */
public enum AudioChannelMask {

    /** Front left speaker channel. */
    FrontLeft(0x000001),
    /** Front right speaker channel. */
    FrontRight(0x000002),
    /** Front center speaker channel. */
    FrontCenter(0x000004),
    /** Low frequency effects channel 1 (subwoofer). */
    LowFrequency1(0x000008),
    /** Back left speaker channel. */
    BackLeft(0x000010),
    /** Back right speaker channel. */
    BackRight(0x000020),
    /** Front left-of-center speaker channel. */
    FrontLeftCenter(0x000040),
    /** Front right-of-center speaker channel. */
    FrontRightCenter(0x000080),
    /** Back center speaker channel. */
    BackCenter(0x000100),
    /** Side left speaker channel. */
    SideLeft(0x000200),
    /** Side right speaker channel. */
    SideRight(0x000400),
    /** Top center speaker channel. */
    TopCenter(0x000800),
    /** Top front left speaker channel. */
    TopFrontLeft(0x001000),
    /** Top front center speaker channel. */
    TopFrontCenter(0x002000),
    /** Top front right speaker channel. */
    TopFrontRight(0x004000),
    /** Top back left speaker channel. */
    TopBackLeft(0x008000),
    /** Top back center speaker channel. */
    TopBackCenter(0x010000),
    /** Top back right speaker channel. */
    TopBackRight(0x020000),

    // Completes 22.2 multichannel audio), as standardized in SMPTE ST2036-2-2008
    // see - <https://en.wikipedia.org/wiki/22.2_surround_sound>
    /** Low frequency effects channel 2 (second subwoofer), part of 22.2 surround sound. */
    LowFrequency2(0x040000),
    /** Top side left speaker channel, part of 22.2 surround sound. */
    TopSideLeft(0x080000),
    /** Top side right speaker channel, part of 22.2 surround sound. */
    TopSideRight(0x100000),
    /** Bottom front center speaker channel, part of 22.2 surround sound. */
    BottomFrontCenter(0x200000),
    /** Bottom front left speaker channel, part of 22.2 surround sound. */
    BottomFrontLeft(0x400000),
    /** Bottom front right speaker channel, part of 22.2 surround sound. */
    BottomFrontRight(0x800000);

    private int mask;

    AudioChannelMask(int mask) {
        this.mask = mask;
    }

    /**
     * <p>Getter for the field <code>mask</code>.</p>
     *
     * @return a int
     */
    public int getMask() {
        return mask;
    }

}
