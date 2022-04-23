/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License") + you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.red5.io.matroska.dtd;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.red5.io.matroska.ParserUtils;
import org.red5.io.matroska.VINT;

/**
 * http://matroska.org/technical/specs/index.html webm tag to hold "binary" value as byte[] array
 *
 */
public class BinaryTag extends Tag {
    private static final char[] hexArray = "0123456789ABCDEF".toCharArray();

    private byte[] value;

    /**
     * Constructor
     * 
     * @see Tag#Tag(String, VINT)
     *
     * @param name
     *            - the name of tag to be created
     * @param id
     *            - the id of tag to be created
     * @throws IOException
     *             - in case of IO error
     */
    public BinaryTag(String name, VINT id) throws IOException {
        super(name, id);
    }

    /**
     * Constructor
     * 
     * @see Tag#Tag(String, VINT, VINT, InputStream)
     * 
     * @param name
     *            - the name of tag to be created
     * @param id
     *            - the id of tag to be created
     * @param size
     *            - the size of tag to be created
     * @param inputStream
     *            - stream to read tag data from
     * @throws IOException
     *             - in case of IO error
     */
    public BinaryTag(String name, VINT id, VINT size, InputStream inputStream) throws IOException {
        super(name, id, size, inputStream);
    }

    /**
     * @see Tag#parse(InputStream)
     */
    @Override
    public void parse(InputStream inputStream) throws IOException {
        value = ParserUtils.parseBinary(inputStream, (int) getSize());
    }

    /**
     * @see Tag#putValue(ByteBuffer)
     */
    @Override
    protected void putValue(ByteBuffer bb) throws IOException {
        bb.put(value);
    }

    /**
     * getter for value
     * 
     * @return - byte array stored by this binary tag
     */
    public byte[] getValue() {
        return value;
    }

    /**
     * setter for value, updates the size of this tag
     * 
     * @param value
     *            - value to be set
     * @return - this for chaining
     */
    public BinaryTag setValue(byte[] value) {
        this.value = value;
        size = VINT.fromValue(value.length);
        return this;
    }

    /**
     * method to get "pretty" represented {@link Tag}
     */
    @Override
    public String toString() {
        return (super.toString() + " = binary " + (int) getSize());
    }

    /**
     * Utility helper method to get string representation of given byte array
     * 
     * @param bytes
     *            - bytes to be printed
     * @return - String representation of byte array
     */
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}
