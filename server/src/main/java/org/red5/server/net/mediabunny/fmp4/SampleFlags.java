package org.red5.server.net.mediabunny.fmp4;

/**
 * Sample flags as defined in ISO/IEC 14496-12 Section 8.8.3.1, written per sample in the trun box.
 */
public final class SampleFlags {

    // sample_depends_on=2 (does not depend on others), sample_is_non_sync_sample=0
    private static final SampleFlags SYNC = new SampleFlags(2 << 24);

    // sample_depends_on=1 (depends on others), sample_is_non_sync_sample=1
    private static final SampleFlags NON_SYNC = new SampleFlags((1 << 24) | (1 << 16));

    private final int flags;

    private SampleFlags(int flags) {
        this.flags = flags;
    }

    int getFlags() {
        return flags;
    }

    /**
     * @return flags for a sync sample (key frame) that does not depend on other samples
     */
    public static SampleFlags createSyncSampleFlags() {
        return SYNC;
    }

    /**
     * @return flags for a non-sync sample (non-key frame) that depends on other samples
     */
    public static SampleFlags createNonSyncSampleFlags() {
        return NON_SYNC;
    }

}
