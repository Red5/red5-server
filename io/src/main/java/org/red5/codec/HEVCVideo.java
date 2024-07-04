/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.codec;

import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.io.IoConstants;
import org.red5.util.ByteNibbler;

/**
 * Red5 video codec for the HEVC (h265) video format. Stores DecoderConfigurationRecord and last keyframe.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class HEVCVideo extends AbstractVideo {

    /**
     * Video decoder configuration record to start the sequence. See ISO/IEC 14496-15, 8.3.3.1.2 for the description of
     * HEVCDecoderConfigurationRecord
     */
    private FrameData decoderConfiguration;

    {
        codec = VideoCodec.HEVC;
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
    @SuppressWarnings("incomplete-switch")
    @Override
    public boolean addData(IoBuffer data, int timestamp) {
        log.trace("{} addData timestamp: {} remaining: {}", codec.name(), timestamp, data.remaining());
        boolean result = false;
        if (data.hasRemaining()) {
            // mark
            data.mark();
            // get frame type and HEVC type
            byte flg = data.get();
            byte hvcType = data.get();
            // reset before reading into a frame farther down
            data.reset();
            // determine if we've got an enhanced codec
            enhanced = ByteNibbler.isBitSet(flg, 15);
            // for frame type we need get 3 bits
            int ft = ((flg & 0b01110000) >> 4);
            frameType = VideoFrameType.valueOf(ft);
            // create mark for frame data
            data.mark();
            // check for keyframe or other non-interframe
            if ((flg & IoConstants.MASK_VIDEO_FRAMETYPE) == 0x10 || (enhanced && frameType != VideoFrameType.INTERFRAME)) {
                if (isDebug) {
                    log.debug("{} - HEVC type: {}", frameType, hvcType);
                }
                if (enhanced) {
                    packetType = VideoPacketType.valueOf(flg & IoConstants.MASK_VIDEO_CODEC);
                    switch (frameType) {
                        case KEYFRAME: // keyframe
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
                        case RESERVED: // configuration
                            if (isDebug) {
                                log.debug("Decoder configuration");
                            }
                            // Store HEVCDecoderConfigurationRecord data
                            if (decoderConfiguration == null) {
                                decoderConfiguration = new FrameData(data);
                            } else {
                                decoderConfiguration.setData(data);
                            }
                            softReset();
                            break;
                    }
                } else {
                    switch (hvcType) {
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
                }
                //log.trace("Keyframes: {}", keyframes.size());
            } else if (bufferInterframes) {
                if (isDebug) {
                    log.debug("Interframe - HEVC type: {}", hvcType);
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
