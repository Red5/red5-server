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
        // Test data format: { input_value, expected_leb128_encoded_int }
        // LEB128 format: little-endian base 128, MSB=1 means more bytes follow
        int[][] testData = { { 0, 0x00 }, // 0 -> 0x00
                { 11, 0x0b }, // 11 -> 0x0b (fits in 7 bits, MSB=0)
                { 127, 0x7f }, // 127 -> 0x7f (fits in 7 bits, MSB=0)
                { 150, 0x9601 }, // 150 -> 0x96 0x01 (little-endian: 0x96 = 22, 0x01 = 1, (22 << 0) | (1 << 7) = 150)
                { 254, 0xfe01 }, // 254 -> 0xfe 0x01
                { 255, 0xff01 }, // 255 -> 0xff 0x01
                { 256, 0x8002 }, // 256 -> 0x80 0x02
                { 400, 0x9003 }, // 400 -> 0x90 0x03
                { 1200, 0xb009 }, // 1200 -> 0xb0 0x09
                { 16383, 0xff7f }, // 16383 -> 0xff 0x7f (max 2-byte value)
                { 16384, 0x808001 }, // 16384 -> 0x80 0x80 0x01 (needs 3 bytes)
                { 1176, 0x9809 }, // 1176 -> 0x98 0x09
                { 1162, 0x8a09 }, // 1162 -> 0x8a 0x09
                { 999999, 0xBF843D } // 999999 -> 0xbf 0x84 0x3d
        };
        for (int i = 0; i < testData.length; i++) {
            int inputValue = testData[i][0];
            int expectedEncoded = testData[i][1];

            // Test encoding
            int encoded = LEB128.encode(inputValue);
            System.out.println("Value: " + inputValue + " encoded: 0x" + Integer.toHexString(encoded));

            if (encoded != expectedEncoded) {
                fail("Encode failed for value " + inputValue + ": got 0x" + Integer.toHexString(encoded) + " expected 0x" + Integer.toHexString(expectedEncoded));
            } else {
                System.out.printf("Encode success: value=%d encoded=0x%x%n", inputValue, encoded);
            }

            // Test round-trip decoding
            byte[] encodedBytes = new byte[5];
            int byteCount = LEB128.encode(inputValue, encodedBytes, 0);
            byte[] actualBytes = new byte[byteCount];
            System.arraycopy(encodedBytes, 0, actualBytes, 0, byteCount);

            LEB128.LEB128Result decoded = LEB128.decode(actualBytes);
            if (decoded.value != inputValue) {
                fail("Decode round-trip failed: got " + decoded.value + " expected " + inputValue);
            } else {
                System.out.printf("Decode round-trip success: value=%d bytes=%d%n", decoded.value, decoded.bytesRead);
            }
        }
    }
}