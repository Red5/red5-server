/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2022 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package org.red5.server.net.rtmp;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.server.net.rtmp.message.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Buffer for incoming data.
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 *
 */
public class ReadBuffer {

    private Logger log = LoggerFactory.getLogger(getClass());

    // buffer for incoming data
    private ByteArrayOutputStream buffer = new ByteArrayOutputStream(Constants.HANDSHAKE_SIZE);

    /**
     * Returns the buffer size.
     * 
     * @return buffer remaining
     */
    public int getBufferSize() {
        return buffer.size();
    }

    /**
     * Add a byte array to the buffer.
     * 
     * @param in
     *            incoming bytes
     */
    public void addBuffer(byte[] in) {
        log.debug("Adding buffer - first: {} length: {}", in[0], in.length);
        buffer.writeBytes(in);
    }

    /**
     * Add a IoBuffer to the buffer.
     * 
     * @param in
     *            incoming IoBuffer
     */
    public void addBuffer(IoBuffer in) {
        byte[] tmp = new byte[in.remaining()];
        in.get(tmp);
        if (log.isDebugEnabled()) {
            log.debug("Adding buffer - first: {} length: {} total buffered: {}", tmp[0], tmp.length, buffer.size());
        }
        buffer.writeBytes(tmp);
    }

    /**
     * Returns buffered IoBuffer itself.
     * 
     * @return IoBuffer
     */
    public IoBuffer getBufferAsIoBuffer() {
        byte[] arr = buffer.toByteArray();
        buffer.reset();
        return IoBuffer.wrap(arr);
    }

    /**
     * Returns buffered byte array.
     * 
     * @return bytes
     */
    public byte[] getBuffer() {
        byte[] arr = buffer.toByteArray();
        buffer.reset();
        return arr;
    }

    /**
     * Returns buffered byte array.
     * 
     * @param length size of the array to return
     * @return bytes
     */
    public byte[] getBuffer(int length) {
        ByteBuffer buf = ByteBuffer.wrap(buffer.toByteArray());
        buffer.reset();
        byte[] slice = new byte[length];
        buf.get(slice);
        byte[] remaining = new byte[buf.remaining()];
        buf.get(remaining);
        buffer.writeBytes(remaining);
        return slice;
    }

    public void clearBuffer() {
        buffer.reset();
    }

}
