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
 * Audio codecs that Red5 supports; which includes some RTMP-E specific codecs.
 *
 * @author Art Clarke
 * @author Paul Gregoire (mondain@gmail.com)
 */
public enum AudioCodec {

    PCM((byte) 0) {

        @Override
        public String getMimeType() {
            return "raw ";
        }

    }, // pcm raw
    ADPCM((byte) 0x01) {

        @Override
        public String getMimeType() {
            return "g722"; // XXX(paul) not sure if this is correct
        }

    }, // adpcm
    MP3((byte) 0x02) {

        @Override
        public IAudioStreamCodec newInstance() {
            return new MP3Audio();
        }

        @Override
        public String getMimeType() {
            return ".mp3";
        }

    }, // MP3 / .mp3 / mp3
    PCM_LE((byte) 0x03), // pcm le
    NELLY_MOSER_16K((byte) 0x04) {

        @Override
        public String getMimeType() {
            return "NELL";
        }

    },
    NELLY_MOSER_8K((byte) 0x05) {

        @Override
        public String getMimeType() {
            return "NELL";
        }

    },
    NELLY_MOSER((byte) 0x06) {

        @Override
        public String getMimeType() {
            return "NELL";
        }

    }, // nelly moser legacy
    PCM_ALAW((byte) 0x07) {

        @Override
        public String getMimeType() {
            return "alaw";
        }

    }, // pcm alaw
    PCM_MULAW((byte) 0x08) {

        @Override
        public String getMimeType() {
            return "ulaw";
        }

    }, // pcm mulaw
    ExHeader((byte) 0x09) {

        @Override
        public IAudioStreamCodec newInstance() {
            return new ExtendedAudio(); // XXX(paul) will yield proper codec class after data parsing
        }

        @Override
        public int getFourcc() {
            return 9; // ExHd
        }

        @Override
        public String getMimeType() {
            return "ExHd";
        }

    }, // used to signal FOURCC mode
    AAC((byte) 0x0a) {

        @Override
        public IAudioStreamCodec newInstance() {
            return new AACAudio();
        }

        @Override
        public String getMimeType() {
            return "mp4a";
        }

    }, // AAC / mp4a / advanced audio codec
    SPEEX((byte) 0x0b) {

        @Override
        public IAudioStreamCodec newInstance() {
            return new SpeexAudio();
        }

        @Override
        public String getMimeType() {
            return "spx ";
        }

    }, // Speex / "spx " / speex
    MP2((byte) 0x0c) {

        @Override
        public String getMimeType() {
            return "mp2a";
        }

    }, // MP2 / mp2a / mpeg2 audio
    OPUS((byte) 0x0d) {

        @Override
        public IAudioStreamCodec newInstance() {
            return new OpusAudio();
        }

        @Override
        public String getMimeType() {
            return "Opus";
        }

    }, // Opus / opus
    MP3_8K((byte) 0x0e) {

        @Override
        public IAudioStreamCodec newInstance() {
            return new MP3Audio();
        }

        @Override
        public String getMimeType() {
            return ".mp3";
        }

    }, // mp3 8khz
    //DEVICE_SPECIFIC((byte) 0x0f), // device specific (reserved)
    L16((byte) 0x0f) {

        @Override
        public int getFourcc() {
            return 15;
        }

        @Override
        public String getMimeType() {
            return "L16 ";
        }

    }, // L16 audio / XXX(paul) update logic that used 0x09 previously for L16
    // RTMP-E specific that weren't already added previously
    AC3((byte) 0x10) {

        @Override
        public IAudioStreamCodec newInstance() {
            return new AC3Audio();
        }

        @Override
        public String getMimeType() {
            return "ac-3";
        }

    }, // AC3 / ac-3 / ac3
    EAC3((byte) 0x11) {

        @Override
        public IAudioStreamCodec newInstance() {
            return new EAC3Audio();
        }

        @Override
        public String getMimeType() {
            return "ec-3";
        }

    }, // EAC3 / ec-3 / eac3
    FLAC((byte) 0x12) {

        @Override
        public IAudioStreamCodec newInstance() {
            return new FLACAudio();
        }

        @Override
        public String getMimeType() {
            return "fLaC";
        }

    }; // FLAC / fLaC / flac

    /**
     * Codecs which have private / config data or type identifiers included.
     */
    private final static EnumSet<AudioCodec> configured = EnumSet.of(AAC, AC3, EAC3, FLAC);

    private final static Map<Byte, AudioCodec> map = new HashMap<>();

    private byte id;

    private int fourcc;

    private String mimeType;

    static {
        for (AudioCodec codec : AudioCodec.values()) {
            map.put(codec.id, codec);
        }
    }

    private AudioCodec(byte id) {
        this.id = id;
        this.fourcc = IOUtils.makeFourcc(getMimeType());
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
     * Returns the four character code for this codec.
     *
     * @return a {@link java.lang.String} object
     */
    public String getMimeType() {
        return mimeType;
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

    /**
     * Returns back the codec that corresponds to the given four character code.
     *
     * @param fourcc
     *            the four character code
     * @return the codec
     */
    public static AudioCodec valueOfByFourCc(int fourcc) {
        for (AudioCodec codec : AudioCodec.values()) {
            if (codec.getFourcc() == fourcc) {
                return codec;
            }
        }
        return null;
    }

    /**
     * <p>Getter for the field <code>configured</code>.</p>
     *
     * @return a {@link java.util.EnumSet} object
     */
    public static EnumSet<AudioCodec> getConfigured() {
        return configured;
    }

}
