package org.red5.server.stream.consumer;

import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.net.rtmp.event.VideoData;
import org.red5.server.stream.IStreamData;

/**
 * Queued data wrapper.
 */
public class QueuedMediaData {

    ImmutableTag tag;

    boolean video;

    boolean audio;

    boolean config;

    int codecId;

    VideoData.FrameType frameType;

    public QueuedMediaData(int timestamp, byte dataType) {
        this.tag = ImmutableTag.build(dataType, timestamp);
    }

    @SuppressWarnings("rawtypes")
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

    public int getTimestamp() {
        return tag.getTimestamp();
    }

    public byte getDataType() {
        return tag.getDataType();
    }

    public ImmutableTag getData() {
        return tag;
    }

    public boolean isVideo() {
        return video;
    }

    public boolean isAudio() {
        return audio;
    }

    public boolean isConfig() {
        return config;
    }

    public boolean hasData() {
        return tag != null;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + tag.getDataType();
        result = prime * result + tag.getTimestamp();
        return result;
    }

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

    public void dispose() {
        tag = null;
    }

}