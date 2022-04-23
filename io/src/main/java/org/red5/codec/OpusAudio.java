/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2016 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package org.red5.codec;

import org.apache.mina.core.buffer.IoBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Red5 audio codec for the Opus audio format.
 *
 * Opus has no decoder configuration; its all in-band.
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class OpusAudio extends AbstractAudio {

    private static Logger log = LoggerFactory.getLogger(OpusAudio.class);

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

    // default to 48kHz and stereo
    private int index = 0, channels = 2;

    // ensure we store at least one config chunk; default is false, since Opus doesnt require configuration
    // 48k stereo is the default configuration.
    private volatile boolean needConfig;

    @Override
    public String getName() {
        return CODEC_NAME;
    }

    @Override
    public boolean canHandleData(IoBuffer data) {
        if (data.limit() == 0) {
            // Empty buffer
            return false;
        }
        byte first = data.get();
        boolean result = (((first & 0xf0) >> 4) == AudioCodec.OPUS.getId());
        data.rewind();
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean addData(IoBuffer data, int timestamp, boolean amf) {
        log.trace("addData timestamp: {} remaining: {} amf? {}", timestamp, data.remaining(), amf);
        if (data.hasRemaining()) {
            // mark starting position
            int start = data.position();
            // are we amf?
            if (!amf) {
                int remaining = data.remaining();
                // if we're not amf, figure out how to proceed based on data contents
                // slice-out the existing data
                IoBuffer slice = data.getSlice(start, remaining);
                // prefix the data with amf markers (two bytes)
                data.put((byte) (AudioCodec.OPUS.getId() << 4));
                // determine if we need to add config data (frequency and channels)
                if (needConfig) {
                    // expand the data to hold the amf markers
                    data.expand(remaining + 4);
                    data.put(new byte[] { (byte) 0, (byte) OPUS_SAMPLERATES[index], (byte) channels });
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
                // reset start (which technically will be the same position)
                start = data.position();
                // jump back to the starting pos
                data.position(start);
            }
        }
        return true;
    }

    @Override
    public IoBuffer getDecoderConfiguration() {
        return IoBuffer.wrap(new byte[] { (byte) OPUS_SAMPLERATES[index], (byte) channels });
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getChannels() {
        return channels;
    }

    public void setChannels(int channels) {
        this.channels = channels;
    }

    public boolean isNeedConfig() {
        return needConfig;
    }

    public void setNeedConfig(boolean needConfig) {
        this.needConfig = needConfig;
    }

}
