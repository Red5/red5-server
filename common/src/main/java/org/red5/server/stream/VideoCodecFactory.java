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
import org.red5.io.IoConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for video codecs. Creates and returns video codecs
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
            // get the codec identifying byte
            byte c = data.array()[data.position()]; // prevent advancing the position at least once
            int codecId = (c & IoConstants.MASK_VIDEO_CODEC);
            VideoCodec codec = VideoCodec.valueOfById(codecId);
            if (codec != null) {
                log.debug("Codec found: {}", codec);
                // this will be reset if the codec cannot handle the data
                result = codec.newInstance();
                // check if the codec can handle the data
                if (result.canHandleData(data)) {
                    log.debug("Codec {} can handle the data", result);
                } else {
                    log.warn("Codec {} cannot handle data", codec);
                    result = null;
                }
            } else {
                log.warn("Codec not found for id: {}", codecId);
            }
        } catch (Exception ex) {
            log.error("Error creating codec instance", ex);
        } finally {
            data.rewind();
        }
        return result;
    }

}
