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
 * Red5 video codec for the sorenson video format.
 *
 * @author The Red5 Project
 * @author Joachim Bauch (jojo@struktur.de)
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class SorensonVideo extends AbstractVideo {

    /**
     * Sorenson video codec constant
     */
    static final String CODEC_NAME = "SorensonVideo";

    /**
     * Block of data
     */
    private byte[] blockData;

    /**
     * Number of data blocks
     */
    private int dataCount;

    /**
     * Data block size
     */
    private int blockSize;

    {
        codec = VideoCodec.H263;
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
    public void reset() {
        this.blockData = null;
        this.blockSize = 0;
        this.dataCount = 0;
    }

    /** {@inheritDoc} */
    @Override
    public boolean addData(IoBuffer data) {
        log.info("addData remaining: {} limit: {}", data.remaining(), data.limit());
        boolean result = false;
        if (data.hasRemaining()) {
            // mark
            data.mark();
            byte first = data.get();
            //log.trace("First byte: {}", HexDump.toHexString(first));
            data.reset();
            // create mark for frame data
            data.mark();
            // get frame type
            VideoFrameType frame = VideoFrameType.valueOf((first & IoConstants.MASK_VIDEO_FRAMETYPE) >> 4);
            if (VideoFrameType.KEYFRAME != frame) {
                // Not a keyframe
                try {
                    int lastInterframe = numInterframes.getAndIncrement();
                    if (VideoFrameType.DISPOSABLE != frame) {
                        log.trace("Buffering interframe #{}", lastInterframe);
                        if (lastInterframe < interframes.size()) {
                            interframes.get(lastInterframe).setData(data);
                        } else {
                            interframes.add(new FrameData(data));
                        }
                    } else {
                        numInterframes.set(lastInterframe);
                    }
                } catch (Throwable e) {
                    log.error("Failed to buffer interframe", e);
                }
            } else {
                numInterframes.set(0);
                interframes.clear();
                // Store last keyframe
                dataCount = data.remaining();
                if (blockSize < dataCount) {
                    blockSize = dataCount;
                    blockData = new byte[blockSize];
                }
                data.get(blockData, 0, dataCount);
            }
            data.reset();
            result = true;
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public IoBuffer getKeyframe() {
        if (dataCount > 0) {
            IoBuffer result = IoBuffer.allocate(dataCount);
            result.put(blockData, 0, dataCount);
            result.flip();
            return result;
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public FrameData[] getKeyframes() {
        return dataCount > 0 ? new FrameData[] { new FrameData(getKeyframe()) } : new FrameData[0];
    }

}
