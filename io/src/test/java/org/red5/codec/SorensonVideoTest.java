/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.codec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;
import org.red5.codec.IVideoStreamCodec.FrameData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SorensonVideoTest {

    private static Logger log = LoggerFactory.getLogger(SorensonVideoTest.class);

    private static byte keyFrameType = (byte) 0x12;

    private static byte interFrameType = (byte) 0x22;

    @SuppressWarnings("unused")
    private static byte disposableFrameType = (byte) 0x32;

    @Test
    public void testRealisticFlow() {
        log.info("testRealisticFlow");

        IoBuffer data = IoBuffer.allocate(128);
        data.put((byte) keyFrameType);
        data.put(RandomStringUtils.random(24).getBytes());
        data.flip();

        SorensonVideo video = new SorensonVideo();
        video.setBufferInterframes(true);
        assertTrue(video.canHandleData(data));
        assertTrue(video.addData(data));
        for (int i = 0; i < 10; i++) {
            // interframe
            IoBuffer inter = IoBuffer.allocate(128);
            inter.put((byte) interFrameType);
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
            buf.skip(1);
            //assertEquals(buf.getInt(), i);
        }
        // non-existent
        fd = video.getInterframe(11);
        assertNull(fd);
        // re-add the key
        assertTrue(video.addData(data));
        for (int i = 0; i < 4; i++) {
            // interframe
            IoBuffer inter = IoBuffer.allocate(128);
            inter.put((byte) interFrameType);
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
            buf.skip(1);
            //assertEquals(buf.getInt(), i + 10);
        }
        // non-existent
        fd = video.getInterframe(4);
        assertNull(fd);
        log.info("testRealisticFlow end\n");
    }
}
