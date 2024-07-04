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

    /**
     * AV1 video codec constant
     */
    static final String CODEC_NAME = "AV1";

    public static final byte[] AV1_KEYFRAME_PREFIX = new byte[] { 0x0d, 0x01 };

    public static final byte[] AV1_FRAME_PREFIX = new byte[] { 0x2d, 0x01 };

    // buffer holding OBU's
    @SuppressWarnings("unused")
    private IoBuffer obuBuffer;

    {
        codec = VideoCodec.AV1;
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return CODEC_NAME;
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
        if (data.hasRemaining()) {
            // mark
            data.mark();
            // get flags
            byte flg = data.get();
            // reset before reading into a frame farther down
            data.reset();
            // determine if we've got an enhanced codec
            enhanced = ByteNibbler.isBitSet(flg, 15);
            // for frame type we need get 3 bits
            int ft = ((flg & 0b01110000) >> 4);
            VideoFrameType frameType = VideoFrameType.valueOf(ft);
            // create mark for frame data
            data.mark();
            // check for keyframe or other non-interframe
            if ((flg & IoConstants.MASK_VIDEO_FRAMETYPE) == 0x10 || (enhanced && frameType != VideoFrameType.INTERFRAME)) {
                if (isDebug) {
                    log.debug("{} - AV1", frameType);
                }
                if (enhanced) {
                    packetType = VideoPacketType.valueOf(flg & IoConstants.MASK_VIDEO_CODEC);
                    switch (frameType) {
                        // XXX implement this to look at the kf flag in AV1 data
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
                    }
                } else {
                    // no non-enhanced support att
                }
                //log.trace("Keyframes: {}", keyframes.size());
            } else if (bufferInterframes) {
                if (isDebug) {
                    log.debug("Interframe - AV1");
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
