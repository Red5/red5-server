package org.red5.io.obu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Regression tests for OBU payload bounds validation (issue #471).
 */
public class OBUParserBoundsTest {

    // OBU header: type SEQUENCE_HEADER (1), no extension, has_size_field set
    private static final byte OBU_HEADER_WITH_SIZE = 0x0A;

    @Test
    public void testOverflowingSizeFieldIsRejectedAsParseError() {
        // LEB128 for 0x7FFFFFFF; pos + size overflows int
        byte[] buf = { OBU_HEADER_WITH_SIZE, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x07, 0x00, 0x00 };
        try {
            OBUParser.getNextObu(buf, 0, buf.length);
            fail("Expected OBUParseException");
        } catch (OBUParseException expected) {
            // controlled failure
        } catch (RuntimeException | Error e) {
            fail("Oversized OBU produced an uncontrolled failure: " + e);
        }
    }

    @Test
    public void testSizeLargerThanRemainingIsRejected() {
        // declares 64 bytes but only 2 follow
        byte[] buf = { OBU_HEADER_WITH_SIZE, 0x40, 0x01, 0x02 };
        try {
            OBUParser.getNextObu(buf, 0, buf.length);
            fail("Expected OBUParseException");
        } catch (OBUParseException expected) {
        }
    }

    @Test
    public void testExactBoundarySizeIsAccepted() throws Exception {
        byte[] buf = { OBU_HEADER_WITH_SIZE, 0x02, 0x11, 0x22 };
        OBUInfo info = OBUParser.getNextObu(buf, 0, buf.length);
        assertEquals(2, info.size);
        assertEquals(2, info.data.remaining());
        assertEquals(0x11, info.data.get(0));
    }
}
