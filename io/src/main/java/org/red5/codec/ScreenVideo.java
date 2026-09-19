/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.codec;

import org.apache.mina.core.buffer.IoBuffer;

/**
 * Red5 video codec for the screen capture format.
 *
 * @author The Red5 Project
 * @author Joachim Bauch (jojo@struktur.de)
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class ScreenVideo extends AbstractVideo {

    /**
     * FLV codec name constant
     */
    static final String CODEC_NAME = "ScreenVideo";

    /**
     * Block data
     */
    private byte[] blockData;

    /**
     * Block size
     */
    private int[] blockSize;

    /**
     * Video width
     */
    private int width;

    /**
     * Video height
     */
    private int height;

    /**
     * Width info
     */
    private int widthInfo;

    /**
     * Height info
     */
    private int heightInfo;

    /**
     * Block width
     */
    private int blockWidth;

    /**
     * Block height
     */
    private int blockHeight;

    /**
     * Number of blocks
     */
    private int blockCount;

    /**
     * Block data size
     */
    private int blockDataSize;

    /**
     * Total block data size
     */
    private int totalBlockDataSize;

    {
        codec = VideoCodec.SCREEN_VIDEO;
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return CODEC_NAME;
    }

    /** {@inheritDoc} */
    @Override
    public void reset() {
        this.blockData = null;
        this.blockSize = null;
        this.width = 0;
        this.height = 0;
        this.widthInfo = 0;
        this.heightInfo = 0;
        this.blockWidth = 0;
        this.blockHeight = 0;
        this.blockCount = 0;
        this.blockDataSize = 0;
        this.totalBlockDataSize = 0;
    }

    /*
     * This uses the same algorithm as "compressBound" from zlib
     */
    private static int maxCompressedSize(int size) {
        return size + (size >> 12) + (size >> 14) + 11;
    }

    /**
     * Update total block size
     *
     * @param data
     *            Byte buffer
     */
    private boolean updateSize(IoBuffer data) {
        int widthInfo = data.getShort() & 0xffff;
        int heightInfo = data.getShort() & 0xffff;
        // extract width and height of the frame
        int width = widthInfo & 0xfff;
        int height = heightInfo & 0xfff;
        // calculate size of blocks
        int blockWidth = (((widthInfo & 0xf000) >> 12) + 1) << 4;
        int blockHeight = (((heightInfo & 0xf000) >> 12) + 1) << 4;
        if (width == 0 || height == 0) {
            log.debug("Rejecting screen video frame with zero dimension: {}x{}", width, height);
            return false;
        }
        int xblocks = width / blockWidth;
        if ((width % blockWidth) != 0) {
            // partial block
            xblocks += 1;
        }
        int yblocks = height / blockHeight;
        if ((height % blockHeight) != 0) {
            // partial block
            yblocks += 1;
        }
        int blockCount = xblocks * yblocks;
        // every block carries at least a 2 byte size field, so a frame that is smaller than that cannot describe the
        // dimensions it declares; reject it before retaining any codec state (issue #455)
        long minimumPayload = 2L * blockCount;
        if (data.remaining() < minimumPayload) {
            log.debug("Rejecting screen video frame {}x{} ({} blocks) with only {} payload bytes", width, height, blockCount, data.remaining());
            return false;
        }
        this.widthInfo = widthInfo;
        this.heightInfo = heightInfo;
        this.width = width;
        this.height = height;
        this.blockWidth = blockWidth;
        this.blockHeight = blockHeight;
        this.blockCount = blockCount;
        int blockSize = maxCompressedSize(blockWidth * blockHeight * 3);
        int totalBlockSize = blockSize * blockCount;
        if (this.totalBlockDataSize != totalBlockSize) {
            log.info("Allocating memory for {} compressed blocks.", blockCount);
            this.blockDataSize = blockSize;
            this.totalBlockDataSize = totalBlockSize;
            this.blockData = new byte[totalBlockSize];
            this.blockSize = new int[blockCount];
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean addData(IoBuffer data) {
        if (!this.canHandleData(data)) {
            return false;
        }

        data.get();
        if (!this.updateSize(data)) {
            data.rewind();
            return false;
        }
        int idx = 0;
        int pos = 0;
        byte[] tmpData = new byte[this.blockDataSize];

        int countBlocks = this.blockCount;
        while (data.remaining() > 0 && countBlocks > 0) {
            if (data.remaining() < 2) {
                data.rewind();
                return false;
            }
            short size = data.getShort();
            countBlocks--;
            if (size == 0) {
                // Block has not been modified
                idx += 1;
                pos += this.blockDataSize;
                continue;
            }
            if (size < 0 || size > this.blockDataSize || data.remaining() < size) {
                data.rewind();
                return false;
            }

            // Store new block data
            this.blockSize[idx] = size;
            data.get(tmpData, 0, size);
            System.arraycopy(tmpData, 0, this.blockData, pos, size);
            idx += 1;
            pos += this.blockDataSize;
        }

        data.rewind();
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public IoBuffer getKeyframe() {
        IoBuffer result = IoBuffer.allocate(1024);
        result.setAutoExpand(true);
        // Header
        result.put((byte) (FLV_FRAME_KEY | VideoCodec.SCREEN_VIDEO.getId()));
        // Frame size
        result.putShort((short) this.widthInfo);
        result.putShort((short) this.heightInfo);
        // Get compressed blocks
        byte[] tmpData = new byte[this.blockDataSize];
        int pos = 0;
        for (int idx = 0; idx < this.blockCount; idx++) {
            int size = this.blockSize[idx];
            if (size == 0) {
                // this should not happen: no data for this block
                return null;
            }

            result.putShort((short) size);
            System.arraycopy(this.blockData, pos, tmpData, 0, size);
            result.put(tmpData, 0, size);
            pos += this.blockDataSize;
        }
        result.rewind();
        return result;
    }

}
