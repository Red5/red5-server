package org.red5.io.flv.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import org.junit.Test;
import org.red5.io.ITag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FLVReaderTest {

    private static Logger log = LoggerFactory.getLogger(FLVReaderTest.class);

    private static final String FIXTURES = "target/test-classes/fixtures/";

    private static File fixture(String name) {
        File file = Paths.get(FIXTURES + name).toFile();
        assertTrue("Missing fixture: " + file, file.isFile());
        return file;
    }

    /**
     * Reads the first tags of a file and checks each has a body.
     */
    private static void readLeadingTags(File file, boolean generateMetadata, int count) throws IOException {
        log.info("Reading: {}", file.getName());
        FLVReader reader = new FLVReader(file, generateMetadata);
        try {
            for (int t = 0; t < count; t++) {
                assertTrue(file.getName() + " ran out of tags at " + t, reader.hasMoreTags());
                ITag tag = reader.readTag();
                log.debug("Tag: {}", tag);
                assertNotNull(file.getName() + " tag " + t + " is null", tag);
                assertNotNull(file.getName() + " tag " + t + " has no body", tag.getBody());
            }
        } finally {
            reader.close();
        }
        log.info("Finished reading: {}\n", file.getName());
    }

    /**
     * Reads every tag of a file and returns the number of tags read, with a count of the non audio/video tags.
     */
    private static int[] readAllTags(File file, boolean generateMetadata) throws IOException {
        log.info("Reading: {}", file.getName());
        int total = 0, meta = 0;
        FLVReader reader = new FLVReader(file, generateMetadata);
        try {
            while (reader.hasMoreTags()) {
                ITag tag = reader.readTag();
                assertNotNull(file.getName() + " tag " + total + " is null", tag);
                total++;
                if (tag.getDataType() > 9) {
                    log.debug("Tag: {}", tag);
                    meta++;
                }
            }
        } finally {
            reader.close();
        }
        log.info("Finished reading: {} tags: {} meta: {}\n", file.getName(), total, meta);
        return new int[] { total, meta };
    }

    /**
     * webrtctestrecord.flv is a recording with large zero-filled gaps between tags. With metadata generation enabled the
     * reader must pre-scan the damaged file without throwing, hand back the leading valid tags, and then stop cleanly by
     * returning null at the first unreadable tag rather than throwing or spinning.
     */
    @Test
    public void testFLVReaderFileWithPreProcessInfo() throws IOException {
        log.info("\n testFLVReaderFileWithPreProcessInfo");
        File file = fixture("webrtctestrecord.flv");
        FLVReader reader = new FLVReader(file, true);
        try {
            // file onMetaData, generated onMetaData, then the AVC sequence header
            int[] expectedTypes = { ITag.TYPE_METADATA, ITag.TYPE_METADATA, ITag.TYPE_VIDEO };
            for (int t = 0; t < expectedTypes.length; t++) {
                assertTrue("ran out of tags at " + t, reader.hasMoreTags());
                ITag tag = reader.readTag();
                log.debug("Tag: {}", tag);
                assertNotNull("tag " + t + " is null", tag);
                assertEquals("tag " + t + " type", expectedTypes[t], tag.getDataType());
                assertNotNull("tag " + t + " has no body", tag.getBody());
                assertTrue("tag " + t + " body is empty", tag.getBody().limit() > 0);
            }
            // the next tag header sits in a zero-filled gap; the reader must give up cleanly
            assertNull("expected null at the damaged region", reader.readTag());
        } finally {
            reader.close();
        }
    }

    @Test
    public void testFLVReaderFile() throws IOException {
        log.info("\n testFLVReaderFile");
        String[] names = { "h264_aac.flv", "h264_mp3.flv", "h264_speex.flv", "NAPNAP.flv", "ipadmini-A7.flv" };
        for (String name : names) {
            readLeadingTags(fixture(name), true, 6);
        }
    }

    @Test
    public void testFLVReaderFileWithMetaData() throws IOException {
        log.info("\n testFLVReaderFileWithMetaData");
        String[] names = { "flashContent.flv", "flashContent1.flv" };
        for (String name : names) {
            int[] counts = readAllTags(fixture(name), false);
            assertTrue(name + " has no tags", counts[0] > 0);
            assertTrue(name + " has no metadata tag", counts[1] > 0);
        }
    }

    @Test
    public void testFLVReaderFileGenerateMetaData() throws IOException {
        log.info("\n testFLVReaderFileGenerateMetaData");
        File file = fixture("h264_aac.flv");
        FLVReader reader = new FLVReader(file, true);
        try {
            ITag first = reader.readTag();
            assertNotNull("first tag is null", first);
            assertEquals("first tag should be the generated metadata", ITag.TYPE_METADATA, first.getDataType());
        } finally {
            reader.close();
        }
        int[] counts = readAllTags(file, true);
        assertTrue("no tags read", counts[0] > 0);
        assertTrue("no metadata tag produced", counts[1] > 0);
    }

}
