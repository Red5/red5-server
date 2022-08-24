/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2016 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.codec;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Video codecs that Red5 supports.
 *
 * @author Art Clarke
 * @author Paul Gregoire (mondain@gmail.com)
 */
public enum VideoCodec {

    JPEG((byte) 0x01), H263((byte) 0x02), SCREEN_VIDEO((byte) 0x03), VP6((byte) 0x04), VP6a((byte) 0x05), SCREEN_VIDEO2((byte) 0x06), AVC((byte) 0x07), VP8((byte) 0x08), VP9((byte) 0x09), AV1((byte) 0x0a), MPEG1((byte) 0x0b), HEVC((byte) 0x0c);

    /**
     * Codecs which have private / config data or frame type identifiers included.
     */
    private final static EnumSet<VideoCodec> configured = EnumSet.of(AVC, HEVC, VP8, VP9, AV1);

    private final static Map<Byte, VideoCodec> map = new HashMap<>();

    private byte id;

    static {
        for (VideoCodec codec : VideoCodec.values()) {
            map.put(codec.id, codec);
        }
    }

    private VideoCodec(byte id) {
        this.id = id;
    }

    /**
     * Returns back a numeric id for this codec, that happens to correspond to the numeric identifier that FLV will use for this codec.
     *
     * @return the codec id
     */
    public byte getId() {
        return id;
    }

    public static VideoCodec valueOfById(int id) {
        return (VideoCodec) map.get((byte) id);
    }

    public static EnumSet<VideoCodec> getConfigured() {
        return configured;
    }

}