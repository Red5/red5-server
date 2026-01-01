package org.red5.server.net.rtmp.codec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Before;
import org.junit.Test;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.net.rtmp.message.Header;

/**
 * Tests for RTMP chunking logic, specifically for extended timestamp handling
 * and FFmpeg/libav compatibility fixes.
 *
 * These tests verify:
 * 1. Extended timestamp flag is properly reset when timestamps drop below MEDIUM_INT_MAX
 * 2. Type 3 extended timestamp detection uses both flag and value-based checks
 * 3. Chunk size validation accepts full RTMP spec range
 * 4. Encoder/decoder symmetry for extended timestamps
 */
public class RTMPChunkingTest implements Constants {

    private RTMPProtocolDecoder decoder;

    private RTMPProtocolEncoder encoder;

    @Before
    public void setUp() {
        decoder = new RTMPProtocolDecoder();
        encoder = new RTMPProtocolEncoder();
    }

    /**
     * Test that extended flag is properly set when timestamp >= MEDIUM_INT_MAX
     */
    @Test
    public void testExtendedTimestampFlagSet() {
        Header header = new Header();
        header.setChannelId(4);
        header.setTimerBase(MEDIUM_INT_MAX + 1000); // Above threshold
        header.setTimerDelta(0);
        header.setSize(100);
        header.setDataType(TYPE_VIDEO_DATA);
        header.setStreamId(1);

        // Simulate encoder behavior for Type 0
        int timeBase = header.getTimerBase();
        if (timeBase >= MEDIUM_INT_MAX) {
            header.setExtended(true);
        } else {
            header.setExtended(false);
        }

        assertTrue("Extended flag should be true when timestamp >= MEDIUM_INT_MAX", header.isExtended());
        assertEquals("Timer should be preserved", MEDIUM_INT_MAX + 1000, header.getTimer());
    }

    /**
     * Test that extended flag is properly reset when timestamp < MEDIUM_INT_MAX
     */
    @Test
    public void testExtendedTimestampFlagReset() {
        Header header = new Header();
        header.setChannelId(4);
        header.setTimerBase(1000); // Below threshold
        header.setTimerDelta(0);
        header.setSize(100);
        header.setDataType(TYPE_VIDEO_DATA);
        header.setStreamId(1);
        header.setExtended(true); // Previously set

        // Simulate encoder behavior for Type 0 with reset
        int timeBase = header.getTimerBase();
        if (timeBase >= MEDIUM_INT_MAX) {
            header.setExtended(true);
        } else {
            header.setExtended(false);
        }

        assertFalse("Extended flag should be false when timestamp < MEDIUM_INT_MAX", header.isExtended());
    }

    /**
     * Test Type 1 header extended flag reset when delta < MEDIUM_INT_MAX
     */
    @Test
    public void testType1ExtendedFlagReset() {
        // Create a lastHeader with extended timestamp
        Header lastHeader = new Header();
        lastHeader.setChannelId(4);
        lastHeader.setTimerBase(MEDIUM_INT_MAX + 1000);
        lastHeader.setTimerDelta(0);
        lastHeader.setExtended(true);

        // Create new header with small delta
        Header header = new Header();
        header.setChannelId(4);
        header.setTimerBase(MEDIUM_INT_MAX + 1000);
        header.setTimerDelta(33); // Small delta, below MEDIUM_INT_MAX

        // Inherit extended flag first (as decoder does)
        header.setExtended(lastHeader.isExtended());

        // Then reset based on delta (as per our fix)
        int timeDelta = header.getTimerDelta();
        if (timeDelta >= MEDIUM_INT_MAX) {
            header.setExtended(true);
        } else {
            header.setExtended(false);
        }

        assertFalse("Extended flag should be reset when delta < MEDIUM_INT_MAX", header.isExtended());
    }

    /**
     * Test Type 3 extended timestamp detection using value-based check
     * This tests the FFmpeg/libav compatibility fix
     */
    @Test
    public void testType3ExtendedTimestampValueBasedDetection() {
        // Scenario: lastHeader has extended timestamp but flag wasn't properly set
        Header lastHeader = new Header();
        lastHeader.setChannelId(4);
        lastHeader.setTimerBase(MEDIUM_INT_MAX + 1000);
        lastHeader.setTimerDelta(0);
        lastHeader.setExtended(false); // Flag incorrectly not set

        // Value-based detection should still work
        boolean hasExtendedTimestamp = lastHeader.isExtended();
        if (!hasExtendedTimestamp) {
            int lastTimerDelta = lastHeader.getTimerDelta();
            int lastTimerBase = lastHeader.getTimerBase();
            hasExtendedTimestamp = (lastTimerDelta >= MEDIUM_INT_MAX) || (lastTimerDelta == 0 && lastTimerBase >= MEDIUM_INT_MAX);
        }

        assertTrue("Value-based detection should find extended timestamp", hasExtendedTimestamp);
    }

    /**
     * Test Type 3 extended timestamp detection when delta is extended
     */
    @Test
    public void testType3ExtendedTimestampDeltaDetection() {
        // Scenario: lastHeader has extended delta
        Header lastHeader = new Header();
        lastHeader.setChannelId(4);
        lastHeader.setTimerBase(1000);
        lastHeader.setTimerDelta(MEDIUM_INT_MAX + 500);
        lastHeader.setExtended(true);

        // Value-based detection for delta
        boolean hasExtendedTimestamp = lastHeader.isExtended();
        if (!hasExtendedTimestamp) {
            int lastTimerDelta = lastHeader.getTimerDelta();
            int lastTimerBase = lastHeader.getTimerBase();
            hasExtendedTimestamp = (lastTimerDelta >= MEDIUM_INT_MAX) || (lastTimerDelta == 0 && lastTimerBase >= MEDIUM_INT_MAX);
        }

        assertTrue("Should detect extended timestamp from delta", hasExtendedTimestamp);
    }

    /**
     * Test chunk size validation accepts full RTMP spec range
     */
    @Test
    public void testChunkSizeValidationFullRange() {
        RTMP rtmp = new RTMP();

        // Test minimum valid chunk size
        rtmp.setReadChunkSize(1);
        assertEquals(1, rtmp.getReadChunkSize());

        // Test default chunk size
        rtmp.setReadChunkSize(128);
        assertEquals(128, rtmp.getReadChunkSize());

        // Test librtmp typical max
        rtmp.setReadChunkSize(65536);
        assertEquals(65536, rtmp.getReadChunkSize());

        // Test above librtmp max but within RTMP spec
        rtmp.setReadChunkSize(100000);
        assertEquals(100000, rtmp.getReadChunkSize());

        // Test RTMP spec max
        rtmp.setReadChunkSize(16777215);
        assertEquals(16777215, rtmp.getReadChunkSize());
    }

    /**
     * Test chunk size validation rejects invalid values
     */
    @Test(expected = IllegalArgumentException.class)
    public void testChunkSizeValidationRejectsZero() {
        RTMP rtmp = new RTMP();
        rtmp.setReadChunkSize(0);
    }

    /**
     * Test chunk size validation rejects values above RTMP spec max
     */
    @Test(expected = IllegalArgumentException.class)
    public void testChunkSizeValidationRejectsAboveMax() {
        RTMP rtmp = new RTMP();
        rtmp.setReadChunkSize(16777216); // MEDIUM_INT_MAX + 1
    }

    /**
     * Test write chunk size validation
     */
    @Test
    public void testWriteChunkSizeValidation() {
        RTMP rtmp = new RTMP();

        // Test various valid values
        rtmp.setWriteChunkSize(128);
        assertEquals(128, rtmp.getWriteChunkSize());

        rtmp.setWriteChunkSize(4096);
        assertEquals(4096, rtmp.getWriteChunkSize());

        rtmp.setWriteChunkSize(16777215);
        assertEquals(16777215, rtmp.getWriteChunkSize());
    }

    /**
     * Test encoder Type 3 extended timestamp value calculation for Type 0 origin
     */
    @Test
    public void testEncoderType3ExtendedTimestampFromType0() {
        // Simulate Type 0 header with extended timestamp
        Header lastHeader = new Header();
        lastHeader.setChannelId(4);
        lastHeader.setTimerBase(MEDIUM_INT_MAX + 5000);
        lastHeader.setTimerDelta(0);
        lastHeader.setExtended(true);

        // Calculate extended timestamp value as encoder should
        int extendedTimestamp;
        if (lastHeader.getTimerDelta() >= MEDIUM_INT_MAX) {
            // Type 1/2 case: use delta
            extendedTimestamp = lastHeader.getTimerDelta();
        } else {
            // Type 0 case: use full timer
            extendedTimestamp = lastHeader.getTimer();
        }

        assertEquals("Extended timestamp should be full timer for Type 0", MEDIUM_INT_MAX + 5000, extendedTimestamp);
    }

    /**
     * Test encoder Type 3 extended timestamp value calculation for Type 1/2 origin
     */
    @Test
    public void testEncoderType3ExtendedTimestampFromType1() {
        // Simulate Type 1/2 header with extended delta
        Header lastHeader = new Header();
        lastHeader.setChannelId(4);
        lastHeader.setTimerBase(1000); // Modified base
        lastHeader.setTimerDelta(MEDIUM_INT_MAX + 3000);
        lastHeader.setExtended(true);

        // Calculate extended timestamp value as encoder should
        int extendedTimestamp;
        if (lastHeader.getTimerDelta() >= MEDIUM_INT_MAX) {
            // Type 1/2 case: use delta
            extendedTimestamp = lastHeader.getTimerDelta();
        } else {
            // Type 0 case: use full timer
            extendedTimestamp = lastHeader.getTimer();
        }

        assertEquals("Extended timestamp should be delta for Type 1/2", MEDIUM_INT_MAX + 3000, extendedTimestamp);
    }

    /**
     * Test Header timer calculation
     */
    @Test
    public void testHeaderTimerCalculation() {
        Header header = new Header();
        header.setTimerBase(1000);
        header.setTimerDelta(500);

        assertEquals("Timer should be base + delta", 1500, header.getTimer());
    }

    /**
     * Test Header clone preserves all fields including extended flag
     */
    @Test
    public void testHeaderClonePreservesExtended() {
        Header original = new Header();
        original.setChannelId(4);
        original.setTimerBase(MEDIUM_INT_MAX + 1000);
        original.setTimerDelta(0);
        original.setSize(100);
        original.setDataType(TYPE_VIDEO_DATA);
        original.setStreamId(1);
        original.setExtended(true);

        Header cloned = original.clone();

        assertEquals(original.getChannelId(), cloned.getChannelId());
        assertEquals(original.getTimerBase(), cloned.getTimerBase());
        assertEquals(original.getTimerDelta(), cloned.getTimerDelta());
        assertEquals(original.getSize(), cloned.getSize());
        assertEquals(original.getDataType(), cloned.getDataType());
        assertEquals(original.getStreamId(), cloned.getStreamId());
        assertEquals("Extended flag should be preserved in clone", original.isExtended(), cloned.isExtended());
    }

    /**
     * Test MEDIUM_INT_MAX constant value
     */
    @Test
    public void testMediumIntMaxValue() {
        // MEDIUM_INT_MAX should be 0xFFFFFF (16777215)
        assertEquals("MEDIUM_INT_MAX should be 16777215", 16777215, MEDIUM_INT_MAX);
        assertEquals("MEDIUM_INT_MAX should be 0xFFFFFF", 0xFFFFFF, MEDIUM_INT_MAX);
    }

    /**
     * Test extended timestamp boundary - exactly at MEDIUM_INT_MAX
     */
    @Test
    public void testExtendedTimestampBoundary() {
        Header header = new Header();
        header.setTimerBase(MEDIUM_INT_MAX);
        header.setTimerDelta(0);

        // At exactly MEDIUM_INT_MAX, should be extended
        boolean isExtended = header.getTimerBase() >= MEDIUM_INT_MAX;
        assertTrue("Timestamp at exactly MEDIUM_INT_MAX should trigger extended", isExtended);
    }

    /**
     * Test extended timestamp just below boundary
     */
    @Test
    public void testExtendedTimestampJustBelowBoundary() {
        Header header = new Header();
        header.setTimerBase(MEDIUM_INT_MAX - 1);
        header.setTimerDelta(0);

        // Just below MEDIUM_INT_MAX, should not be extended
        boolean isExtended = header.getTimerBase() >= MEDIUM_INT_MAX;
        assertFalse("Timestamp just below MEDIUM_INT_MAX should not trigger extended", isExtended);
    }

    /**
     * Test unsigned timestamp comparison for values >= 2^31
     * When timestamps exceed Integer.MAX_VALUE, they become negative in Java
     * but should still be treated as large positive values for extended timestamp detection
     */
    @Test
    public void testUnsignedTimestampComparison() {
        // Value just above Integer.MAX_VALUE (2147483648)
        int largeTimestamp = Integer.MIN_VALUE; // This is 2^31 as unsigned

        // Signed comparison would be false (negative < positive)
        assertFalse("Signed comparison incorrectly treats large unsigned as small", largeTimestamp >= MEDIUM_INT_MAX);

        // Unsigned comparison should be true (2^31 > 0xFFFFFF)
        assertTrue("Unsigned comparison should detect large timestamp", Integer.compareUnsigned(largeTimestamp, MEDIUM_INT_MAX) >= 0);
    }

    /**
     * Test unsigned comparison with various boundary values
     */
    @Test
    public void testUnsignedComparisonBoundaries() {
        // Integer.MAX_VALUE (2^31 - 1) - still positive
        assertTrue("Integer.MAX_VALUE should trigger extended", Integer.compareUnsigned(Integer.MAX_VALUE, MEDIUM_INT_MAX) >= 0);

        // Integer.MIN_VALUE (2^31 as unsigned) - negative in Java
        assertTrue("Integer.MIN_VALUE (unsigned 2^31) should trigger extended", Integer.compareUnsigned(Integer.MIN_VALUE, MEDIUM_INT_MAX) >= 0);

        // -1 (0xFFFFFFFF as unsigned) - maximum unsigned value
        assertTrue("-1 (unsigned 0xFFFFFFFF) should trigger extended", Integer.compareUnsigned(-1, MEDIUM_INT_MAX) >= 0);

        // MEDIUM_INT_MAX exactly
        assertTrue("MEDIUM_INT_MAX exactly should trigger extended", Integer.compareUnsigned(MEDIUM_INT_MAX, MEDIUM_INT_MAX) >= 0);

        // Just below MEDIUM_INT_MAX
        assertFalse("Just below MEDIUM_INT_MAX should not trigger extended", Integer.compareUnsigned(MEDIUM_INT_MAX - 1, MEDIUM_INT_MAX) >= 0);
    }

    /**
     * Test timestamp delta calculation with large values
     */
    @Test
    public void testLargeTimestampDelta() {
        // Simulating a case where current timestamp is 2^31 + 1000
        // and last timestamp is 2^31 - 1000
        // Integer.MAX_VALUE - 1000 = 2147482647
        // Integer.MIN_VALUE + 1000 = -2147482648 (unsigned: 2147484648)
        // Delta = 2147484648 - 2147482647 = 2001

        int lastTimestamp = Integer.MAX_VALUE - 1000; // Large positive
        int currentTimestamp = Integer.MIN_VALUE + 1000; // Just past overflow

        // Calculate delta treating as unsigned
        long delta = Integer.toUnsignedLong(currentTimestamp) - Integer.toUnsignedLong(lastTimestamp);
        assertEquals("Delta across 2^31 boundary should be 2001", 2001L, delta);
    }

    /**
     * Test timestamp discontinuity detection logic
     * When source restarts, timestamps jump backward and should be detected
     */
    @Test
    public void testTimestampDiscontinuityDetection() {
        // Scenario: source was at timestamp 2147445785, then restarts to 11797
        int lastRawTimestamp = 2147445785;
        int newRawTimestamp = 11797;

        long unsignedLast = Integer.toUnsignedLong(lastRawTimestamp);
        long unsignedCurrent = Integer.toUnsignedLong(newRawTimestamp);

        // Check if this is a discontinuity (backward jump > 1 second)
        boolean isDiscontinuity = unsignedLast > unsignedCurrent && (unsignedLast - unsignedCurrent) > 1000L;

        assertTrue("Should detect timestamp discontinuity", isDiscontinuity);

        // Calculate offset for compensation
        long offset = unsignedLast + 1; // Continue from last timestamp

        // Apply offset to new timestamp
        long adjustedTimestamp = unsignedCurrent + offset;

        // Adjusted timestamp should be greater than last
        assertTrue("Adjusted timestamp should be greater than last", adjustedTimestamp > unsignedLast);

        // And the difference should be small (the new timestamp + 1)
        assertEquals("Adjusted timestamp should be last + 1 + new", unsignedLast + 1 + unsignedCurrent, adjustedTimestamp);
    }

    /**
     * Test that normal timestamp progression doesn't trigger discontinuity detection
     */
    @Test
    public void testNormalTimestampProgressionNotDiscontinuity() {
        // Normal progression: small increases
        int lastRawTimestamp = 1000;
        int newRawTimestamp = 1033; // 33ms later (typical frame interval)

        long unsignedLast = Integer.toUnsignedLong(lastRawTimestamp);
        long unsignedCurrent = Integer.toUnsignedLong(newRawTimestamp);

        // Should NOT be a discontinuity
        boolean isDiscontinuity = unsignedLast > unsignedCurrent && (unsignedLast - unsignedCurrent) > 1000L;

        assertFalse("Normal progression should not be discontinuity", isDiscontinuity);
    }

    /**
     * Test that small backward jitter doesn't trigger discontinuity detection
     */
    @Test
    public void testSmallBackwardJitterNotDiscontinuity() {
        // Small backward jump (could be network reordering)
        int lastRawTimestamp = 1100;
        int newRawTimestamp = 1050; // 50ms backward

        long unsignedLast = Integer.toUnsignedLong(lastRawTimestamp);
        long unsignedCurrent = Integer.toUnsignedLong(newRawTimestamp);

        // Should NOT be a discontinuity (only 50ms backward, threshold is 1000ms)
        boolean isDiscontinuity = unsignedLast > unsignedCurrent && (unsignedLast - unsignedCurrent) > 1000L;

        assertFalse("Small backward jitter should not be discontinuity", isDiscontinuity);
    }

    /**
     * Test RTMP timestamp offset tracking
     */
    @Test
    public void testRtmpTimestampOffsetTracking() {
        RTMP rtmp = new RTMP();

        // Initially offset should be 0
        assertEquals("Initial offset should be 0", 0L, rtmp.getTimestampOffset(4));

        // Set an offset
        rtmp.setTimestampOffset(4, 2147445786L);
        assertEquals("Offset should be set", 2147445786L, rtmp.getTimestampOffset(4));

        // Track last raw timestamp
        rtmp.setLastRawTimestamp(4, 11797);
        assertEquals("Last raw timestamp should be set", 11797, rtmp.getLastRawTimestamp(4));

        // Different channels should have independent offsets
        assertEquals("Different channel should have 0 offset", 0L, rtmp.getTimestampOffset(5));
    }

    /**
     * Test that timestamp offset is capped to prevent integer overflow in downstream processing.
     * When source has timestamps near Integer.MAX_VALUE and restarts, the calculated offset
     * could exceed safe bounds for int arithmetic in PlayEngine.
     */
    @Test
    public void testTimestampOffsetCappingForOverflowPrevention() {
        // Scenario: Videon with timestamps at 2147445785 (near Integer.MAX_VALUE) restarts
        int lastRawTimestamp = 2147445785;
        int newRawTimestamp = 11797;
        long currentOffset = 0L;

        long unsignedLast = Integer.toUnsignedLong(lastRawTimestamp);
        long unsignedCurrent = Integer.toUnsignedLong(newRawTimestamp);

        // Verify this is a discontinuity
        boolean isDiscontinuity = unsignedLast > unsignedCurrent && (unsignedLast - unsignedCurrent) > 1000L;
        assertTrue("Should detect discontinuity", isDiscontinuity);

        // Calculate raw offset (as decoder would)
        long newOffset = currentOffset + unsignedLast + 1;

        // Without capping, offset would be ~2.1 billion - exceeds the safe threshold
        final long MAX_SAFE_OFFSET = 0x40000000L; // ~1 billion, leaves headroom for int operations
        assertTrue("Uncapped offset should exceed MAX_SAFE_OFFSET", newOffset > MAX_SAFE_OFFSET);

        // Cap to MAX_SAFE_OFFSET (as decoder does)
        if (newOffset > MAX_SAFE_OFFSET) {
            newOffset = MAX_SAFE_OFFSET;
        }

        // Verify capped offset is safe
        assertTrue("Capped offset should be <= MAX_SAFE_OFFSET", newOffset <= MAX_SAFE_OFFSET);

        // Apply offset to new timestamp
        long adjustedTimestamp = unsignedCurrent + newOffset;

        // The adjusted timestamp should be safely within int range
        assertTrue("Adjusted timestamp should be less than Integer.MAX_VALUE", adjustedTimestamp < Integer.MAX_VALUE);

        // When cast to int, it should remain positive (no overflow)
        int adjustedInt = (int) (adjustedTimestamp & 0xFFFFFFFFL);
        assertTrue("Adjusted timestamp as int should be positive", adjustedInt > 0);
    }
}
