/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License") + you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.red5.io.matroska.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.red5.io.matroska.ConverterException;
import org.red5.io.matroska.ParserUtils;
import org.red5.io.matroska.dtd.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * class able to walk through the webm file and pass parsed tags to registered handlers
 *
 */
public class TagCrawler {
    private static Logger log = LoggerFactory.getLogger(TagCrawler.class);

    private final Map<String, TagHandler> handlers = new HashMap<>();

    private final TagHandler skipHandler;

    /**
     * Constructor
     *
     */
    public TagCrawler() {
        skipHandler = createSkipHandler();
    }

    /**
     * Method to add {@link TagHandler}
     *
     * @param name
     *            - unique name of tag handler
     * @param handler
     *            - handler
     * @return - this for chaining
     */
    public TagCrawler addHandler(String name, TagHandler handler) {
        handlers.put(name, handler);
        return this;
    }

    /**
     * Method to remove {@link TagHandler}
     *
     * @param name
     *            - unique name of tag handler
     * @return - this for chaining
     */
    public TagCrawler removeHandler(String name) {
        if (handlers.containsKey(name)) {
            handlers.remove(name);
        }
        return this;
    }

    /**
     * Method to get {@link TagHandler} by tag, can be overridden to change the logic of handler searching
     *
     * @param tag
     *            - tag to be handled
     * @return - this for chaining
     */
    public TagHandler getHandler(Tag tag) {
        if (handlers.containsKey(tag.getName())) {
            return handlers.get(tag.getName());
        }
        return null;
    }

    /**
     * Method to create "default" handler (the one will be used if none other handlers were found) can be overridden to change the logic
     *
     * @return - this for chaining
     */
    public TagHandler createSkipHandler() {
        return new TagHandler() {
            @Override
            public void handle(Tag tag, InputStream input) throws IOException, ConverterException {
                log.debug("Going to skip tag: " + tag.getName());
                long size = tag.getSize();
                while (size > 0) {
                    size -= input.skip(size);
                }
            }
        };
    }

    /**
     * Method to process the input stream given, will stop as soon as input stream will be empty
     *
     * @param input
     *            - input stream to process
     * @throws IOException
     *             - in case of any IO errors
     * @throws ConverterException
     *             - in case of any conversion errorss
     */
    public void process(InputStream input) throws IOException, ConverterException {
        while (0 != input.available()) {
            Tag tag = ParserUtils.parseTag(input);
            TagHandler handler = getHandler(tag);
            if (null == handler) {
                skipHandler.handle(tag, input);
            } else {
                handler.handle(tag, input);
            }
        }
    }
}
