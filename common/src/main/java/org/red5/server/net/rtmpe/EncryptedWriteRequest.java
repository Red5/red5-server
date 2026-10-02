package org.red5.server.net.rtmpe;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.write.DefaultWriteRequest;
import org.apache.mina.core.write.WriteRequest;

/**
 * Used to parcel encrypted content for RTMPE.
 *
 * @author Paul Gregoire
 */
public class EncryptedWriteRequest extends DefaultWriteRequest {

    private final IoBuffer encryptedMessage;

    /**
     * <p>Constructor for EncryptedWriteRequest.</p>
     *
     * @param writeRequest a {@link org.apache.mina.core.write.WriteRequest} object
     * @param encryptedMessage a {@link org.apache.mina.core.buffer.IoBuffer} object
     */
    public EncryptedWriteRequest(WriteRequest writeRequest, IoBuffer encryptedMessage) {
        super(encryptedMessage, writeRequest.getFuture(), writeRequest.getDestination());
        this.encryptedMessage = encryptedMessage;
    }

    /** {@inheritDoc} */
    @Override
    public WriteRequest getOriginalRequest() {
        // we dont want to return the original request because its message is essentially overwritten
        // prevents java.nio.InvalidMarkException in AbstractPollingIoProcessor
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Object getMessage() {
        return encryptedMessage;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEncoded() {
        return true;
    }

}
