/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.codec;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.io.IoConstants;

/**
 * Red5 audio codec for the AAC audio format.
 *
 * Stores the decoder configuration.
 *
 * <a href="https://en.wikipedia.org/wiki/Advanced_Audio_Coding">Advanced Audio Coding</a>
 * The following AAC profiles, denoted by their object types, are supported
 * 1 = main profile
 * 2 = low complexity, a.k.a., LC
 * 5 = high efficiency / scale band replication, a.k.a., HE / SBR
 *
 * @author Paul Gregoire (mondain@gmail.com)
 * @author Wittawas Nakkasem (vittee@hotmail.com)
 * @author Vladimir Hmelyoff (vlhm@splitmedialabs.com)
 */
public class AACAudio extends AbstractAudio {

    /** Constant <code>AAC_SAMPLERATES</code> */
    public static final int[] AAC_SAMPLERATES = { 96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350 };

    /**
     * Block of data (AAC DecoderConfigurationRecord)
     */
    private byte[] blockDataAACDCR;

    {
        codec = AudioCodec.AAC;
    }

    /** {@inheritDoc} */
    @Override
    public void reset() {
        blockDataAACDCR = null;
    }

    /** {@inheritDoc} */
    @Override
    public boolean canHandleData(IoBuffer data) {
        boolean result = false;
        if (data != null && data.limit() > 0) {
            byte flgs = data.get();
            byte hdr = data.get();
            int codecId = (flgs & IoConstants.MASK_SOUND_FORMAT) >> 4;
            // check for AAC codec id
            result = codecId == AudioCodec.AAC.getId();
            // attempt configuration parsing if we've identified the codec and have in-band data
            if (result) {
                if (hdr == 0) {
                    // rewind the buffer without causing an error with mark and reset
                    data.position(0);
                    blockDataAACDCR = new byte[data.remaining()]; // expect > 2 bytes
                    // example: AF 00 11 90
                    data.get(blockDataAACDCR);
                    // the sound "header" data is ignored for AAC and the bitstream is parsed instead
                    int objectType = (blockDataAACDCR[2] >> 3) & 0x1f; // five bits
                    int freqIndex = ((blockDataAACDCR[2] & 0x7) << 1) | ((blockDataAACDCR[3] >> 7) & 0x1);
                    channels = (blockDataAACDCR[3] & 0x78) >> 3;
                    sampleRate = AAC_SAMPLERATES[freqIndex];
                    log.info("aac config sample rate {} type {} channels {}", sampleRate, objectType, channels);
                } else {
                    // maybe mark that we weren't a config packet?
                }
            }
            // when the result is returned, an expected rewind of the buffer should be done
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean addData(IoBuffer data) {
        // if we don't have the AACDecoderConfigurationRecord stored
        if (blockDataAACDCR == null) {
            // set a mark
            data.mark();
            // attempt to parse the configuration
            canHandleData(data);
            // reset the data buffer mark
            data.reset();
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public IoBuffer getDecoderConfiguration() {
        if (blockDataAACDCR != null) {
            return IoBuffer.wrap(blockDataAACDCR);
        }
        return null;
    }

    @SuppressWarnings("unused")
    private static long sample2TC(long time, int sampleRate) {
        return (time * 1000L / sampleRate);
    }

}
