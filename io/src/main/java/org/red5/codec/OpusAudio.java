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
 * Red5 audio codec for the Opus audio format.
 *
 * Opus has no decoder configuration; its all in-band.
 *
 * <a href="https://opus-codec.org/">Opus audio</a>
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class OpusAudio extends AbstractAudio {

    /**
     * Sample rates:
       <pre>
        Abbreviation            Audio bandwidth     Effective sample rate
        NB (narrowband)         4 kHz               8 kHz
        MB (medium-band)        6 kHz               12 kHz
        WB (wideband)           8 kHz               16 kHz
        SWB (super-wideband)    12 kHz              24 kHz
        FB (fullband)           20 kHz[nb 2]        48 kHz
       </pre>
     */
    public static final int[] OPUS_SAMPLERATES = { 48000, 24000, 16000, 12000, 8000 };

    static final String CODEC_NAME = "Opus";

    // default to 48kHz
    private int index = 0;

    // ensure we store at least one config chunk; default is false, since Opus doesnt require configuration
    // 48k stereo is the default configuration.
    private volatile boolean needConfig;

    {
        codec = AudioCodec.OPUS;
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return CODEC_NAME;
    }

    /** {@inheritDoc} */
    @Override
    public boolean canHandleData(IoBuffer data) {
        boolean result = false;
        if (data != null && data.limit() > 0) {
            if (data.remaining() < 2) {
                return false;
            }
            byte flgs = data.get();
            byte hdr = data.get();
            result = (((flgs & IoConstants.MASK_SOUND_FORMAT) >> 4) == AudioCodec.OPUS.getId());
            if (result && hdr == 0) {
                // we have an opus header
                log.debug("Received opus header");
                // set the sample rate and channels
                if (data.remaining() < 2) {
                    return false;
                }
                index = data.get();
                channels = data.get();
                if (index < 0 || index >= OPUS_SAMPLERATES.length) {
                    return false;
                }
                sampleRate = OPUS_SAMPLERATES[index];
                log.info("opus sample rate {} channels {}", sampleRate, channels);
            }
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean addData(IoBuffer data, int timestamp, boolean amf) {
        log.trace("addData timestamp: {} remaining: {} amf? {}", timestamp, data.remaining(), amf);
        if (data.hasRemaining()) {
            // are we amf?
            if (!amf) {
                // mark starting position
                data.mark();
                int remaining = data.remaining();
                // if we're not amf, figure out how to proceed based on data contents
                // slice-out the existing data
                IoBuffer slice = data.getSlice(data.position(), remaining);
                // prefix the data with amf markers (two bytes)
                data.put((byte) (AudioCodec.OPUS.getId() << 4));
                // determine if we need to add config data (frequency and channels)
                if (needConfig) {
                    // expand the data to hold the amf markers
                    data.expand(remaining + 4);
                    // sample rate index and channels are the primary config data
                    data.put(new byte[] { (byte) index, (byte) channels });
                    // flip config flag
                    needConfig = false;
                } else {
                    data.expand(remaining + 2);
                    data.put((byte) 0x01);
                }
                // drop the slice in behind the prefix
                data.put(slice);
                // flip it
                data.flip();
                // reset the mark / position
                data.reset();
            }
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public IoBuffer getDecoderConfiguration() {
        return IoBuffer.wrap(new byte[] { (byte) index, (byte) channels });
    }

    /**
     * <p>Getter for the field <code>index</code>.</p>
     *
     * @return a int
     */
    public int getIndex() {
        return index;
    }

    /**
     * <p>Setter for the field <code>index</code>.</p>
     *
     * @param index a int
     */
    public void setIndex(int index) {
        this.index = index;
    }

    /**
     * <p>isNeedConfig.</p>
     *
     * @return a boolean
     */
    public boolean isNeedConfig() {
        return needConfig;
    }

    /**
     * <p>Setter for the field <code>needConfig</code>.</p>
     *
     * @param needConfig a boolean
     */
    public void setNeedConfig(boolean needConfig) {
        this.needConfig = needConfig;
    }

}
