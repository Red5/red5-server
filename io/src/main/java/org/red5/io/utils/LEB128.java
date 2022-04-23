/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2022 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.utils;

/**
 * This class encodes and decodes integers in the LEB128 compression format.
 * 
 * Reference examples: 
 * @see <a href="https://github.com/pion/rtp/blob/master/pkg/obu/leb128.go">leb128.go</a>
 * @see <a href="https://github.com/hathibelagal-dev/LEB128/blob/master/lib/leb128.dart">leb128.dart</a>
 * 
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
public final class LEB128 {

    public static final int SEVEN_LSB_BITMASK = 0b01111111;

    public static final int MSB_BITMASK = 0b10000000; // 0x80

    /**
     * Encodes an int into an LEB128 unsigned integer.
     * 
     * @param value integer to encode
     * @return unsigned integer in LEB128 format
     */
    public static int encode(int value) {
        int out = 0;
        while (true) {
            out |= (value & SEVEN_LSB_BITMASK);
            value >>= 7;
            if (value != 0) {
                out |= MSB_BITMASK;
                out <<= 8;
            } else {
                break;
            }
        }
        return out;
    }

    /**
     * Decodes an LEB128 unsigned integer into a regular int.
     * 
     * @param value unsigned integer in LEB128 format to decode
     * @return int
     */
    public static int decode(int value) {
        int out = 0;
        while (true) {
            out |= (value & SEVEN_LSB_BITMASK);
            value >>= 8;
            if (value == 0) {
                break;
            }
            out <<= 7;
        }
        return out;
    }

    public static void main(String[] args) {
        int[][] testData = { { 0, 0 }, { 5, 5 }, { 999999, 0xBF843D } };
        for (int i = 0; i < testData.length; i++) {
            int encoded = encode(testData[i][0]);
            if (encoded != testData[i][1]) {
                System.out.printf("Encode failed: %d expected %d%n", encoded, testData[i][1]);
            } else {
                System.out.printf("Encode success: %d decoded: %d%n", encoded, testData[i][0]);
            }
            int decoded = decode(encoded);
            if (decoded != testData[i][0]) {
                System.out.printf("Decode failed: %d expected %d%n", decoded, testData[i][0]);
            } else {
                System.out.printf("Decode success: %d encoded: %d%n", decoded, testData[i][1]);
            }
        }
    }

}