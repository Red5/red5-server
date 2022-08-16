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

import org.red5.io.matroska.dtd.CompoundTag;
import org.red5.io.matroska.dtd.Tag;
import org.red5.io.matroska.dtd.TagFactory;

import static org.red5.io.matroska.VINT.MASK_BYTE_1;
import static org.red5.io.matroska.VINT.MASK_BYTE_2;
import static org.red5.io.matroska.VINT.MASK_BYTE_3;
import static org.red5.io.matroska.VINT.MASK_BYTE_4;
import static org.red5.io.matroska.VINT.MASK_BYTE_8;

public class ParserUtils {

    public static final int BIT_IN_BYTE = 8;

    /**
     * method used to parse : int, uint and date
     *
     * @param inputStream
     *            - stream to get value
     * @param size
     *            - size of the value in bytes
     * @return - parsed value as long
     * @throws IOException
     *             - in case of IO error
     */
    public static long parseInteger(InputStream inputStream, final int size) throws IOException {
        byte[] buffer = new byte[size];
        int numberOfReadsBytes = inputStream.read(buffer, 0, size);
        assert numberOfReadsBytes == size;

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
     * @return - parsed value as {@link String}
     * @throws IOException
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
     * @throws IOException
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
     * method used to parse subelements of {@link CompoundTag}
     *
     * @param inputStream
     *            - stream to get value
     * @param size
     *            - size of the value in bytes
     * @return - parsed tag
     * @throws IOException
     *             - in case of IO error
     * @throws ConverterException
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
     * @throws IOException
     *             - in case of IO error
     */
    public static byte[] parseBinary(InputStream inputStream, final int size) throws IOException {
        byte value[] = new byte[size];
        int i = value.length;
        while (i != 0) {
            i -= inputStream.read(value, value.length - i, i);
        }

        return value;
    }

    /**
     * method to parse {@link VINT}
     *
     * @param inputStream
     *            - stream to get value
     * @return - parsed value
     * @throws IOException
     *             - in case of IO error
     */
    public static VINT readVINT(InputStream inputStream) throws IOException {
        byte[] vint;
        int fb = inputStream.read();
        int read = 0;
        assert fb > 0;

        int len = (fb >> 4);
        long mask = MASK_BYTE_4;
        if (len >= 0b1000) {
            read = 0;
            mask = MASK_BYTE_1;
            vint = new byte[1];
        } else if (len >= 0b0100) {
            mask = MASK_BYTE_2;
            vint = new byte[2];
            read = inputStream.read(vint, 1, 1);
            assert read == 1;
        } else if (len >= 0b0010) {
            mask = MASK_BYTE_3;
            vint = new byte[3];
            read = inputStream.read(vint, 1, 2);
            assert read == 2;
        } else if (len >= 0b0001) {
            vint = new byte[4];
            read = inputStream.read(vint, 1, 3);
            assert read == 3;
        } else {
            mask = MASK_BYTE_8;
            vint = new byte[8];
            read = inputStream.read(vint, 1, 7);
            assert read == 7;
        }
        vint[0] = (byte) fb;
        long binaryV = 0;
        for (int i = 0; i < vint.length; ++i) {
            binaryV += (0x00FF & vint[i]);
            if (i != vint.length - 1) {
                binaryV <<= BIT_IN_BYTE;
            }
        }
        return new VINT(binaryV, (byte) (read + 1), mask & binaryV);
    }

    /**
     * parsing tag by matroska specification <a href="http://matroska.org/technical/specs/index.html">matroska spec</a>
     *
     * tag = VINT id, VINT size, data
     *
     * @param inputStream
     *            - stream to get value
     * @return tag, without parsing tag data, because it delegate to an tag itself
     * @throws IOException
     *             - in case of IO error
     * @throws ConverterException
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
     * @throws IOException
     *             - in case of IO error
     */
    public static void skip(long size, InputStream input) throws IOException {
        while (size > 0) {
            size -= input.skip(size);
        }
    }
}
