package org.red5.io.obu;

/**
 * <p>OBPTxMode class.</p>
 *
 * @author mondain
 */
public enum OBPTxMode {
    /** Only 4x4 transform blocks are used. */
    ONLY_4X4,
    /** The largest possible transform size is used for each block. */
    LARGEST,
    /** The transform size is selected per block (signaled in the bitstream). */
    SELECT
}
