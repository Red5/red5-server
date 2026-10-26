package org.red5.server.net.mediabunny.fmp4;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Leaf boxes of a media fragment (moof + mdat) - ISO/IEC 14496-12 Section 8.8.
 */
final class FragmentBoxes {

    private FragmentBoxes() {
    }

    /**
     * Movie Fragment Header Box (mfhd).
     */
    static final class MfhdBox extends Box {

        private final long sequenceNumber;

        MfhdBox(long sequenceNumber) {
            super("mfhd");
            this.sequenceNumber = sequenceNumber;
        }

        @Override
        int payloadSize() {
            return 4 + 4;
        }

        @Override
        void writePayload(ByteBuffer buffer) {
            putVersionFlags(buffer, 0, 0);
            buffer.putInt((int) sequenceNumber);
        }

    }

    /**
     * Track Fragment Header Box (tfhd) with default-base-is-moof set and no optional fields.
     */
    static final class TfhdBox extends Box {

        private static final int DEFAULT_BASE_IS_MOOF = 0x020000;

        private final long trackId;

        TfhdBox(long trackId) {
            super("tfhd");
            this.trackId = trackId;
        }

        @Override
        int payloadSize() {
            return 4 + 4;
        }

        @Override
        void writePayload(ByteBuffer buffer) {
            putVersionFlags(buffer, 0, DEFAULT_BASE_IS_MOOF);
            buffer.putInt((int) trackId);
        }

    }

    /**
     * Track Fragment Decode Time Box (tfdt), version 1 (64-bit decode time).
     */
    static final class TfdtBox extends Box {

        private final long baseMediaDecodeTime;

        TfdtBox(long baseMediaDecodeTime) {
            super("tfdt");
            this.baseMediaDecodeTime = baseMediaDecodeTime;
        }

        @Override
        int payloadSize() {
            return 4 + 8;
        }

        @Override
        void writePayload(ByteBuffer buffer) {
            putVersionFlags(buffer, 1, 0);
            buffer.putLong(baseMediaDecodeTime);
        }

    }

    /**
     * Track Fragment Run Box (trun) carrying data offset plus per-sample duration, size, flags and, optionally,
     * composition time offsets.
     */
    static final class TrunBox extends Box {

        private static final int DATA_OFFSET_PRESENT = 0x000001;

        private static final int SAMPLE_DURATION_PRESENT = 0x000100;

        private static final int SAMPLE_SIZE_PRESENT = 0x000200;

        private static final int SAMPLE_FLAGS_PRESENT = 0x000400;

        private static final int SAMPLE_COMPOSITION_TIME_OFFSET_PRESENT = 0x000800;

        private final List<Fmp4FragmentBuilder.SampleData> samples;

        private final boolean compositionOffsets;

        // version 1 signals signed composition time offsets
        private final int version;

        private int dataOffset;

        TrunBox(List<Fmp4FragmentBuilder.SampleData> samples, boolean compositionOffsets, boolean signedCompositionOffsets) {
            super("trun");
            this.samples = samples;
            this.compositionOffsets = compositionOffsets;
            this.version = signedCompositionOffsets ? 1 : 0;
        }

        void setDataOffset(int dataOffset) {
            this.dataOffset = dataOffset;
        }

        @Override
        int payloadSize() {
            return 4 + 4 + 4 + samples.size() * (compositionOffsets ? 16 : 12);
        }

        @Override
        void writePayload(ByteBuffer buffer) {
            int flags = DATA_OFFSET_PRESENT | SAMPLE_DURATION_PRESENT | SAMPLE_SIZE_PRESENT | SAMPLE_FLAGS_PRESENT;
            if (compositionOffsets) {
                flags |= SAMPLE_COMPOSITION_TIME_OFFSET_PRESENT;
            }
            putVersionFlags(buffer, version, flags);
            buffer.putInt(samples.size());
            buffer.putInt(dataOffset);
            for (Fmp4FragmentBuilder.SampleData sample : samples) {
                buffer.putInt((int) sample.duration);
                buffer.putInt((int) sample.size);
                buffer.putInt(sample.flags.getFlags());
                if (compositionOffsets) {
                    buffer.putInt(sample.compositionTimeOffset != null ? sample.compositionTimeOffset.intValue() : 0);
                }
            }
        }

    }

    /**
     * Media Data Box (mdat).
     */
    static final class MdatBox extends Box {

        private final byte[] data;

        MdatBox(byte[] data) {
            super("mdat");
            this.data = data;
        }

        @Override
        int payloadSize() {
            return data.length;
        }

        @Override
        void writePayload(ByteBuffer buffer) {
            buffer.put(data);
        }

    }

}
