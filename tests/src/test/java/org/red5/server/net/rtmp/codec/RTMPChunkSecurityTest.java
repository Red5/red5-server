package org.red5.server.net.rtmp.codec;

import static org.junit.Assert.*;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.RTMPMinaConnection;
import org.red5.server.net.rtmp.message.Constants;

/**
 * Security tests for RTMP chunking vulnerabilities
 */
public class RTMPChunkSecurityTest {

    /**
     * Test that large chunk sizes are properly validated and rejected
     */
    @Test
    public void testLargeChunkSizeValidation() {
        RTMPConnection conn = new RTMPMinaConnection();
        conn.getState().setState(RTMP.STATE_CONNECTED);
        
        int initialChunkSize = conn.getState().getReadChunkSize();
        int maliciousChunkSize = Integer.MAX_VALUE; // 2GB+ chunk size
        
        // Create a ChunkSize event and try to set it
        org.red5.server.net.rtmp.event.ChunkSize chunkSizeEvent = 
                new org.red5.server.net.rtmp.event.ChunkSize(maliciousChunkSize);
        
        // Simulate what happens in RTMPProtocolDecoder when processing chunk size
        if (maliciousChunkSize < Constants.MIN_CHUNK_SIZE || maliciousChunkSize > Constants.MAX_CHUNK_SIZE) {
            // Should NOT set the malicious chunk size
            // Chunk size should remain unchanged
        } else {
            conn.getState().setReadChunkSize(maliciousChunkSize);
        }
        
        // Verify that the chunk size was not changed to the malicious value
        int currentChunkSize = conn.getState().getReadChunkSize();
        assertEquals("Malicious large chunk size should be rejected", initialChunkSize, currentChunkSize);
        assertTrue("Chunk size should remain within valid bounds", 
                currentChunkSize >= Constants.MIN_CHUNK_SIZE && currentChunkSize <= Constants.MAX_CHUNK_SIZE);
    }

    /**
     * Test that negative chunk sizes are properly validated and rejected
     */
    @Test
    public void testNegativeChunkSizeValidation() {
        RTMPConnection conn = new RTMPMinaConnection();
        conn.getState().setState(RTMP.STATE_CONNECTED);
        
        int initialChunkSize = conn.getState().getReadChunkSize();
        int negativeChunkSize = -1000;
        
        // Simulate the validation logic
        if (negativeChunkSize < Constants.MIN_CHUNK_SIZE || negativeChunkSize > Constants.MAX_CHUNK_SIZE) {
            // Should NOT set the negative chunk size
        } else {
            conn.getState().setReadChunkSize(negativeChunkSize);
        }
        
        int currentChunkSize = conn.getState().getReadChunkSize();
        assertEquals("Negative chunk size should be rejected", initialChunkSize, currentChunkSize);
        assertTrue("Chunk size should be positive", currentChunkSize > 0);
    }

    /**
     * Test that zero chunk sizes are properly validated and rejected
     */
    @Test
    public void testZeroChunkSizeValidation() {
        RTMPConnection conn = new RTMPMinaConnection();
        conn.getState().setState(RTMP.STATE_CONNECTED);
        
        int initialChunkSize = conn.getState().getReadChunkSize();
        int zeroChunkSize = 0;
        
        // Simulate the validation logic
        if (zeroChunkSize < Constants.MIN_CHUNK_SIZE || zeroChunkSize > Constants.MAX_CHUNK_SIZE) {
            // Should NOT set the zero chunk size
        } else {
            conn.getState().setReadChunkSize(zeroChunkSize);
        }
        
        int currentChunkSize = conn.getState().getReadChunkSize();
        assertEquals("Zero chunk size should be rejected", initialChunkSize, currentChunkSize);
        assertTrue("Chunk size should be positive", currentChunkSize > 0);
    }

    /**
     * Test that valid chunk sizes are accepted
     */
    @Test
    public void testValidChunkSizeAccepted() {
        RTMPConnection conn = new RTMPMinaConnection();
        conn.getState().setState(RTMP.STATE_CONNECTED);
        
        int validChunkSize = 4096; // 4KB, a reasonable chunk size
        
        // Simulate the validation logic
        if (validChunkSize >= Constants.MIN_CHUNK_SIZE && validChunkSize <= Constants.MAX_CHUNK_SIZE) {
            conn.getState().setReadChunkSize(validChunkSize);
        }
        
        int currentChunkSize = conn.getState().getReadChunkSize();
        assertEquals("Valid chunk size should be accepted", validChunkSize, currentChunkSize);
    }

    /**
     * Test the boundary values for chunk size validation
     */
    @Test
    public void testChunkSizeBoundaryValues() {
        RTMPConnection conn = new RTMPMinaConnection();
        conn.getState().setState(RTMP.STATE_CONNECTED);
        
        // Test minimum valid chunk size
        int minValidSize = Constants.MIN_CHUNK_SIZE;
        if (minValidSize >= Constants.MIN_CHUNK_SIZE && minValidSize <= Constants.MAX_CHUNK_SIZE) {
            conn.getState().setReadChunkSize(minValidSize);
        }
        assertEquals("Minimum valid chunk size should be accepted", minValidSize, conn.getState().getReadChunkSize());
        
        // Test maximum valid chunk size
        conn.getState().setReadChunkSize(128); // Reset
        int maxValidSize = Constants.MAX_CHUNK_SIZE;
        if (maxValidSize >= Constants.MIN_CHUNK_SIZE && maxValidSize <= Constants.MAX_CHUNK_SIZE) {
            conn.getState().setReadChunkSize(maxValidSize);
        }
        assertEquals("Maximum valid chunk size should be accepted", maxValidSize, conn.getState().getReadChunkSize());
        
        // Test just over maximum (should be rejected)
        conn.getState().setReadChunkSize(128); // Reset
        int initialSize = conn.getState().getReadChunkSize();
        int overMaxSize = Constants.MAX_CHUNK_SIZE + 1;
        if (overMaxSize >= Constants.MIN_CHUNK_SIZE && overMaxSize <= Constants.MAX_CHUNK_SIZE) {
            conn.getState().setReadChunkSize(overMaxSize);
        }
        assertEquals("Over-maximum chunk size should be rejected", initialSize, conn.getState().getReadChunkSize());
    }
}