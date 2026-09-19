package org.red5.io.obu;

/**
 * <p>OBPError class.</p>
 *
 * @author mondain
 */
public class OBPError {
    /** Description of the parsing error encountered while reading the AV1 OBU bitstream. */
    public String error;

    /** Number of bytes successfully consumed before the error occurred. */
    public long size;
}
