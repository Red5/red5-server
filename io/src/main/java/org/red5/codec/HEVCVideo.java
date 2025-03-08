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
 * Red5 video codec for the HEVC (h265) video format. Stores DecoderConfigurationRecord and last keyframe.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class HEVCVideo extends AbstractVideo implements IEnhancedRTMPVideoCodec {

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

    public void handleNonEnhanced(VideoFrameType type, IoBuffer data, int timestamp) {
        // get the codecs frame type
        data.rewind();
        byte hvcType = data.get();
        // reset back to the beginning after we got the hvc type
        data.rewind();

        //only called from super if codecs matched.
        //if((hvcType & 0x0f) != VideoCodec.HEVC.getId()) {
        //}

        if (isDebug) {
            log.debug("HEVC type: {}", hvcType);
        }
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
                // Store HEVCDecoderConfigurationRecord data
                if (decoderConfiguration == null) {
                    decoderConfiguration = new FrameData(data);
                } else {
                    decoderConfiguration.setData(data);
                }
                // new configuration, clear keyframe and interframe collections
                softReset();
                break;
            default:
                if (bufferInterframes) {
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
                break;
        }
    }

    @SuppressWarnings("incomplete-switch")
    public void handleFrame(VideoPacketType packetType, VideoFrameType frameType, IoBuffer data, int timestamp) {
        switch (packetType) {
            case SequenceStart:
                if (frameType == VideoFrameType.KEYFRAME) {
                    if (isDebug) {
                        log.debug("Decoder configuration");
                    }
                    // Store HEVCDecoderConfigurationRecord data
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
            default:
                // not handled
                break;
        }
        data.rewind();
    }

    //    /** {@inheritDoc} */
    //    @SuppressWarnings("incomplete-switch")
    //    @Override
    //    public boolean addData(IoBuffer data, int timestamp) {
    //        log.trace("{} addData timestamp: {} remaining: {}", codec.name(), timestamp, data.remaining());
    //        boolean result = false;
    //        // go back to the beginning, this only works in non-multitrack scenarios
    //        if (data.position() > 0) {
    //            data.rewind();
    //        }
    //        // no data, no operation
    //        if (data.hasRemaining()) {
    //            // mark the position before we get the flags
    //            data.mark();
    //            // get the first byte for v1 codec type or enhanced codec bit
    //            byte flg = data.get();
    //            // determine if we've got an enhanced codec
    //            enhanced = ByteNibbler.isBitSet(flg, 7);
    //            // for frame type we need get 3 bits
    //            int ft = ((flg & 0b01110000) >> 4);
    //            frameType = VideoFrameType.valueOf(ft);
    //            if (enhanced) {
    //                // get the packet type
    //                packetType = VideoPacketType.valueOf(flg & IoConstants.MASK_VIDEO_CODEC);
    //
    //                // get the fourcc
    //                //This is only a correct fourCC if the packet type is not command or multitrack.
    //                int fourcc = data.getInt();
    //                // reset back to the beginning after we got the fourcc
    //                data.reset();
    //                VideoCodec frameCodec = VideoCodec.valueOfByFourCc(fourcc);
    //                if(frameCodec!= codec) {
    //                	return false;
    //                }
    //
    //                if (isDebug) {
    //                    log.debug("{} - frame type: {} packet type: {}",frameCodec, frameType, packetType);
    //                }
    //                if (packetType != VideoPacketType.Metadata && frameType == VideoFrameType.COMMAND_FRAME) {
    //                	return true;
    //                }
    //                //Multitrack support not implemented for HEVC yet.
    //                //Abstract video should call abstract methods from it's 'Add data' method for handling the payload types it parses.
    //                switch (packetType) {
    //                    case SequenceStart:
    //                        if (frameType == VideoFrameType.KEYFRAME) {
    //                            if (isDebug) {
    //                                log.debug("Decoder configuration");
    //                            }
    //                            // Store HEVCDecoderConfigurationRecord data
    //                            if (decoderConfiguration == null) {
    //                                decoderConfiguration = new FrameData(data);
    //                            } else {
    //                                decoderConfiguration.setData(data);
    //                            }
    //                            // new sequence, clear keyframe and interframe collections
    //                            softReset();
    //                        }
    //                        break;
    //                    case CodedFramesX: // pass coded data without comp time offset
    //                        switch (frameType) {
    //                            case KEYFRAME: // keyframe
    //                                if (isDebug) {
    //                                    log.debug("Keyframe - keyframeTimestamp: {}", keyframeTimestamp);
    //                                }
    //                                // get the time stamp and compare with the current value
    //                                if (timestamp != keyframeTimestamp) {
    //                                    //log.trace("New keyframe");
    //                                    // new keyframe
    //                                    keyframeTimestamp = timestamp;
    //                                    // if its a new keyframe, clear keyframe and interframe collections
    //                                    softReset();
    //                                }
    //                                // store keyframe
    //                                keyframes.add(new FrameData(data));
    //                                break;
    //                            case INTERFRAME:
    //                                if (bufferInterframes) {
    //                                    if (isDebug) {
    //                                        log.debug("Interframe - timestamp: {}", timestamp);
    //                                    }
    //                                    if (interframes == null) {
    //                                        interframes = new CopyOnWriteArrayList<>();
    //                                    }
    //                                    try {
    //                                        int lastInterframe = numInterframes.getAndIncrement();
    //                                        //log.trace("Buffering interframe #{}", lastInterframe);
    //                                        if (lastInterframe < interframes.size()) {
    //                                            interframes.get(lastInterframe).setData(data);
    //                                        } else {
    //                                            interframes.add(new FrameData(data));
    //                                        }
    //                                    } catch (Throwable e) {
    //                                        log.warn("Failed to buffer interframe", e);
    //                                    }
    //                                    //log.trace("Interframes: {}", interframes.size());
    //                                }
    //                                break;
    //                        }
    //                        break;
    //                    case CodedFrames: // pass coded data
    //                        int compTimeOffset = (data.get() << 16 | data.get() << 8 | data.get());
    //                        switch (frameType) {
    //                            case KEYFRAME: // keyframe
    //                                if (isDebug) {
    //                                    log.debug("Keyframe - keyframeTimestamp: {} compTimeOffset: {}", keyframeTimestamp, compTimeOffset);
    //                                }
    //                                keyframes.add(new FrameData(data, compTimeOffset));
    //                                break;
    //                        }
    //                    default:
    //                        // not handled
    //                        break;
    //                }
    //            } else {
    //                // get the codecs frame type
    //                byte hvcType = data.get();
    //                // reset back to the beginning after we got the hvc type
    //                data.reset();
    //                if((hvcType & 0x0f) != VideoCodec.HEVC.getId()) {
    //                	return false;
    //                }
    //                if (isDebug) {
    //                    log.debug("HEVC type: {}", hvcType);
    //                }
    //                switch (hvcType) {
    //                    case 1: // keyframe
    //                        //log.trace("Keyframe - keyframeTimestamp: {} {}", keyframeTimestamp, timestamp);
    //                        // get the time stamp and compare with the current value
    //                        if (timestamp != keyframeTimestamp) {
    //                            //log.trace("New keyframe");
    //                            // new keyframe
    //                            keyframeTimestamp = timestamp;
    //                            // if its a new keyframe, clear keyframe and interframe collections
    //                            softReset();
    //                        }
    //                        // store keyframe
    //                        keyframes.add(new FrameData(data));
    //                        break;
    //                    case 0: // configuration
    //                        if (isDebug) {
    //                            log.debug("Decoder configuration");
    //                        }
    //                        // Store HEVCDecoderConfigurationRecord data
    //                        if (decoderConfiguration == null) {
    //                            decoderConfiguration = new FrameData(data);
    //                        } else {
    //                            decoderConfiguration.setData(data);
    //                        }
    //                        // new configuration, clear keyframe and interframe collections
    //                        softReset();
    //                        break;
    //                    default:
    //                        if (bufferInterframes) {
    //                            if (isDebug) {
    //                                log.debug("Interframe - HEVC type: {}", hvcType);
    //                            }
    //                            if (interframes == null) {
    //                                interframes = new CopyOnWriteArrayList<>();
    //                            }
    //                            try {
    //                                int lastInterframe = numInterframes.getAndIncrement();
    //                                //log.trace("Buffering interframe #{}", lastInterframe);
    //                                if (lastInterframe < interframes.size()) {
    //                                    interframes.get(lastInterframe).setData(data);
    //                                } else {
    //                                    interframes.add(new FrameData(data));
    //                                }
    //                            } catch (Throwable e) {
    //                                log.warn("Failed to buffer interframe", e);
    //                            }
    //                            //log.trace("Interframes: {}", interframes.size());
    //                        }
    //                        break;
    //                }
    //            }
    //            //log.trace("Keyframes: {}", keyframes.size());
    //            //If we got this far, we handled the data. On mismatched codecs, we returned above.
    //            result = true;
    //        }
    //        // reset the position
    //        data.rewind();
    //        return result;
    //    }

    /** {@inheritDoc} */
    @Override
    public IoBuffer getDecoderConfiguration() {
        return decoderConfiguration != null ? decoderConfiguration.getFrame() : null;
    }

}
