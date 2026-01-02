/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.httpflv;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.codec.IAudioStreamCodec;
import org.red5.codec.IStreamCodecInfo;
import org.red5.codec.IVideoStreamCodec;
import org.red5.io.IoConstants;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.api.stream.IStreamPacket;
import org.red5.server.net.rtmp.event.Notify;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds stream configuration data including codec configurations, metadata,
 * and GOP (Group of Pictures) cache for HTTP-FLV streaming.
 *
 * This class caches the essential data needed to start playing a stream
 * from a clean state, including:
 * - Video decoder configuration (SPS/PPS for H.264/HEVC)
 * - Audio decoder configuration (AudioSpecificConfig for AAC)
 * - Stream metadata (onMetaData)
 * - Last keyframe and its dependent frames (GOP cache)
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class StreamConfiguration {

    private static final Logger log = LoggerFactory.getLogger(StreamConfiguration.class);

    /** Stream name this configuration belongs to */
    private final String streamName;

    /** Video decoder configuration data (AVC sequence header / SPS+PPS) */
    private final AtomicReference<byte[]> videoDecoderConfig = new AtomicReference<>();

    /** Audio decoder configuration data (AAC sequence header / AudioSpecificConfig) */
    private final AtomicReference<byte[]> audioDecoderConfig = new AtomicReference<>();

    /** Stream metadata as FLV tag bytes */
    private final AtomicReference<byte[]> metadataTag = new AtomicReference<>();

    /** GOP cache - stores frames from last keyframe */
    private final CopyOnWriteArrayList<CachedFrame> gopCache = new CopyOnWriteArrayList<>();

    /** Maximum number of frames to cache in GOP */
    private int maxGopFrames = 300;

    /** Whether video config has been received */
    private volatile boolean hasVideoConfig;

    /** Whether audio config has been received */
    private volatile boolean hasAudioConfig;

    /** Whether metadata has been received */
    private volatile boolean hasMetadata;

    /** Timestamp of last keyframe */
    private volatile int lastKeyframeTimestamp;

    /**
     * Creates a new stream configuration.
     *
     * @param streamName the stream name
     */
    public StreamConfiguration(String streamName) {
        this.streamName = streamName;
    }

    /**
     * Updates configuration from a broadcast stream.
     *
     * @param stream the broadcast stream to read configuration from
     */
    public void updateFromStream(IBroadcastStream stream) {
        if (stream == null) {
            return;
        }
        // Get codec info
        IStreamCodecInfo codecInfo = stream.getCodecInfo();
        if (codecInfo != null) {
            // Update video decoder config
            IVideoStreamCodec videoCodec = codecInfo.getVideoCodec();
            if (videoCodec != null) {
                IoBuffer decoderConfig = videoCodec.getDecoderConfiguration();
                if (decoderConfig != null && decoderConfig.hasRemaining()) {
                    setVideoDecoderConfig(decoderConfig);
                }
            }
            // Update audio decoder config
            IAudioStreamCodec audioCodec = codecInfo.getAudioCodec();
            if (audioCodec != null) {
                IoBuffer decoderConfig = audioCodec.getDecoderConfiguration();
                if (decoderConfig != null && decoderConfig.hasRemaining()) {
                    setAudioDecoderConfig(decoderConfig);
                }
            }
        }
        // Update metadata
        Notify metaData = stream.getMetaData();
        if (metaData != null) {
            setMetadata(metaData);
        }
    }

    /**
     * Sets video decoder configuration from IoBuffer.
     *
     * @param config the decoder configuration data
     */
    public void setVideoDecoderConfig(IoBuffer config) {
        if (config == null || !config.hasRemaining()) {
            return;
        }
        int pos = config.position();
        byte[] configBytes = new byte[config.remaining()];
        config.get(configBytes);
        config.position(pos);
        // Create FLV tag for video config
        byte[] flvTag = FLVStreamWriter.createFLVTag(IoConstants.TYPE_VIDEO, 0, configBytes);
        if (flvTag != null) {
            videoDecoderConfig.set(flvTag);
            hasVideoConfig = true;
            log.debug("Video decoder config set for stream: {}, size: {}", streamName, configBytes.length);
        }
    }

    /**
     * Sets audio decoder configuration from IoBuffer.
     *
     * @param config the decoder configuration data
     */
    public void setAudioDecoderConfig(IoBuffer config) {
        if (config == null || !config.hasRemaining()) {
            return;
        }
        int pos = config.position();
        byte[] configBytes = new byte[config.remaining()];
        config.get(configBytes);
        config.position(pos);
        // Create FLV tag for audio config
        byte[] flvTag = FLVStreamWriter.createFLVTag(IoConstants.TYPE_AUDIO, 0, configBytes);
        if (flvTag != null) {
            audioDecoderConfig.set(flvTag);
            hasAudioConfig = true;
            log.debug("Audio decoder config set for stream: {}, size: {}", streamName, configBytes.length);
        }
    }

    /**
     * Sets stream metadata from Notify event.
     *
     * @param metadata the metadata notify event
     */
    public void setMetadata(Notify metadata) {
        if (metadata == null) {
            return;
        }
        IoBuffer data = metadata.getData();
        if (data == null || !data.hasRemaining()) {
            return;
        }
        int pos = data.position();
        byte[] metaBytes = new byte[data.remaining()];
        data.get(metaBytes);
        data.position(pos);
        // Create FLV tag for metadata
        byte[] flvTag = FLVStreamWriter.createFLVTag(IoConstants.TYPE_METADATA, 0, metaBytes);
        if (flvTag != null) {
            metadataTag.set(flvTag);
            hasMetadata = true;
            log.debug("Metadata set for stream: {}, size: {}", streamName, metaBytes.length);
        }
    }

    /**
     * Adds a frame to the GOP cache.
     * If frame is a keyframe, clears the existing cache first.
     *
     * @param packet the stream packet
     * @param isKeyframe true if this is a keyframe
     */
    public void addToGopCache(IStreamPacket packet, boolean isKeyframe) {
        if (packet == null) {
            return;
        }
        byte[] flvTag = FLVStreamWriter.packetToFLVTag(packet);
        if (flvTag == null) {
            return;
        }
        if (isKeyframe) {
            // Clear GOP cache and start fresh from keyframe
            gopCache.clear();
            lastKeyframeTimestamp = packet.getTimestamp();
            log.trace("GOP cache cleared, new keyframe at timestamp: {}", lastKeyframeTimestamp);
        }
        // Add to cache if within limits
        if (gopCache.size() < maxGopFrames) {
            gopCache.add(new CachedFrame(packet.getDataType(), packet.getTimestamp(), flvTag));
        }
    }

    /**
     * Gets the video decoder configuration as FLV tag bytes.
     *
     * @return video decoder config FLV tag, or null if not available
     */
    public byte[] getVideoDecoderConfig() {
        return videoDecoderConfig.get();
    }

    /**
     * Gets the audio decoder configuration as FLV tag bytes.
     *
     * @return audio decoder config FLV tag, or null if not available
     */
    public byte[] getAudioDecoderConfig() {
        return audioDecoderConfig.get();
    }

    /**
     * Gets the metadata as FLV tag bytes.
     *
     * @return metadata FLV tag, or null if not available
     */
    public byte[] getMetadataTag() {
        return metadataTag.get();
    }

    /**
     * Gets a copy of the GOP cache.
     *
     * @return list of cached frames
     */
    public CachedFrame[] getGopCache() {
        return gopCache.toArray(new CachedFrame[0]);
    }

    /**
     * Clears the GOP cache.
     */
    public void clearGopCache() {
        gopCache.clear();
    }

    /**
     * Resets all configuration data.
     */
    public void reset() {
        videoDecoderConfig.set(null);
        audioDecoderConfig.set(null);
        metadataTag.set(null);
        gopCache.clear();
        hasVideoConfig = false;
        hasAudioConfig = false;
        hasMetadata = false;
        lastKeyframeTimestamp = 0;
    }

    public String getStreamName() {
        return streamName;
    }

    public boolean hasVideoConfig() {
        return hasVideoConfig;
    }

    public boolean hasAudioConfig() {
        return hasAudioConfig;
    }

    public boolean hasMetadata() {
        return hasMetadata;
    }

    public int getLastKeyframeTimestamp() {
        return lastKeyframeTimestamp;
    }

    public int getGopCacheSize() {
        return gopCache.size();
    }

    public int getMaxGopFrames() {
        return maxGopFrames;
    }

    public void setMaxGopFrames(int maxGopFrames) {
        this.maxGopFrames = maxGopFrames;
    }

    /**
     * Represents a cached frame in the GOP cache.
     */
    public static class CachedFrame {

        private final byte dataType;

        private final int timestamp;

        private final byte[] data;

        public CachedFrame(byte dataType, int timestamp, byte[] data) {
            this.dataType = dataType;
            this.timestamp = timestamp;
            this.data = data;
        }

        public byte getDataType() {
            return dataType;
        }

        public int getTimestamp() {
            return timestamp;
        }

        public byte[] getData() {
            return data;
        }

    }

}
