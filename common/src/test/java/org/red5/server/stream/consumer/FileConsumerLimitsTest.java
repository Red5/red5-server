package org.red5.server.stream.consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.red5.server.api.stream.IClientStream;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.stream.message.RTMPMessage;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ByteArrayResource;

/**
 * Verifies recording byte, duration and free space limits (issue #462).
 */
public class FileConsumerLimitsTest {

    private Path dir;

    private FileConsumer consumer;

    @Before
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("filecons-limits");
        consumer = new FileConsumer(null, new File(dir.toFile(), "rec.flv"));
        consumer.setMode(IClientStream.MODE_RECORD);
    }

    @After
    public void tearDown() {
        consumer.uninit();
    }

    @Test
    public void testByteLimitStopsRecording() throws Exception {
        consumer.setMaxRecordingBytes(10_000);
        push(0, 200, 1000, 20);
        String f = awaitFailure();
        assertNotNull("byte limit must stop the recording", f);
        // until the writer is closed the tags are in the data file next to the flv
        Path data = dir.resolve("rec.flv.ser");
        long size = Files.size(data);
        // later data is dropped
        push(20_000, 20, 1000, 20);
        Thread.sleep(200);
        assertEquals("file must not grow once the limit is reached", size, Files.size(data));
        assertTrue("file must stop near the limit, size " + size, size <= 10_000 + 2 * 1024);
    }

    @Test
    public void testDurationLimitStopsRecording() throws Exception {
        consumer.setMaxRecordingDurationMs(1_000);
        push(0, 100, 100, 20);
        String failure = awaitFailure();
        assertNotNull("duration limit must stop the recording", failure);
        assertTrue(failure.contains("duration"));
    }

    @Test
    public void testFreeSpaceLimitStopsRecording() throws Exception {
        consumer.setMinFreeDiskBytes(Long.MAX_VALUE);
        push(0, 300, 100, 20);
        String failure = awaitFailure();
        assertNotNull("free space minimum must stop the recording", failure);
        assertTrue(failure.contains("usable"));
    }

    @Test
    public void testNoLimitsKeepsRecording() throws Exception {
        push(0, 300, 100, 20);
        Thread.sleep(300);
        assertNull(consumer.getFailure());
    }

    /**
     * An application overrides the server wide limits by declaring its own fileConsumer bean, which inherits the server definition and
     * replaces only the properties it sets.
     */
    @Test
    public void testApplicationContextOverridesLimits() throws Exception {
        String server = "<bean id=\"fileConsumer\" scope=\"prototype\" lazy-init=\"true\" class=\"" + FileConsumer.class.getName() + "\">" //
                + "<property name=\"queueThreshold\" value=\"320\"/>" //
                + "<property name=\"maxRecordingBytes\" value=\"1000000\"/>" //
                + "<property name=\"maxRecordingDurationMs\" value=\"60000\"/>" //
                + "<property name=\"minFreeDiskBytes\" value=\"5000\"/></bean>";
        String app = "<bean id=\"fileConsumer\" parent=\"fileConsumer\" scope=\"prototype\" lazy-init=\"true\">" //
                + "<property name=\"maxRecordingBytes\" value=\"2000\"/></bean>";
        try (GenericApplicationContext common = context(null, server); GenericApplicationContext web = context(common, app)) {
            FileConsumer serverConsumer = (FileConsumer) common.getBean("fileConsumer");
            assertEquals(1_000_000, serverConsumer.getMaxRecordingBytes());
            FileConsumer appConsumer = (FileConsumer) web.getBean("fileConsumer");
            assertEquals("the application value replaces the server value", 2000, appConsumer.getMaxRecordingBytes());
            assertEquals("values the application does not set are inherited", 60_000, appConsumer.getMaxRecordingDurationMs());
            assertEquals("values the application does not set are inherited", 5000, appConsumer.getMinFreeDiskBytes());
        }
    }

    private static GenericApplicationContext context(ApplicationContext parent, String beans) {
        GenericApplicationContext ctx = new GenericApplicationContext(parent);
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><beans xmlns=\"http://www.springframework.org/schema/beans\"" //
                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" //
                + " xsi:schemaLocation=\"http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans.xsd\">" //
                + beans + "</beans>";
        new XmlBeanDefinitionReader(ctx).loadBeanDefinitions(new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8)));
        ctx.refresh();
        return ctx;
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

    private String awaitFailure() throws InterruptedException {
        for (int i = 0; i < 100 && consumer.getFailure() == null; i++) {
            Thread.sleep(50);
        }
        return consumer.getFailure();
    }

}
