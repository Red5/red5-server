/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License") + you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.red5.io.matroska;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import org.red5.io.matroska.dtd.Tag;
import org.red5.io.matroska.dtd.TagFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>ParserUtils class.</p>
 *
 * @author mondain
 */
public class ParserUtils {

    private static Logger log = LoggerFactory.getLogger(ParserUtils.class);

    /** Constant <code>BIT_IN_BYTE=8</code> */
    public static final int BIT_IN_BYTE = 8;

    /**
     * method used to parse : int, uint and date
     *
     * @param inputStream
     *            - stream to get value
     * @param size
     *            - size of the value in bytes
     * @return - parsed value as long
     * @throws java.io.IOException
     *             - in case of IO error
     */
    public static long parseInteger(InputStream inputStream, final int size) throws IOException {
        log.debug("parseInteger inputStream: {} size: {}", inputStream, size);
        byte[] buffer = new byte[size];
        int numberOfReadsBytes = inputStream.read(buffer, 0, size);
        log.debug("numberOfReadsBytes: {}", numberOfReadsBytes);
        //assert numberOfReadsBytes == size;
        long value = buffer[0] & (long) 0xff;
        for (int i = 1; i < size; ++i) {
            value = (value << BIT_IN_BYTE) | ((long) buffer[i] & (long) 0xff);
        }
        return value;
    }

    /**
     * method used to parse string
     *
     * @param inputStream
     *            - stream to get value
     * @param size
     *            - size of the value in bytes
     * @return - parsed value as {@link java.lang.String}
     * @throws java.io.IOException
     *             - in case of IO error
     */
    public static String parseString(InputStream inputStream, final int size) throws IOException {
        if (0 == size) {
            return "";
        }

        byte[] buffer = new byte[size];
        int numberOfReadsBytes = inputStream.read(buffer, 0, size);
        assert numberOfReadsBytes == size;

        return new String(buffer, "UTF-8");
    }

    /**
     * method used to parse float and double
     *
     * @param inputStream
     *            - stream to get value
     * @param size
     *            - size of the value in bytes
     * @return - parsed value as double
     * @throws java.io.IOException
     *             - in case of IO error
     */
    public static double parseFloat(InputStream inputStream, final int size) throws IOException {
        byte[] buffer = new byte[size];
        int numberOfReadsBytes = inputStream.read(buffer, 0, size);
        assert numberOfReadsBytes == size;

        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.BIG_ENDIAN);
        if (8 == size) {
            return byteBuffer.getDouble();
        }

        return byteBuffer.getFloat();
    }

    /**
     * method used to parse subelements of {@link org.red5.io.matroska.dtd.CompoundTag}
     *
     * @param inputStream
     *            - stream to get value
     * @param size
     *            - size of the value in bytes
     * @return - parsed tag
     * @throws java.io.IOException
     *             - in case of IO error
     * @throws org.red5.io.matroska.ConverterException
     *             - in case of any conversion exception
     */
    public static ArrayList<Tag> parseMasterElement(InputStream inputStream, final int size) throws IOException, ConverterException {
        byte bufferForSubElements[] = new byte[size];
        int readOfBytes = inputStream.read(bufferForSubElements, 0, size);
        assert readOfBytes == size;

        ArrayList<Tag> subElements = new ArrayList<Tag>();
        ByteArrayInputStream inputStreamForSubElements = new ByteArrayInputStream(bufferForSubElements);
        while (0 != inputStreamForSubElements.available()) {
            subElements.add(parseTag(inputStreamForSubElements));
        }

        return subElements;
    }

    /**
     * method to parse byte array
     *
     * @param inputStream
     *            - stream to get value
     * @param size
     *            - size of the value in bytes
     * @return - parsed value as array of bytes
     * @throws java.io.IOException
     *             - in case of IO error
     */
    public static byte[] parseBinary(InputStream inputStream, final int size) throws IOException {
        byte value[] = new byte[size];
        int i = value.length;
        while (i != 0) {
            int read = inputStream.read(value, value.length - i, i);
            if (read < 0) {
                throw new IOException("Unexpected end of stream while reading binary");
            }
            i -= read;
        }

        return value;
    }

    /**
     * method to parse {@link org.red5.io.matroska.VINT}
     *
     * @param inputStream
     *            - stream to get value
     * @return - parsed value
     * @throws java.io.IOException
     *             - in case of IO error
     */
    public static VINT readVINT(InputStream inputStream) throws IOException {
        int fb = inputStream.read();
        if (fb < 0) {
            throw new IOException("Unexpected end of stream while reading VINT");
        }
        if (fb == 0) {
            throw new IOException("Invalid VINT marker byte: 0x00");
        }

        int leadingZeros = Integer.numberOfLeadingZeros(fb & 0xFF) - 24;
        int len = leadingZeros + 1;
        byte[] vint = new byte[len];
        vint[0] = (byte) fb;
        int offset = 1;
        int remaining = len - 1;
        while (remaining > 0) {
            int read = inputStream.read(vint, offset, remaining);
            if (read < 0) {
                throw new IOException("Unexpected end of stream while reading VINT");
            }
            offset += read;
            remaining -= read;
        }

        long mask = (1L << (len * 7)) - 1;
        long binaryV = 0;
        for (int i = 0; i < vint.length; ++i) {
            binaryV += (0x00FF & vint[i]);
            if (i != vint.length - 1) {
                binaryV <<= BIT_IN_BYTE;
            }
        }
        return new VINT(binaryV, (byte) len, mask & binaryV);
    }

    /**
     * parsing tag by matroska specification <a href="http://matroska.org/technical/specs/index.html">matroska spec</a>
     *
     * tag = VINT id, VINT size, data
     *
     * @param inputStream
     *            - stream to get value
     * @return tag, without parsing tag data, because it delegate to an tag itself
     * @throws java.io.IOException
     *             - in case of IO error
     * @throws org.red5.io.matroska.ConverterException
     *             - in case of any conversion exception
     */
    public static Tag parseTag(InputStream inputStream) throws IOException, ConverterException {
        VINT id = readVINT(inputStream);
        VINT size = readVINT(inputStream);

        return TagFactory.createTag(id, size, inputStream);
    }

    /**
     * method to encode long as byte array of given size
     *
     * @param val
     *            - value to encode
     * @param size
     *            - size
     * @return - byte array
     */
    public static byte[] getBytes(long val, long size) {
        byte[] res = new byte[(int) size];
        long bv = val;
        for (int i = (int) size - 1; i >= 0; --i) {
            res[i] = (byte) (bv & 0xFF);
            bv >>= BIT_IN_BYTE;
        }
        return res;
    }

    /**
     * method to skip given amount of bytes in stream
     *
     * @param size
     *            - size to skip
     * @param input
     *            - input stream to process
     * @throws java.io.IOException
     *             - in case of IO error
     */
    public static void skip(long size, InputStream input) throws IOException {
        while (size > 0) {
            long skipped = input.skip(size);
            if (skipped <= 0) {
                // if skip is not making progress, consume one byte to avoid an infinite loop
                if (input.read() == -1) {
                    throw new IOException("Unexpected end of stream while skipping");
                }
                skipped = 1;
            }
            size -= skipped;
        }
    }
}
