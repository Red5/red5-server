/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License") + you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.red5.io.matroska;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;
import org.red5.io.matroska.dtd.StringTag;
import org.red5.io.matroska.dtd.Tag;
import org.red5.io.matroska.dtd.UnsignedIntegerTag;

/**
 * Class to test webm tags parse methods
 *
 */
public class ParserTest {
    static final byte[] vint1Bytes = { (byte) 0x81 };

    static final byte[] vint2Bytes = { 0x41, (byte) 0xF4 };

    // size = 1, value = 0x37
    static final byte[] ebmlTagBytes = { 0x1A, 0x45, (byte) 0xdf, (byte) 0xa3, (byte) 0x81, 0x37 };

    // size = 1, value = 1
    static final byte[] ebmlVersionTagBytes = { 0x42, (byte) 0x86, (byte) 0x81, 0x01 };

    // size = 1, value = 255
    static final byte[] ebmlReadVersionTagBytes = { 0x42, (byte) 0xf7, (byte) 0x81, (byte) 0xff };

    // size = 8, value = "matroska" -> positive scenario
    static final byte[] ebmlDocTypeTagBytesMatroska = { 0x42, (byte) 0x82, (byte) 0x88, 0x6d, 0x61, 0x74, 0x72, 0x6f, 0x73, 0x6b, 0x61 };

    // size = 4, value = "webm" -> positive scenario
    static final byte[] ebmlDocTypeTagBytesWebm = { 0x42, (byte) 0x82, (byte) 0x84, 0x77, 0x65, 0x62, 0x6d };

    // size = 4, value = "arch" -> negative scenario
    static final byte[] ebmlDocTypeTagBytesArch = { 0x42, (byte) 0x82, (byte) 0x84, 0x61, 0x72, 0x63, 0x68 };

    // size = 1, value = 0x37
    static final byte[] trackEntryTagBytes = { (byte) 0xae, (byte) 0xEB };

    /**
     * tests if EBML tag parsed as expected
     *
     * @throws IOException
     *             - in case of any IO errors
     * @throws ConverterException
     *             - in case of any errors during conversion
     */
    @Test
    public void testParseTagEBML() throws IOException, ConverterException {
        InputStream inputStream = new ByteArrayInputStream(ebmlTagBytes);

        Tag tag = ParserUtils.parseTag(inputStream);

        // by specification
        assertEquals("EBML", tag.getName());
        assertEquals(0x1a45dfa3, tag.getId());
        assertEquals(1, tag.getSize());
    }

    /**
     * tests if EBMLVersion tag parsed as expected
     *
     * @throws IOException
     *             - in case of any IO errors
     * @throws ConverterException
     *             - in case of any errors during conversion
     */
    @Test
    public void testParseTagEBMLVersion() throws IOException, ConverterException {
        InputStream inputStream = new ByteArrayInputStream(ebmlVersionTagBytes);

        Tag tag = ParserUtils.parseTag(inputStream);

        // by specification
        assertEquals("EBMLVersion", tag.getName());
        assertEquals(0x4286, tag.getId());
        assertEquals(1, tag.getSize());
        tag.parse();
        assertEquals(1, ((UnsignedIntegerTag) tag).getValue());
    }

    /**
     * tests if EBMLReadVersion tag parsed as expected
     *
     * @throws IOException
     *             - in case of any IO errors
     * @throws ConverterException
     *             - in case of any errors during conversion
     */
    @Test
    public void testParseTagEBMLReadVersion() throws IOException, ConverterException {
        InputStream inputStream = new ByteArrayInputStream(ebmlReadVersionTagBytes);

        Tag tag = ParserUtils.parseTag(inputStream);

        // by specification
        assertEquals(tag.getName(), "EBMLReadVersion");
        assertEquals(tag.getId(), 0x42f7);
        assertEquals(tag.getSize(), 1);
        tag.parse();
        assertEquals(((UnsignedIntegerTag) tag).getValue(), 255);
    }

    /**
     * tests if DocType tag parsed as expected
     *
     * @throws IOException
     *             - in case of any IO errors
     * @throws ConverterException
     *             - in case of any errors during conversion
     */
    @Test
    public void testParseTagDocType() throws IOException, ConverterException {
        InputStream inputStream = new ByteArrayInputStream(ebmlDocTypeTagBytesMatroska);
        Tag tag = ParserUtils.parseTag(inputStream);

        assertEquals(tag.getName(), "DocType");
        assertEquals(tag.getId(), 0x4282);
        assertEquals(tag.getSize(), 8);
        tag.parse();
        assertEquals(((StringTag) tag).getValue(), "matroska");

        inputStream = new ByteArrayInputStream(ebmlDocTypeTagBytesWebm);
        tag = ParserUtils.parseTag(inputStream);
        assertEquals(tag.getName(), "DocType");
        assertEquals(tag.getId(), 0x4282);
        assertEquals(tag.getSize(), 4);
        tag.parse();
        assertEquals(((StringTag) tag).getValue(), "webm");

        inputStream = new ByteArrayInputStream(ebmlDocTypeTagBytesArch);
        tag = ParserUtils.parseTag(inputStream);
        assertEquals(tag.getName(), "DocType");
        assertEquals(tag.getId(), 0x4282);
        assertEquals(tag.getSize(), 4);
        tag.parse();
        assertFalse("webm".equals(((StringTag) tag).getValue()));
        assertFalse("matroska".equals(((StringTag) tag).getValue()));
        assertEquals(((StringTag) tag).getValue(), "arch");
    }
}
