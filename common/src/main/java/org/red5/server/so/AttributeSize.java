/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.so;

import java.nio.charset.StandardCharsets;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.io.amf.Output;
import org.red5.io.object.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Measures shared object attributes by their AMF0 encoded length, the form they take on the wire and in persistent storage.
 *
 * @author mondain
 */
final class AttributeSize {

    private static final Logger log = LoggerFactory.getLogger(AttributeSize.class);

    private AttributeSize() {
    }

    /**
     * Returns the AMF0 encoded length of a value.
     *
     * @param value
     *            value, may be null
     * @return encoded length in bytes, or -1 if the value cannot be encoded
     */
    static int ofValue(Object value) {
        IoBuffer buf = IoBuffer.allocate(256);
        buf.setAutoExpand(true);
        try {
            Serializer.serialize(new Output(buf), value);
            return buf.position();
        } catch (Exception e) {
            log.debug("Could not encode value of type {}", (value != null ? value.getClass().getName() : null), e);
            return -1;
        } finally {
            buf.free();
        }
    }

    /**
     * Returns the size accounted for an attribute: the UTF-8 length of its name plus the encoded length of its value. A value that cannot
     * be encoded is accounted by its name only.
     *
     * @param name
     *            attribute name
     * @param valueSize
     *            encoded length of the value, as returned by {@link #ofValue(Object)}
     * @return attribute size in bytes
     */
    static int of(String name, int valueSize) {
        return name.getBytes(StandardCharsets.UTF_8).length + Math.max(valueSize, 0);
    }

}
