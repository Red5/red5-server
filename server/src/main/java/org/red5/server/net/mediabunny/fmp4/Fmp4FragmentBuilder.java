package org.red5.server.net.mediabunny.fmp4;

import java.util.ArrayList;
import java.util.List;

import org.red5.server.net.mediabunny.fmp4.FragmentBoxes.MdatBox;
import org.red5.server.net.mediabunny.fmp4.FragmentBoxes.MfhdBox;
import org.red5.server.net.mediabunny.fmp4.FragmentBoxes.TfdtBox;
import org.red5.server.net.mediabunny.fmp4.FragmentBoxes.TfhdBox;
import org.red5.server.net.mediabunny.fmp4.FragmentBoxes.TrunBox;

/**
 * Builds single-track fMP4 fragments (moof + mdat). No styp box is written; MediaBunny clients receive fragments as a
 * continuous fragmented MP4 stream following the init segment.
 *
 * Derived from the write path of the CMAF packager in red5-moq-pkgr 1.3.11.
 */
public class Fmp4FragmentBuilder {

    /**
     * Builds and serializes a fragment.
     *
     * @param config fragment configuration
     * @return serialized moof + mdat
     */
    public byte[] buildFragment(FragmentConfig config) {
        if (config.mediaData == null) {
            throw new IllegalStateException("Fragment mediaData is required");
        }
        if (config.samples.isEmpty()) {
            throw new IllegalStateException("Fragment must contain at least one sample");
        }
        TrunBox trun = new TrunBox(config.samples, config.hasCompositionOffsets, config.hasSignedCompositionOffsets);
        ContainerBox traf = new ContainerBox("traf", new TfhdBox(config.trackId), new TfdtBox(config.baseDecodeTime), trun);
        ContainerBox moof = new ContainerBox("moof", new MfhdBox(config.sequenceNumber), traf);
        // data offset is relative to the start of moof (default-base-is-moof); mdat header is 8 bytes
        trun.setDataOffset(moof.size() + 8);
        return Box.serialize(moof, new MdatBox(config.mediaData));
    }

    /**
     * Describes one media fragment (moof + mdat) to build: sequence number, track, base decode time, sample table and media payload.
     */
    public static class FragmentConfig {

        private long sequenceNumber;

        private long trackId;

        private long baseDecodeTime;

        private boolean hasCompositionOffsets;

        private boolean hasSignedCompositionOffsets;

        private byte[] mediaData;

        private List<SampleData> samples = new ArrayList<>();

        /**
         * Sets the fragment sequence number written to the movie fragment header.
         *
         * @param sequenceNumber fragment sequence number, starting at 1
         * @return this configuration
         */
        public FragmentConfig setSequenceNumber(long sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
            return this;
        }

        /**
         * Sets the track the fragment belongs to.
         *
         * @param trackId track identifier matching the init segment
         * @return this configuration
         */
        public FragmentConfig setTrackId(long trackId) {
            this.trackId = trackId;
            return this;
        }

        /**
         * Sets the decode time of the first sample in the fragment.
         *
         * @param baseDecodeTime decode time in track timescale units
         * @return this configuration
         */
        public FragmentConfig setBaseDecodeTime(long baseDecodeTime) {
            this.baseDecodeTime = baseDecodeTime;
            return this;
        }

        /**
         * Sets the concatenated sample payload placed in the mdat box.
         *
         * @param mediaData media bytes for all samples, in sample order
         * @return this configuration
         */
        public FragmentConfig setMediaData(byte[] mediaData) {
            this.mediaData = mediaData;
            return this;
        }

        /**
         * Sets the sample table and records whether any sample carries a (signed) composition time offset.
         *
         * @param samples samples in decode order; null clears the list
         * @return this configuration
         */
        public FragmentConfig setSamples(List<SampleData> samples) {
            this.samples = samples != null ? samples : new ArrayList<>();
            for (SampleData sample : this.samples) {
                if (sample.compositionTimeOffset != null) {
                    hasCompositionOffsets = true;
                    if (sample.compositionTimeOffset < 0) {
                        hasSignedCompositionOffsets = true;
                    }
                }
            }
            return this;
        }

    }

    /**
     * Timing, size and flags of a single sample in a fragment.
     */
    public static class SampleData {

        final long duration;

        final long size;

        final SampleFlags flags;

        final Long compositionTimeOffset;

        /**
         * Creates a sample description.
         *
         * @param duration sample duration in track timescale units
         * @param size sample size in bytes
         * @param flags sample flags
         * @param compositionTimeOffset composition time offset in track timescale units, or null if none
         */
        public SampleData(long duration, long size, SampleFlags flags, Long compositionTimeOffset) {
            this.duration = duration;
            this.size = size;
            this.flags = flags;
            this.compositionTimeOffset = compositionTimeOffset;
        }

    }

}
