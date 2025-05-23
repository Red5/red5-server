/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License") + you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.red5.io.webm;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.red5.io.matroska.ConverterException;
import org.red5.io.matroska.dtd.CompoundTag;
import org.red5.io.matroska.dtd.StringTag;
import org.red5.io.matroska.dtd.Tag;
import org.red5.io.matroska.dtd.TagFactory;
import org.red5.io.matroska.dtd.UnsignedIntegerTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class able to write webm tags to file
 *
 * @author mondain
 */
public class WebmWriter implements Closeable, TagConsumer {

    private static Logger log = LoggerFactory.getLogger(WebmWriter.class);

    private boolean append;

    private RandomAccessFile dataFile;

    private File file;

    @SuppressWarnings("unused")
    private volatile long bytesWritten;

    private String filePath;

    /**
     * Constructor
     *
     * @param file
     *            - file the data need to written to
     * @param append
     *            - if <code>false</code> the file will be rewritten, if <code>true</code> data will be appended
     */
    public WebmWriter(File file, boolean append) {
        // the final version of the file will go here
        this.file = file;
        filePath = file.getAbsolutePath();
        try {
            this.append = append;
            if (append) {
                // grab the file we will append to
                if (!file.exists() || !file.canRead() || !file.canWrite()) {
                    throw new FileNotFoundException("File does not exist or cannot be accessed");
                } else {
                    log.trace("File size: {} last modified: {}", file.length(), file.lastModified());
                    // update the bytes written so we write to the correct starting position
                    bytesWritten = file.length();
                }
                this.dataFile = new RandomAccessFile(file, "rws");
            } else {
                // temporary data file for storage of stream data
                File dat = new File(filePath + ".ser");
                if (dat.exists()) {
                    dat.delete();
                    dat.createNewFile();
                }
                this.dataFile = new RandomAccessFile(dat, "rws");
            }
        } catch (Exception e) {
            log.error("Failed to create FLV writer", e);
        }
    }

    /**
     * method to write webm header to the new file
     *
     * @throws java.io.IOException
     *             - in case of IO errors
     * @throws org.red5.io.matroska.ConverterException
     *             - in case of conversion errors
     */
    public void writeHeader() throws IOException, ConverterException {
        if (append) {
            return;
        }
        try {
            CompoundTag ebml = TagFactory.<CompoundTag> create("EBML").add(TagFactory.<UnsignedIntegerTag> create("EBMLVersion").setValue(1)).add(TagFactory.<UnsignedIntegerTag> create("EBMLReadVersion").setValue(1)).add(TagFactory.<UnsignedIntegerTag> create("EBMLMaxIDLength").setValue(4)).add(TagFactory.<UnsignedIntegerTag> create("EBMLMaxSizeLength").setValue(8))
                    .add(TagFactory.<StringTag> create("DocType").setValue("webm")).add(TagFactory.<UnsignedIntegerTag> create("DocTypeVersion").setValue(3)).add(TagFactory.<UnsignedIntegerTag> create("DocTypeReadVersion").setValue(2));
            byte[] hb = ebml.encode();
            bytesWritten += hb.length;
            dataFile.write(hb);
        } catch (IOException | ConverterException e) {
            log.error("Failed to write header", e);
            throw e;
        }
    }

    /**
     * will write tag bytesWritten counter will be increased by the number of bytes actually written
     *
     * @param tag
     *            - tag to be written
     * @throws java.io.IOException
     *             - in case of any IO errors
     */
    public void writeTag(Tag tag) throws IOException {
        byte[] hb = tag.encode();
        bytesWritten += hb.length;
        dataFile.write(hb);
    }

    /**
     * {@inheritDoc}
     *
     * Will close all opened resources and "finalize" the write process
     */
    @Override
    public void close() throws IOException {
        if (dataFile != null) {
            //TODO create separate method for this
            if (!append) {
                dataFile.seek(0);
                try (RandomAccessFile rf = new RandomAccessFile(file, "rw")) {
                    rf.getChannel().transferFrom(dataFile.getChannel(), 0, dataFile.length());
                }
            }
            try {
                dataFile.close();
                dataFile = null;
            } catch (Throwable th) {
                //no-op
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void consume(Tag tag) throws IOException {
        //TODO add mode switch
        writeTag(tag);
    }
}
