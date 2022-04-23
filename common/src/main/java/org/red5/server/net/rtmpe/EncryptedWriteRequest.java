package org.red5.server.net.rtmpe;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.core.write.WriteRequestWrapper;

/**
 * Used to parcel encrypted content for RTMPE.
 * 
 * @author Paul Gregoire
 */
public class EncryptedWriteRequest extends WriteRequestWrapper {

    private final IoBuffer encryptedMessage;

    public EncryptedWriteRequest(WriteRequest writeRequest, IoBuffer encryptedMessage) {
        super(writeRequest);
        this.encryptedMessage = encryptedMessage;
    }

    @Override
    public WriteRequest getOriginalRequest() {
        // we dont want to return the original request because its message is essentially overwritten
        // prevents java.nio.InvalidMarkException in AbstractPollingIoProcessor
        return null;
    }

    @Override
    public Object getMessage() {
        return encryptedMessage;
    }

    @Override
    public boolean isEncoded() {
        return true;
    }

}
