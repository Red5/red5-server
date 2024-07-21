package org.red5.io.obu;

public class OBPConstants {

    public static final byte Z_MASK = (byte) 0b10000000;

    public static final int Z_BITSHIFT = 7;

    public static final byte Y_MASK = (byte) 0b01000000;

    public static final int Y_BITSHIFT = 6;

    public static final byte W_MASK = (byte) 0b00110000;

    public static final int W_BITSHIFT = 4;

    public static final byte N_MASK = (byte) 0b00001000;

    public static final int N_BITSHIFT = 3;

    public static final byte OBU_FRAME_TYPE_MASK = (byte) 0b01111000; // 0x78

    public static final int OBU_FRAME_TYPE_BITSHIFT = 3;

    public static final byte OBU_FRAME_TYPE_SEQUENCE_HEADER = 1;

    public static final int REFS_PER_FRAME = 7;

    public static final int NUM_REF_FRAMES = 8;

    public static final int TOTAL_REFS_PER_FRAME = 8;

    public static final int MAX_SEGMENTS = 8;

    public static final int SEG_LVL_MAX = 8;

    public static final int[] Segmentation_Feature_Bits = { 8, 6, 6, 6, 6, 3, 0, 0 };

    public static final int[] Segmentation_Feature_Max = { 255, 63, 63, 63, 63, 7, 0, 0 };

    public static final int[] Segmentation_Feature_Signed = { 1, 1, 1, 1, 1, 0, 0, 0 };

}