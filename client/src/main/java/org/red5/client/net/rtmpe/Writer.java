/*
 * RED5 Open Source Flash Server - http://code.google.com/p/red5/ Copyright 2006-2012 by respective authors (see below). All rights reserved. Licensed under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.client.net.rtmpe;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.client.net.rtmp.IClientListener;
import org.red5.io.ITag;
import org.red5.io.ITagWriter;
import org.red5.io.flv.impl.FLVWriter;
import org.red5.io.flv.impl.Tag;
import org.red5.server.net.rtmp.event.Aggregate;
import org.red5.server.net.rtmp.event.IRTMPEvent;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.stream.IStreamData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A tag writer that may be used to write stream data to disk.
 *
 * @author Paul Gregoire
 */
public class Writer implements IClientListener {

    private static final Logger log = LoggerFactory.getLogger(Writer.class);

    private ITagWriter writer;

    /**
     * Instantiates a writer for the given path.
     *
     * @param filePath a {@link java.lang.String} object
     */
    public Writer(String filePath) {
        File file = new File(filePath);
        try {
            file.createNewFile();
            writer = new FLVWriter(file, false);
        } catch (IOException e) {
            log.error("Output file for the writer creation failed", e);
        }
    }

    /**
     * Instantiates a writer for the given path.
     *
     * @param filePath a {@link java.nio.file.Path} object
     */
    public Writer(Path filePath) {
        File file = filePath.toFile();
        try {
            file.createNewFile();
            writer = new FLVWriter(file, false);
        } catch (IOException e) {
            log.error("Output file for the writer creation failed", e);
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("rawtypes")
    public void onClientListenerEvent(IRTMPEvent event) {
        log.debug("onClientListenerEvent: {}", event);
        if (event instanceof IStreamData) {
            if (event.getHeader().getSize() > 0 && writer != null) {
                synchronized (writer) {
                    ITag tag = new Tag();
                    byte dataType = event.getDataType();
                    switch (dataType) {
                        case Constants.TYPE_AGGREGATE:
                            Aggregate aggregate = (Aggregate) event;
                            int aggTimestamp = event.getTimestamp();
                            log.debug("Timestamp (aggregate): {}", aggTimestamp);
                            LinkedList<IRTMPEvent> parts = aggregate.getParts();
                            for (IRTMPEvent part : parts) {
                                tag.setDataType(part.getDataType());
                                tag.setTimestamp(part.getTimestamp());
                                IoBuffer data = ((IStreamData) part).getData();
                                tag.setBodySize(data.limit());
                                tag.setBody(data);
                                log.debug("Data: {}", data);
                                try {
                                    if (writer.writeTag(tag)) {
                                        log.trace("Tag was written {}", tag);
                                    }
                                } catch (Exception e) {
                                    log.error("Exception writing to file", e);
                                } finally {
                                    data.free();
                                }
                            }
                            parts.clear();
                            break;
                        case Constants.TYPE_AUDIO_DATA:
                        case Constants.TYPE_VIDEO_DATA:
                            tag.setDataType(dataType);
                            tag.setTimestamp(event.getTimestamp());
                            IoBuffer data = ((IStreamData) event).getData();
                            tag.setBodySize(data.limit());
                            tag.setBody(data);
                            log.debug("Data: {}", data);
                            try {
                                if (writer.writeTag(tag)) {
                                    log.trace("Tag was written {}", tag);
                                }
                            } catch (Exception e) {
                                log.error("Exception writing to file", e);
                            } finally {
                                data.free();
                            }
                            break;
                        default:
                            log.debug("Non A/V data detected, it will not be written: {}", dataType);
                    }
                }
            } else {
                log.debug("Skipping event where header size <= 0");
            }
        } else {
            log.debug("Skipping non stream data");
        }
    }

    /**
     * {@inheritDoc}
     */
    public void stopListening() {
        log.debug("stopListening, client is finished providing data");
        if (writer != null) {
            try {
                writer.close();
            } catch (Exception e) {
                log.warn("Exception closing the writer", e);
            }
            log.debug("Bytes written: {}", writer.getBytesWritten());
            writer = null;
        }
    }

}
