package org.red5.server.net.rtmpe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.write.DefaultWriteRequest;
import org.junit.Test;

public class EncryptedWriteRequestTest {

    @Test
    public void preservesWriteMetadataAndUsesEncryptedMessage() {
        Object originalMessage = new Object();
        DefaultWriteRequest originalRequest = new DefaultWriteRequest(originalMessage, null, new InetSocketAddress("127.0.0.1", 1935));
        IoBuffer encryptedMessage = IoBuffer.wrap(new byte[] { 1, 2, 3 });

        EncryptedWriteRequest request = new EncryptedWriteRequest(originalRequest, encryptedMessage);

        assertSame(encryptedMessage, request.getMessage());
        assertSame(originalRequest.getFuture(), request.getFuture());
        assertEquals(originalRequest.getDestination(), request.getDestination());
        assertNull(request.getOriginalRequest());
        assertTrue(request.isEncoded());
    }
}
