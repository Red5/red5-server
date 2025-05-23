/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.mp4.impl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;

import org.red5.io.ITagReader;
import org.red5.io.ITagWriter;
import org.red5.io.flv.meta.IMetaData;
import org.red5.io.flv.meta.IMetaService;
import org.red5.io.flv.meta.MetaService;
import org.red5.io.mp4.IMP4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A MP4Impl implements the MP4 api
 *
 * @author The Red5 Project
 * @author Paul Gregoire, (mondain@gmail.com)
 */
public class MP4 implements IMP4 {

    /** Constant <code>log</code> */
    protected static Logger log = LoggerFactory.getLogger(MP4.class);

    private File file;

    private IMetaService metaService;

    private IMetaData<?, ?> metaData;

    /**
     * Default constructor, used by Spring so that parameters may be injected.
     */
    public MP4() {
    }

    /**
     * Create MP4 from given file source.
     *
     * @param file
     *            File source
     */
    public MP4(File file) {
        this.file = file;
        /*
         * try { MP4Reader reader = new MP4Reader(this.file); ITag tag = reader.createFileMeta(); if (metaService == null) { metaService = new MetaService(this.file); } metaData =
         * metaService.readMetaData(tag.getBody()); reader.close(); } catch (Exception e) { log.error("An error occurred looking for metadata:", e); }
         */
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasMetaData() {
        return metaData != null;
    }

    /** {@inheritDoc} */
    @Override
    public IMetaData<?, ?> getMetaData() throws FileNotFoundException {
        metaService.setFile(file);
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasKeyFrameData() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public void setKeyFrameData(Map<?, ?> keyframedata) {
    }

    /** {@inheritDoc} */
    @Override
    public Map<?, ?> getKeyFrameData() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void refreshHeaders() throws IOException {
    }

    /** {@inheritDoc} */
    @Override
    public void flushHeaders() throws IOException {
    }

    /** {@inheritDoc} */
    @Override
    public ITagReader getReader() throws IOException {
        MP4Reader reader = null;
        String fileName = file.getName();
        if (file.exists()) {
            log.debug("File name: {} size: {}", fileName, file.length());
            reader = new MP4Reader(file);
        } else {
            log.info("Creating new file: {}", file);
            file.createNewFile();
        }
        return reader;
    }

    /** {@inheritDoc} */
    @Override
    public ITagReader readerFromNearestKeyFrame(int seekPoint) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public ITagWriter getWriter() throws IOException {
        log.info("getWriter: {}", file);
        //return new MP4Writer(file.toPath(), false);
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public ITagWriter writerFromNearestKeyFrame(int seekPoint) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void setMetaData(IMetaData<?, ?> meta) throws IOException {
        if (metaService == null) {
            metaService = new MetaService(file);
        }
        //if the file is not checked the write may produce an NPE
        if (metaService.getFile() == null) {
            metaService.setFile(file);
        }
        metaService.write(meta);
        metaData = meta;
    }

    /** {@inheritDoc} */
    @Override
    public void setMetaService(IMetaService service) {
        metaService = service;
    }

    /** {@inheritDoc} */
    @Override
    public ITagWriter getAppendWriter() throws IOException {
        return null;
    }

}
