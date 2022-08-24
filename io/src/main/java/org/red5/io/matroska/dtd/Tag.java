/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License") + you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.red5.io.matroska.dtd;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.red5.io.matroska.ConverterException;
import org.red5.io.matroska.ParserUtils;
import org.red5.io.matroska.VINT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for all webm tags
 *
 */
public abstract class Tag {
    static Logger log = LoggerFactory.getLogger(Tag.class);

    private String name;

    VINT id;

    VINT size;

    private byte[] data;

    /**
     * Constructor, internally calls {@link Tag#Tag(String, VINT, VINT, InputStream)} to create tag with 0 size
     *
     * @param name
     *            - the name of tag to be created
     * @param id
     *            - the id of tag to be created
     * @throws IOException
     *             - in case of IO error
     */
    public Tag(String name, VINT id) throws IOException {
        this(name, id, new VINT(0L, (byte) 0, 0L), null);
    }

    /**
     * Constructor
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
     *             - in case of any IO errors
     */
    public Tag(String name, VINT id, VINT size, InputStream inputStream) throws IOException {
        this.name = name;
        this.id = id;
        this.size = size;
        readData(inputStream);
    }

    /**
     * method to read and to parse tag from inputStream given
     *
     * @param inputStream
     *            - stream to parse tag data from
     * @throws IOException
     *             - in case of any IO errors
     * @throws ConverterException
     *             - in case of any conversion errors
     */
    public abstract void parse(InputStream inputStream) throws IOException, ConverterException;

    /**
     * method to parse tag from inner bytes array - data
     *
     * @throws IOException
     *             - in case of any IO errors
     * @throws ConverterException
     *             - in case of any conversion errors
     */
    public void parse() throws IOException, ConverterException {
        parse(new ByteArrayInputStream(data));
    }

    /**
     * method to read tag data from inputStream given
     *
     * @param inputStream InputStream
     * @throws IOException
     *             - in case of any IO errors
     */
    public void readData(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return;
        }

        data = ParserUtils.parseBinary(inputStream, (int) size.getValue());
    }

    /**
     * method to store tag value to {@link ByteBuffer} given
     *
     * @param bb
     *            - {@link ByteBuffer} to store value
     * @throws IOException
     *             - in case of any IO errors
     */
    protected abstract void putValue(ByteBuffer bb) throws IOException;

    /**
     * getter for name
     *
     * @return name of this {@link Tag}
     */
    public String getName() {
        return name;
    }

    /**
     * getter for id
     *
     * @return id of this {@link Tag} as binary value of correspondent {@link VINT}
     */
    public long getId() {
        return id.getBinary();
    }

    /**
     * getter for size
     *
     * @return size of this {@link Tag} as value of correspondent {@link VINT}
     */
    public long getSize() {
        return size.getValue();
    }

    /**
     * method to get total size of this tag: "header" + "contents"
     *
     * @return - total size as int
     */
    public int totalSize() {
        return (int) (id.getLength() + size.getLength() + size.getValue());
    }

    /**
     * method to encode {@link Tag} as sequence of bytes
     *
     * @return - encoded {@link Tag}
     * @throws IOException
     *             - in case of any IO errors
     */
    public byte[] encode() throws IOException {
        final ByteBuffer buf = ByteBuffer.allocate(totalSize());
        log.debug("Tag: " + this);
        buf.put(id.encode());
        buf.put(size.encode());
        putValue(buf);
        buf.flip();
        return buf.array();
    }

    /**
     * method to get "pretty" represented {@link Tag}
     */
    @Override
    public String toString() {
        return String.format("%s [id: %s, size: %s]", name, id, size);
    }
}
