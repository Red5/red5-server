package org.red5.server.net.rtmp;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class RTMPHandshakeTest {

    @Test
    public void testRC4() throws Exception {
        // Create a dummy shared secret
        byte[] sharedSecret = new byte[16];
        for (int i = 0; i < sharedSecret.length; i++) {
            sharedSecret[i] = (byte) i;
        }

        // Create a dummy public key
        byte[] publicKey = new byte[128];
        for (int i = 0; i < publicKey.length; i++) {
            publicKey[i] = (byte) i;
        }

        // Create a dummy handshake
        RTMPHandshake handshake = new InboundHandshake();
        handshake.outgoingPublicKey = publicKey;
        handshake.incomingPublicKey = publicKey;

        // Initialize the RC4 ciphers
        handshake.initRC4Encryption(sharedSecret);

        // Encrypt and decrypt a message
        byte[] message = "Hello, World!".getBytes();
        byte[] encrypted = handshake.cipherOut.doFinal(message);
        byte[] decrypted = handshake.cipherIn.doFinal(encrypted);

        // The decrypted message should be the same as the original message
        assertArrayEquals(message, decrypted);
    }
}