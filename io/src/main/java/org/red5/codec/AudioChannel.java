package org.red5.codec;

/**
 * Audio channel enumeration.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Surround_sound#Standard_speaker_channels">Standard_speaker_channels</a>
 * @author Paul Gregoire
 */
public enum AudioChannel {

    /** Front left speaker, i.e., FrontLeft is assigned to channel zero. */
    FrontLeft(0),
    /** Front right speaker. */
    FrontRight(1),
    /** Front center speaker. */
    FrontCenter(2),
    /** Low frequency effects speaker (subwoofer), first channel. */
    LowFrequency1(3),
    /** Back left speaker. */
    BackLeft(4),
    /** Back right speaker. */
    BackRight(5),
    /** Front left-of-center speaker. */
    FrontLeftCenter(6),
    /** Front right-of-center speaker. */
    FrontRightCenter(7),
    /** Back center speaker. */
    BackCenter(8),
    /** Side left speaker. */
    SideLeft(9),
    /** Side right speaker. */
    SideRight(10),
    /** Top center speaker. */
    TopCenter(11),
    /** Top front left speaker. */
    TopFrontLeft(12),
    /** Top front center speaker. */
    TopFrontCenter(13),
    /** Top front right speaker. */
    TopFrontRight(14),
    /** Top back left speaker. */
    TopBackLeft(15),
    /** Top back center speaker. */
    TopBackCenter(16),
    /** Top back right speaker. */
    TopBackRight(17),

    // mappings to complete 22.2 multichannel audio, as standardized in SMPTE ST2036-2-2008
    // see - <https://en.wikipedia.org/wiki/22.2_surround_sound>
    /** Low frequency effects speaker (subwoofer), second channel; part of the 22.2 layout. */
    LowFrequency2(18),
    /** Top side left speaker; part of the 22.2 layout. */
    TopSideLeft(19),
    /** Top side right speaker; part of the 22.2 layout. */
    TopSideRight(20),
    /** Bottom front center speaker; part of the 22.2 layout. */
    BottomFrontCenter(21),
    /** Bottom front left speaker; part of the 22.2 layout. */
    BottomFrontLeft(22),
    /** Bottom front right speaker; part of the 22.2 layout. */
    BottomFrontRight(23),

    /** Channel is empty and can be safely skipped. */
    Unused(0xfe),

    /** Channel contains data, but its speaker configuration is unknown. */
    Unknown(0xff);

    private byte channel;

    AudioChannel(int channel) {
        this.channel = (byte) channel;
    }

    /**
     * <p>Getter for the field <code>channel</code>.</p>
     *
     * @return a byte
     */
    public byte getChannel() {
        return channel;
    }

    /**
     * <p>fromChannel.</p>
     *
     * @param channel a int
     * @return a {@link org.red5.codec.AudioChannel} object
     */
    public static AudioChannel fromChannel(int channel) {
        for (AudioChannel ac : AudioChannel.values()) {
            if (ac.getChannel() == channel) {
                return ac;
            }
        }
        return Unknown;
    }

}
