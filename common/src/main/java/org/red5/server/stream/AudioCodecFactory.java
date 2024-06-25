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
     * Create and return new audio codec applicable for byte buffer data
     *
     * @param data
     *            Byte buffer data
     * @return audio codec
     */
    public static IAudioStreamCodec getAudioCodec(IoBuffer data) {
        IAudioStreamCodec result = null;
        try {
            //get the codec identifying byte
            int codecId = (data.get() & 0xf0) >> 4;
            AudioCodec codec = AudioCodec.valueOfById(codecId);
            if (codec != null) {
                log.debug("Codec found: {}", codec);
                result = codec.newInstance();
                log.debug("Codec instance: {}", result);
            } else {
                log.warn("Codec not found for id: {}", codecId);
            }
        } catch (Exception ex) {
            log.error("Error creating codec instance", ex);
        } finally {
            data.rewind();
        }
        return result;
    }

}
