package org.red5.io.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.red5.io.utils.LEB128.LEB128Exception;

public class LEB128Test {

    @Test
    public void testLEB128ResultToString() {
        LEB128.LEB128Result result = new LEB128.LEB128Result(123, 3);
        String expected = "LEB128Result [value=123, bytesRead=3]";
        assertEquals(expected, result.toString());
    }

    @Test
    public void testLEB128ExceptionMessage() {
        try {
            throw new LEB128.LEB128Exception("Test Exception");
        } catch (LEB128.LEB128Exception e) {
            assertEquals("Test Exception", e.getMessage());
        }
    }

    @Test
    public void testEncodeAndDecode() throws LEB128Exception {
        int[][] testData = { { 0, 0 }, { 127, 0x7f }, { 11, 0x0b, 0x0b08 }, { 150, 0x9601 }, { 254, 0xfe01 }, { 400, 0x9003 }, { 1200, 0xb009 }, { 999999, 0xBF843D }, { 1176, 0x9809 }, { 1162, 0x8a09 } };
        // add max value at some point { Integer.MAX_VALUE, 0xffffff07 }
        for (int i = 0; i < testData.length; i++) {
            int encoded = LEB128.encode(testData[i][0]);
            System.out.println("Value: " + testData[i][0] + " encoded: " + Integer.toHexString(encoded));
            if (encoded != testData[i][1]) {
                if (testData[i][2] == 0) {
                    fail("Encode failed: " + encoded + " expected " + testData[i][1]);
                } else {
                    fail("Encode failed: " + encoded + " expected " + testData[i][1] + " or " + testData[i][2]);
                }
            } else {
                System.out.printf("Encode success: %d decoded: %d%n", encoded, testData[i][0]);
            }
            LEB128.LEB128Result decoded = LEB128.decode(encoded);
            if (decoded.value != testData[i][0]) {
                fail("Decode failed: " + decoded.value + " expected " + testData[i][0]);
            } else {
                System.out.printf("Decode success: %d encoded: %d%n", decoded.value, testData[i][1]);
            }
        }
        //int bytesEncoded = encode(2824, test, 0);
        //int bytesEncoded = encode(0, test, 0);
        //System.out.println("Bytes encoded: " + bytesEncoded + " value: " + HexDump.byteArrayToHexString(test) + " decoded: " + decode(test, 0)[0]);
        //byte[] fragmentLen = new byte[] { 0x01, (byte) 0x88, 0x16, 0, 0 }; // 2824 index 1
        //byte[] fragmentLen = new byte[] { 0x07, 0x07, (byte) 0xfe, 1, (byte) 0x8f, 0 }; // 254 index 2
        //LEB128Result result = decode(fragmentLen, 2);
        //System.out.println("Decoded: " + result);
    }
}