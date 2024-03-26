/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License") + you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.red5.io.webm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Before;
import org.junit.Test;
import org.red5.io.matroska.ConverterException;
import org.red5.io.matroska.dtd.Tag;
import org.red5.io.matroska.parser.TagCrawler;
import org.red5.io.matroska.parser.TagHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebmTest {

    private static Logger log = LoggerFactory.getLogger(WebmTest.class);

    private static final String WEBM_FILE_PROPERTY = "webm.file.path";

    private static String webmTestFilePath;

    /**
     * This check will cancel further tests in case there is no system property
     * "webm.file.path" specified with path to the test web file.
     */
    @Before
    public void before() {
        webmTestFilePath = System.getProperty(WEBM_FILE_PROPERTY, "target/webm_sample.webm");
    }

    /**
     * This test checks if test webm file can be read till the end with no
     * exceptions
     *
     * @throws IOException
     *                            - in case of any IO exception
     * @throws ConverterException
     *                            - in case of any conversion exception
     */
    @Test
    public void crawl() throws IOException, ConverterException {
        final TagHandler logHandle = new TagHandler() {
            @Override
            public void handle(Tag tag, InputStream input) throws IOException, ConverterException {
                log.debug("Tag found: " + tag.getName());
            }
        };
        TagCrawler crawler = new TagCrawler() {
            @Override
            public TagHandler getHandler(Tag tag) {
                return logHandle;
            }
        };
        File webmF = new File(webmTestFilePath);
        if (webmF.exists() && webmF.isFile()) {
            try (FileInputStream fis = new FileInputStream(webmF)) {
                crawler.process(fis);
                assertEquals("Zero bytes should remain in file", 0, fis.available());
            }
        }
    }

    /**
     * This test checks if test webm file can be read and then be written with no
     * exceptions
     *
     * @throws IOException
     *                            - in case of any IO exception
     * @throws ConverterException
     *                            - in case of any conversion exception
     */
    @Test
    public void testReaderWriter() throws IOException, ConverterException {
        File webmF = new File(webmTestFilePath);
        assertTrue("Invalid webM file is specified", webmF.exists() && webmF.isFile());
        File out = File.createTempFile("webmwriter", ".webm");
        try (WebmWriter w = new WebmWriter(out, false); WebmReader r = new WebmReader(webmF, w);) {
            r.process();
        }
        log.debug("Temporary file was created: " + out.getAbsolutePath());
        assertEquals("", webmF.length(), out.length());
    }

    @Test
    public void testReader() throws IOException, ConverterException {
        // https://www.matroska.org/technical/tagging.html
        //File webmF = new File("/media/mondain/terrorbyte/Videos/bbb-fullhd.webm");
        File webmF = new File("/media/mondain/terrorbyte/Videos/BladeRunner2049.webm");
        assertTrue("Invalid webM file is specified", webmF.exists() && webmF.isFile());
        File out = File.createTempFile("webmwriter", ".webm");
        try (WebmReader r = new WebmReader(webmF, new TagConsumer() {
            @Override
            public void consume(Tag tag) {
                log.debug("Tag found: " + tag.getName());
            }
        });) {
            r.process();
        }
        log.debug("Temporary file was created: " + out.getAbsolutePath());
        assertEquals("", webmF.length(), out.length());
    }

}
