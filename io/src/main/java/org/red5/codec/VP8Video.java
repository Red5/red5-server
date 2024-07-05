/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.codec;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.io.IoConstants;

/**
 * Red5 video codec for the VP8 video format.
 *
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class VP8Video extends AbstractVideo {

    /**
     * I bit from the X byte of the payload descriptor.
     */
    protected static final byte I_BIT = (byte) 0x80;

    /**
     * K bit from the X byte of the payload descriptor.
     */
    protected static final byte K_BIT = (byte) 0x10;

    /**
     * L bit from the X byte of the payload descriptor.
     */
    protected static final byte L_BIT = (byte) 0x40;

    /**
     * I bit from the I byte of the payload descriptor.
     */
    protected static final byte M_BIT = (byte) 0x80;

    /**
     * S bit from the first byte of the payload descriptor.
     */
    protected static final byte S_BIT = (byte) 0x10;

    /**
     * T bit from the X byte of the payload descriptor.
     */
    protected static final byte T_BIT = (byte) 0x20;

    /**
     * X bit from the first byte of the payload descriptor.
     */
    protected static final byte X_BIT = (byte) 0x80;

    /**
     * Maximum length of a VP8 payload descriptor.
     */
    public static final int MAX_LENGTH = 6;

    {
        codec = VideoCodec.VP8;
    }

    /** {@inheritDoc} */
    @Override
    public boolean canDropFrames() {
        return true;
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
        boolean result = false;
        // go back to the beginning, this only works in non-multitrack scenarios
        if (data.position() > 0) {
            data.rewind();
        }
        // no data, no operation
        if (data.hasRemaining()) {
            // are we amf?
            if (!amf) {
                int remaining = data.remaining();
                // if we're not amf, figure out how to proceed based on data contents
                if (remaining > 7) {
                    // grab the first 8 bytes
                    data.mark();
                    byte[] vpxData = new byte[remaining];
                    data.get(vpxData);
                    // jump back to the starting pos
                    data.reset();
                    // int sz = getDesciptorSize(peek,0,peek.length);
                    boolean isKey = (vpxData[0] & S_BIT) == 0;
                    // expand the data to hold the enhanced rtmp bytes
                    data.expand(remaining + 5);
                    data.clear();
                    // prefix the data with amf markers
                    byte flg = (byte) 0b10000000;
                    if (isKey) {
                        // add frame type at position 4
                        flg = (byte) (flg | (VideoFrameType.KEYFRAME.getValue() << 4));
                        // add packet type at position 0
                        flg = (byte) (flg | VideoPacketType.CodedFramesX.getPacketType());
                    } else {
                        // add frame type at position 4
                        flg = (byte) (flg | (VideoFrameType.INTERFRAME.getValue() << 4));
                        // add packet type at position 0
                        flg = (byte) (flg | VideoPacketType.CodedFramesX.getPacketType());
                    }
                    // frame type and packet type need to be set
                    data.put(flg);
                    // add codec fourcc
                    data.putInt(codec.getFourcc());
                    // drop the vpxData in behind the header
                    data.put(vpxData);
                    // flip it
                    data.flip();
                } else {
                    log.warn("Remaining VP8 content was less than expected: {}", remaining);
                }
            }
            // get frame type (codec + type)
            byte frameType = data.get();
            // get sub frame / sequence type (config, keyframe, interframe)
            byte subFrameType = data.get();
            if ((frameType & IoConstants.MASK_VIDEO_CODEC) == codec.getId()) {
                // check for keyframe (we're not storing non-keyframes here)
                if ((frameType & IoConstants.MASK_VIDEO_FRAMETYPE) == FLV_FRAME_KEY) {
                    //log.trace("Key frame");
                    if (log.isDebugEnabled()) {
                        log.debug("Keyframe - VP8 type: {}", subFrameType);
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
                result = true;
            }
            // go back to where we started
            data.rewind();
        }
        return result;
    }

    /**
     * The size in bytes of the payload descriptor at offset in input. The size is between 1 and 6.
     *
     * @param input
     *            input
     * @param offset
     *            offset
     * @param length
     *            length
     * @return The size in bytes of the payload descriptor at offset in input, or -1 if the input is not valid
     */
    public static int getDesciptorSize(byte[] input, int offset, int length) {
        if ((input[offset] & X_BIT) == 0) {
            return 1;
        }
        int size = 2;
        if ((input[offset + 1] & I_BIT) != 0) {
            size++;
            if ((input[offset + 2] & M_BIT) != 0) {
                size++;
            }
        }
        if ((input[offset + 1] & L_BIT) != 0) {
            size++;
        }
        if ((input[offset + 1] & (T_BIT | K_BIT)) != 0) {
            size++;
        }
        return size;
    }

    public static boolean isKeyFrame(byte[] input, int offset) {
        // if set to 0 the frame is a key frame, if set to 1 its an interframe. Defined in [RFC6386]
        return (input[offset] & S_BIT) == 0;
    }

}
