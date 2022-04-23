/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2016 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.mp3.impl;

import java.io.File;

import junit.framework.TestCase;

import org.junit.Test;
import org.red5.io.ITag;
import org.red5.io.IoConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MP3ReaderTest extends TestCase {

    private static Logger log = LoggerFactory.getLogger(MP3ReaderTest.class);

    @Test
    public void testCtor() throws Exception {
        log.debug("\n testCtor");
        File file = new File("target/test-classes/fixtures/p-ok.mp3");
        @SuppressWarnings("unused")
        File file2 = new File("target/test-classes/fixtures/p-err.mp3");
        //File file = new File("target/test-classes/fixtures/01 Cherub Rock.mp3");
        //File file = new File("target/test-classes/fixtures/CodeMonkey.mp3");
        MP3Reader reader = new MP3Reader(file);
        ITag tag = reader.readTag();
        log.info("Tag: {}", tag);
        assertEquals(IoConstants.TYPE_METADATA, tag.getDataType());
        assertFalse(reader.hasVideo());
        assertEquals(3228578, reader.getTotalBytes());
        do {
            tag = reader.readTag();
            log.info("Tag: {}", tag);
        } while (reader.hasMoreTags());

    }

}
