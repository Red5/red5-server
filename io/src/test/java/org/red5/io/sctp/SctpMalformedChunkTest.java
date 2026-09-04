package org.red5.io.sctp;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.red5.io.sctp.packet.chunks.ChunkFactory;
import org.red5.io.sctp.packet.chunks.StateCookie;

/**
 * Malformed SCTP chunks must fail with SctpException, never an uncontrolled runtime failure (issue #472).
 */
public class SctpMalformedChunkTest {

    @Test
    public void testUnknownChunkTypeByteIsControlledFailure() {
        // 0xff is outside ChunkType.values(); as a signed byte it is also negative
        byte[] data = { (byte) 0xff, 0x00, 0x00, 0x04 };
        try {
            ChunkFactory.createChunk(data, 0, data.length);
            fail("Expected SctpException");
        } catch (SctpException expected) {
        } catch (RuntimeException e) {
            fail("Malformed chunk type escaped as " + e);
        }
    }

    @Test
    public void testHighChunkTypeByteIsControlledFailure() {
        byte[] data = { 0x7f, 0x00, 0x00, 0x04 };
        try {
            ChunkFactory.createChunk(data, 0, data.length);
            fail("Expected SctpException");
        } catch (SctpException expected) {
        } catch (RuntimeException e) {
            fail("Malformed chunk type escaped as " + e);
        }
    }

    @Test
    public void testStateCookieWithOversizedMacLengthIsRejected() {
        byte[] data = { 0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff, 0, 0, 0, 0 };
        try {
            new StateCookie(data, 0, data.length);
            fail("Expected SctpException");
        } catch (Exception e) {
            assertTrue("expected SctpException but got " + e, e instanceof SctpException);
        } catch (Error e) {
            fail("Oversized cookie escaped as " + e);
        }
    }

    @Test
    public void testStateCookieWithMacLengthBeyondBufferIsRejected() {
        byte[] data = { 0, 0, 0, 16, 1, 2, 3, 4 };
        try {
            new StateCookie(data, 0, data.length);
            fail("Expected SctpException");
        } catch (Exception e) {
            assertTrue("expected SctpException but got " + e, e instanceof SctpException);
        } catch (Error e) {
            fail("Truncated cookie escaped as " + e);
        }
    }
}
