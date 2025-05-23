/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License") + you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.red5.io.matroska.dtd;

import static org.red5.io.matroska.ParserUtils.BIT_IN_BYTE;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.red5.io.matroska.ParserUtils;
import org.red5.io.matroska.VINT;

/**
 * http://matroska.org/technical/specs/index.html
 *
 * UnsignedInteger tag is class able to store long
 *
 * @author mondain
 */
public class UnsignedIntegerTag extends Tag {
    private long value;

    /**
     * Constructor
     *
     * @see Tag#Tag(String, VINT)
     * @param name
     *            - the name of tag to be created
     * @param id
     *            - the id of tag to be created
     * @throws java.io.IOException
     *             - in case of IO error
     */
    public UnsignedIntegerTag(String name, VINT id) throws IOException {
        super(name, id);
    }

    /**
     * Constructor
     *
     * @see Tag#Tag(String, VINT, VINT, InputStream)
     * @param name
     *            - the name of tag to be created
     * @param id
     *            - the id of tag to be created
     * @param size
     *            - the size of tag to be created
     * @param inputStream
     *            - stream to read tag data from
     * @throws java.io.IOException
     *             - in case of IO error
     */
    public UnsignedIntegerTag(String name, VINT id, VINT size, InputStream inputStream) throws IOException {
        super(name, id, size, inputStream);
    }

    /** {@inheritDoc} */
    @Override
    public void parse(InputStream inputStream) throws IOException {
        value = ParserUtils.parseInteger(inputStream, (int) getSize());
    }

    /** {@inheritDoc} */
    @Override
    protected void putValue(ByteBuffer bb) throws IOException {
        bb.put(ParserUtils.getBytes(value, getSize()));
    }

    /**
     * getter for value
     *
     * @return - value
     */
    public long getValue() {
        return value;
    }

    /**
     * setter for value, updates the size of this tag
     *
     * @param value
     *            - value to be set
     * @return - this for chaining
     */
    public UnsignedIntegerTag setValue(long value) {
        this.value = value;
        byte length = 1;
        long v = (value + 1) >> BIT_IN_BYTE;
        while (v > 0) {
            length++;
            v = v >> BIT_IN_BYTE;
        }
        size = VINT.fromValue(length);
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * method to get "pretty" represented {@link Tag}
     */
    @Override
    public String toString() {
        return (super.toString() + " = " + value);
    }
}
