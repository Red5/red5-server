package org.red5.codec;

/**
 * <p>AudioPacketType class.</p>
 *
 * @author mondain
 */
public enum AudioPacketType {

    SequenceStart((byte) 0), CodedFrames((byte) 0x01),

    // RTMP includes a previously undocumented 'audio silence' message. This silence message is identified when an
    // audio message contains a zero-length payload, or more precisely, an empty audio message without an
    // AudioTagHeader, indicating a period of silence. The action to take after receiving a silence message is
    // system dependent. The semantics of the silence message in the Flash Media playback and timing model are as
    // follows:
    // - Ensure all buffered audio data is played out before entering the silence period:
    //   Make sure that any audio data currently in the buffer is fully processed and played. This ensures a clean
    //   transition into the silence period without cutting off any audio.
    //
    // - After playing all buffered audio data, flush the audio decoder: Clear the audio decoder to reset its state
    //   and prepare it for new input after the silence period.
    //
    // - During the silence period, the audio clock can't be used as the master clock for synchronizing playback:
    //   Switch to using the system's wall-clock time to maintain the correct timing for video and other streams.
    //
    // - Don't wait for audio frames for synchronized A+V playback: Normally, audio frames drive the
    //   synchronization of audio and video playback. During the silence period, playback should not stall waiting
    //   for audio frames. Video and other data streams should continue to play based on the wall-clock time,
    //   ensuring smooth playback without audio.
    //
    // AudioPacketType.SequenceEnd is to have no less than the same meaning as a silence message. We need to
    // introduce this enum to ensure we can signal the end of the audio sequence for any audio track.
    SequenceEnd((byte) 0x02), MultichannelConfig((byte) 0x04),
    // Turns on multitrack mode
    Multitrack((byte) 0x05);

    private final byte packetType;

    AudioPacketType(byte packetType) {
        this.packetType = packetType;
    }

    /**
     * <p>Getter for the field <code>packetType</code>.</p>
     *
     * @return a byte
     */
    public byte getPacketType() {
        return packetType;
    }

    /**
     * <p>valueOf.</p>
     *
     * @param packetType a int
     * @return a {@link org.red5.codec.AudioPacketType} object
     */
    public static AudioPacketType valueOf(int packetType) {
        for (AudioPacketType apt : values()) {
            if (apt.getPacketType() == packetType) {
                return apt;
            }
        }
        return null;
    }

}
