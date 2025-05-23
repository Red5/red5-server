package org.red5.io.obu;

/**
 * <p>OBPConstants class.</p>
 *
 * @author mondain
 */
public class OBPConstants {

    /** Constant <code>Z_MASK=(byte) 0b10000000</code> */
    public static final byte Z_MASK = (byte) 0b10000000;

    /** Constant <code>Z_BITSHIFT=7</code> */
    public static final int Z_BITSHIFT = 7;

    /** Constant <code>Y_MASK=(byte) 0b01000000</code> */
    public static final byte Y_MASK = (byte) 0b01000000;

    /** Constant <code>Y_BITSHIFT=6</code> */
    public static final int Y_BITSHIFT = 6;

    /** Constant <code>W_MASK=(byte) 0b00110000</code> */
    public static final byte W_MASK = (byte) 0b00110000;

    /** Constant <code>W_BITSHIFT=4</code> */
    public static final int W_BITSHIFT = 4;

    /** Constant <code>N_MASK=(byte) 0b00001000</code> */
    public static final byte N_MASK = (byte) 0b00001000;

    /** Constant <code>N_BITSHIFT=3</code> */
    public static final int N_BITSHIFT = 3;

    /** Constant <code>OBU_FRAME_TYPE_MASK=(byte) 0b01111000</code> */
    public static final byte OBU_FRAME_TYPE_MASK = (byte) 0b01111000; // 0x78

    /** Constant <code>OBU_FRAME_TYPE_BITSHIFT=3</code> */
    public static final int OBU_FRAME_TYPE_BITSHIFT = 3;

    /** Constant <code>OBU_FRAME_TYPE_SEQUENCE_HEADER=1</code> */
    public static final byte OBU_FRAME_TYPE_SEQUENCE_HEADER = 1;

    /** Constant <code>REFS_PER_FRAME=7</code> */
    public static final int REFS_PER_FRAME = 7;

    /** Constant <code>NUM_REF_FRAMES=8</code> */
    public static final int NUM_REF_FRAMES = 8;

    /** Constant <code>TOTAL_REFS_PER_FRAME=8</code> */
    public static final int TOTAL_REFS_PER_FRAME = 8;

    /** Constant <code>MAX_SEGMENTS=8</code> */
    public static final int MAX_SEGMENTS = 8;

    /** Constant <code>SEG_LVL_MAX=8</code> */
    public static final int SEG_LVL_MAX = 8;

    /** Constant <code>Segmentation_Feature_Bits</code> */
    public static final int[] Segmentation_Feature_Bits = { 8, 6, 6, 6, 6, 3, 0, 0 };

    /** Constant <code>Segmentation_Feature_Max</code> */
    public static final int[] Segmentation_Feature_Max = { 255, 63, 63, 63, 63, 7, 0, 0 };

    /** Constant <code>Segmentation_Feature_Signed</code> */
    public static final int[] Segmentation_Feature_Signed = { 1, 1, 1, 1, 1, 0, 0, 0 };

}
