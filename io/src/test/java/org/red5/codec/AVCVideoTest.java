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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;
import org.red5.codec.IVideoStreamCodec.FrameData;
import org.red5.io.ITag;
import org.red5.io.flv.impl.FLVReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AVCVideoTest {

    private static Logger log = LoggerFactory.getLogger(AVCVideoTest.class);

    @Test
    public void testCanHandleData() {
        log.info("testCanHandleData");
        IoBuffer data = IoBuffer.allocate(8);
        data.put((byte) 0x17);
        data.flip();
        //
        IoBuffer badData = IoBuffer.allocate(8);
        badData.put((byte) 0x44);
        badData.flip();

        AVCVideo video = new AVCVideo();
        assertTrue(video.canHandleData(data));

        assertFalse(video.canHandleData(badData));
        log.info("testCanHandleData end\n");
    }

    @Test
    public void testSimpleFlow() {
        log.info("testSimpleFlow");
        IoBuffer data = IoBuffer.allocate(128);
        data.put((byte) 0x17);
        data.put((byte) 0x01);
        data.put(RandomStringUtils.random(24).getBytes());
        data.flip();

        AVCVideo video = new AVCVideo();
        assertTrue(video.canHandleData(data));
        assertTrue(video.addData(data));
        for (int i = 0; i < 10; i++) {
            // interframe
            IoBuffer inter = IoBuffer.allocate(128);
            inter.put((byte) 0x27);
            inter.put((byte) 0x01);
            inter.put(RandomStringUtils.random(24).getBytes());
            inter.flip();
            // add it
            assertTrue(video.addData(inter));
        }
        log.info("testSimpleFlow end\n");
    }

    @Test
    public void testSimpleFlowNoInterframeBuffer() {
        log.info("testSimpleFlowNoInterframeBuffer");
        IoBuffer data = IoBuffer.allocate(128);
        data.put((byte) 0x17);
        data.put((byte) 0x01);
        data.put(RandomStringUtils.random(24).getBytes());
        data.flip();

        AVCVideo video = new AVCVideo();
        video.setBufferInterframes(false);
        assertTrue(video.canHandleData(data));
        assertTrue(video.addData(data));
        for (int i = 0; i < 10; i++) {
            // interframe
            IoBuffer inter = IoBuffer.allocate(128);
            inter.put((byte) 0x27);
            inter.put((byte) 0x01);
            inter.put(RandomStringUtils.random(24).getBytes());
            inter.flip();
            // add it
            assertTrue(video.addData(inter));
        }
        assertTrue(video.getNumInterframes() == 0);
        log.info("testSimpleFlowNoInterframeBuffer end\n");
    }

    @Test
    public void testRealisticFlow() {
        log.info("testRealisticFlow");
        IoBuffer data = IoBuffer.allocate(128);
        data.put((byte) 0x17);
        data.put((byte) 0x01);
        data.put(RandomStringUtils.random(24).getBytes());
        data.flip();

        AVCVideo video = new AVCVideo();
        assertTrue(video.canHandleData(data));
        assertTrue(video.addData(data));
        for (int i = 0; i < 10; i++) {
            // interframe
            IoBuffer inter = IoBuffer.allocate(128);
            inter.put((byte) 0x27);
            inter.put((byte) 0x01);
            inter.putInt(i); // store our counter for testing
            inter.put(RandomStringUtils.random(24).getBytes());
            inter.flip();
            // add it
            assertTrue(video.addData(inter));
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
            assertEquals(buf.getInt(), i);
        }
        // non-existent
        fd = video.getInterframe(10);
        assertNull(fd);
        // re-add the key
        assertTrue(video.addData(data));
        for (int i = 0; i < 4; i++) {
            // interframe
            IoBuffer inter = IoBuffer.allocate(128);
            inter.put((byte) 0x27);
            inter.put((byte) 0x01);
            inter.putInt(i + 10); // store our counter for testing
            inter.put(RandomStringUtils.random(24).getBytes());
            inter.flip();
            // add it
            assertTrue(video.addData(inter));
        }
        // verify
        for (int i = 0; i < 4; i++) {
            // read them out to verify
            fd = video.getInterframe(i);
            assertNotNull(fd);
            IoBuffer buf = fd.getFrame();
            buf.skip(2);
            assertEquals(buf.getInt(), i + 10);
        }
        // non-existent
        fd = video.getInterframe(4);
        assertNull(fd);
        log.info("testRealisticFlow end\n");
    }

    @Test
    public void testA7SliceBug() {
        log.info("\n testA7SliceBug");
        Path path = Paths.get("target/test-classes/fixtures/ipadmini-A7.flv");
        try {
            File file = path.toFile();
            log.info("Reading: {}", file.getName());
            FLVReader reader = new FLVReader(file, true);
            ITag tag = null;
            AVCVideo video = new AVCVideo();
            while (reader.hasMoreTags()) {
                tag = reader.readTag();
                int timestamp = tag.getTimestamp();
                log.debug("Tag: {} timestamp: {}", tag.getDataType(), timestamp);
                if (tag.getDataType() == 9) {
                    IoBuffer buf = tag.getBody();
                    if (video.canHandleData(buf)) {
                        video.addData(buf, tag.getTimestamp());
                    }
                }
                // when the audio comes in for ts 2176, check for the 2 proceeding sliced keyframes
                if (timestamp == 2176) {
                    assertTrue(video.getKeyframes().length == 2);
                }
            }
            reader.close();
            log.info("Finished reading: {}\n", file.getName());
        } catch (IOException e) {
            e.printStackTrace();
        }
        log.info("testA7SliceBug end\n");
    }

}
