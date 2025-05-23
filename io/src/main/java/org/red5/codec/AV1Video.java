/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2022 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
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
 * Red5 video codec for the AV1 video format. Portions of this AV1 code are based on the work of the Pion project.
 *
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class AV1Video extends AbstractVideo {

    /* AV1
       https://aomediacodec.github.io/av1-isobmff/v1.3.0.html#av1codecconfigurationbox-definition

       class AV1CodecConfigurationBox extends Box('av1C')
        {
        AV1CodecConfigurationRecord av1Config;
        }

        aligned(8) class AV1CodecConfigurationRecord {
            unsigned int(1) marker = 1;
            unsigned int(7) version = 1;
            unsigned int(3) seq_profile;
            unsigned int(5) seq_level_idx_0;
            unsigned int(1) seq_tier_0;
            unsigned int(1) high_bitdepth;
            unsigned int(1) twelve_bit;
            unsigned int(1) monochrome;
            unsigned int(1) chroma_subsampling_x;
            unsigned int(1) chroma_subsampling_y;
            unsigned int(2) chroma_sample_position;
            unsigned int(3) reserved = 0;

            unsigned int(1) initial_presentation_delay_present;
            if (initial_presentation_delay_present) {
                unsigned int(4) initial_presentation_delay_minus_one;
            } else {
                unsigned int(4) reserved = 0;
            }

            unsigned int(8) configOBUs[];
        }
    */
    private FrameData decoderConfiguration;

    {
        codec = VideoCodec.AV1;
    }

    /** {@inheritDoc} */
    @Override
    public boolean canDropFrames() {
        return true;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("incomplete-switch")
    @Override
    public boolean addData(IoBuffer data, int timestamp) {
        log.trace("{} addData timestamp: {} remaining: {} pos: {}", codec.name(), timestamp, data.remaining(), data.position());
        boolean result = false;
        // go back to the beginning, this only works in non-multitrack scenarios
        if (data.position() > 0) {
            data.rewind();
        }
        // no data, no operation
        if (data.hasRemaining()) {
            // mark the position before we get the flags
            data.mark();
            // get the first byte for v1 codec type or enhanced codec bit
            byte flg = data.get();
            // determine if we've got an enhanced codec
            enhanced = ByteNibbler.isBitSet(flg, 7);
            // for frame type we need get 3 bits
            int ft = ((flg & 0b01110000) >> 4);
            frameType = VideoFrameType.valueOf(ft);
            if (enhanced) {
                // get the packet type
                packetType = VideoPacketType.valueOf(flg & IoConstants.MASK_VIDEO_CODEC);
                // get the fourcc
                int fourcc = data.getInt();
                // reset back to the beginning after we got the fourcc
                data.reset();
                if (isDebug) {
                    log.debug("{} - frame type: {} packet type: {}", VideoCodec.valueOfByFourCc(fourcc), frameType, packetType);
                }
                switch (packetType) {
                    case SequenceStart:
                        if (frameType == VideoFrameType.KEYFRAME) {
                            if (isDebug) {
                                log.debug("Decoder configuration");
                            }
                            // Store AV1 DecoderConfigurationRecord data, if one exists
                            if (decoderConfiguration == null) {
                                decoderConfiguration = new FrameData(data);
                            } else {
                                decoderConfiguration.setData(data);
                            }
                            // new sequence, clear keyframe and interframe collections
                            softReset();
                        }
                        break;
                    case CodedFramesX: // pass coded data without comp time offset
                        switch (frameType) {
                            case KEYFRAME: // keyframe
                                if (isDebug) {
                                    log.debug("Keyframe - keyframeTimestamp: {}", keyframeTimestamp);
                                }
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
                            case INTERFRAME:
                                if (bufferInterframes) {
                                    if (isDebug) {
                                        log.debug("Interframe - timestamp: {}", timestamp);
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
                                break;
                        }
                        break;
                    case CodedFrames: // pass coded data
                        int compTimeOffset = (data.get() << 16 | data.get() << 8 | data.get());
                        switch (frameType) {
                            case KEYFRAME: // keyframe
                                if (isDebug) {
                                    log.debug("Keyframe - keyframeTimestamp: {} compTimeOffset: {}", keyframeTimestamp, compTimeOffset);
                                }
                                keyframes.add(new FrameData(data, compTimeOffset));
                                break;
                        }
                        break;
                }
            } else {
                // no non-enhanced codec suspport yet
            }
            //log.trace("Keyframes: {}", keyframes.size());
            // we handled the data
            result = true;
        }
        // reset the position
        data.rewind();
        return result;
    }

}
