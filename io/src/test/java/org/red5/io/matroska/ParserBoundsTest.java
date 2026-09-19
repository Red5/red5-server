package org.red5.io.matroska;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;
import org.red5.io.matroska.dtd.Tag;

/**
 * EBML element sizes must be bounded before allocation and skip must make progress (issue #480).
 */
public class ParserBoundsTest {

    // EBML header id (a compound tag, data is read on parse())
    private static final byte[] EBML_ID = { 0x1A, 0x45, (byte) 0xdf, (byte) 0xa3 };

    // DocType id (a string tag, data is read on construction)
    private static final byte[] DOCTYPE_ID = { 0x42, (byte) 0x82 };

    private static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) {
            len += p.length;
        }
        byte[] out = new byte[len];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    private static void expectIOException(byte[] bytes) throws ConverterException {
        boolean threw = false;
        try {
            ParserUtils.parseTag(new ByteArrayInputStream(bytes));
        } catch (IOException expected) {
            threw = true;
        } catch (RuntimeException | Error e) {
            fail("Oversized element produced an uncontrolled failure: " + e);
        }
        assertTrue("Expected IOException for oversized element", threw);
    }

    @Test
    public void testSizeOverflowingIntIsRejected() throws Exception {
        // 8-byte VINT, value 0x00FFFFFFFFFFFFFF -> narrows to -1
        byte[] size = { 0x01, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff };
        expectIOException(concat(DOCTYPE_ID, size, new byte[] { 0x37 }));
    }

    @Test
    public void testHugeSizeWithTruncatedStreamIsRejected() throws Exception {
        // 5-byte VINT, value 0x087FFFFFFF -> narrows to Integer.MAX_VALUE
        byte[] size = { 0x08, 0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff };
        expectIOException(concat(DOCTYPE_ID, size, new byte[] { 0x37 }));
    }

    @Test
    public void testDeclaredSizeBeyondAvailableIsRejected() throws Exception {
        // declares 64 bytes, only 1 present
        byte[] size = { (byte) 0xC0 };
        expectIOException(concat(DOCTYPE_ID, size, new byte[] { 0x37 }));
    }

    @Test
    public void testCompoundTagWithHugeSizeIsRejectedOnParse() throws Exception {
        // 5-byte VINT, value 0x087FFFFFFF -> narrows to Integer.MAX_VALUE
        byte[] size = { 0x08, 0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xff };
        byte[] bytes = concat(EBML_ID, size, new byte[] { 0x37 });
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        Tag tag = ParserUtils.parseTag(in);
        boolean threw = false;
        try {
            tag.parse(in);
        } catch (IOException expected) {
            threw = true;
        } catch (RuntimeException | Error e) {
            fail("Oversized master element produced an uncontrolled failure: " + e);
        }
        assertTrue("Expected IOException for oversized master element", threw);
    }

    @Test
    public void testSkipMakesProgressWhenStreamSkipReturnsZero() throws IOException {
        InputStream noSkip = new FilterInputStream(new ByteArrayInputStream(new byte[] { 1, 2, 3, 4 })) {
            @Override
            public long skip(long n) {
                return 0;
            }
        };
        ParserUtils.skip(3, noSkip);
        assertEquals(4, noSkip.read());
    }

    @Test
    public void testSkipPastEndOfStreamFails() throws IOException {
        try {
            ParserUtils.skip(10, new ByteArrayInputStream(new byte[] { 1, 2 }));
            fail("Expected IOException");
        } catch (IOException expected) {
        }
    }

    @Test
    public void testNormalTagStillParses() throws Exception {
        Tag tag = ParserUtils.parseTag(new ByteArrayInputStream(concat(EBML_ID, new byte[] { (byte) 0x81, 0x37 })));
        assertEquals(1, tag.getSize());
    }
}
