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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Red5 video codec for the AV1 video format.
 *
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class AV1Video extends AbstractVideo {

    private static Logger log = LoggerFactory.getLogger(AV1Video.class);

    // not sure if this is needed or not
    private FrameData decoderConfiguration;

    // buffer holding OBU's
    @SuppressWarnings("unused")
    private IoBuffer obuBuffer;

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
            enhanced = ByteNibbler.isBitSet(flg, 15);
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
                            // Store AV1 DecoderConfigurationRecord data
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
            } else {
                // no non-enhanced codec suspport yet
            }
            //log.trace("Keyframes: {}", keyframes.size());
            // we handled the data
            result = true;
        }
        return result;
    }

    /*
     * public ByteBuffer[] readFrames(ByteBuffer packet) { ByteBuffer[] obuList = new ByteBuffer[0]; boolean isFirstOBUFragment = packet.get(0) & LEB128.MSB_BITMASK > 0; for (int i =
     * 1; i <= packet.get(0) & LEB128.SEVEN_LSB_BITMASK; i++) { obuList = pushOBUElement(isFirstOBUFragment, packet.get(i), obuList); } if (packet.get(0) & LEB128.MSB_BITMASK > 0) { //
     * Take copy of OBUElement that is being cached if (obuBuffer == null) { return obuList; } obuBuffer.put(packet.array(), 1, packet.get(0) & LEB128.SEVEN_LSB_BITMASK);
     * packet.position(packet.position() + packet.get(0) & LEB128.SEVEN_LSB_BITMASK); packet.compact(); obuList = pushOBUElement(isFirstOBUFragment, obuBuffer.array(), obuList);
     * obuBuffer = null; } return obuList; } private ByteBuffer[] pushOBUElement(boolean isFirstOBUFragment, ByteBuffer obuElement, ByteBuffer[] obuList) { if (isFirstOBUFragment) {
     * isFirstOBUFragment = false; // Discard pushed because we don't have a fragment to combine it with if (obuBuffer == null) { return obuList; } obuElement.put(obuBuffer.array());
     * obuBuffer = null; } return append(obuList, obuElement); } private ByteBuffer[] append(ByteBuffer[] obuList, ByteBuffer obuElement) { ByteBuffer[] newObuList = new
     * ByteBuffer[obuList.length + 1]; System.arraycopy(obuList, 0, newObuList, 0, obuList.length); newObuList[obuList.length] = obuElement; return newObuList; }
     */

}
