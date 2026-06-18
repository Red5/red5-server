package org.red5.server.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;
import org.red5.codec.AbstractVideo;
import org.red5.codec.IVideoStreamCodec;
import org.red5.codec.VideoCodec;
import org.red5.io.utils.IOUtils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

public class VideoCodecFactoryTest {

    @Test
    public void testEnhancedAv1SequenceStartDoesNotUseRejectingProbeCodec() {
        assertEnhancedSequenceStartDoesNotUseRejectingProbeCodec("av01", VideoCodec.AV1, new byte[] { (byte) 0x81, 0x08, 0x0d });
    }

    @Test
    public void testEnhancedHevcSequenceStartDoesNotUseRejectingProbeCodec() {
        assertEnhancedSequenceStartDoesNotUseRejectingProbeCodec("hvc1", VideoCodec.HEVC, new byte[] { 0x01, 0x01, 0x60, 0x00 });
    }

    private static void assertEnhancedSequenceStartDoesNotUseRejectingProbeCodec(String fourcc, VideoCodec expectedCodec, byte[] configPrefix) {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(AbstractVideo.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            IoBuffer data = IoBuffer.allocate(16);
            data.put((byte) 0x90);
            data.putInt(IOUtils.makeFourcc(fourcc));
            data.put(configPrefix);
            data.flip();

            IVideoStreamCodec codec = VideoCodecFactory.getVideoCodec(data);

            assertEquals(expectedCodec, codec.getCodec());
            assertTrue("Codec factory must not emit AbstractVideo rejection while probing enhanced FourCC", rejectLogs(appender.list).isEmpty());
        } finally {
            logger.detachAppender(appender);
        }
    }

    private static List<ILoggingEvent> rejectLogs(List<ILoggingEvent> events) {
        return events.stream().filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN)).filter(event -> event.getFormattedMessage().contains("AbstractVideo rejected")).toList();
    }
}
