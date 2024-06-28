package org.red5.codec;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.io.IoConstants;
import org.red5.util.ByteNibbler;

/**
 * Extended audio codec used for enhanced RTMP encoded audio. This codec is used to signal that the audio data is
 * encoded with a codec that is not originally supported by Red5. An actual codec implemenation will be identified in
 * canHandleData() and returned by the getTrackCodec(trackId) method. An internal map contains all the codecs for
 * each track.
 *
 * @author Paul Gregoire
 */
public class ExtendedAudio extends AbstractAudio {

    // tracks for multitrack audio, if size = 1, theres only one track
    private ConcurrentMap<Integer, IAudioStreamCodec> tracks = new ConcurrentSkipListMap<>();

    // multitrack flag
    private boolean multitrack;

    // track codec - this is temporary when used for multitrack audio
    private IAudioStreamCodec trackCodec;

    // track length in bytes
    private int trackSize = 0;

    {
        codec = AudioCodec.ExHeader;
    }

    /**
     * Returns the codec for the specified track.
     *
     * @param trackId
     * @return codec for the track id or null if not found
     */
    public IAudioStreamCodec getTrackCodec(int trackId) {
        return tracks.values().stream().filter(c -> c.getTrackId() == trackId).findFirst().orElse(null);
    }

    @Override
    public boolean canHandleData(IoBuffer data) {
        boolean result = false;
        if (data != null && data.limit() > 0) {
            result = ((data.get() & IoConstants.MASK_SOUND_FORMAT) >> 4) == codec.getId();
        }
        return result;
    }

    @SuppressWarnings("incomplete-switch")
    @Override
    public boolean addData(IoBuffer data) {
        boolean result = false;
        // set up the track(s) per enhanced codec entry
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
        // attempt to parse the configuration
        ByteNibbler nibbler = new ByteNibbler(data.get());
        // codec id 9 is the extended audio codec, we need to determine the enhanced codec via fourcc
        int codecId = nibbler.nibble(4);
        if (codecId == codec.getId()) {
            // The UB[4] bits are interpreted as AudioPacketType instead of sound rate, size and type
            packetType = AudioPacketType.valueOf((byte) nibbler.nibble(4));
            if (packetType == AudioPacketType.Multitrack) {
                // set the multitrack flag
                multitrack = true;
                // set up for reading more bits
                nibbler = new ByteNibbler(data.get());
                multitrackType = AvMultitrackType.valueOf((byte) nibbler.nibble(4));
                // Fetch AudioPacketType for all audio tracks in the message
                // This fetch MUST not result in a AudioPacketType.Multitrack
                packetType = AudioPacketType.valueOf(nibbler.nibble(4));
                if (multitrackType != AvMultitrackType.ManyTracksManyCodecs) {
                    // The tracks are encoded with the same codec identified by the FOURCC
                    Integer fourcc = data.getInt();
                    if (!tracks.containsKey(fourcc)) {
                        // create a new codec instance
                        trackCodec = AudioCodec.valueOfByFourCc(fourcc).newInstance();
                        tracks.put(fourcc, trackCodec);
                    } else {
                        trackCodec = tracks.get(fourcc);
                    }
                }
            } else {
                // The tracks are encoded with the same codec identified by the FOURCC
                Integer fourcc = data.getInt();
                if (!tracks.containsKey(fourcc)) {
                    // create a new codec instance
                    trackCodec = AudioCodec.valueOfByFourCc(fourcc).newInstance();
                    tracks.put(fourcc, trackCodec);
                } else {
                    trackCodec = tracks.get(fourcc);
                }
            }
        }
        // read all the data
        while (data.hasRemaining()) {
            // handle multiple tracks
            if (multitrack) {
                // handle tracks that each have their own codec
                if (multitrackType == AvMultitrackType.ManyTracksManyCodecs) {
                    // The tracks are encoded with their own codec identified by the FOURCC
                    Integer fourcc = data.getInt();
                    if (!tracks.containsKey(fourcc)) {
                        // create a new codec instance
                        trackCodec = AudioCodec.valueOfByFourCc(fourcc).newInstance();
                        tracks.put(fourcc, trackCodec);
                    } else {
                        trackCodec = tracks.get(fourcc);
                    }
                }
                // track ordering
                // For identifying the highest priority (a.k.a., default track) or highest quality track, it is RECOMMENDED
                // to use trackId set to zero. For tracks of lesser priority or quality, use multiple instances of trackId
                // with ascending numerical values. The concept of priority or quality can have multiple interpretations,
                // including but not limited to bitrate, resolution, default angle, and language. This recommendation
                // serves as a guideline intended to standardize track numbering across various applications.
                trackId = data.get();
                if (multitrackType != AvMultitrackType.OneTrack) {
                    // The 'sizeOfAudioTrack' specifies the size in bytes of the current track that is being processed.
                    // This size starts counting immediately after the position where the 'sizeOfAudioTrack' value is
                    // located. You can use this value as an offset to locate the next audio track in a multitrack system.
                    // The data pointer is positioned immediately after this field. Depending on the MultiTrack type, the
                    // offset points to either a 'fourCc' or a 'trackId.'
                    trackSize = (data.get() & 0xff) << 16 | (data.get() & 0xff) << 8 | data.get() & 0xff;
                }
                // we're multitrack and multicodec so update track id
                if (multitrackType == AvMultitrackType.ManyTracksManyCodecs) {
                    trackCodec.setTrackId(trackId);
                }
            }
            switch (packetType) {
                case SequenceStart: // start of sequence
                case CodedFrames: // pass coded data
                    result = trackCodec.addData(data);
                    break;
                case SequenceEnd: // end of sequence
                    break;
                case MultichannelConfig:
                    // Specify a speaker for a channel as it appears in the bitstream. This is needed if the codec is
                    // not self-describing for channel mapping
                    AudioChannelOrder audioChannelOrder = AudioChannelOrder.valueOf(data.get());
                    // number of channels
                    channels = data.get() & 0xff;
                    if (audioChannelOrder == AudioChannelOrder.Custom) {
                        // Each entry specifies the speaker layout (see AudioChannel enum for layout definition)
                        // in the order that it appears in the bitstream. First entry (i.e., index 0) specifies the
                        // speaker layout for channel 1. Subsequent entries specify the speaker layout for the next
                        // channels (e.g., second entry for channel 2, third entry for channel 3, etc.).
                        audioChannelMap = new AudioChannel[channels];
                        for (int i = 0; i < channels; i++) {
                            audioChannelMap[i] = AudioChannel.fromChannel(data.get());
                        }
                    }
                    if (audioChannelOrder == AudioChannelOrder.Native) {
                        // audioChannelFlags indicates which channels are present in the multi-channel stream. You can
                        // perform a Bitwise AND (i.e., audioChannelFlags & AudioChannelMask.xxx) to see if a specific
                        // audio channel is present
                        audioChannelFlags = (data.get() & 0xff) << 24 | (data.get() & 0xff) << 16 | (data.get() & 0xff) << 8 | data.get() & 0xff;
                    }
                    break;
            }
            // check for multiple tracks
            if (multitrack && trackSize > 0) {
                data.skip(trackSize);
                continue;
            }
            // break out of the loop
            break;
        }
        // rewind the data buffer
        data.rewind();
        return result;
    }

}
