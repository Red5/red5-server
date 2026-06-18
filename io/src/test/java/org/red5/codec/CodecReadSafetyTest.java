/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2026 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.codec;

import static org.junit.Assert.assertFalse;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;

public class CodecReadSafetyTest {

    @Test
    public void testAV1RejectsShortEnhancedHeader() {
        IoBuffer data = IoBuffer.allocate(1);
        data.put((byte) 0x90);
        data.flip();

        AV1Video video = new AV1Video();
        assertFalse(video.addData(data, 0));
    }

    @Test
    public void testExtendedAudioRejectsShortFourCc() {
        IoBuffer data = IoBuffer.allocate(1);
        data.put((byte) 0x91);
        data.flip();

        ExtendedAudio audio = new ExtendedAudio();
        assertFalse(audio.addData(data));
    }

    @Test
    public void testEnhancedVideoRejectsShortMultitrackFourCc() {
        IoBuffer data = IoBuffer.allocate(2);
        data.put((byte) 0x96);
        data.put((byte) 0x01);
        data.flip();

        HEVCVideo video = new HEVCVideo();
        assertFalse(video.addData(data, 0));
    }

}
