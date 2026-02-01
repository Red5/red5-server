package org.red5.server.net.rtmp.codec;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Random;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Before;
import org.junit.Test;
import org.red5.server.net.protocol.RTMPDecodeState;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.net.rtmp.event.VideoData;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.net.rtmp.message.Header;
import org.red5.server.net.rtmp.message.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for origin-to-edge RTMP communication, specifically focusing on
 * large packet chunking and decoding to ensure chunk stream synchronization.
 *
 * This test simulates the scenario where:
 * 1. Origin server encodes large video packets (e.g., 150KB+ keyframes)
 * 2. Edge server decodes the chunked stream
 * 3. Both sides must maintain chunk stream alignment
 */
public class OriginEdgeChunkTest implements Constants {

    private static final Logger log = LoggerFactory.getLogger(OriginEdgeChunkTest.class);

    private RTMPProtocolEncoder encoder;

    private RTMPProtocolDecoder decoder;

    private TestRTMPConnection originConn;

    private TestRTMPConnection edgeConn;

    @Before
    public void setUp() {
        encoder = new RTMPProtocolEncoder();
        decoder = new RTMPProtocolDecoder();

        // Setup origin connection (encoder side)
        originConn = new TestRTMPConnection("origin-test");
        encoder.setConnection(originConn);

        // Setup edge connection (decoder side)
        edgeConn = new TestRTMPConnection("edge-test");
    }

    /**
     * Test encoding and decoding a large video packet (simulating a keyframe).
     * This is the core test for origin-to-edge communication.
     */
    @Test
    public void testLargeVideoPacketChunking() {
        int packetSize = 169501; // ~165KB, similar to real-world keyframes
        int chunkSize = 1024;

        // Set chunk sizes on both connections
        originConn.getState().setWriteChunkSize(chunkSize);
        edgeConn.getState().setReadChunkSize(chunkSize);

        // Create a video packet with random data
        byte[] videoData = new byte[packetSize];
        new Random(42).nextBytes(videoData);

        // Create header
        Header header = new Header();
        header.setChannelId(5); // Video channel
        header.setDataType(TYPE_VIDEO_DATA);
        header.setStreamId(1);
        header.setTimerBase(3075994); // About 51 minutes
        header.setTimerDelta(0);
        header.setSize(packetSize);

        // Create video event and packet
        VideoData video = new VideoData(IoBuffer.wrap(videoData));
        video.setTimestamp(header.getTimer());

        Packet packet = new Packet(header);
        packet.setMessage(video);

        // Encode the packet
        IoBuffer encoded = encoder.encodePacket(packet);
        assertNotNull("Encoded buffer should not be null", encoded);

        log.debug("Encoded {} bytes of video data into {} bytes of chunked data", packetSize, encoded.remaining());

        // Calculate expected size: data + headers
        // First chunk: 12 bytes (Type 0) + 1024 bytes data
        // Subsequent chunks: 1 byte (Type 3) + 1024 bytes data each
        int numChunks = (int) Math.ceil(packetSize / (float) chunkSize);
        int expectedSize = 12 + packetSize + (numChunks - 1); // Type 0 header + data + Type 3 headers

        log.debug("Expected {} chunks, {} bytes total", numChunks, expectedSize);

        // Now decode on the edge side
        // Note: encodePacket already returns buffer in read mode (flipped)

        try {
            List<?> decoded = decoder.decodeBuffer(edgeConn, encoded);

            assertNotNull("Decoded list should not be null", decoded);
            assertEquals("Should decode exactly one packet", 1, decoded.size());

            Object decodedObj = decoded.get(0);
            assertNotNull("Decoded object should not be null", decodedObj);

            if (decodedObj instanceof Packet) {
                Packet decodedPacket = (Packet) decodedObj;
                Header decodedHeader = decodedPacket.getHeader();

                assertEquals("Channel ID should match", 5, decodedHeader.getChannelId());
                assertEquals("Data type should match", TYPE_VIDEO_DATA, decodedHeader.getDataType());
                assertEquals("Size should match", packetSize, decodedHeader.getSize());
                assertEquals("Stream ID should match", 1, decodedHeader.getStreamId().intValue());

                // Verify the data content
                IoBuffer decodedData = decodedPacket.getData();
                if (decodedData != null) {
                    decodedData.flip();
                    byte[] decodedBytes = new byte[decodedData.remaining()];
                    decodedData.get(decodedBytes);
                    assertArrayEquals("Video data should match", videoData, decodedBytes);
                }
            }

            log.info("Successfully encoded and decoded {} byte video packet", packetSize);

        } catch (Exception e) {
            fail("Decoding failed: " + e.getMessage());
        }
    }

    /**
     * Test multiple video packets in sequence (simulating a video stream).
     */
    @Test
    public void testMultipleVideoPacketsInSequence() {
        int chunkSize = 1024;
        originConn.getState().setWriteChunkSize(chunkSize);
        edgeConn.getState().setReadChunkSize(chunkSize);

        // Simulate a sequence of packets: I-frame, P-frame, P-frame
        int[] packetSizes = { 156351, 8500, 12000 }; // Large keyframe, smaller interframes
        int[] timestamps = { 0, 33, 66 }; // 30fps timing

        Random rand = new Random(42);
        IoBuffer allEncoded = IoBuffer.allocate(300000);
        allEncoded.setAutoExpand(true);

        // Encode all packets
        for (int i = 0; i < packetSizes.length; i++) {
            byte[] videoData = new byte[packetSizes[i]];
            rand.nextBytes(videoData);

            Header header = new Header();
            header.setChannelId(5);
            header.setDataType(TYPE_VIDEO_DATA);
            header.setStreamId(1);
            header.setTimerBase(timestamps[i]);
            header.setTimerDelta(i > 0 ? timestamps[i] - timestamps[i - 1] : 0);
            header.setSize(packetSizes[i]);

            VideoData video = new VideoData(IoBuffer.wrap(videoData));
            video.setTimestamp(header.getTimer());

            Packet packet = new Packet(header);
            packet.setMessage(video);

            IoBuffer encoded = encoder.encodePacket(packet);
            assertNotNull("Encoded buffer should not be null for packet " + i, encoded);

            // Append to combined buffer (encodePacket already returns buffer in read mode)
            allEncoded.put(encoded);

            log.debug("Encoded packet {} (size={}, ts={})", i, packetSizes[i], timestamps[i]);
        }

        // Decode all packets
        allEncoded.flip();

        try {
            List<?> decoded = decoder.decodeBuffer(edgeConn, allEncoded);

            assertNotNull("Decoded list should not be null", decoded);
            assertEquals("Should decode exactly " + packetSizes.length + " packets", packetSizes.length, decoded.size());

            for (int i = 0; i < decoded.size(); i++) {
                Object obj = decoded.get(i);
                if (obj instanceof Packet) {
                    Packet p = (Packet) obj;
                    assertEquals("Packet " + i + " size should match", packetSizes[i], p.getHeader().getSize());
                }
            }

            log.info("Successfully encoded and decoded {} video packets in sequence", packetSizes.length);

        } catch (Exception e) {
            fail("Decoding failed: " + e.getMessage());
        }
    }

    /**
     * Test interleaved audio and video packets (realistic streaming scenario).
     */
    @Test
    public void testInterleavedAudioVideoPackets() {
        int chunkSize = 1024;
        originConn.getState().setWriteChunkSize(chunkSize);
        edgeConn.getState().setReadChunkSize(chunkSize);

        Random rand = new Random(42);
        IoBuffer allEncoded = IoBuffer.allocate(200000);
        allEncoded.setAutoExpand(true);

        // Simulate: Video(large), Audio, Video(small), Audio, Video(small)
        int[][] packets = { { 5, TYPE_VIDEO_DATA, 100000, 0 }, // Large video keyframe
                { 6, TYPE_AUDIO_DATA, 512, 0 }, // Audio
                { 5, TYPE_VIDEO_DATA, 8000, 33 }, // Video P-frame
                { 6, TYPE_AUDIO_DATA, 512, 21 }, // Audio
                { 5, TYPE_VIDEO_DATA, 8500, 66 }, // Video P-frame
        };

        for (int i = 0; i < packets.length; i++) {
            int channelId = packets[i][0];
            byte dataType = (byte) packets[i][1];
            int size = packets[i][2];
            int timestamp = packets[i][3];

            byte[] data = new byte[size];
            rand.nextBytes(data);

            Header header = new Header();
            header.setChannelId(channelId);
            header.setDataType(dataType);
            header.setStreamId(1);
            header.setTimerBase(timestamp);
            header.setSize(size);

            IoBuffer dataBuffer = IoBuffer.wrap(data);

            Packet packet = new Packet(header);
            if (dataType == TYPE_VIDEO_DATA) {
                VideoData video = new VideoData(dataBuffer);
                video.setTimestamp(timestamp);
                packet.setMessage(video);
            } else {
                AudioData audio = new AudioData(dataBuffer);
                audio.setTimestamp(timestamp);
                packet.setMessage(audio);
            }

            IoBuffer encoded = encoder.encodePacket(packet);
            assertNotNull("Encoded buffer should not be null for packet " + i, encoded);

            // encodePacket already returns buffer in read mode
            allEncoded.put(encoded);

            log.debug("Encoded {} packet {} (ch={}, size={}, ts={})", dataType == TYPE_VIDEO_DATA ? "video" : "audio", i, channelId, size, timestamp);
        }

        // Decode all packets
        allEncoded.flip();

        try {
            List<?> decoded = decoder.decodeBuffer(edgeConn, allEncoded);

            assertNotNull("Decoded list should not be null", decoded);
            assertEquals("Should decode exactly " + packets.length + " packets", packets.length, decoded.size());

            for (int i = 0; i < decoded.size(); i++) {
                Object obj = decoded.get(i);
                if (obj instanceof Packet) {
                    Packet p = (Packet) obj;
                    assertEquals("Packet " + i + " channel should match", packets[i][0], p.getHeader().getChannelId());
                    assertEquals("Packet " + i + " type should match", (byte) packets[i][1], p.getHeader().getDataType());
                    assertEquals("Packet " + i + " size should match", packets[i][2], p.getHeader().getSize());
                }
            }

            log.info("Successfully encoded and decoded {} interleaved packets", packets.length);

        } catch (Exception e) {
            fail("Decoding failed: " + e.getMessage());
        }
    }

    /**
     * Test with extended timestamps (stream running for > 4.6 hours).
     */
    @Test
    public void testExtendedTimestampPackets() {
        int chunkSize = 1024;
        originConn.getState().setWriteChunkSize(chunkSize);
        edgeConn.getState().setReadChunkSize(chunkSize);

        // Timestamp above MEDIUM_INT_MAX (16777215 = ~4.6 hours)
        int extendedTimestamp = MEDIUM_INT_MAX + 1000000; // About 4.9 hours
        int packetSize = 50000;

        byte[] videoData = new byte[packetSize];
        new Random(42).nextBytes(videoData);

        Header header = new Header();
        header.setChannelId(5);
        header.setDataType(TYPE_VIDEO_DATA);
        header.setStreamId(1);
        header.setTimerBase(extendedTimestamp);
        header.setSize(packetSize);

        VideoData video = new VideoData(IoBuffer.wrap(videoData));
        video.setTimestamp(extendedTimestamp);

        Packet packet = new Packet(header);
        packet.setMessage(video);

        IoBuffer encoded = encoder.encodePacket(packet);
        assertNotNull("Encoded buffer should not be null", encoded);

        log.debug("Encoded packet with extended timestamp {} ({} hours)", extendedTimestamp, extendedTimestamp / 3600000.0);

        // Verify extended timestamp handling
        // encodePacket already returns buffer in read mode

        try {
            List<?> decoded = decoder.decodeBuffer(edgeConn, encoded);

            assertNotNull("Decoded list should not be null", decoded);
            assertEquals("Should decode exactly one packet", 1, decoded.size());

            if (decoded.get(0) instanceof Packet) {
                Packet decodedPacket = (Packet) decoded.get(0);
                Header decodedHeader = decodedPacket.getHeader();

                assertEquals("Extended timestamp should be preserved", extendedTimestamp, decodedHeader.getTimer());
            }

            log.info("Successfully handled extended timestamp packet");

        } catch (Exception e) {
            fail("Decoding failed with extended timestamp: " + e.getMessage());
        }
    }

    /**
     * Test partial buffer decoding (simulating network fragmentation).
     */
    @Test
    public void testPartialBufferDecoding() {
        int chunkSize = 1024;
        originConn.getState().setWriteChunkSize(chunkSize);
        edgeConn.getState().setReadChunkSize(chunkSize);

        int packetSize = 10000; // 10 chunks worth
        byte[] videoData = new byte[packetSize];
        new Random(42).nextBytes(videoData);

        Header header = new Header();
        header.setChannelId(5);
        header.setDataType(TYPE_VIDEO_DATA);
        header.setStreamId(1);
        header.setTimerBase(1000);
        header.setSize(packetSize);

        VideoData video = new VideoData(IoBuffer.wrap(videoData));
        video.setTimestamp(1000);

        Packet packet = new Packet(header);
        packet.setMessage(video);

        IoBuffer encoded = encoder.encodePacket(packet);
        assertNotNull("Encoded buffer should not be null", encoded);
        // encodePacket already returns buffer in read mode

        byte[] fullData = new byte[encoded.remaining()];
        encoded.get(fullData);

        log.debug("Full encoded size: {} bytes", fullData.length);

        // Simulate receiving data in fragments
        int[] fragmentSizes = { 500, 1500, 2000, fullData.length - 4000 }; // Various sizes
        int offset = 0;
        IoBuffer accumulator = IoBuffer.allocate(fullData.length);
        accumulator.setAutoExpand(true);

        List<?> decoded = null;

        for (int i = 0; i < fragmentSizes.length; i++) {
            int fragSize = fragmentSizes[i];

            // Add fragment to accumulator
            accumulator.put(fullData, offset, fragSize);
            offset += fragSize;

            // Try to decode
            accumulator.flip();

            try {
                decoded = decoder.decodeBuffer(edgeConn, accumulator);
                log.debug("Fragment {}: added {} bytes, decoded {} packets", i, fragSize, decoded != null ? decoded.size() : 0);
            } catch (Exception e) {
                log.debug("Fragment {}: decoding incomplete (expected)", i);
            }

            // Prepare for next fragment (compact preserves unprocessed data)
            // Note: decodeBuffer already calls compact(), so we just need to reset for next put
        }

        // After all fragments, should have decoded the packet
        assertNotNull("Should have decoded data after all fragments", decoded);
        assertEquals("Should decode exactly one packet", 1, decoded.size());

        log.info("Successfully decoded packet from {} fragments", fragmentSizes.length);
    }

    /**
     * Simple test RTMP connection for unit testing.
     */
    private static class TestRTMPConnection extends RTMPConnection {

        private final RTMP state;

        private final RTMPDecodeState decoderState;

        private final String name;

        public TestRTMPConnection(String name) {
            super("PERSISTENT"); // Must use valid IConnection.Type enum value
            this.name = name;
            this.state = new RTMP();
            this.state.setState(RTMP.STATE_CONNECTED);
            this.decoderState = new RTMPDecodeState(name);
        }

        @Override
        public RTMP getState() {
            return state;
        }

        @Override
        public byte getStateCode() {
            return RTMP.STATE_CONNECTED;
        }

        @Override
        public RTMPDecodeState getDecoderState() {
            return decoderState;
        }

        @Override
        public String getSessionId() {
            return name;
        }

        @Override
        public void write(Packet out) {
            // No-op for test
        }

        @Override
        public void writeRaw(IoBuffer out) {
            // No-op for test
        }

        @Override
        protected void onInactive() {
            // No-op for test
        }
    }

    /**
     * Test with actual byte sequence from origin-to-edge cluster communication.
     * This sequence caused BufferUnderflowException due to buffer management issues.
     *
     * The bytes represent: ServerBW + ClientBW + Ping + partial Invoke (_result)
     * from origin server to edge client during cluster connection.
     */
    @Test
    public void testClusterConnectionByteSequence() {
        // Actual bytes from cluster communication that caused failures
        // ServerBW(ch2) + ClientBW(ch2) + Ping(ch2) + _result Invoke(ch3)
        String hexData = "020000000000040500000000009896804200000000000506009896800242000000000006040000000000000000000000000000000000000000000000000000000300000000008114000000000200075f726573756c74003ff000000000000005030004636f646502001d4e6574436f6e6e656374696f6e2e436f6e6e6563742e53756363657373000b6170706c69636174696f6e0500056c6576656c020006737461747573000b6465736372697074696f6e020015436f6e6e656374696f6e207375636365656465642e000009";

        byte[] fullData;
        try {
            fullData = org.apache.commons.codec.binary.Hex.decodeHex(hexData.toCharArray());
        } catch (Exception e) {
            fail("Failed to decode hex data: " + e.getMessage());
            return;
        }

        log.info("Testing cluster byte sequence decode: {} bytes", fullData.length);

        // Simulate Mina buffer reuse pattern that caused the bug
        IoBuffer sessionBuffer = IoBuffer.allocate(1024);
        sessionBuffer.setAutoExpand(true);

        // Split data into multiple "network reads" to simulate real-world fragmentation
        int[] fragmentSizes = { 50, 80, fullData.length - 130 }; // Three fragments
        int offset = 0;

        TestRTMPConnection conn = new TestRTMPConnection("cluster-test");
        int totalDecoded = 0;

        for (int i = 0; i < fragmentSizes.length; i++) {
            int fragSize = Math.min(fragmentSizes[i], fullData.length - offset);
            if (fragSize <= 0)
                break;

            // Simulate network read arriving
            byte[] fragment = new byte[fragSize];
            System.arraycopy(fullData, offset, fragment, 0, fragSize);
            offset += fragSize;

            // This is how RTMPMinaCodecFactory handles incoming data:
            // 1. Put new data into session buffer (buffer is in write mode after compact)
            sessionBuffer.put(fragment);
            // 2. Flip to read mode
            sessionBuffer.flip();

            log.debug("Fragment {}: added {} bytes, buffer position={}, limit={}", i, fragSize, sessionBuffer.position(), sessionBuffer.limit());

            try {
                // 3. Decode - NOTE: decodeBuffer() calls compact() internally in its finally block
                List<?> decoded = decoder.decodeBuffer(conn, sessionBuffer);
                if (decoded != null) {
                    totalDecoded += decoded.size();
                    log.debug("Fragment {}: decoded {} objects (total: {})", i, decoded.size(), totalDecoded);
                }
                // Buffer is now compacted by decodeBuffer(), ready for next put()
            } catch (Exception e) {
                fail("Decoding failed on fragment " + i + ": " + e.getMessage());
            }
        }

        log.info("Successfully decoded {} messages from cluster byte sequence", totalDecoded);
        // Should decode at least ServerBW, ClientBW, Ping, and _result
        assertTrue("Should decode at least 4 messages", totalDecoded >= 4);
    }

    /**
     * Test transitioning from extended timestamps to non-extended timestamps on the same channel.
     *
     * This test covers the bug where the decoder's Type 0 header handling did not reset
     * the extended flag when timestamp dropped below MEDIUM_INT_MAX. The inherited
     * extended=true from the previous header would cause Type 3 chunks to incorrectly
     * read 4 extra bytes, desynchronizing the chunk stream.
     *
     * Scenario:
     * 1. First packet on channel 5: timestamp = 17000000 (above MEDIUM_INT_MAX, requires extended)
     * 2. Second packet on channel 5: timestamp = 14514 (below MEDIUM_INT_MAX, no extended)
     * 3. Both packets should decode correctly
     */
    @Test
    public void testExtendedToNonExtendedTimestampTransition() {
        int chunkSize = 1024;
        originConn.getState().setWriteChunkSize(chunkSize);
        edgeConn.getState().setReadChunkSize(chunkSize);

        Random rand = new Random(42);
        IoBuffer allEncoded = IoBuffer.allocate(100000);
        allEncoded.setAutoExpand(true);

        // First packet: large timestamp requiring extended timestamp field
        int extendedTimestamp = MEDIUM_INT_MAX + 222785; // 17000000 - simulates stream running > 4.7 hours
        int packet1Size = 5000; // Multiple chunks to exercise Type 3 handling

        byte[] data1 = new byte[packet1Size];
        rand.nextBytes(data1);

        Header header1 = new Header();
        header1.setChannelId(5);
        header1.setDataType(TYPE_VIDEO_DATA);
        header1.setStreamId(1);
        header1.setTimerBase(extendedTimestamp);
        header1.setSize(packet1Size);

        VideoData video1 = new VideoData(IoBuffer.wrap(data1));
        video1.setTimestamp(extendedTimestamp);

        Packet packet1 = new Packet(header1);
        packet1.setMessage(video1);

        IoBuffer encoded1 = encoder.encodePacket(packet1);
        assertNotNull("First packet encoding should succeed", encoded1);
        allEncoded.put(encoded1);

        log.debug("Encoded packet 1: ts={} (extended), size={}", extendedTimestamp, packet1Size);

        // Second packet: normal timestamp (simulates stream restart or timestamp reset)
        int normalTimestamp = 14514; // Well below MEDIUM_INT_MAX
        int packet2Size = 8000; // Multiple chunks to exercise Type 3 handling

        byte[] data2 = new byte[packet2Size];
        rand.nextBytes(data2);

        Header header2 = new Header();
        header2.setChannelId(5); // Same channel as first packet
        header2.setDataType(TYPE_VIDEO_DATA);
        header2.setStreamId(1);
        header2.setTimerBase(normalTimestamp);
        header2.setSize(packet2Size);

        VideoData video2 = new VideoData(IoBuffer.wrap(data2));
        video2.setTimestamp(normalTimestamp);

        Packet packet2 = new Packet(header2);
        packet2.setMessage(video2);

        IoBuffer encoded2 = encoder.encodePacket(packet2);
        assertNotNull("Second packet encoding should succeed", encoded2);
        allEncoded.put(encoded2);

        log.debug("Encoded packet 2: ts={} (non-extended), size={}", normalTimestamp, packet2Size);

        // Decode both packets
        allEncoded.flip();

        try {
            List<?> decoded = decoder.decodeBuffer(edgeConn, allEncoded);

            assertNotNull("Decoded list should not be null", decoded);
            assertEquals("Should decode exactly 2 packets", 2, decoded.size());

            // Verify first packet
            Packet decodedPacket1 = (Packet) decoded.get(0);
            assertEquals("First packet timestamp should match", extendedTimestamp, decodedPacket1.getHeader().getTimer());
            assertEquals("First packet size should match", packet1Size, decodedPacket1.getHeader().getSize());

            // Verify second packet - this is where the bug would manifest
            // Without the fix, the decoder would try to read 4 extra bytes for Type 3 chunks
            // causing chunk stream desynchronization and "Last header null" errors
            Packet decodedPacket2 = (Packet) decoded.get(1);
            assertEquals("Second packet size should match", packet2Size, decodedPacket2.getHeader().getSize());
            assertEquals("Second packet channel should match", 5, decodedPacket2.getHeader().getChannelId());

            log.info("Successfully decoded extended-to-non-extended timestamp transition on same channel");

        } catch (Exception e) {
            fail("Decoding failed during timestamp transition: " + e.getMessage() + ". This indicates the extended flag was not properly reset on Type 0 header decode.");
        }
    }

}
