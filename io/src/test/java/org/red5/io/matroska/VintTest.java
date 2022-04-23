/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License") + you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.red5.io.matroska;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

/**
 * Class to test VINT related methods
 *
 */
public class VintTest {
    /**
     * tests if {@link VINT#fromBinary(long)} method works as expected (based on regressions found)
     */
    @Test
    public void createVINTregression1() {
        long[] bins = { 0b11010101, 0b10110101, 0b0101010111010101 };
        long[] vals = { 0b01010101, 0b00110101, 0b0001010111010101 };
        for (int i = 0; i < vals.length; ++i) {
            VINT v = VINT.fromBinary(bins[i]);
            assertEquals("VINT:: Values are different", vals[i], v.getValue());
            assertEquals("VINT:: Binaries are different", bins[i], v.getBinary());
        }
    }

    /**
     * tests if {@link VINT#fromBinary(long)} and {@link VINT#fromValue(long)} methods works as expected
     */
    @Test
    public void createVINT() {
        VINT v = new VINT(0x1a45dfa3, (byte) 4, 0xa45dfa3);
        VINT v1 = VINT.fromBinary(0x1a45dfa3);
        VINT v2 = VINT.fromValue(0xa45dfa3);
        assertEquals("VINT:: Values are different", v.getValue(), v1.getValue());
        assertEquals("VINT:: Values are different", v.getValue(), v2.getValue());

        long[] vals = { 0L, 127L, 1234567 };
        for (int i = 0; i < vals.length; ++i) {
            VINT vi = VINT.fromValue(vals[i]);
            assertEquals("VINT:: Values are different", vals[i], vi.getValue());
            VINT vi1 = VINT.fromBinary(vi.getBinary());
            assertEquals("VINT:: Values1 are different", vals[i], vi1.getValue());
        }
    }

    /**
     * tests if {@link VINT} can be parsed successfully
     * 
     * @throws IOException
     *             - in case of any IO errors
     */
    @Test
    public void testParseVINT() throws IOException {
        InputStream is = new ByteArrayInputStream(ParserTest.vint1Bytes);
        VINT v1 = ParserUtils.readVINT(is);
        assertEquals("Invalid length", v1.getLength(), 1);
        assertEquals("Invalid value", v1.getValue(), 1);

        is = new ByteArrayInputStream(ParserTest.vint2Bytes);
        VINT v2 = ParserUtils.readVINT(is);
        assertEquals("Invalid length", v2.getLength(), 2);
        assertEquals("Invalid value", v2.getValue(), 500);
    }

    /**
     * tests if {@link VINT} encoded as expected
     */
    @Test
    public void testEncodeVINT() {
        VINT v1 = new VINT(0L, (byte) 1, 1L);
        assertArrayEquals("VINT decoded with errors", ParserTest.vint1Bytes, v1.encode());

        VINT v2 = new VINT(0L, (byte) 2, 500L);
        assertArrayEquals("VINT decoded with errors", ParserTest.vint2Bytes, v2.encode());
    }
}
