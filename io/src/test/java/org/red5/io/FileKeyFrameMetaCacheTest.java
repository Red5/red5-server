/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2016 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package org.red5.io;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.red5.io.flv.IKeyFrameDataAnalyzer.KeyFrameMeta;

public class FileKeyFrameMetaCacheTest {

    @Test
    public void testSerialization() throws IOException {
        FileKeyFrameMetaCache cache = new FileKeyFrameMetaCache();
        File f = File.createTempFile("red5", "MetaCacheTest");
        f.setLastModified(1481275039000L);
        KeyFrameMeta meta = new KeyFrameMeta();
        meta.positions = new long[] { -666, 0, 666 };
        meta.timestamps = new int[] { 0, 666, 666 * 2 };
        cache.saveKeyFrameMeta(f, meta);
        try (InputStreamReader ir1 = new InputStreamReader(FileKeyFrameMetaCacheTest.class.getResourceAsStream("Red5MetaCacheTest1.xml"), StandardCharsets.UTF_8); FileReader fr2 = new FileReader(f.getCanonicalPath() + ".meta")) {
            Assert.assertTrue("Generated file has bad contents", IOUtils.contentEqualsIgnoreEOL(ir1, fr2));
        }
    }
}
