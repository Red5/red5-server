package org.red5.server.net.rtmp.codec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Before;
import org.junit.Test;
import org.red5.server.net.rtmp.StubRTMPConnection;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.net.rtmp.message.Packet;

/**
 * Timestamp handling in the RTMP decoder: discontinuity detection on Type 0 headers, where the resulting offset is
 * applied (message only; stored headers keep wire time), and delta inheritance across header types.
 */
public class RTMPProtocolDecoderTimestampTest implements Constants {

    private static final int CHANNEL = 4;

    private static final byte[] BODY = { (byte) 0xAF, 0x01, 0x00 };

    private RTMPProtocolDecoder decoder;

    private StubRTMPConnection conn;

    @Before
    public void setUp() {
        decoder = new RTMPProtocolDecoder();
        conn = new StubRTMPConnection("ts");
    }

    private static void medium(IoBuffer in, int value) {
        in.put((byte) ((value >> 16) & 0xff));
        in.put((byte) ((value >> 8) & 0xff));
        in.put((byte) (value & 0xff));
    }

    private static IoBuffer type0(int ts) {
        IoBuffer in = IoBuffer.allocate(64);
        in.put((byte) CHANNEL);
        medium(in, ts);
        medium(in, BODY.length);
        in.put(TYPE_AUDIO_DATA);
        in.putInt(0);
        in.put(BODY);
        in.flip();
        return in;
    }

    private static IoBuffer type1(int delta) {
        IoBuffer in = IoBuffer.allocate(64);
        in.put((byte) (0x40 | CHANNEL));
        medium(in, delta);
        medium(in, BODY.length);
        in.put(TYPE_AUDIO_DATA);
        in.put(BODY);
        in.flip();
        return in;
    }

    private static IoBuffer type2(int delta) {
        IoBuffer in = IoBuffer.allocate(64);
        in.put((byte) (0x80 | CHANNEL));
        medium(in, delta);
        in.put(BODY);
        in.flip();
        return in;
    }

    /** Type 3 chunk that starts a new message (no header fields, same size and type as the previous one). */
    private static IoBuffer type3() {
        IoBuffer in = IoBuffer.allocate(64);
        in.put((byte) (0xC0 | CHANNEL));
        in.put(BODY);
        in.flip();
        return in;
    }

    private Packet decode(IoBuffer in) {
        Packet packet = decoder.decodePacket(conn, conn.getDecoderState(), in);
        assertNotNull("expected a complete packet", packet);
        return packet;
    }

    private static int messageTime(Packet packet) {
        return packet.getMessage().getTimestamp();
    }

    private static int headerTime(Packet packet) {
        return packet.getHeader().getTimer();
    }

    // discontinuity detection

    @Test
    public void testMonotonicTimestampsAreUntouched() {
        assertEquals(1000, messageTime(decode(type0(1000))));
        assertEquals(2000, messageTime(decode(type0(2000))));
        assertEquals(0L, conn.getState().getTimestampOffset(CHANNEL));
    }

    @Test
    public void testRestartNearZeroSetsOffsetAndShiftsMessageOnly() {
        decode(type0(20000));
        Packet restarted = decode(type0(500));
        // offset = previous offset (0) + last raw (20000) + 1
        assertEquals(20001L, conn.getState().getTimestampOffset(CHANNEL));
        assertEquals("message is rebased once", 20501, messageTime(restarted));
        // the header chain keeps wire time, which is what makes the offset apply only once
        assertEquals(500, headerTime(restarted));
    }

    @Test
    public void testOffsetIsNotCompoundedOnFollowingMessages() {
        decode(type0(20000));
        decode(type0(500));
        // Type 1 deltas build on the wire-time header; the offset must be added exactly once per message
        assertEquals(20501 + 40, messageTime(decode(type1(40))));
        assertEquals(20501 + 80, messageTime(decode(type2(40))));
    }

    @Test
    public void testLateFrameWithinThirtySecondsPassesThrough() {
        decode(type0(20000));
        // 5s backward step landing above 1s: a late frame, not a restart
        Packet late = decode(type0(15000));
        assertEquals(0L, conn.getState().getTimestampOffset(CHANNEL));
        assertEquals(15000, messageTime(late));
    }

    @Test
    public void testBackwardStepAtOneSecondBoundaryIsNotADiscontinuity() {
        decode(type0(10000));
        // exactly 1000ms backward is jitter
        assertEquals(9000, messageTime(decode(type0(9000))));
        assertEquals(0L, conn.getState().getTimestampOffset(CHANNEL));
    }

    @Test
    public void testLargeBackwardJumpIsADiscontinuityEvenAboveOneSecond() {
        decode(type0(100000));
        Packet restarted = decode(type0(50000));
        assertEquals(100001L, conn.getState().getTimestampOffset(CHANNEL));
        assertEquals(150001, messageTime(restarted));
    }

    @Test
    public void testRestartLandingAboveOneSecondAfterShortJumpIsDetected() {
        decode(type0(20000));
        // 15s back, landing at under half of the previous time
        Packet restarted = decode(type0(5000));
        assertEquals(20001L, conn.getState().getTimestampOffset(CHANNEL));
        assertEquals(25001, messageTime(restarted));
    }

    @Test
    public void testLateFramesJustOutsideTheRestartRulesPassThrough() {
        decode(type0(20000));
        // 5s back is a late frame
        assertEquals(15000, messageTime(decode(type0(15000))));
        // a further 5s back from 15s is still within the late frame window
        assertEquals(10000, messageTime(decode(type0(10000))));
        assertEquals(0L, conn.getState().getTimestampOffset(CHANNEL));
    }

    @Test
    public void testIsTimestampRestartRules() {
        assertFalse("jitter", RTMPProtocolDecoder.isTimestampRestart(10000, 9000));
        assertFalse("forward", RTMPProtocolDecoder.isTimestampRestart(1000, 20000));
        assertFalse("equal", RTMPProtocolDecoder.isTimestampRestart(5000, 5000));
        assertTrue("near zero", RTMPProtocolDecoder.isTimestampRestart(3000, 999));
        assertFalse("1s boundary on near zero", RTMPProtocolDecoder.isTimestampRestart(1999, 1000));
        assertFalse("5s late frame", RTMPProtocolDecoder.isTimestampRestart(20000, 15000));
        assertTrue("under half after more than 5s", RTMPProtocolDecoder.isTimestampRestart(20000, 9999));
        assertFalse("exactly half is not under half", RTMPProtocolDecoder.isTimestampRestart(20000, 10000));
        assertFalse("30s back is not always a restart", RTMPProtocolDecoder.isTimestampRestart(100000, 70000));
        assertTrue("more than 30s back", RTMPProtocolDecoder.isTimestampRestart(100000, 69999));
    }

    @Test
    public void testFirstTimestampAtZeroNeverTriggersCompensation() {
        decode(type0(0));
        decode(type0(0));
        assertEquals(0L, conn.getState().getTimestampOffset(CHANNEL));
        assertEquals(0, conn.getState().getLastRawTimestamp(CHANNEL));
    }

    @Test
    public void testOffsetAccumulatesAcrossRepeatedRestarts() {
        decode(type0(20000));
        decode(type0(100));
        assertEquals(20001L, conn.getState().getTimestampOffset(CHANNEL));
        decode(type1(1900)); // wire time now 2000
        Packet second = decode(type0(50));
        // previous offset + last raw (2000) + 1
        assertEquals(22002L, conn.getState().getTimestampOffset(CHANNEL));
        assertEquals(22002 + 50, messageTime(second));
    }

    @Test
    public void testOffsetIsCappedToLeaveHeadroom() {
        decode(type0(0x00FFFFFE));
        // force the accumulated offset past the cap, then restart again
        conn.getState().setTimestampOffset(CHANNEL, 0x40000000L);
        decode(type0(10));
        assertEquals(0x40000000L, conn.getState().getTimestampOffset(CHANNEL));
        assertTrue("rebased timestamp stays a positive int", messageTime(decode(type1(5))) > 0);
    }

    @Test
    public void testOffsetIsPerChannel() {
        decode(type0(20000));
        decode(type0(500));
        assertEquals(0L, conn.getState().getTimestampOffset(CHANNEL + 1));
        assertEquals(0L, conn.getState().getTimestampOffset(2));
    }

    @Test
    public void testOffsetAppliedOnceWhenPacketSpansDecodeRounds() {
        decode(type0(20000));
        IoBuffer full = type0(500);
        // deliver only the header and part of the body, forcing a re-decode of the header
        IoBuffer partial = IoBuffer.allocate(64);
        partial.put(full.array(), 0, full.limit() - 1);
        partial.flip();
        assertNull(decoder.decodePacket(conn, conn.getDecoderState(), partial));
        Packet packet = decode(full);
        assertEquals(20001L, conn.getState().getTimestampOffset(CHANNEL));
        assertEquals(20501, messageTime(packet));
    }

    // Type 0 delta handling

    @Test
    public void testType0AfterType1IgnoresPreviousDelta() {
        decode(type0(1000));
        decode(type1(40)); // delta 40 stored on the channel
        // an absolute timestamp must not have the stale delta added to it
        assertEquals(3000, messageTime(decode(type0(3000))));
    }

    @Test
    public void testType3AfterType1RepeatsTheDelta() {
        decode(type0(1000));
        decode(type1(40));
        assertEquals(1080, messageTime(decode(type3())));
        assertEquals(1120, messageTime(decode(type3())));
    }

    /** RTMP spec 5.3.1.2.4: the implied delta of a Type 3 chunk that follows a Type 0 chunk is the Type 0 timestamp. */
    @Test
    public void testType3DirectlyAfterType0UsesTheType0TimestampAsDelta() {
        assertEquals(1000, messageTime(decode(type0(1000))));
        assertEquals(2000, messageTime(decode(type3())));
        assertEquals(3000, messageTime(decode(type3())));
    }

    @Test
    public void testType1AfterType0IsUnaffectedByTheImpliedDelta() {
        decode(type0(1000));
        assertEquals(1040, messageTime(decode(type1(40))));
        assertEquals(1080, messageTime(decode(type3())));
    }

    // header chain

    @Test
    public void testStoredHeaderKeepsWireTimeAfterRestart() {
        decode(type0(20000));
        decode(type0(500));
        assertEquals("last header holds wire time, not the offset time", 500, conn.getState().getLastReadHeader(CHANNEL).getTimerBase());
    }

    /**
     * Packet.readExternal rebuilds the message timestamp from the header, so a deserialized packet (clustering)
     * loses the discontinuity offset. This pins the current behavior.
     */
    @Test
    public void testHeaderOfRestartedPacketDoesNotCarryOffset() {
        decode(type0(20000));
        Packet restarted = decode(type0(500));
        assertEquals(500, headerTime(restarted));
        assertTrue(messageTime(restarted) != headerTime(restarted));
    }
}
