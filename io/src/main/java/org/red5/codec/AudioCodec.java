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
 * Audio codecs that Red5 supports; which includes some RTMP-E specific codecs.
 *
 * @author Art Clarke
 * @author Paul Gregoire (mondain@gmail.com)
 */
public enum AudioCodec {

    PCM((byte) 0), ADPCM((byte) 0x01), // pcm / adpcm
    MP3((byte) 0x02) {

        @Override
        public IAudioStreamCodec newInstance() {
            return new MP3Audio();
        }

        @Override
        public int getFourcc() {
            return 778924083; // MP3 / .mp3
        }

    }, // mp3
    PCM_LE((byte) 0x03), // pcm le
    NELLY_MOSER_16K((byte) 0x04), NELLY_MOSER_8K((byte) 0x05), NELLY_MOSER((byte) 0x06), // nelly moser legacy
    PCM_ALAW((byte) 0x07), PCM_MULAW((byte) 0x08), // pcm alaw / mulaw
    ExHeader((byte) 0x09) {

        @Override
        public IAudioStreamCodec newInstance() {
            return new ExtendedAudio(); // XXX(paul) will yield proper codec class after data parsing
        }

        @Override
        public int getFourcc() {
            return 9; // ExHd
        }

    }, // used to signal FOURCC mode
    AAC((byte) 0x0a) {

        @Override
        public IAudioStreamCodec newInstance() {
            return new AACAudio();
        }

        @Override
        public int getFourcc() {
            return 1836069985; // AAC / mp4a
        }

    }, // advanced audio codec
    SPEEX((byte) 0x0b) {

        @Override
        public IAudioStreamCodec newInstance() {
            return new SpeexAudio();
        }

        @Override
        public int getFourcc() {
            return 1936750624; // Speex / "spx "
        }

    }, // speex
    MP2((byte) 0x0c), // mpeg2 audio
    OPUS((byte) 0x0d) {

        @Override
        public IAudioStreamCodec newInstance() {
            return new OpusAudio();
        }

        @Override
        public int getFourcc() {
            return 1332770163; // Opus
        }

    }, // opus
    MP3_8K((byte) 0x0e), // mp3 8khz
    //DEVICE_SPECIFIC((byte) 0x0f), // device specific (reserved)
    L16((byte) 0x0f), // L16 audio / XXX(paul) update logic that used 0x09 previously for L16
    // RTMP-E specific that weren't already added previously
    AC3((byte) 0x10) {

        @Override
        public IAudioStreamCodec newInstance() {
            return new AC3Audio();
        }

        @Override
        public int getFourcc() {
            return 1633889587; // AC3 / ac-3
        }

    }, // ac3
    EAC3((byte) 0x11) {

        @Override
        public IAudioStreamCodec newInstance() {
            return new EAC3Audio();
        }

        @Override
        public int getFourcc() {
            return 1700998451; // EAC3 / ec-3
        }

    }, // eac3
    FLAC((byte) 0x12) {

        @Override
        public IAudioStreamCodec newInstance() {
            return new FLACAudio();
        }

        @Override
        public int getFourcc() {
            return 1716281667; // FLAC / fLaC
        }

    }; // flac

    /**
     * Codecs which have private / config data or type identifiers included.
     */
    private final static EnumSet<AudioCodec> configured = EnumSet.of(AAC, OPUS, AC3, EAC3, FLAC, ExHeader);

    private final static Map<Byte, AudioCodec> map = new HashMap<>();

    private byte id;

    private int fourcc;

    static {
        for (AudioCodec codec : AudioCodec.values()) {
            map.put(codec.id, codec);
        }
    }

    private AudioCodec(byte id) {
        this.id = id;
    }

    /**
     * Returns a new instance of the codec.
     *
     * @return codec implementation
     */
    public IAudioStreamCodec newInstance() {
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
     * Returns back the codec that corresponds to the given id.
     *
     * @param id
     *            the id
     * @return the codec
     */
    public static AudioCodec valueOfById(int id) {
        return (AudioCodec) map.get((byte) id);
    }

    public static EnumSet<AudioCodec> getConfigured() {
        return configured;
    }

}