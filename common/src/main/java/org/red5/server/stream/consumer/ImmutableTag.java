package org.red5.server.stream.consumer;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.io.ITag;

/**
 * An ImmutableTag represents immutable encapsulation of flash media data. The timestamp is the only mutable field.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class ImmutableTag implements ITag {

    /**
     * Tag data type
     */
    private final byte dataType;

    /**
     * Timestamp
     */
    private int timestamp;

    /**
     * Tag body as byte buffer
     */
    private final byte[] body;

    /**
     * Previous tag size
     */
    private final int previousTagSize;

    /**
     * ImmutableTag Constructor
     *
     * @param dataType
     *            Tag data type
     * @param timestamp
     *            Timestamp
     * @param body
     *            Tag body
     */
    private ImmutableTag(byte dataType, int timestamp, byte[] data) {
        this.dataType = dataType;
        this.timestamp = timestamp;
        this.body = data;
        this.previousTagSize = 0;
    }

    /**
     * ImmutableTag Constructor
     *
     * @param dataType
     *            Tag data type
     * @param timestamp
     *            Timestamp
     * @param body
     *            Tag body
     * @param previousTagSize
     *            Previous tag size information
     */
    private ImmutableTag(byte dataType, int timestamp, byte[] data, int previousTagSize) {
        this.dataType = dataType;
        this.timestamp = timestamp;
        this.body = data;
        this.previousTagSize = previousTagSize;
    }

    /**
     * Get the data type
     *
     * @return Tag data type
     */
    public byte getDataType() {
        return dataType;
    }

    /**
     * Return the timestamp
     *
     * @return Tag timestamp
     */
    public int getTimestamp() {
        return timestamp;
    }

    /** {@inheritDoc} */
    @Override
    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * {@inheritDoc}
     *
     * @return a {@link org.apache.mina.core.buffer.IoBuffer} object
     */
    public IoBuffer getData() {
        return IoBuffer.wrap(body);
    }

    /**
     * Return the body IoBuffer
     *
     * @return Tag body
     */
    public IoBuffer getBody() {
        return IoBuffer.wrap(body);
    }

    /**
     * Return the size of the body
     *
     * @return Tag body size
     */
    public int getBodySize() {
        return body.length;
    }

    /**
     * Return previous tag size
     *
     * @return Previous tag size
     */
    public int getPreviousTagSize() {
        return previousTagSize;
    }

    /** {@inheritDoc} */
    @Override
    public void setBody(IoBuffer body) {
    }

    /** {@inheritDoc} */
    @Override
    public void setBodySize(int size) {
    }

    /** {@inheritDoc} */
    @Override
    public void setDataType(byte datatype) {
    }

    /** {@inheritDoc} */
    @Override
    public void setPreviousTagSize(int size) {
    }

    /**
     * {@inheritDoc}
     *
     * Prints out the contents of the tag
     */
    @Override
    public String toString() {
        String ret = "Data Type\t=" + dataType + "\n";
        ret += "Prev. Tag Size\t=" + previousTagSize + "\n";
        ret += "Body size\t=" + body.length + "\n";
        ret += "timestamp\t=" + timestamp + "\n";
        ret += "Body Data\t=" + body + "\n";
        return ret;
    }

    /**
     * <p>build.</p>
     *
     * @param dataType a byte
     * @param timestamp a int
     * @return a {@link org.red5.server.stream.consumer.ImmutableTag} object
     */
    public static ImmutableTag build(byte dataType, int timestamp) {
        return ImmutableTag.build(dataType, timestamp, null);
    }

    /**
     * <p>build.</p>
     *
     * @param dataType a byte
     * @param timestamp a int
     * @param data a {@link java.lang.Object} object
     * @return a {@link org.red5.server.stream.consumer.ImmutableTag} object
     */
    public static ImmutableTag build(byte dataType, int timestamp, Object data) {
        if (data != null) {
            if (data instanceof IoBuffer) {
                IoBuffer buf = (IoBuffer) data;
                byte[] body = new byte[buf.remaining()];
                buf.mark();
                buf.get(body);
                buf.reset();
                return new ImmutableTag(dataType, timestamp, body);
            } else {
                byte[] buf = (byte[]) data;
                byte[] body = new byte[buf.length];
                System.arraycopy(buf, 0, body, 0, body.length);
                return new ImmutableTag(dataType, timestamp, body);
            }
        } else {
            return new ImmutableTag(dataType, timestamp, null);
        }
    }

    /**
     * <p>build.</p>
     *
     * @param dataType a byte
     * @param timestamp a int
     * @param data a {@link org.apache.mina.core.buffer.IoBuffer} object
     * @return a {@link org.red5.server.stream.consumer.ImmutableTag} object
     */
    public static ImmutableTag build(byte dataType, int timestamp, IoBuffer data) {
        if (data != null) {
            byte[] body = new byte[data.remaining()];
            data.mark();
            data.get(body);
            data.reset();
            return new ImmutableTag(dataType, timestamp, body);
        } else {
            return new ImmutableTag(dataType, timestamp, null);
        }
    }

    /**
     * <p>build.</p>
     *
     * @param dataType a byte
     * @param timestamp a int
     * @param data an array of {@link byte} objects
     * @param previousTagSize a int
     * @return a {@link org.red5.server.stream.consumer.ImmutableTag} object
     */
    public static ImmutableTag build(byte dataType, int timestamp, byte[] data, int previousTagSize) {
        if (data != null) {
            byte[] body = new byte[data.length];
            System.arraycopy(data, 0, body, 0, body.length);
            return new ImmutableTag(dataType, timestamp, body, previousTagSize);
        } else {
            return new ImmutableTag(dataType, timestamp, null, previousTagSize);
        }
    }

    /**
     * <p>build.</p>
     *
     * @param dataType a byte
     * @param timestamp a int
     * @param data a {@link org.apache.mina.core.buffer.IoBuffer} object
     * @param previousTagSize a int
     * @return a {@link org.red5.server.stream.consumer.ImmutableTag} object
     */
    public static ImmutableTag build(byte dataType, int timestamp, IoBuffer data, int previousTagSize) {
        if (data != null) {
            byte[] body = new byte[data.remaining()];
            data.mark();
            data.get(body);
            data.reset();
            return new ImmutableTag(dataType, timestamp, body, previousTagSize);
        } else {
            return new ImmutableTag(dataType, timestamp, null, previousTagSize);
        }
    }

}
