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

import org.red5.io.matroska.ConverterException;
import org.red5.io.matroska.ParserUtils;
import org.red5.io.matroska.VINT;

/**
 * Tag representing complex block of different tags
 *
 */
public class SimpleBlock extends Tag {
    private VINT trackNumber;

    private long timeCode;

    private boolean keyFrame;

    private byte[] binary;

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
    public SimpleBlock(String name, VINT id) throws IOException {
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
    public SimpleBlock(String name, VINT id, VINT size, InputStream inputStream) throws IOException {
        super(name, id, size, inputStream);
    }

    /**
     * @see Tag#parse(InputStream)
     */
    @Override
    public void parse(InputStream inputStream) throws IOException, ConverterException {
        trackNumber = ParserUtils.readVINT(inputStream);
        timeCode = ParserUtils.parseInteger(inputStream, 2); // int16 by specification
        keyFrame = (0x80 == (inputStream.read() & 0x80));
        binary = ParserUtils.parseBinary(inputStream, (int) getSize() - 4);
    }

    /**
     * @see Tag#putValue(ByteBuffer)
     */
    @Override
    protected void putValue(ByteBuffer bb) throws IOException {
        bb.put(trackNumber.encode());
        bb.put(ParserUtils.getBytes(timeCode, 2));
        bb.put((byte) (keyFrame ? 0x80 : 0x00));
        bb.put(binary);
    }

    /**
     * getter for binary
     * 
     * @return - binary
     */
    public byte[] getBinary() {
        return binary;
    }

    /**
     * getter for time code
     * 
     * @return - time code
     */
    public long getTimeCode() {
        return timeCode;
    }

    /**
     * getter for track number
     * 
     * @return - track number
     */
    public int getTrackNumber() {
        return (int) trackNumber.getValue();
    }

    /**
     * getter for key frame
     * 
     * @return - key frame
     */
    public boolean isKeyFrame() {
        return keyFrame;
    }

    /**
     * method to get "pretty" represented {@link Tag}
     */
    @Override
    public String toString() {
        return (super.toString() + " = binary " + binary.length);
    }
}
