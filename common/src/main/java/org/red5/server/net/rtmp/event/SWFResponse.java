package org.red5.server.net.rtmp.event;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;

/**
 * Control message used in response to a SWF verification request.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class SWFResponse extends Ping {

    private static final long serialVersionUID = -6478248060425544925L;

    private byte[] bytes;

    /**
     * <p>Constructor for SWFResponse.</p>
     */
    public SWFResponse() {
        super();
        this.eventType = PingType.getType(PingType.PONG_SWF_VERIFY);
    }

    /**
     * <p>Constructor for SWFResponse.</p>
     *
     * @param bytes an array of {@link byte} objects
     */
    public SWFResponse(byte[] bytes) {
        this();
        this.bytes = bytes;
    }

    /**
     * <p>Getter for the field <code>bytes</code>.</p>
     *
     * @return the bytes
     */
    public byte[] getBytes() {
        return bytes;
    }

    /**
     * <p>Setter for the field <code>bytes</code>.</p>
     *
     * @param bytes
     *            the bytes to set
     */
    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    /** {@inheritDoc} */
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        eventType = in.readShort();
        if (bytes == null) {
            bytes = new byte[42];
        }
        in.read(bytes);
    }

    /** {@inheritDoc} */
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        out.writeShort(eventType);
        out.write(bytes);
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "SWFResponse [bytes=" + Arrays.toString(bytes) + "]";
    }

}
