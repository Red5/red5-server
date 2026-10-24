package org.red5.client.net.rtmp.codec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Before;
import org.junit.Test;
import org.red5.server.net.rtmp.RTMPMinaConnection;
import org.red5.server.net.rtmp.codec.RTMP;
import org.red5.server.net.rtmp.codec.RTMPProtocolDecoder;
import org.red5.server.net.rtmp.codec.RTMPProtocolEncoder;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.net.rtmp.event.VideoData;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.net.rtmp.message.Header;
import org.red5.server.net.rtmp.message.Packet;

/**
 * Chunk header timestamps between the Red5 client codec and the server codec, in both directions: a client publishing
 * to the server and a client playing from it. Every decoded timestamp must equal the encoded one, which only holds
 * when both sides agree on the delta a Type 3 chunk implies (RTMP spec 5.3.1.2.4).
 */
public class RTMPClientTimestampTest implements Constants {

    private static final int AUDIO_CHANNEL = 4;

    private static final int VIDEO_CHANNEL = 6;

    // repeated timestamps, a steady cadence, a late frame (Type 0) and recovery
    private static final int[] TIMESTAMPS = { 0, 0, 23, 46, 46, 69, 92, 92, 92, 115, 138, 100, 100, 123, 146, 169 };

    private RTMPMinaConnection sender;

    private RTMPMinaConnection receiver;

    @Before
    public void setUp() {
        sender = new RTMPMinaConnection();
        sender.getState().setState(RTMP.STATE_CONNECTED);
        receiver = new RTMPMinaConnection();
        receiver.getState().setState(RTMP.STATE_CONNECTED);
    }

    private static Packet packet(int channelId, byte dataType, int timestamp, int size) {
        Header header = new Header();
        header.setChannelId(channelId);
        header.setDataType(dataType);
        header.setStreamId(1);
        header.setTimer(timestamp);
        IoBuffer body = IoBuffer.wrap(new byte[size]);
        if (dataType == TYPE_VIDEO_DATA) {
            VideoData video = new VideoData(body);
            video.setTimestamp(timestamp);
            return new Packet(header, video);
        }
        AudioData audio = new AudioData(body);
        audio.setTimestamp(timestamp);
        return new Packet(header, audio);
    }

    private int roundTrip(RTMPProtocolEncoder encoder, RTMPProtocolDecoder decoder, Packet packet) {
        IoBuffer out = encoder.encodePacket(packet);
        assertNotNull("expected encoded data", out);
        Packet decoded = null;
        // decodePacket consumes one chunk per call
        while (decoded == null && out.hasRemaining()) {
            decoded = decoder.decodePacket(receiver, receiver.getDecoderState(), out);
        }
        assertNotNull("expected a complete packet", decoded);
        assertEquals("message fully consumed", 0, out.remaining());
        return decoded.getMessage().getTimestamp();
    }

    private void assertTimestampsSurvive(RTMPProtocolEncoder encoder, RTMPProtocolDecoder decoder) {
        encoder.setConnection(sender);
        int large = sender.getState().getWriteChunkSize() * 2 + 1;
        receiver.getState().setReadChunkSize(sender.getState().getWriteChunkSize());
        for (int timestamp : TIMESTAMPS) {
            assertEquals("audio", timestamp, roundTrip(encoder, decoder, packet(AUDIO_CHANNEL, TYPE_AUDIO_DATA, timestamp, 3)));
            // multi-chunk video interleaved on its own channel
            assertEquals("video", timestamp, roundTrip(encoder, decoder, packet(VIDEO_CHANNEL, TYPE_VIDEO_DATA, timestamp, large)));
        }
    }

    @Test
    public void testClientPublishToServer() {
        assertTimestampsSurvive(new RTMPClientProtocolEncoder(), new RTMPProtocolDecoder());
    }

    @Test
    public void testClientPlayFromServer() {
        assertTimestampsSurvive(new RTMPProtocolEncoder(), new RTMPClientProtocolDecoder());
    }

    @Test
    public void testClientCodecsTalkToEachOther() {
        assertTimestampsSurvive(new RTMPClientProtocolEncoder(), new RTMPClientProtocolDecoder());
    }

}
