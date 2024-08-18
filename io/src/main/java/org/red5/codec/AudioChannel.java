package org.red5.codec;

/**
 * Audio channel enumeration.
 *
 * @link <https://en.wikipedia.org/wiki/Surround_sound#Standard_speaker_channels>
 *
 * @author Paul Gregoire
 */
public enum AudioChannel {

    FrontLeft(0), // i.e., FrontLeft is assigned to channel zero
    FrontRight(1), FrontCenter(2), LowFrequency1(3), BackLeft(4), BackRight(5), FrontLeftCenter(6), FrontRightCenter(7), BackCenter(8), SideLeft(9), SideRight(10), TopCenter(11), TopFrontLeft(12), TopFrontCenter(13), TopFrontRight(14), TopBackLeft(15), TopBackCenter(16), TopBackRight(17),

    // mappings to complete 22.2 multichannel audio, as standardized in SMPTE ST2036-2-2008
    // see - <https://en.wikipedia.org/wiki/22.2_surround_sound>
    LowFrequency2(18), TopSideLeft(19), TopSideRight(20), BottomFrontCenter(21), BottomFrontLeft(22), BottomFrontRight(23),

    // Channel is empty and can be safely skipped.
    Unused(0xfe),

    // Channel contains data, but its speaker configuration is unknown.
    Unknown(0xff);

    private byte channel;

    AudioChannel(int channel) {
        this.channel = (byte) channel;
    }

    public byte getChannel() {
        return channel;
    }

    public static AudioChannel fromChannel(int channel) {
        for (AudioChannel ac : AudioChannel.values()) {
            if (ac.getChannel() == channel) {
                return ac;
            }
        }
        return Unknown;
    }

}