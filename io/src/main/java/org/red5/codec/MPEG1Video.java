/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.codec;

import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.mina.core.buffer.IoBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Red5 video codec for the MPEG1 video format. Stores DecoderConfigurationRecord and last keyframe.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class MPEG1Video extends AbstractVideo {

    private static Logger log = LoggerFactory.getLogger(MPEG1Video.class);

    private static boolean isDebug = log.isDebugEnabled();

    /** Video decoder configuration data */
    private FrameData decoderConfiguration;

    {
        codec = VideoCodec.MPEG1;
    }

    /** {@inheritDoc} */
    @Override
    public boolean canDropFrames() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public void reset() {
        decoderConfiguration = new FrameData();
        softReset();
    }

    /** {@inheritDoc} */
    @Override
    public boolean addData(IoBuffer data) {
        return addData(data, (keyframeTimestamp + 1));
    }

    /** {@inheritDoc} */
    @Override
    public boolean addData(IoBuffer data, int timestamp) {
        //log.trace("addData timestamp: {} remaining: {}", timestamp, data.remaining());
        if (data.hasRemaining()) {
            // mark
            int start = data.position();
            // get frame type
            byte frameType = data.get();
            byte avcType = data.get();
            if ((frameType & 0x0f) == VideoCodec.AVC.getId()) {
                // check for keyframe
                if ((frameType & 0xf0) == FLV_FRAME_KEY) {
                    if (isDebug) {
                        log.debug("Keyframe - AVC type: {}", avcType);
                    }
                    // rewind
                    data.rewind();
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
                            //log.trace("Decoder configuration");
                            // Store AVCDecoderConfigurationRecord data
                            decoderConfiguration.setData(data);
                            softReset();
                            break;
                    }
                    //log.trace("Keyframes: {}", keyframes.size());
                } else if (bufferInterframes) {
                    //log.trace("Interframe");
                    if (isDebug) {
                        log.debug("Interframe - AVC type: {}", avcType);
                    }
                    if (interframes == null) {
                        interframes = new CopyOnWriteArrayList<>();
                    }
                    // rewind
                    data.rewind();
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
            } else {
                // not AVC data
                log.debug("Non-AVC data, rejecting");
                // go back to where we started
                data.position(start);
                return false;
            }
            // go back to where we started
            data.position(start);
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public IoBuffer getDecoderConfiguration() {
        return decoderConfiguration.getFrame();
    }

}
