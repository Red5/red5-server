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
import java.util.HashMap;
import java.util.Map;

import org.red5.io.matroska.ConverterException;
import org.red5.io.matroska.ParserUtils;
import org.red5.io.matroska.VINT;

public class CompoundTag extends Tag {
    private Map<String, Tag> subElements = new HashMap<>();

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
    public CompoundTag(String name, VINT id) throws IOException {
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
    public CompoundTag(String name, VINT id, VINT size, InputStream inputStream) throws IOException {
        super(name, id, size, inputStream);
    }

    /**
     * @see Tag#readData(InputStream)
     *
     * @param inputStream
     *            - stream to read tag data from
     * @throws IOException
     *             - in case of any IO errors
     */
    @Override
    public void readData(InputStream inputStream) throws IOException {
        // we save RAM here
        return;
    }

    /**
     * @see Tag#parse(InputStream)
     */
    @Override
    public void parse(InputStream inputStream) throws IOException, ConverterException {
        for (Tag tag : ParserUtils.parseMasterElement(inputStream, (int) getSize())) {
            subElements.put(tag.getName(), tag);
        }
    }

    @Override
    public int totalSize() {
        return (int) (id.getLength() + size.getLength() + (!subElements.isEmpty() ? size.getValue() : 0));
    }

    /**
     * @see Tag#putValue(ByteBuffer)
     */
    @Override
    protected void putValue(ByteBuffer bb) throws IOException {
        for (Tag tag : subElements.values()) {
            bb.put(tag.encode());
        }
    }

    /**
     * method to add child tag to this {@link CompoundTag}, updates the size on add
     *
     * @param ch
     *            - child {@link Tag} to be added
     * @return - this for chaining
     */
    public CompoundTag add(Tag ch) {
        subElements.put(ch.getName(), ch);
        long sz = getSize() + ch.totalSize();
        byte length = 1;
        long v = (sz + 1) >> BIT_IN_BYTE;
        while (v > 0) {
            length++;
            v = v >> BIT_IN_BYTE;
        }
        size = new VINT(0L, length, sz);
        return this;
    }

    public int getNumberOfSubElements() {
        return subElements.size();
    }

    public Tag get(String tagName) {
        return subElements.get(tagName);
    }

    /**
     * method to get "pretty" represented {@link Tag}
     */
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(super.toString() + "\n");
        for (Tag tag : subElements.values()) {
            result.append("\t" + tag + "\n");
        }
        return result.toString();
    }
}
