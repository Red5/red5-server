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

import org.red5.io.matroska.ConverterException;
import org.red5.io.matroska.dtd.Tag;

/**
 * Any class able to handle {@link org.red5.io.matroska.dtd.Tag} using {@link java.io.InputStream} given
 *
 * @author mondain
 */
public interface TagHandler {
    /**
     * <p>handle.</p>
     *
     * @param tag a {@link org.red5.io.matroska.dtd.Tag} object
     * @param input a {@link java.io.InputStream} object
     * @throws java.io.IOException if any.
     * @throws org.red5.io.matroska.ConverterException if any.
     */
    void handle(Tag tag, InputStream input) throws IOException, ConverterException;
}
