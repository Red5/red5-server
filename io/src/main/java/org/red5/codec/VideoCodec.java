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

/**
 * Video codecs that Red5 supports; which includes some RTMP-E specific codecs.
 *
 * @author Art Clarke
 * @author Paul Gregoire (mondain@gmail.com)
 */
public enum VideoCodec {

    JPEG((byte) 0x01), // jpeg
    H263((byte) 0x02) {

        @Override
        public IVideoStreamCodec newInstance() {
            return new SorensonVideo();
        }

        @Override
        public int getFourcc() {
            return 1748121139; // h263
        }

    }, // h263
    SCREEN_VIDEO((byte) 0x03) {

        @Override
        public IVideoStreamCodec newInstance() {
            return new ScreenVideo();
        }

        @Override
        public int getFourcc() {
            return 1179866673; // FSV1
        }

    }, // screen video
    VP6((byte) 0x04), VP6a((byte) 0x05), // vp6 / vp6 alpha
    SCREEN_VIDEO2((byte) 0x06) {

        @Override
        public IVideoStreamCodec newInstance() {
            return new ScreenVideo2();
        }

        @Override
        public int getFourcc() {
            return 1179866674; // FSV2
        }

    }, // screen video 2
    AVC((byte) 0x07) {

        @Override
        public IVideoStreamCodec newInstance() {
            return new AVCVideo();
        }

        @Override
        public int getFourcc() {
            return 1635148593; // AVC / avc1
        }

    }, // h264
    VP8((byte) 0x08) {

        @Override
        public IVideoStreamCodec newInstance() {
            return new VP8Video();
        }

        @Override
        public int getFourcc() {
            return 1987063864; // VP8 / vp08 / 1987063864
        }

    }, // vp8
    VP9((byte) 0x09) {

        @Override
        public IVideoStreamCodec newInstance() {
            return new VP9Video();
        }

        @Override
        public int getFourcc() {
            return 1987063865; // VP9 / vp09
        }

    }, // vp9
    AVAILABLE((byte) 0x0a), // available
    MPEG1((byte) 0x0b) {

        @Override
        public IVideoStreamCodec newInstance() {
            return new MPEG1Video();
        }

        @Override
        public int getFourcc() {
            return 1836069238; // MPEG / mpeg
        }

    }, // mpeg1 video
    HEVC((byte) 0x0c) {

        @Override
        public IVideoStreamCodec newInstance() {
            return new HEVCVideo();
        }

        @Override
        public int getFourcc() {
            return 1752589105; // HEVC / hvc1
        }

    }, // h265
    AV1((byte) 0x0d) {

        @Override
        public IVideoStreamCodec newInstance() {
            return new AV1Video();
        }

        @Override
        public int getFourcc() {
            return 1635135537; // AV1 / av01
        }

    }; // av1

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

    static {
        for (VideoCodec codec : VideoCodec.values()) {
            map.put(codec.id, codec);
        }
    }

    private VideoCodec(byte id) {
        this.id = id;
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