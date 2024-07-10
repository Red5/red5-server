package org.red5.io.obu;

public class OBPConstants {

    public static final int REFS_PER_FRAME = 7;

    public static final int NUM_REF_FRAMES = 8;

    public static final int TOTAL_REFS_PER_FRAME = 8;

    public static final int MAX_SEGMENTS = 8;

    public static final int SEG_LVL_MAX = 8;

    public static final int[] Segmentation_Feature_Bits = { 8, 6, 6, 6, 6, 3, 0, 0 };

    public static final int[] Segmentation_Feature_Max = { 255, 63, 63, 63, 63, 7, 0, 0 };

    public static final int[] Segmentation_Feature_Signed = { 1, 1, 1, 1, 1, 0, 0, 0 };

}