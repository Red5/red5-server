/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2022 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.codec;

import org.apache.mina.core.buffer.IoBuffer;
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

    public static final byte[] AV1_KEYFRAME_PREFIX = new byte[] { 0x0a, 0x01 };

    public static final byte[] AV1_FRAME_PREFIX = new byte[] { 0x2a, 0x01 };

    // buffer holding OBU's
    @SuppressWarnings("unused")
    private IoBuffer obuBuffer;

    public AV1Video() {
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
    @Override
    public boolean canHandleData(IoBuffer data) {
        // XXX also add support for handling non-amf AV1, maybe
        if (data.hasRemaining()) {
            byte first = data.get();
            data.rewind();
            return ((first & 0x0f) == VideoCodec.AV1.getId());
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean addData(IoBuffer data, int timestamp) {
        //log.trace("addData timestamp: {} remaining: {}", timestamp, data.remaining());
        return addData(data, timestamp, true);
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
                if (remaining > 7) {
                    // grab the first 8 bytes
                    byte[] peek = new byte[8];
                    data.get(peek);
                    // int sz = getDesciptorSize(peek,0,peek.length);
                    boolean isKey = isKeyFrame(peek, 0);
                    // jump back to the starting pos
                    data.position(start);
                    // slice-out the existing data
                    IoBuffer slice = data.getSlice(start, remaining);
                    log.info("Data start: {} post-slice: {}", start, data.position());
                    // expand the data by two to hold the amf markers
                    data.expand(remaining + 2);
                    // prefix the data with amf markers (two bytes)
                    if (isKey) {
                        // AV1 keyframe
                        data.put(AV1_KEYFRAME_PREFIX);
                    } else {
                        // AV1 non-keyframe
                        data.put(AV1_FRAME_PREFIX);
                    }
                    // drop the slice in behind the prefix
                    data.put(slice);
                    // flip it
                    data.flip();
                    // reset start (which technically will be the same position)
                    start = data.position();
                } else {
                    log.warn("Remaining AV1 content was less than expected: {}", remaining);
                }
                // jump back to the starting pos
                data.position(start);
            }
            // get frame type (codec + type)
            byte frameType = data.get();
            // get sub frame / sequence type (config, keyframe, interframe)
            byte subFrameType = data.get();
            if ((frameType & 0x0f) == VideoCodec.AV1.getId()) {
                // check for keyframe (we're not storing non-keyframes here)
                if ((frameType & 0xf0) == FLV_FRAME_KEY) {
                    //log.trace("Key frame");
                    if (log.isDebugEnabled()) {
                        log.debug("Keyframe - AV1 type: {}", subFrameType);
                    }
                    // rewind
                    data.rewind();
                    switch (subFrameType) {
                        case 1: // keyframe
                            //log.trace("Keyframe - keyframeTimestamp: {} {}", keyframeTimestamp, timestamp);
                            // get the time stamp and compare with the current value
                            if (timestamp != keyframeTimestamp) {
                                //log.trace("New keyframe");
                                // new keyframe
                                keyframeTimestamp = timestamp;
                                keyframes.clear();
                            }
                            // store keyframe
                            keyframes.add(new FrameData(data));
                            break;
                        case 0: // no decoder configuration for vp8
                            //log.trace("Decoder configuration");
                            break;
                    }
                    //log.trace("Keyframes: {}", keyframes.size());
                }
            } else {
                // not AV1 data
                log.debug("Non-AV1 data, rejecting");
                // go back to where we started
                data.position(start);
                return false;
            }
            // go back to where we started
            data.position(start);
        }
        return true;
    }

    private boolean isKeyFrame(byte[] data, int index) {
        // XXX implement this to look at the kf flag in AV1 data
        return true;
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
