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
    @SuppressWarnings("incomplete-switch")
    @Override
    public boolean addData(IoBuffer data, int timestamp) {
        log.trace("{} addData timestamp: {} remaining: {}", codec.name(), timestamp, data.remaining());
        boolean result = false;
        // go back to the beginning, this only works in non-multitrack scenarios
        if (data.position() > 0) {
            data.rewind();
        }
        int fourcc = 0;
        // no data, no operation
        if (data.hasRemaining()) {
            // mark the position before we get the flags
            data.mark();
            // get the first byte for v1 codec type or enhanced codec bit
            byte flg = data.get();
            // determine if we've got an enhanced codec
            enhanced = ByteNibbler.isBitSet(flg, 7); // network order so its rtl
            // for frame type we need get 3 bits
            int ft = ((flg & 0b01110000) >> 4);
            frameType = VideoFrameType.valueOf(ft);
            if (enhanced) {
                // get the packet type
                packetType = VideoPacketType.valueOf(flg & IoConstants.MASK_VIDEO_CODEC);
                if(frameType.getValue() < 5 &&  packetType.getPacketType() < 5 ) {
                    // get the fourcc
                    fourcc = data.getInt();                
                    result = (codec.getFourcc() == fourcc);
                    if(!result) {
                        data.reset();
                        return result;
                    }
                }
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
                            // Store AVCDecoderConfigurationRecord data
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
                        data.reset();
                        switch (frameType) {
                            case KEYFRAME: // keyframe
                                if (isDebug) {
                                    log.debug("Keyframe - keyframeTimestamp: {} compTimeOffset: {}", keyframeTimestamp, compTimeOffset);
                                }
                                // get the time stamp and compare with the current value
                                if (timestamp != keyframeTimestamp) {
                                    //log.trace("New keyframe");
                                    // new keyframe
                                    keyframeTimestamp = timestamp;
                                    // if its a new keyframe, clear keyframe and interframe collections
                                    softReset();
                                }
                                keyframes.add(new FrameData(data, compTimeOffset));
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
                    default:
                        // not handled
                        break;
                }
                
                
            } else if((flg & IoConstants.MASK_VIDEO_CODEC) == codec.getId()){
                result = true;
                // get the codecs frame type
                byte avcType = data.get();
                // reset back to the beginning after we got the avc type
                data.reset();
                if (isDebug) {
                    log.debug("AVC type: {}", avcType);
                }
                switch (avcType) {
                    case 1: // VCL video coding layer, 
                        frameType = VideoFrameType.valueOf((flg & IoConstants.MASK_VIDEO_FRAMETYPE) >> 4);
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
                                keyframes.add(new FrameData(data));
                                break;
                            case INTERFRAME:
                                if (bufferInterframes) {
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
                                break;
                        } 
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
                    default:
                        break;
                }
            }
            //log.trace("Keyframes: {}", keyframes.size());
        }
        // reset the position
        data.rewind();
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public IoBuffer getDecoderConfiguration() {
        return decoderConfiguration != null ? decoderConfiguration.getFrame() : null;
    }

}
