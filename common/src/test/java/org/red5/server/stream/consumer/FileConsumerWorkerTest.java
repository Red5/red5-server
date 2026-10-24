package org.red5.server.stream.consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.red5.io.ITag;
import org.red5.io.flv.impl.FLVReader;
import org.red5.server.api.stream.IClientStream;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.stream.message.RTMPMessage;

/**
 * Verifies the FileConsumer write worker exits on uninit and writes what was queued before it (issue #462).
 */
public class FileConsumerWorkerTest {

    private Path dir;

    private FileConsumer consumer;

    private String threadName;

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("filecons-worker");
        File file = new File(dir.toFile(), "worker.flv");
        threadName = "ProFileConsumer-" + file.getName();
        consumer = new FileConsumer(null, file);
        consumer.setMode(IClientStream.MODE_RECORD);
    }

    @After
    public void tearDown() {
        consumer.uninit();
    }

    @Test
    public void testUninitEndsWorkerThread() throws Exception {
        push(0, 10, 100, 20);
        for (int i = 0; i < 100 && !workerAlive(); i++) {
            Thread.sleep(20);
        }
        assertTrue("write worker should be running", workerAlive());
        consumer.uninit();
        for (int i = 0; i < 100 && workerAlive(); i++) {
            Thread.sleep(20);
        }
        assertFalse("write worker must exit once the recording is stopped", workerAlive());
    }

    @Test
    public void testUninitWritesQueuedTags() throws Exception {
        int count = 200;
        push(0, count, 100, 20);
        // stop right away, while tags are still queued
        consumer.uninit();
        assertEquals("every tag pushed before the stop is written", count, countAudioTags(dir.resolve("worker.flv")));
    }

    @Test
    public void testMessagesAfterUninitAreIgnored() throws Exception {
        push(0, 10, 100, 20);
        consumer.uninit();
        // must not throw now that the worker executor is shut down
        push(1000, 10, 100, 20);
        assertFalse(workerAlive());
    }

    private boolean workerAlive() {
        return Thread.getAllStackTraces().keySet().stream().anyMatch(t -> t.isAlive() && threadName.equals(t.getName()));
    }

    private static int countAudioTags(Path file) throws Exception {
        int audio = 0;
        FLVReader reader = new FLVReader(file.toFile());
        try {
            while (reader.hasMoreTags()) {
                ITag tag = reader.readTag();
                if (tag != null && tag.getDataType() == ITag.TYPE_AUDIO) {
                    audio++;
                }
            }
        } finally {
            reader.close();
        }
        return audio;
    }

    private void push(int startTimestamp, int count, int size, int step) throws Exception {
        for (int i = 0; i < count; i++) {
            IoBuffer data = IoBuffer.allocate(size);
            // MP3 audio header, which needs no decoder configuration before frames are written
            data.put((byte) 0x2F);
            data.position(size);
            data.flip();
            AudioData audio = new AudioData(data);
            audio.setTimestamp(startTimestamp + i * step);
            consumer.pushMessage(null, RTMPMessage.build(audio));
        }
    }

}
