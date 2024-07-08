/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.codec;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.red5.io.utils.IOUtils;

/**
 * Video codecs that Red5 supports; which includes some RTMP-E specific codecs.
 *
 * @author Art Clarke
 * @author Paul Gregoire (mondain@gmail.com)
 */
public enum VideoCodec {

    JPEG((byte) 0x01) {

        @Override
        public String getMimeType() {
            return "jpeg";
        }

    }, // jpeg
    H263((byte) 0x02) {

        @Override
        public IVideoStreamCodec newInstance() {
            return new SorensonVideo();
        }

        @Override
        public String getMimeType() {
            return "h263";
        }

    }, // h263
    SCREEN_VIDEO((byte) 0x03) {

        @Override
        public IVideoStreamCodec newInstance() {
            return new ScreenVideo();
        }

        @Override
        public String getMimeType() {
            return "FSV1";
        }

    }, // FSV1 / screen video
    VP6((byte) 0x04) {

        @Override
        public String getMimeType() {
            return "VP6F";
        }

    }, // VP6 / vp6f / vp6
    VP6a((byte) 0x05) {

        @Override
        public String getMimeType() {
            return "VP6A";
        }

    }, // VP6A / vp6 alpha
    SCREEN_VIDEO2((byte) 0x06) {

        @Override
        public IVideoStreamCodec newInstance() {
            return new ScreenVideo2();
        }

        @Override
        public String getMimeType() {
            return "FSV2";
        }

    }, // FSV2 / screen video 2
    AVC((byte) 0x07) {

        @Override
        public IVideoStreamCodec newInstance() {
            return new AVCVideo();
        }

        @Override
        public String getMimeType() {
            return "avc1";
        }

    }, // AVC / avc1 / h264
    VP8((byte) 0x08) {

        @Override
        public IVideoStreamCodec newInstance() {
            return new VP8Video();
        }

        @Override
        public String getMimeType() {
            return "vp08";
        }

    }, // VP8 / vp08 / vp8
    VP9((byte) 0x09) {

        @Override
        public IVideoStreamCodec newInstance() {
            return new VP9Video();
        }

        @Override
        public String getMimeType() {
            return "vp09";
        }

    }, // VP9 / vp09
    AVAILABLE((byte) 0x0a), // available
    MPEG1((byte) 0x0b) {

        @Override
        public IVideoStreamCodec newInstance() {
            return new MPEG1Video();
        }

        @Override
        public String getMimeType() {
            return "mpeg";
        }

    }, // MPEG / mpeg / mpeg1 video
    HEVC((byte) 0x0c) {

        @Override
        public IVideoStreamCodec newInstance() {
            return new HEVCVideo();
        }

        @Override
        public String getMimeType() {
            return "hvc1";
        }

    }, // HEVC / hvc1 / h265
    AV1((byte) 0x0d) {

        @Override
        public IVideoStreamCodec newInstance() {
            return new AV1Video();
        }

        @Override
        public String getMimeType() {
            return "av01";
        }

    }; // AV1 / av01

    /**
     * Codecs which have private / config data or frame type identifiers included.
     */
    private final static EnumSet<VideoCodec> configured = EnumSet.of(AVC, HEVC);

    /**
     * Codecs supplying composition time offset.
     */
    private final static EnumSet<VideoCodec> compositionTime = EnumSet.of(AVC, HEVC);

    private final static Map<Byte, VideoCodec> map = new HashMap<>();

    private byte id;

    private int fourcc;

    private String mimeType;

    static {
        for (VideoCodec codec : VideoCodec.values()) {
            map.put(codec.id, codec);
        }
    }

    private VideoCodec(byte id) {
        this.id = id;
        this.fourcc = IOUtils.makeFourcc(getMimeType());
    }

    /**
     * Returns a new instance of the codec.
     *
     * @return codec implementation
     */
    public IVideoStreamCodec newInstance() {
        return null;
    }

    /**
     * Returns back a numeric id for this codec, that happens to correspond to the numeric identifier that FLV will use for this codec.
     *
     * @return the codec id
     */
    public byte getId() {
        return id;
    }

    /**
     * Returns back a four character code for this codec.
     *
     * @return the four character code
     */
    public int getFourcc() {
        return fourcc;
    }

    /**
     * Returns the four character code for this codec.
     *
     * @return
     */
    public String getMimeType() {
        return mimeType;
    }

    public static VideoCodec valueOfById(int id) {
        return (VideoCodec) map.get((byte) id);
    }

    /**
     * Returns back the codec that corresponds to the given four character code.
     *
     * @param fourcc
     *            the four character code
     * @return the codec
     */
    public static VideoCodec valueOfByFourCc(int fourcc) {
        for (VideoCodec codec : VideoCodec.values()) {
            if (codec.getFourcc() == fourcc) {
                return codec;
            }
        }
        return null;
    }

    public static EnumSet<VideoCodec> getConfigured() {
        return configured;
    }

    public static EnumSet<VideoCodec> getCompositionTime() {
        return compositionTime;
    }

}