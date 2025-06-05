/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.rtmp.event;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.server.api.event.IEventListener;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.net.rtmp.message.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base abstract class for all RTMP events
 *
 * @author mondain
 */
public abstract class BaseEvent implements Constants, IRTMPEvent, Externalizable {

    protected Logger log = LoggerFactory.getLogger(getClass());

    // XXX we need a better way to inject allocation debugging
    // (1) make it configurable in xml
    // (2) make it aspect oriented
    private static final boolean allocationDebugging = false;

    protected AtomicInteger forkRefs = new AtomicInteger();

    /**
     * Event type
     */
    private Type type;

    /**
     * Multi-threaded copy-able array.
     */
    protected ForkableData forkableData;

    protected IoBuffer data;

    /**
     * Source type
     */
    protected byte sourceType;

    /**
     * Event target object
     */
    protected Object object;

    /**
     * Event listener
     */
    protected transient IEventListener source;

    /**
     * Event timestamp
     */
    protected int timestamp;

    /**
     * Event RTMP packet header
     */
    protected Header header = null;

    /**
     * Event references count
     */
    protected AtomicInteger refcount = new AtomicInteger(1);

    /**
     * <p>Constructor for BaseEvent.</p>
     */
    public BaseEvent() {
        // set a default type
        this(Type.SERVER, null);
    }

    /**
     * Create new event of given type
     *
     * @param type
     *            Event type
     */
    public BaseEvent(Type type) {
        this(type, null);
    }

    /**
     * Creates a raw byte array which is used to allow concurrent non-blocking copying of this event.
     * Owning thread should call addForkReference for each thread making a copy and copying threads should call removeForkReference.
     * @return
     */
    public boolean prepareForkedDuplication() {
        if (data != null && data.rewind().hasRemaining()) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            data.rewind();
            forkableData = new ForkableData(bytes);
            return true;
        }
        return false;
    }

    protected IoBuffer concurrentDataCopy() {
        return IoBuffer.wrap(forkableData.rawData).asReadOnlyBuffer();
    }

    public void addForkReference() {
        forkRefs.incrementAndGet();
    }

    public void removeForkReference() {
        int ref = forkRefs.decrementAndGet();
        if (ref <= 0) {
            forkableData = null;
        }
    }

    public BaseEvent forkedDuplicate() {
        return null;
    };

    /**
     * Create new event of given type
     *
     * @param type
     *            Event type
     * @param source
     *            Event source
     */
    public BaseEvent(Type type, IEventListener source) {
        this.type = type;
        this.source = source;
        if (allocationDebugging) {
            AllocationDebugger.getInstance().create(this);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return a Type object
     */
    public Type getType() {
        return type;
    }

    /**
     * <p>Setter for the field <code>type</code>.</p>
     *
     * @param type a Type object
     */
    @Deprecated(since = "1.3.26")
    public void setType(Type type) {
        //this.type = type;
        throw new UnsupportedOperationException("Type is immutable");
    }

    /**
     * <p>Getter for the field <code>sourceType</code>.</p>
     *
     * @return a byte
     */
    public byte getSourceType() {
        return sourceType;
    }

    /** {@inheritDoc} */
    public void setSourceType(byte sourceType) {
        this.sourceType = sourceType;
    }

    /**
     * {@inheritDoc}
     *
     * @return a {@link java.lang.Object} object
     */
    public Object getObject() {
        return object;
    }

    /**
     * {@inheritDoc}
     *
     * @return a {@link org.red5.server.net.rtmp.message.Header} object
     */
    public Header getHeader() {
        return header;
    }

    /** {@inheritDoc} */
    public void setHeader(Header header) {
        this.header = header;
    }

    /**
     * {@inheritDoc}
     *
     * @return a boolean
     */
    public boolean hasSource() {
        return source != null;
    }

    /**
     * {@inheritDoc}
     *
     * @return a {@link org.red5.server.api.event.IEventListener} object
     */
    public IEventListener getSource() {
        return source;
    }

    /** {@inheritDoc} */
    public void setSource(IEventListener source) {
        this.source = source;
    }

    /**
     * {@inheritDoc}
     *
     * @return a byte
     */
    public abstract byte getDataType();

    /**
     * {@inheritDoc}
     *
     * @return a int
     */
    public int getTimestamp() {
        return timestamp;
    }

    /** {@inheritDoc} */
    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("all")
    public void retain() {
        if (allocationDebugging) {
            AllocationDebugger.getInstance().retain(this);
        }
        final int baseCount = refcount.getAndIncrement();
        if (allocationDebugging && baseCount < 1) {
            throw new RuntimeException("attempt to retain object with invalid ref count");
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("all")
    public void release() {
        if (allocationDebugging) {
            AllocationDebugger.getInstance().release(this);
        }
        final int baseCount = refcount.decrementAndGet();
        if (baseCount == 0) {
            releaseInternal();
        } else if (allocationDebugging && baseCount < 0) {
            throw new RuntimeException("attempt to retain object with invalid ref count");
        }
    }

    /**
     * Release event
     */
    protected abstract void releaseInternal();

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        result = prime * result + sourceType;
        result = prime * result + timestamp;
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BaseEvent other = (BaseEvent) obj;
        if (type != other.type)
            return false;
        if (sourceType != other.sourceType)
            return false;
        if (timestamp != other.timestamp)
            return false;
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        type = Type.valueOf(in.readUTF());
        sourceType = in.readByte();
        timestamp = in.readInt();
        if (log.isTraceEnabled()) {
            log.trace("readExternal - type: {} sourceType: {} timestamp: {}", type, sourceType, timestamp);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        if (log.isTraceEnabled()) {
            log.trace("writeExternal - type: {} sourceType: {} timestamp: {}", type, sourceType, timestamp);
        }
        out.writeUTF(type.name());
        out.writeByte(sourceType);
        out.writeInt(timestamp);
    }

    /**
     * Provides final field of bytes for mounting non-blocking concurrent read access.
     */
    private static class ForkableData {
        final byte[] rawData;

        private ForkableData(byte[] bytes) {
            rawData = bytes;
        }
    }

}
