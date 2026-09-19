package org.red5.io.mp4.impl;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;

import org.junit.Test;
import org.red5.io.ITag;

/**
 * MP4 sample sizes must be validated against the file before allocation (issue #476).
 */
public class MP4ReaderBoundsTest {

    private static File craftedFixture(int firstSampleSize) throws Exception {
        byte[] bytes = Files.readAllBytes(new File("target/test-classes/fixtures/mov_h264.mp4").toPath());
        int idx = indexOf(bytes, "stsz".getBytes());
        // stsz layout: type(4) version/flags(4) sample_size(4) count(4) entries...
        ByteBuffer.wrap(bytes, idx + 16, 4).putInt(firstSampleSize);
        File out = File.createTempFile("red5", "bad-stsz.mp4");
        out.deleteOnExit();
        Files.write(out.toPath(), bytes);
        return out;
    }

    private static int indexOf(byte[] hay, byte[] needle) {
        outer: for (int i = 0; i <= hay.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static void readAll(File f) throws Exception {
        long fileSize = f.length();
        MP4Reader reader = new MP4Reader(f);
        try {
            for (int i = 0; i < 64 && reader.hasMoreTags(); i++) {
                ITag tag = reader.readTag();
                if (tag == null) {
                    break;
                }
                assertTrue("tag body larger than the file", tag.getBodySize() <= fileSize);
            }
        } finally {
            reader.close();
        }
    }

    @Test
    public void testHugeSampleSizeDoesNotAllocate() throws Exception {
        try {
            readAll(craftedFixture(0x7fffffff));
        } catch (OutOfMemoryError | IllegalArgumentException e) {
            fail("huge declared sample size must be rejected before allocation: " + e);
        }
    }

    @Test
    public void testSampleBeyondEndOfFileIsRejected() throws Exception {
        // sample fits in int but extends past EOF
        readAll(craftedFixture(0x00ffffff));
    }
}
