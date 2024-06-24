/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.stream;

import java.util.Set;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.codec.AV1Video;
import org.red5.codec.AVCVideo;
import org.red5.codec.AudioCodec;
import org.red5.codec.HEVCVideo;
import org.red5.codec.IVideoStreamCodec;
import org.red5.codec.ScreenVideo;
import org.red5.codec.ScreenVideo2;
import org.red5.codec.SorensonVideo;
import org.red5.codec.VP8Video;
import org.red5.codec.VP9Video;
import org.red5.codec.VideoCodec;
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
        //get the codec identifying byte
        int codecId = data.get() & 0x0f;
        try {
            switch (codecId) {
                case 2: // sorenson
                    result = (IVideoStreamCodec) Class.forName("org.red5.codec.SorensonVideo").getDeclaredConstructor().newInstance();
                    break;
                case 3: // screen video
                    result = (IVideoStreamCodec) Class.forName("org.red5.codec.ScreenVideo").getDeclaredConstructor().newInstance();
                    break;
                case 6: // screen video 2
                    result = (IVideoStreamCodec) Class.forName("org.red5.codec.ScreenVideo2").getDeclaredConstructor().newInstance();
                    break;
                case 7: // avc/h.264 video
                    result = (IVideoStreamCodec) Class.forName("org.red5.codec.AVCVideo").getDeclaredConstructor().newInstance();
                    break;
                case 8: // vp8
                    result = (IVideoStreamCodec) Class.forName("org.red5.codec.VP8Video").getDeclaredConstructor().newInstance();
                    break;
                case 9: // vp9
                    result = (IVideoStreamCodec) Class.forName("org.red5.codec.VP9Video").getDeclaredConstructor().newInstance();
                    break;
                case 10: // av1
                    result = (IVideoStreamCodec) Class.forName("org.red5.codec.AV1Video").getDeclaredConstructor().newInstance();
                    break;
                case 11: // mpeg1video
                    result = (IVideoStreamCodec) Class.forName("org.red5.codec.MPEG1Video").getDeclaredConstructor().newInstance();
                    break;
                case 12: // hevc
                    result = (IVideoStreamCodec) Class.forName("org.red5.codec.HEVCVideo").getDeclaredConstructor().newInstance();
                    break;
            }
        } catch (Exception ex) {
            log.error("Error creating codec instance", ex);
        }

        try {
            //get the codec identifying byte
            int codecId = (data.get() & 0xf0) >> 4;
            VideoCodec codec = VideoCodec.valueOfById(codecId);
            if (codec != null) {
                log.debug("Codec found: {}", codec);
                result = codec.newInstance();
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
