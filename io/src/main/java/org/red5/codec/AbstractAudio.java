package org.red5.codec;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.util.ByteNibbler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract audio codec implementation.
 *
 */
public class AbstractAudio implements IAudioStreamCodec {

    protected static Logger log = LoggerFactory.getLogger("Audio");

    protected static boolean isTrace = log.isTraceEnabled(), isDebug = log.isDebugEnabled();

    protected AudioCodec codec;

    protected AvMultitrackType multitrackType;

    protected AudioPacketType packetType;

    // defaulting to 48khz, 16bit, stereo
    protected int sampleRate = 48000, sampleSizeInBits = 16, channels = 2;

    // defaulting to unsigned simply to support 8 bit audio / older codecs
    protected boolean signed;

    // track id
    protected int trackId;

    // audio channel order
    protected AudioChannelOrder audioChannelOrder = AudioChannelOrder.Unspecified;

    // each entry specifies the speaker layout
    protected AudioChannel[] audioChannelMap;

    // indicates which channels are present in the multi-channel stream
    protected int audioChannelFlags;

    @Override
    public AudioCodec getCodec() {
        return codec;
    }

    @Override
    public String getName() {
        return codec.name();
    }

    @Override
    public void setTrackId(int trackId) {
        this.trackId = trackId;
    }

    @Override
    public int getTrackId() {
        return trackId;
    }

    @Override
    public void reset() {
    }

    @Override
    public boolean canHandleData(IoBuffer data) {
        boolean result = false;
        if (data != null && data.limit() > 0) {
            byte flgs = data.get();
            ByteNibbler nibbler = new ByteNibbler(flgs);
            int codecId = nibbler.nibble(4);
            // check codec id against bitstream
            result = codecId == codec.getId();
            if (result) {
                sampleRate = nibbler.nibble(2);
                sampleSizeInBits = nibbler.nibble(1) == 0 ? 8 : 16;
                // PCM and PCM_LE are the same, just the endianess is different, 16 bit are always signed
                signed = (codecId != 3 && codecId != 0) || sampleSizeInBits == 16;
                channels = 1 + nibbler.nibble(1);
                // set the sample rate in kHz (this may change depending upon the codec)
                switch (sampleRate) {
                    case 0:
                        sampleRate = 5500;
                        break;
                    case 1:
                        sampleRate = 11025;
                        break;
                    case 2:
                        sampleRate = 22050;
                        break;
                    case 3: // flash sends this for 48kHz also
                        sampleRate = 44100;
                        break;
                }
            }
        }
        return result;
    }

    @Override
    public boolean addData(IoBuffer data) {
        return false;
    }

    @Override
    public boolean addData(IoBuffer data, int timestamp, boolean amf) {
        return false;
    }

    @Override
    public IoBuffer getDecoderConfiguration() {
        return null;
    }

    @Override
    public AvMultitrackType getMultitrackType() {
        return multitrackType;
    }

    @Override
    public AudioPacketType getPacketType() {
        return packetType;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    @Override
    public int getSampleRate() {
        return sampleRate;
    }

    @Override
    public int getSampleSizeInBits() {
        return sampleSizeInBits;
    }

    public void setChannels(int channels) {
        this.channels = channels;
    }

    @Override
    public int getChannels() {
        return channels;
    }

    @Override
    public boolean isSigned() {
        return signed;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((codec == null) ? 0 : codec.hashCode());
        result = prime * result + sampleRate;
        result = prime * result + sampleSizeInBits;
        result = prime * result + channels;
        result = prime * result + trackId;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AbstractAudio other = (AbstractAudio) obj;
        if (codec != other.codec)
            return false;
        if (sampleRate != other.sampleRate)
            return false;
        if (sampleSizeInBits != other.sampleSizeInBits)
            return false;
        if (channels != other.channels)
            return false;
        if (trackId != other.trackId)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "Audio [codec=" + codec + ", multitrackType=" + multitrackType + ", sampleRate=" + sampleRate + ", sampleSizeInBits=" + sampleSizeInBits + ", channels=" + channels + ", signed=" + signed + ", trackId=" + trackId + "]";
    }

}
