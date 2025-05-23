package org.red5.codec;

import java.util.Map;

import org.apache.mina.core.buffer.IoBuffer;

/**
 * Extended interface for new E-rtmp video handlers. Methods called by abstract video codec when allowing it to parse the data.
 * Methods are called only if codec id of frame is valid.
 *
 * @author mondain
 */
public interface IEnhancedRTMPVideoCodec extends IVideoStreamCodec {

    /**
     * Called when frame type is video command.
     *
     * @param command a {@link org.red5.codec.VideoCommand} object
     * @param data a {@link org.apache.mina.core.buffer.IoBuffer} object
     * @param timestamp a int
     */
    default void onVideoCommand(VideoCommand command, IoBuffer data, int timestamp) {
    };

    /**
     * Called when type is video metadata.
     *
     * @param metainfos a {@link java.util.Map} object
     */
    default void onVideoMetadata(Map<String, Object> metainfos) {
    };

    /**
     * called when type is multitrack and valid
     *
     * @param type a {@link org.red5.codec.AvMultitrackType} object
     * @param data a {@link org.apache.mina.core.buffer.IoBuffer} object
     * @param timestamp a int
     */
    default void onMultiTrackParsed(AvMultitrackType type, IoBuffer data, int timestamp) {
    };

    /**
     * called when frame type is anything other than command, metadata, or multitrack.
     * Only will be called if codec id of frame is valid.
     *
     * @param packetType a {@link org.red5.codec.VideoPacketType} object
     * @param frameType a {@link org.red5.codec.VideoFrameType} object
     * @param data a {@link org.apache.mina.core.buffer.IoBuffer} object
     * @param timestamp a int
     */
    default void handleFrame(VideoPacketType packetType, VideoFrameType frameType, IoBuffer data, int timestamp) {
    };

    /**
     * Called if video frame is not enhanced and codec id is valid.
     *
     * @param type a {@link org.red5.codec.VideoFrameType} object
     * @param data a {@link org.apache.mina.core.buffer.IoBuffer} object
     * @param timestamp a int
     */
    default void handleNonEnhanced(VideoFrameType type, IoBuffer data, int timestamp) {
    };
}
