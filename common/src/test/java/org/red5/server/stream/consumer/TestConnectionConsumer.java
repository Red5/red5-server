package org.red5.server.stream.consumer;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.red5.server.net.rtmp.Channel;
import org.red5.server.net.rtmp.RTMPMinaConnection;
import org.red5.server.net.rtmp.event.VideoData;
import org.red5.server.stream.message.RTMPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestConnectionConsumer {

    private final Logger log = LoggerFactory.getLogger(TestConnectionConsumer.class);

    private RTMPMinaConnection connection;

    private Channel channel;

    private ConnectionConsumer underTest;

    @Before
    public void setUp() {
        connection = new RTMPMinaConnection() {

            @Override
            public Channel getChannel(int channelId) {
                Channel channel = new Channel(this, channelId);
                return channel;
            }

        };
        channel = connection.getChannel(1);
        underTest = new ConnectionConsumer(connection, channel, channel, channel);
    }

    @Test
    public void testNegativeTimestampsAreRolledOver() {
        log.debug("\n testNegativeTimestampsAreRolledOver");
        // https://www.rfc-editor.org/rfc/rfc1982
        log.debug("\n max: {} min: {} 0:{} -1:{}", Integer.toHexString(Integer.MAX_VALUE), Integer.toHexString(Integer.MIN_VALUE), Integer.toHexString(0), Integer.toHexString(-1));

        VideoData videoData1 = new VideoData();
        videoData1.setTimestamp(-1); // maximum timestamp value 0xffffffff expect 2147483647
        underTest.pushMessage(null, RTMPMessage.build(videoData1));

        assertEquals(Integer.MAX_VALUE, videoData1.getTimestamp());

        VideoData videoData2 = new VideoData();
        videoData2.setTimestamp(Integer.MIN_VALUE);
        underTest.pushMessage(null, RTMPMessage.build(videoData2));

        assertEquals(0, videoData2.getTimestamp());
    }

    @Test
    public void testPositiveTimestampsAreUnaffected() {
        log.debug("\n testPositiveTimestampsAreUnaffected");
        VideoData videoData1 = new VideoData();
        videoData1.setTimestamp(0);
        underTest.pushMessage(null, RTMPMessage.build(videoData1));

        assertEquals(0, videoData1.getTimestamp());

        VideoData videoData2 = new VideoData();
        videoData2.setTimestamp(Integer.MAX_VALUE);
        underTest.pushMessage(null, RTMPMessage.build(videoData2));

        assertEquals(Integer.MAX_VALUE, videoData2.getTimestamp());
    }

}
