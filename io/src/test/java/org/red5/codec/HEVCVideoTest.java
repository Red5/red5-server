/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.codec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;
import org.red5.codec.IVideoStreamCodec.FrameData;
import org.red5.util.ByteNibbler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HEVCVideoTest {

    private static Logger log = LoggerFactory.getLogger(HEVCVideoTest.class);

    @Test
    public void testHEVCVideo() {
        log.info("testHEVCVideo");
        HEVCVideo video = new HEVCVideo();
        assertNotNull(video);
        assertEquals(VideoCodec.HEVC, video.getCodec());
        assertFalse(video.isEnhanced());
        // create a flag byte with the enhanced bit set and a keyframe for HEVC
        byte flg = (byte) 0x9c; // 0b10011100
        // determine if we've got an enhanced codec
        ByteNibbler nibbler = new ByteNibbler(flg);
        // check for enhanced codec handling first
        boolean enhanced = nibbler.nibble(1) == 1;
        // get video frame type
        VideoFrameType frameType = VideoFrameType.valueOf(nibbler.nibble(3));
        if ((flg & 0xf0) == 0x10 || (enhanced && frameType == VideoFrameType.KEYFRAME)) {
            log.info("Keyframe; enhanced: {}", enhanced);
        } else {
            log.info("Interframe; enhanced: {}", enhanced);
        }
        // create a flag byte with the enhanced bit set and a config data for HEVC
        flg = (byte) 0b10001100;
        nibbler = new ByteNibbler(flg);
        // check for enhanced codec handling first
        enhanced = nibbler.nibble(1) == 1;
        assertTrue(ByteNibbler.isBitSet(flg, 7));
        // get video frame type
        frameType = VideoFrameType.valueOf(nibbler.nibble(3));
        if (((flg & 0xf0) == 0x10 && frameType == VideoFrameType.RESERVED) || (enhanced && frameType == VideoFrameType.RESERVED)) {
            log.info("Reserved (config data); enhanced: {}", enhanced);
        } else {
            log.info("Not reserved (config data); enhanced: {}", enhanced);
        }

        log.info("testHEVCVideo end\n");
    }

    @Test
    public void testCanHandleData() {
        log.info("testCanHandleData");
        IoBuffer data = IoBuffer.allocate(8);
        data.put((byte) 0x1c);
        data.flip();
        //
        IoBuffer badData = IoBuffer.allocate(8);
        badData.put((byte) 0x44);
        badData.flip();

        HEVCVideo video = new HEVCVideo();
        assertTrue(video.canHandleData(data));
        assertFalse(video.isEnhanced());

        assertFalse(video.canHandleData(badData));
        log.info("testCanHandleData end\n");
    }

    @Test
    public void testCanHandleDataEnhanced() {
        log.info("testCanHandleDataEnhanced");
        IoBuffer data = IoBuffer.allocate(8);
        // first bit being set indicates enhanced
        byte enhancedByte = (byte) 0b10011100;
        log.info("enhancedByte: {}", Integer.toHexString(enhancedByte));
        //data.put(enhancedByte);
        data.put((byte) 0x9c);
        data.flip();

        HEVCVideo video = new HEVCVideo();
        assertTrue(video.canHandleData(data));
        assertTrue(video.isEnhanced());

        log.info("testCanHandleDataEnhanced end\n");
    }

    @Test
    public void testRealisticFlow() {
        log.info("testRealisticFlow");
        IoBuffer data = IoBuffer.allocate(128);
        data.put((byte) 0x1c);
        data.put((byte) 0x01);
        data.put(RandomStringUtils.secure().next(24).getBytes());
        data.flip();

        HEVCVideo video = new HEVCVideo();
        video.setBufferInterframes(false);
        assertTrue(video.canHandleData(data));
        assertTrue(video.addData(data, 1000)); // use a timestamp of 1000
        if (!video.isBufferInterframes()) {
            log.warn("Skipping interframe test, interframe buffering is disabled");
            return;
        }
        for (int i = 0; i < 10; i++) {
            // interframe
            IoBuffer inter = IoBuffer.allocate(128);
            inter.put((byte) 0x2c);
            inter.put((byte) 0x01);
            inter.putInt(i); // store our counter for testing
            inter.put(RandomStringUtils.secure().next(24).getBytes());
            inter.flip();
            // add it
            assertTrue(video.addData(inter, 1000 + i));
        }
        // there is no interframe at 0
        FrameData fd = null;
        assertNull(fd);
        // verify
        for (int i = 0; i < 10; i++) {
            // read them out to verify
            fd = video.getInterframe(i);
            assertNotNull(fd);
            IoBuffer buf = fd.getFrame();
            buf.skip(2);
            //assertEquals(buf.getInt(), i);
        }
        // non-existent
        fd = video.getInterframe(11);
        assertNull(fd);
        // re-add the key
        assertTrue(video.addData(data, 2000));
        for (int i = 0; i < 4; i++) {
            // interframe
            IoBuffer inter = IoBuffer.allocate(128);
            inter.put((byte) 0x2c);
            inter.put((byte) 0x01);
            inter.putInt(i + 10); // store our counter for testing
            inter.put(RandomStringUtils.secure().next(24).getBytes());
            inter.flip();
            // add it
            assertTrue(video.addData(inter, 2000 + i));
        }
        // verify
        for (int i = 0; i < 4; i++) {
            // read them out to verify
            fd = video.getInterframe(i);
            assertNotNull(fd);
            IoBuffer buf = fd.getFrame();
            buf.skip(2);
            //assertEquals(buf.getInt(), i + 10);
        }
        // non-existent
        fd = video.getInterframe(5);
        assertNull(fd);
        log.info("testRealisticFlow end\n");
    }

}
