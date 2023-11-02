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
 * Audio codecs that Red5 supports.
 *
 * @author Art Clarke
 * @author Paul Gregoire (mondain@gmail.com)
 */
public enum AudioCodec {

    PCM((byte) 0), ADPCM((byte) 0x01), MP3((byte) 0x02), PCM_LE((byte) 0x03), NELLY_MOSER_16K((byte) 0x04), NELLY_MOSER_8K((byte) 0x05), NELLY_MOSER((byte) 0x06), PCM_ALAW((byte) 0x07), PCM_MULAW((byte) 0x08), RESERVED((byte) 0x09), AAC((byte) 0x0a), SPEEX((byte) 0x0b), MP2((byte) 0x0c), OPUS((byte) 0x0d), MP3_8K((byte) 0x0e), DEVICE_SPECIFIC((byte) 0x0f);

    /**
     * Codecs which have private / config data or type identifiers included.
     */
    private final static EnumSet<AudioCodec> configured = EnumSet.of(AAC, OPUS);

    private final static Map<Byte, AudioCodec> map = new HashMap<>();

    private byte id;

    static {
        for (AudioCodec codec : AudioCodec.values()) {
            map.put(codec.id, codec);
        }
    }

    private AudioCodec(byte id) {
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

    public static AudioCodec valueOfById(int id) {
        return (AudioCodec) map.get((byte) id);
    }

    public static EnumSet<AudioCodec> getConfigured() {
        return configured;
    }

}