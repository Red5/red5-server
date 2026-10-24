package org.red5.server.net.rtmp.codec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Before;
import org.junit.Test;
import org.red5.server.net.rtmp.StubRTMPConnection;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.net.rtmp.message.Header;
import org.red5.server.net.rtmp.message.Packet;

/**
 * Header type selection in the RTMP encoder. A Type 3 chunk that starts a new message implies the previous chunk's
 * timestamp field as its delta (RTMP spec 5.3.1.2.4), so the encoder may only choose it when that is the delta it
 * means; every decoded timestamp must match what was encoded.
 */
public class RTMPProtocolEncoderTimestampTest implements Constants {

    private static final int CHANNEL = 4;

    private static final int BODY_SIZE = 3;

    private RTMPProtocolEncoder encoder;

    private RTMPProtocolDecoder decoder;

    private StubRTMPConnection origin;

    private StubRTMPConnection edge;

    @Before
    public void setUp() {
        encoder = new RTMPProtocolEncoder();
        decoder = new RTMPProtocolDecoder();
        origin = new StubRTMPConnection("origin");
        edge = new StubRTMPConnection("edge");
        encoder.setConnection(origin);
    }

    private IoBuffer encode(int timestamp, int size) {
        Header header = new Header();
        header.setChannelId(CHANNEL);
        header.setDataType(TYPE_AUDIO_DATA);
        header.setStreamId(1);
        header.setTimer(timestamp);
        AudioData audio = new AudioData(IoBuffer.wrap(new byte[size]));
        audio.setTimestamp(timestamp);
        IoBuffer out = encoder.encodePacket(new Packet(header, audio));
        assertNotNull("expected encoded data", out);
        return out;
    }

    private IoBuffer encode(int timestamp) {
        return encode(timestamp, BODY_SIZE);
    }

    /** Chunk header format of the first chunk, from the two high bits of the basic header. */
    private static int format(IoBuffer out) {
        return (out.get(out.position()) & 0xff) >> 6;
    }

    private int decodedTime(IoBuffer out) {
        edge.getState().setReadChunkSize(origin.getState().getWriteChunkSize());
        // decodePacket consumes one chunk per call
        Packet packet = null;
        while (packet == null && out.hasRemaining()) {
            packet = decoder.decodePacket(edge, edge.getDecoderState(), out);
        }
        assertNotNull("expected a complete packet", packet);
        assertEquals("message fully consumed", 0, out.remaining());
        return packet.getMessage().getTimestamp();
    }

    @Test
    public void testType3StartsAMessageOnlyWhenTheDeltaRepeats() {
        assertEquals(HEADER_NEW, format(encode(0)));
        assertEquals(HEADER_TIMER_CHANGE, format(encode(40)));
        // same 40ms delta as the previous Type 2
        assertEquals(HEADER_CONTINUE, format(encode(80)));
        assertEquals(HEADER_CONTINUE, format(encode(120)));
        // delta changes to 10
        assertEquals(HEADER_TIMER_CHANGE, format(encode(130)));
    }

    @Test
    public void testRepeatedTimestampAfterType2IsNotType3() {
        encode(0);
        encode(40);
        // delta 0 differs from the previous 40, which a Type 3 would imply
        assertEquals(HEADER_TIMER_CHANGE, format(encode(40)));
    }

    @Test
    public void testRepeatedTimestampAfterType0IsNotType3() {
        encode(1000);
        // a Type 3 here would imply a delta of 1000
        assertEquals(HEADER_TIMER_CHANGE, format(encode(1000)));
    }

    @Test
    public void testNoType3AfterType0EvenWhenTheDeltaMatchesItsTimestamp() {
        encode(1000);
        // spec-legal as a Type 3, but decoders that predate 5.3.1.2.4 handling misread it, so a Type 2 is sent
        assertEquals(HEADER_TIMER_CHANGE, format(encode(2000)));
    }

    @Test
    public void testContinuationChunksStayType3() {
        int chunkSize = origin.getState().getWriteChunkSize();
        encode(0, chunkSize + 10);
        IoBuffer out = encode(40, chunkSize + 10);
        assertEquals(HEADER_TIMER_CHANGE, format(out));
        // a Type 2 header for a low channel id is 4 bytes, the second chunk starts right after the first chunk's data
        assertEquals(HEADER_CONTINUE, (out.get(4 + chunkSize) & 0xff) >> 6);
    }

    @Test
    public void testDecodedTimestampsMatchEncodedTimestamps() {
        // repeated timestamps, a steady cadence, a late frame (Type 0) and recovery
        int[] timestamps = { 0, 0, 23, 46, 46, 69, 92, 92, 92, 115, 138, 100, 100, 123, 146, 169 };
        for (int timestamp : timestamps) {
            assertEquals(timestamp, decodedTime(encode(timestamp)));
        }
    }

    @Test
    public void testDecodedTimestampsMatchAcrossMultiChunkMessages() {
        int large = origin.getState().getWriteChunkSize() * 2 + 1;
        int[] timestamps = { 0, 40, 80, 80, 120, 160 };
        for (int i = 0; i < timestamps.length; i++) {
            assertEquals(timestamps[i], decodedTime(encode(timestamps[i], i % 2 == 0 ? BODY_SIZE : large)));
        }
    }

    @Test
    public void testExtendedTimestampsRoundTrip() {
        int base = MEDIUM_INT_MAX + 100;
        IoBuffer first = encode(base);
        assertEquals(HEADER_NEW, format(first));
        assertEquals(base, decodedTime(first));
        assertEquals(base + 40, decodedTime(encode(base + 40)));
        IoBuffer repeat = encode(base + 80);
        // the 40ms delta repeats and is not extended, so a Type 3 with no extended field follows
        assertEquals(HEADER_CONTINUE, format(repeat));
        assertEquals(1 + BODY_SIZE, repeat.remaining());
        assertEquals(base + 80, decodedTime(repeat));
        assertEquals(base + 80, decodedTime(encode(base + 80)));
    }

}
