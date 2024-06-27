package org.red5.codec;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.util.ByteNibbler;

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
                /*
                  SoundFormat: UB[4] Format of SoundData

                  Standard RTMP bits (for SoundFormat != 9):
                  soundRate = UB[2]
                  soundSize = UB[1]
                  soundType = UB[1]

                  Enhanced RTMP bits:
                  audioPacketType = UB[4] as AudioPacketType
                  if audioPacketType == Multitrack
                    audioMultitrackType = UB[4] as AvMultitrackType
                    audioPacketType = UB[4] as AudioPacketType
                    if audioMultitrackType != AvMultitrackType.ManyTracksManyCodecs
                       audioFourCc = FOURCC as AudioFourCc
                 else if audioPacketType != Multitrack
                    audioFourCc = FOURCC as AudioFourCc
                */
                ByteNibbler nibbler = new ByteNibbler(data.get());
                // codec id 9 is the extended audio codec, we need to determine the enhanced codec via fourcc
                int codecId = nibbler.nibble(4);
                if (codecId == codec.getId()) {
                    // The UB[4] bits are interpreted as AudioPacketType instead of sound rate, size and type
                    packetType = AudioPacketType.valueOf((byte) nibbler.nibble(4));
                    if (packetType == AudioPacketType.Multitrack) {
                        nibbler = new ByteNibbler(data.get());
                        multitrackType = AvMultitrackType.valueOf((byte) nibbler.nibble(4));
                        // Fetch AudioPacketType for all audio tracks in the audio message
                        // This fetch MUST not result in a AudioPacketType.Multitrack
                        packetType = AudioPacketType.valueOf(nibbler.nibble(4));
                        if (multitrackType != AvMultitrackType.ManyTracksManyCodecs) {
                            // The tracks are encoded with the same codec identified by the FOURCC
                            extendedCodec = AudioCodec.valueOfByFourCc(data.getInt());
                        }
                    } else {
                        // The tracks are encoded with the same codec identified by the FOURCC
                        extendedCodec = AudioCodec.valueOfByFourCc(data.getInt());
                    }
                    // create the codec implementation instance
                    if (extendedCodec == null) {
                        extendedAudio = extendedCodec.newInstance();
                    }
                }
            }
            result = extendedCodec != null;
        }
        return result;
    }

    @Override
    public boolean addData(IoBuffer data) {
        boolean result = false;
        if (extendedAudio != null) {
            // attempt to parse the configuration
            if (extendedAudio.canHandleData(data)) {
                // rewind the data buffer (due to position changes in canHandleData)
                data.rewind();
                // handle the extended codecs data
                result = extendedAudio.addData(data);
            }
            // rewind the data buffer
            data.rewind();
        }
        return result;
    }

}
