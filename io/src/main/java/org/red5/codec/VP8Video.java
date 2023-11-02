/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.codec;

import org.apache.mina.core.buffer.IoBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Red5 video codec for the VP8 video format.
 *
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class VP8Video extends AbstractVideo {

    private static Logger log = LoggerFactory.getLogger(VP8Video.class);

    /**
     * VP8 video codec constant
     */
    static final String CODEC_NAME = "VP8";

    public static final byte[] VP8_KEYFRAME_PREFIX = new byte[] { 0x18, 0x01 };

    public static final byte[] VP8_FRAME_PREFIX = new byte[] { 0x28, 0x01 };

    /**
     * I bit from the X byte of the payload descriptor.
     */
    private static final byte I_BIT = (byte) 0x80;

    /**
     * K bit from the X byte of the payload descriptor.
     */
    private static final byte K_BIT = (byte) 0x10;

    /**
     * L bit from the X byte of the payload descriptor.
     */
    private static final byte L_BIT = (byte) 0x40;

    /**
     * I bit from the I byte of the payload descriptor.
     */
    private static final byte M_BIT = (byte) 0x80;

    /**
     * Maximum length of a VP8 payload descriptor.
     */
    public static final int MAX_LENGTH = 6;

    /**
     * S bit from the first byte of the payload descriptor.
     */
    private static final byte S_BIT = (byte) 0x10;

    /**
     * T bit from the X byte of the payload descriptor.
     */
    private static final byte T_BIT = (byte) 0x20;

    /**
     * X bit from the first byte of the payload descriptor.
     */
    private static final byte X_BIT = (byte) 0x80;

    public VP8Video() {
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
        // XXX also add support for handling non-amf VP8, maybe
        if (data.hasRemaining()) {
            byte first = data.get();
            data.rewind();
            return ((first & 0x0f) == VideoCodec.VP8.getId());
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
                        // VP8 keyframe
                        data.put(VP8_KEYFRAME_PREFIX);
                    } else {
                        // VP8 non-keyframe
                        data.put(VP8_FRAME_PREFIX);
                    }
                    // drop the slice in behind the prefix
                    data.put(slice);
                    // flip it
                    data.flip();
                    // reset start (which technically will be the same position)
                    start = data.position();
                } else {
                    log.warn("Remaining VP8 content was less than expected: {}", remaining);
                }
                // jump back to the starting pos
                data.position(start);
            }
            // get frame type (codec + type)
            byte frameType = data.get();
            // get sub frame / sequence type (config, keyframe, interframe)
            byte subFrameType = data.get();
            if ((frameType & 0x0f) == VideoCodec.VP8.getId()) {
                // check for keyframe (we're not storing non-keyframes here)
                if ((frameType & 0xf0) == FLV_FRAME_KEY) {
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
            } else {
                // not VP8 data
                log.debug("Non-VP8 data, rejecting");
                // go back to where we started
                data.position(start);
                return false;
            }
            // go back to where we started
            data.position(start);
        }
        return true;
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
    @SuppressWarnings("unused")
    private static int getDesciptorSize(byte[] input, int offset, int length) {
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

    private static boolean isKeyFrame(byte[] input, int offset) {
        // if set to 0 the frame is a key frame, if set to 1 its an interframe. Defined in [RFC6386]
        return (input[offset] & S_BIT) == 0;
    }

}
