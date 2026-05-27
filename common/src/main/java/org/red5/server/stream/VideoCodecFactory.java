/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.stream;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.codec.IVideoStreamCodec;
import org.red5.codec.VideoCodec;
import org.red5.codec.VideoFrameType;
import org.red5.codec.VideoPacketType;
import org.red5.io.IoConstants;
import org.red5.util.ByteNibbler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for video codecs. Creates and returns video codecs.
 *
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class VideoCodecFactory {
    /**
     * Object key
     */
    public static final String KEY = "videoCodecFactory";

    /**
     * Logger for video factory
     */
    private static Logger log = LoggerFactory.getLogger(VideoCodecFactory.class);

    /**
     * Create and return new video codec applicable for byte buffer data
     *
     * @param data
     *            Byte buffer data
     * @return Video codec
     */
    public static IVideoStreamCodec getVideoCodec(IoBuffer data) {
        IVideoStreamCodec result = null;
        try {
            // holder for codec
            VideoCodec codec = null;
            // get the codec identifying byte
            data.mark();
            byte c = data.get();
            data.reset();
            log.debug("Flag: {}", Integer.toBinaryString(c & 0xff));
            boolean enhanced = ByteNibbler.isBitSet(c, 7);
            if (enhanced) {
                log.debug("Enhanced codec handling; pos: {}", data.position());
                codec = getEnhancedVideoCodec(data);
            } else {
                int codecId = (c & IoConstants.MASK_VIDEO_CODEC);
                codec = VideoCodec.valueOfById(codecId);
            }
            if (codec != null) {
                log.debug("Codec found: {} pos: {}", codec, data.position());
                // this will be reset if the codec cannot handle the data
                result = codec.newInstance();
                // add the data to the codec
                if (result.addData(data)) {
                    log.debug("Codec {} accepted data", result);
                } else {
                    data.rewind();
                    byte[] peek = new byte[Math.min(8, data.remaining())];
                    data.get(peek);
                    data.rewind();
                    log.warn("Codec {} rejected data, flag: 0x{}, enhanced: {}, first bytes: {}", codec, String.format("%02x", c), enhanced, ByteNibbler.toHexString(peek));
                    result = null;
                }
            } else {
                log.warn("Codec not found for {}", String.format("%02x", c));
            }
        } catch (Exception ex) {
            log.error("Error creating codec instance", ex);
        }
        return result;
    }

    private static VideoCodec getEnhancedVideoCodec(IoBuffer data) {
        VideoCodec codec = null;
        data.mark();
        try {
            byte flags = data.get();
            VideoFrameType frameType = VideoFrameType.valueOf((flags & IoConstants.MASK_VIDEO_FRAMETYPE) >> 4);
            VideoPacketType packetType = VideoPacketType.valueOf(flags & IoConstants.MASK_VIDEO_CODEC);
            if (frameType == VideoFrameType.COMMAND_FRAME && packetType != VideoPacketType.Metadata) {
                return null;
            }
            if (packetType == VideoPacketType.Multitrack) {
                if (!data.hasRemaining()) {
                    return null;
                }
                data.skip(1);
            }
            if (data.remaining() >= Integer.BYTES) {
                codec = VideoCodec.valueOfByFourCc(data.getInt());
            }
        } finally {
            data.reset();
        }
        return codec;
    }

}
