package org.red5.server.net.rtmp.event;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Control message used to set a buffer.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class SetBuffer extends Ping {

    private static final long serialVersionUID = -6478248060425544924L;

    private int streamId;

    private int bufferLength;

    /**
     * <p>Constructor for SetBuffer.</p>
     */
    public SetBuffer() {
        super();
        this.eventType = Ping.CLIENT_BUFFER;
    }

    /**
     * <p>Constructor for SetBuffer.</p>
     *
     * @param streamId a int
     * @param bufferLength a int
     */
    public SetBuffer(int streamId, int bufferLength) {
        this();
        this.streamId = streamId;
        this.bufferLength = bufferLength;
    }

    /**
     * <p>Getter for the field <code>streamId</code>.</p>
     *
     * @return the streamId
     */
    public int getStreamId() {
        return streamId;
    }

    /**
     * <p>Setter for the field <code>streamId</code>.</p>
     *
     * @param streamId
     *            the streamId to set
     */
    public void setStreamId(int streamId) {
        this.streamId = streamId;
    }

    /**
     * <p>Getter for the field <code>bufferLength</code>.</p>
     *
     * @return the bufferLength
     */
    public int getBufferLength() {
        return bufferLength;
    }

    /**
     * <p>Setter for the field <code>bufferLength</code>.</p>
     *
     * @param bufferLength
     *            the bufferLength to set
     */
    public void setBufferLength(int bufferLength) {
        this.bufferLength = bufferLength;
    }

    /** {@inheritDoc} */
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        eventType = in.readShort();
        streamId = in.readInt();
        bufferLength = in.readInt();
    }

    /** {@inheritDoc} */
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        out.writeShort(eventType);
        out.writeInt(streamId);
        out.writeInt(bufferLength);
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "SetBuffer [streamId=" + streamId + ", bufferLength=" + bufferLength + "]";
    }

}
