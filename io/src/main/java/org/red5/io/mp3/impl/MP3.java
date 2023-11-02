/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.mp3.impl;

import java.io.File;
import java.io.IOException;

import org.red5.io.ITagReader;
import org.red5.io.ITagWriter;
import org.red5.io.mp3.IMP3;

/**
 * Represents MP3 file
 */
public class MP3 implements IMP3 {
    /**
     * Actual file object
     */
    private File file;

    /**
     * Creates MP3 object using given file
     *
     * @param file
     *            File object to use
     */
    public MP3(File file) {
        this.file = file;
    }

    /** {@inheritDoc} */
    @Override
    public ITagReader getReader() throws IOException {
        return new MP3Reader(file);
    }

    /** {@inheritDoc} */
    @Override
    public ITagWriter getWriter() throws IOException {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public ITagWriter getAppendWriter() throws IOException {
        return null;
    }

}
