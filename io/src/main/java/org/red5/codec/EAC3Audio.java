/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.codec;

import org.apache.mina.core.buffer.IoBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Red5 audio codec for the Enhanced AC3 audio format.
 *
 * Stores the decoder configuration.
 *
 * AC-3/E-AC-3 - <https://en.wikipedia.org/wiki/Dolby_Digital>
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class EAC3Audio extends AbstractAudio {

    private static Logger log = LoggerFactory.getLogger(EAC3Audio.class);

    /**
     * Block of data private to the codec.
     */
    private byte[] privateData;

    public EAC3Audio() {
        codec = AudioCodec.EAC3;
        this.reset();
    }

    @Override
    public String getName() {
        return codec.name();
    }

    @Override
    public void reset() {
        privateData = null;
    }

    @Override
    public boolean canHandleData(IoBuffer data) {
        if (data.limit() == 0) {
            // Empty buffer
            return false;
        }
        byte first = data.get();
        boolean result = (((first & 0xf0) >> 4) == codec.getId());
        data.rewind();
        return result;
    }

    @Override
    public boolean addData(IoBuffer data) {
        if (data.hasRemaining()) {
            // mark
            int start = data.position();
            // ensure we are at the beginning
            data.rewind();
            byte frameType = data.get();
            log.trace("Frame type: {}", frameType);
            byte header = data.get();
            // if we don't have the privateData stored
            if (privateData == null) {
                if ((((frameType & 0xf0) >> 4) == codec.getId()) && (header == 0)) {
                    // back to the beginning
                    data.rewind();
                    privateData = new byte[data.remaining()];
                    data.get(privateData);
                }
            }
            data.position(start);
        }
        return true;
    }

    @Override
    public IoBuffer getDecoderConfiguration() {
        if (privateData == null) {
            return null;
        }
        IoBuffer result = IoBuffer.allocate(4);
        result.setAutoExpand(true);
        result.put(privateData);
        result.rewind();
        return result;
    }

}
