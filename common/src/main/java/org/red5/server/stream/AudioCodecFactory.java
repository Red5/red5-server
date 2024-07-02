/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.stream;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.codec.AudioCodec;
import org.red5.codec.IAudioStreamCodec;
import org.red5.io.IoConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for audio codecs. Creates and returns audio codecs
 *
 * @author The Red5 Project
 * @author Vladimir Hmelyoff (vlhm@splitmedialabs.com)
 */
public class AudioCodecFactory {
    /**
     * Object key
     */
    public static final String KEY = "audioCodecFactory";

    /**
     * Logger for audio factory
     */
    private static Logger log = LoggerFactory.getLogger(AudioCodecFactory.class);

    /**
     * Create and return new audio codec applicable for byte buffer data; return the codec and its configuration if
     * available.
     *
     * SoundFormat: UB[4] Format of SoundData
     *
     * Standard RTMP bits (for SoundFormat != 9):
     * soundRate = UB[2]
     * soundSize = UB[1]
     * soundType = UB[1]
     *
     * Enhanced RTMP bits:
     * audioPacketType = UB[4] as AudioPacketType
     * if audioPacketType == Multitrack
     *   audioMultitrackType = UB[4] as AvMultitrackType
     *     audioPacketType = UB[4] as AudioPacketType
     *     if audioMultitrackType != AvMultitrackType.ManyTracksManyCodecs
     *       audioFourCc = FOURCC as AudioFourCc
     * else if audioPacketType != Multitrack
     *   audioFourCc = FOURCC as AudioFourCc
     *
     * Format 3, linear PCM, stores raw PCM samples. If the data is 8-bit, the samples are unsigned bytes. If the data
     * is 16-bit, the samples are stored as little endian, signed numbers. If the data is stereo, left and right
     * samples are stored interleaved: left - right - left - right - and so on.
     *
     * Format 0 PCM is the same as format 3 PCM, except that format 0 stores 16-bit PCM samples in the endian order of
     * the platform on which the file was created. For this reason, format 0 is not recommended for use.
     *
     * Nellymoser 8-kHz and 16-kHz are special cases 8 and 16 sampling rates are not supported in other formats, and
     * the SoundRate bits can’t represent this value. When Nellymoser 8-kHz or Nellymoser 16-kHz is specified in
     * SoundFormat, the SoundRate and SoundType fields are ignored. For other Nellymoser sampling rates, specify the
     * normal Nellymoser SoundFormat and use the SoundRate and SoundType fields as usual.
     *
     * If the SoundFormat indicates AAC, the SoundType should be set to 1 (stereo) and the SoundRate should be set to 3
     * (44 kHz). However, this does not mean that AAC audio in FLV is always stereo, 44 kHz data. Instead, the Flash
     * Player ignores these values and extracts the channel and sample rate data is encoded in the AAC bitstream.
     *
     * @param data
     *            Byte buffer data
     * @return audio codec
     */
    public static IAudioStreamCodec getAudioCodec(IoBuffer data) {
        IAudioStreamCodec result = null;
        try {
            // get the codec identifying byte
            data.mark();
            byte c = data.get();
            data.reset();
            int codecId = (c & IoConstants.MASK_SOUND_FORMAT) >> 4;
            AudioCodec codec = AudioCodec.valueOfById(codecId);
            if (codec != null) {
                log.debug("Codec found: {}", codec);
                // this will be reset if the codec cannot handle the data
                result = codec.newInstance();
                // set mark first
                data.mark();
                // check if the codec can handle the data
                if (result.canHandleData(data)) {
                    log.debug("Codec {} can handle the data", result);
                } else {
                    log.warn("Codec {} cannot handle data", codec);
                    result = null;
                }
                // reset the data buffer mark
                data.reset();
            } else {
                log.warn("Codec not found for id: {}", codecId);
            }
        } catch (Exception ex) {
            log.error("Error creating codec instance", ex);
        } finally {
            if (data.markValue() > 0) {
                data.reset();
            }
        }
        return result;
    }

}
