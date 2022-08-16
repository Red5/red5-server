/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License") + you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.red5.io.matroska;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import org.junit.Test;
import org.red5.io.matroska.dtd.CompoundTag;
import org.red5.io.matroska.dtd.DateTag;
import org.red5.io.matroska.dtd.FloatTag;
import org.red5.io.matroska.dtd.StringTag;
import org.red5.io.matroska.dtd.Tag;
import org.red5.io.matroska.dtd.TagFactory;
import org.red5.io.matroska.dtd.UnsignedIntegerTag;

/**
 * Class to test webm tags encode methods
 *
 */
public class EncoderTest {
    /**
     * tests if created and parsed {@link Tag}s have same IDs
     *
     * @throws IOException
     *             - in case of any IO errors
     * @throws ConverterException
     *             - in case of any errors during conversion
     */
    @Test
    public void testCreateTags() throws IOException, ConverterException {
        InputStream inputStream = new ByteArrayInputStream(ParserTest.ebmlTagBytes);
        Tag ebml1 = ParserUtils.parseTag(inputStream);
        Tag ebml2 = TagFactory.createTag("EBML");
        assertEquals("EBML:: IDs are not equals", ebml1.getId(), ebml2.getId());

        inputStream = new ByteArrayInputStream(ParserTest.ebmlVersionTagBytes);
        Tag ebmlV1 = ParserUtils.parseTag(inputStream);
        Tag ebmlV2 = TagFactory.createTag("EBMLVersion");
        assertEquals("EBMLVersion:: IDs are not equals", ebmlV1.getId(), ebmlV2.getId());

        inputStream = new ByteArrayInputStream(ParserTest.trackEntryTagBytes);
        Tag te1 = ParserUtils.parseTag(inputStream);
        Tag te2 = TagFactory.createTag("TrackEntry");
        assertEquals("TrackEntry:: IDs are not equals", te1.getId(), te2.getId());
    }

    /**
     * tests if manually created and encoded "master" {@link Tag} can be parsed successfully
     *
     * @throws IOException
     *             - in case of any IO errors
     * @throws ConverterException
     *             - in case of any errors during conversion
     */
    @Test
    public void testEncodeTagEBML() throws IOException, ConverterException {
        CompoundTag t = TagFactory.<CompoundTag> create("EBML").add(TagFactory.<UnsignedIntegerTag> create("EBMLVersion").setValue(1));

        InputStream is = new ByteArrayInputStream(t.encode());
        CompoundTag tag = (CompoundTag) ParserUtils.parseTag(is);
        assertEquals("TrackEntry:: wrong tag was read", "EBML", tag.getName());

        tag.parse(is);
        assertEquals("TrackEntry:: wrong size of tag", 1, tag.getNumberOfSubElements());
        UnsignedIntegerTag ebmlVersion = (UnsignedIntegerTag) tag.get("EBMLVersion");
        ebmlVersion.parse();
        assertEquals("TrackEntry:: wrong sub tag was read", "EBMLVersion", ebmlVersion.getName());
        assertEquals("TrackEntry:: wrong sub tag was read", 1, ebmlVersion.getValue());
    }

    /**
     * tests if manually created and encoded "uint" {@link Tag} can be parsed successfully
     *
     * @throws IOException
     *             - in case of any IO errors
     * @throws ConverterException
     *             - in case of any errors during conversion
     */
    @Test
    public void testEncodeTagUint() throws IOException, ConverterException {
        int[] vals = { 0, 1, 500, 17000, 3000000, 250000000 };
        for (int i = 0; i < vals.length; ++i) {
            UnsignedIntegerTag t = TagFactory.<UnsignedIntegerTag> create("EBMLVersion").setValue(vals[i]);

            InputStream is = new ByteArrayInputStream(t.encode());
            Tag tag = ParserUtils.parseTag(is);
            tag.parse();
            assertEquals("EBML:: IDs are not equals", t.getId(), tag.getId());
            assertEquals("EBML:: Values are not equals", t.getValue(), ((UnsignedIntegerTag) tag).getValue());
        }
    }

    /**
     * tests if manually created and encoded "string" {@link Tag} can be parsed successfully
     *
     * @throws IOException
     *             - in case of any IO errors
     * @throws ConverterException
     *             - in case of any errors during conversion
     */
    @Test
    public void testEncodeTagString() throws IOException, ConverterException {
        String[] vals = { null, "", "abcd", "Some examples of the encoding of integers of width 1 to 4", "A\u00ea\u00f1\u00fcC" };
        for (int i = 0; i < vals.length; ++i) {
            StringTag t = TagFactory.<StringTag> create("DocType").setValue(vals[i]);

            InputStream is = new ByteArrayInputStream(t.encode());
            Tag tag = ParserUtils.parseTag(is);
            tag.parse();
            assertEquals("EBML:: IDs are not equals", t.getId(), tag.getId());
            assertEquals("EBML:: Values are not equals", t.getValue(), ((StringTag) tag).getValue());
        }
    }

    /**
     * tests if manually created and encoded "double" {@link Tag} can be parsed successfully
     *
     * @throws IOException
     *             - in case of any IO errors
     * @throws ConverterException
     *             - in case of any errors during conversion
     */
    @Test
    public void testEncodeTagDouble() throws IOException, ConverterException {
        double[] vals = { 0, .1, 500.12345, Double.MIN_NORMAL, Double.MAX_VALUE };
        for (int i = 0; i < vals.length; ++i) {
            FloatTag t = TagFactory.<FloatTag> create("Duration").setValue(vals[i]);

            InputStream is = new ByteArrayInputStream(t.encode());
            Tag tag = ParserUtils.parseTag(is);
            tag.parse();
            assertEquals("EBML:: IDs are not equals", t.getId(), tag.getId());
            assertEquals("EBML:: Values are not equals", t.getValue(), ((FloatTag) tag).getValue(), 1e-10);
        }
    }

    /**
     * tests if manually created and encoded "date" {@link Tag} can be parsed successfully
     *
     * @throws IOException
     *             - in case of any IO errors
     * @throws ConverterException
     *             - in case of any errors during conversion
     */
    @Test
    public void testEncodeTagDate() throws IOException, ConverterException {
        Date[] vals = { new Date() };
        for (int i = 0; i < vals.length; ++i) {
            DateTag t = TagFactory.<DateTag> create("DateUTC").setValue(vals[i]);

            InputStream is = new ByteArrayInputStream(t.encode());
            Tag tag = ParserUtils.parseTag(is);
            tag.parse();
            assertEquals("EBML:: IDs are not equals", t.getId(), tag.getId());
            assertEquals("EBML:: Values are not equals", t.getValue(), ((DateTag) tag).getValue(), 1e-10);
        }
    }
}
