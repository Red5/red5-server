/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.codec;

import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.mina.core.buffer.IoBuffer;

/**
 * Red5 video codec for the AVC (h264) video format. Stores DecoderConfigurationRecord and last keyframe.
 *
 * @author Tiago Jacobs (tiago@imdt.com.br)
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class AVCVideo extends AbstractVideo {

    /**
     * Video decoder configuration record to start the sequence. See ISO/IEC 14496-15, 5.2.4.1 for the description of
     * AVCDecoderConfigurationRecord
     */
    private FrameData decoderConfiguration;

    {
        codec = VideoCodec.AVC;
    }

    /** {@inheritDoc} */
    @Override
    public boolean canDropFrames() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public void reset() {
        softReset();
    }

    /** {@inheritDoc} */
    @Override
    public boolean addData(IoBuffer data, int timestamp) {
        log.trace("addData timestamp: {} remaining: {}", timestamp, data.remaining());
        boolean result = false;
        if (data.hasRemaining()) {
            // mark
            data.mark();
            // get frame type and AVC type
            byte frameType = data.get();
            byte avcType = data.get();
            // reset before reading into a frame farther down
            data.reset();
            // create mark for frame data
            data.mark();
            // check for keyframe
            if ((frameType & 0xf0) == FLV_FRAME_KEY) {
                if (isDebug) {
                    log.debug("Keyframe - AVC type: {}", avcType);
                }
                switch (avcType) {
                    case 1: // keyframe
                        //log.trace("Keyframe - keyframeTimestamp: {} {}", keyframeTimestamp, timestamp);
                        // get the time stamp and compare with the current value
                        if (timestamp != keyframeTimestamp) {
                            //log.trace("New keyframe");
                            // new keyframe
                            keyframeTimestamp = timestamp;
                            // if its a new keyframe, clear keyframe and interframe collections
                            softReset();
                        }
                        // store keyframe
                        keyframes.add(new FrameData(data));
                        break;
                    case 0: // configuration
                        if (isDebug) {
                            log.debug("Decoder configuration");
                        }
                        // Store AVCDecoderConfigurationRecord data
                        if (decoderConfiguration == null) {
                            decoderConfiguration = new FrameData(data);
                        } else {
                            decoderConfiguration.setData(data);
                        }
                        // new configuration, clear keyframe and interframe collections
                        softReset();
                        break;
                }
                //log.trace("Keyframes: {}", keyframes.size());
            } else if (bufferInterframes) {
                if (isDebug) {
                    log.debug("Interframe - AVC type: {}", avcType);
                }
                if (interframes == null) {
                    interframes = new CopyOnWriteArrayList<>();
                }
                try {
                    int lastInterframe = numInterframes.getAndIncrement();
                    //log.trace("Buffering interframe #{}", lastInterframe);
                    if (lastInterframe < interframes.size()) {
                        interframes.get(lastInterframe).setData(data);
                    } else {
                        interframes.add(new FrameData(data));
                    }
                } catch (Throwable e) {
                    log.warn("Failed to buffer interframe", e);
                }
                //log.trace("Interframes: {}", interframes.size());
            }
            // we handled the data
            result = true;
            // go back to where we started if we're marked
            if (data.markValue() > 0) {
                data.reset();
            }
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public IoBuffer getDecoderConfiguration() {
        return decoderConfiguration != null ? decoderConfiguration.getFrame() : null;
    }

}
