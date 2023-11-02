/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.m4a.impl;

import java.io.File;
import java.io.IOException;

import org.red5.io.ITagReader;
import org.red5.io.ITagWriter;
import org.red5.io.m4a.IM4A;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A M4AImpl implements the M4A api
 *
 * @author The Red5 Project
 * @author Paul Gregoire, (mondain@gmail.com)
 */
public class M4A implements IM4A {

    protected static Logger log = LoggerFactory.getLogger(M4A.class);

    private File file;

    /**
     * Default constructor, used by Spring so that parameters may be injected.
     */
    public M4A() {
    }

    /**
     * Create M4A from given file source
     *
     * @param file
     *            File source
     */
    public M4A(File file) {
        this.file = file;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITagReader getReader() throws IOException {
        return new M4AReader(file);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITagWriter getWriter() throws IOException {
        return null;
    }

    @Override
    public ITagWriter getAppendWriter() throws IOException {
        return null;
    }

}
