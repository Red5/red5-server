package org.red5.server.stream.consumer;

import org.red5.codec.VideoFrameType;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.net.rtmp.event.VideoData;
import org.red5.server.stream.IStreamData;

/**
 * Queued data wrapper.
 *
 * @author mondain
 */
public class QueuedMediaData {

    ImmutableTag tag;

    boolean video;

    boolean audio;

    boolean config;

    int codecId;

    VideoFrameType frameType;

    /**
     * <p>Constructor for QueuedMediaData.</p>
     *
     * @param timestamp a int
     * @param dataType a byte
     */
    public QueuedMediaData(int timestamp, byte dataType) {
        this.tag = ImmutableTag.build(dataType, timestamp);
    }

    @SuppressWarnings("rawtypes")
    /**
     * <p>Constructor for QueuedMediaData.</p>
     *
     * @param timestamp a int
     * @param dataType a byte
     * @param streamData a {@link org.red5.server.stream.IStreamData} object
     */
    public QueuedMediaData(int timestamp, byte dataType, IStreamData streamData) {
        this.tag = ImmutableTag.build(dataType, timestamp, streamData.getData());
        if (streamData instanceof VideoData) {
            video = true;
            config = ((VideoData) streamData).isConfig();
            codecId = ((VideoData) streamData).getCodecId();
            frameType = ((VideoData) streamData).getFrameType();
        } else if (streamData instanceof AudioData) {
            audio = true;
            config = ((AudioData) streamData).isConfig();
        }
    }

    /**
     * <p>getTimestamp.</p>
     *
     * @return a int
     */
    public int getTimestamp() {
        return tag.getTimestamp();
    }

    /**
     * <p>getDataType.</p>
     *
     * @return a byte
     */
    public byte getDataType() {
        return tag.getDataType();
    }

    /**
     * <p>getData.</p>
     *
     * @return a {@link org.red5.server.stream.consumer.ImmutableTag} object
     */
    public ImmutableTag getData() {
        return tag;
    }

    /**
     * <p>isVideo.</p>
     *
     * @return a boolean
     */
    public boolean isVideo() {
        return video;
    }

    /**
     * <p>isAudio.</p>
     *
     * @return a boolean
     */
    public boolean isAudio() {
        return audio;
    }

    /**
     * <p>isConfig.</p>
     *
     * @return a boolean
     */
    public boolean isConfig() {
        return config;
    }

    /**
     * <p>hasData.</p>
     *
     * @return a boolean
     */
    public boolean hasData() {
        return tag != null;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + tag.getDataType();
        result = prime * result + tag.getTimestamp();
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        QueuedMediaData other = (QueuedMediaData) obj;
        if (tag.getDataType() != other.getDataType()) {
            return false;
        }
        if (tag.getTimestamp() != other.getTimestamp()) {
            return false;
        }
        return true;
    }

    /**
     * <p>dispose.</p>
     */
    public void dispose() {
        tag = null;
    }

}
