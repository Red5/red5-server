package org.red5.codec;

import org.apache.mina.core.buffer.IoBuffer;

/**
 * Extended audio codec used for enhanced RTMP encoded audio. This codec is used to signal that the audio data is
 * encoded with a codec that is not originally supported by Red5. An actual codec implemenation will be identified via
 * canHandleData() and returned by the getCodec() method. This codec "has an" extended codec, which is the actual
 * codec.
 *
 * @author Paul Gregoire
 */
public class ExtendedAudio extends AbstractAudio {

    // actual extended codec
    private AudioCodec extendedCodec;

    // actual extended codec implementation
    private IAudioStreamCodec extendedAudio;

    /**
     * Constructs a new ExtendedAudio
     */
    public ExtendedAudio() {
        codec = AudioCodec.ExHeader;
    }

    @Override
    public AudioCodec getCodec() {
        return extendedCodec != null ? extendedCodec : codec;
    }

    @Override
    public String getName() {
        return extendedCodec != null ? extendedCodec.name() : codec.name();
    }

    @Override
    public boolean canHandleData(IoBuffer data) {
        boolean result = false;
        if (data.limit() > 0) {
            // set the extended codec
            if (extendedCodec == null) {
                byte first = data.get();
                extendedCodec = AudioCodec.valueOfById((byte) ((first & 0xf0) >> 4));
                // The UB[4] bits are interpreted as AudioPacketType instead of sound rate, size and type
                packetType = AudioPacketType.valueOf((byte) (first & 0x0f));
                if (packetType == AudioPacketType.Multitrack) {
                    byte second = data.get();
                    multitrackType = AvMultitrackType.valueOf((byte) ((second & 0xf0) >> 4));
                    // Fetch AudioPacketType for all audio tracks in the audio message
                    // This fetch MUST not result in a AudioPacketType.Multitrack 
                    packetType = AudioPacketType.valueOf((byte) (second & 0x0f));
                    if (multitrackType != AvMultitrackType.ManyTracksManyCodecs) {
                        // The tracks are encoded with the same codec.
                    }
                }
                // create the codec implementation instance
                if (extendedCodec == null) {
                    extendedAudio = extendedCodec.newInstance();
                }
                data.rewind();
            }
            result = extendedCodec != null;
        }
        return result;
    }

    @Override
    public boolean addData(IoBuffer data) {
        boolean result = false;
        if (extendedAudio != null) {
            // handle the extended codecs data
            result = extendedAudio.addData(data);
        }
        return result;
    }

}
