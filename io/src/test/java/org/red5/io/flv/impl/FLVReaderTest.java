package org.red5.io.flv.impl;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;
import org.red5.io.ITag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FLVReaderTest {

    private static Logger log = LoggerFactory.getLogger(FLVReaderTest.class);

    @Test
    public void testFLVReaderFileWithPreProcessInfo() {
        log.info("\n testFLVReaderFileWithPreProcessInfo");
        //Path path = Paths.get("target/test-classes/fixtures/flv1_nelly.flv");
        Path path = Paths.get("target/test-classes/fixtures/webrtctestrecord.flv");
        try {
            File file = path.toFile();
            log.info("Reading: {}", file.getName());
            FLVReader reader = new FLVReader(file, true);
            //KeyFrameMeta meta = reader.analyzeKeyFrames();
            //log.debug("Meta: {}", meta);
            ITag tag = null;
            for (int t = 0; t < 6; t++) {
                tag = reader.readTag();
                log.debug("Tag: {}", tag);
                assertNotNull(tag.getBody());
            }
            reader.close();
            log.info("Finished reading: {}\n", file.getName());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testFLVReaderFile() {
        log.info("\n testFLVReaderFile");
        String[] paths = new String[] { "target/test-classes/fixtures/h264_aac.flv", "target/test-classes/fixtures/h264_mp3.flv", "target/test-classes/fixtures/h264_speex.flv", "target/test-classes/fixtures/stray.flv", "target/test-classes/fixtures/NAPNAP.flv", "target/test-classes/fixtures/dummy.flv" };
        try {
            for (String path : paths) {
                File file = Paths.get(path).toFile();
                if (file.exists() && file.canRead()) {
                    log.info("Reading: {}", file.getName());
                    FLVReader reader = new FLVReader(file, true);
                    //KeyFrameMeta meta = reader.analyzeKeyFrames();
                    //log.debug("Meta: {}", meta);
                    ITag tag = null;
                    for (int t = 0; t < 6; t++) {
                        tag = reader.readTag();
                        log.debug("Tag: {}", tag);
                        assertNotNull(tag.getBody());
                    }
                    reader.close();
                    log.info("Finished reading: {}\n", file.getName());
                } else {
                    log.info("File couldn't be accessed or doesnt exist: {}", file.getName());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testFLVReaderFileWithMetaData() {
        log.info("\n testFLVReaderFileWithMetaData");
        String[] paths = new String[] { "target/test-classes/fixtures/flashContent.flv", "target/test-classes/fixtures/flashContent1.flv" };
        try {
            for (String path : paths) {
                File file = Paths.get(path).toFile();
                if (file.exists() && file.canRead()) {
                    log.info("Reading: {}", file.getName());
                    FLVReader reader = new FLVReader(file, false);
                    ITag tag = null;
                    while (reader.hasMoreTags()) {
                        tag = reader.readTag();
                        if (tag != null && tag.getDataType() > 9) {
                            log.debug("Tag: {}", tag);
                        }
                    }
                    reader.close();
                    log.info("Finished reading: {}\n", file.getName());
                } else {
                    log.info("File couldn't be accessed or doesnt exist: {}", file.getName());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testFLVReaderFileGenerateMetaData() {
        log.info("\n testFLVReaderFileGenerateMetaData");
        String[] paths = new String[] { "target/test-classes/fixtures/stray.flv" };
        try {
            for (String path : paths) {
                File file = Paths.get(path).toFile();
                if (file.exists() && file.canRead()) {
                    log.info("Reading: {}", file.getName());
                    FLVReader reader = new FLVReader(file, true);
                    ITag tag = null;
                    while (reader.hasMoreTags()) {
                        tag = reader.readTag();
                        if (tag != null && tag.getDataType() > 9) {
                            log.debug("Tag: {}", tag);
                        }
                    }
                    reader.close();
                    log.info("Finished reading: {}\n", file.getName());
                } else {
                    log.info("File couldn't be accessed or doesnt exist: {}", file.getName());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
