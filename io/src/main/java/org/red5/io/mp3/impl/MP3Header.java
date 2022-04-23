/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2016 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.mp3.impl;

/**
 * Header of a MP3 frame.
 * 
 * @author The Red5 Project
 * @author Joachim Bauch (jojo@struktur.de)
 * @see <a href="http://mpgedit.org/mpgedit/mpeg_format/mpeghdr.htm">File format</a>
 */

public class MP3Header {
    /**
     * MP3 bitrates
     */
    private static final int[][] BITRATES = { { 0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448, -1 }, { 0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384, -1 }, { 0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, -1 }, { 0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256, -1 }, { 0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, -1 }, };

    /**
     * Sample rates
     */
    private static final int[][] SAMPLERATES = {
            // Version 2.5
            { 11025, 12000, 8000, -1 },
            // Unknown version
            { -1, -1, -1, -1 },
            // Version 2
            { 22050, 24000, 16000, -1 },
            // Version 1
            { 44100, 48000, 32000, -1 }, };

    /**
     * Frame sync data
     */
    private int data;

    /**
     * Audio version id
     */
    private byte audioVersionId;

    /**
     * Layer description
     */
    private byte layerDescription;

    /**
     * Protection bit
     */
    private boolean protectionBit;

    /**
     * Bitrate used (index in array of bitrates)
     */
    private byte bitRateIndex;

    /**
     * Sampling rate used (index in array of sample rates)
     */
    private byte samplingRateIndex;

    /**
     * Padding bit
     */
    private boolean paddingBit;

    /**
     * Channel mode
     */
    private byte channelMode;

    /**
     * Creates MP3 header from frame sync value
     * 
     * @param data
     *            Frame sync data
     * @throws Exception
     *             On invalid frame synchronization
     */
    public MP3Header(int data) throws Exception {
        if ((data & 0xffe00000) == 0xffe00000) {
            this.data = data;
            // strip signed bit
            data &= 0x1fffff;
            audioVersionId = (byte) ((data >> 19) & 3);
            layerDescription = (byte) ((data >> 17) & 3);
            protectionBit = ((data >> 16) & 1) == 0;
            bitRateIndex = (byte) ((data >> 12) & 15);
            samplingRateIndex = (byte) ((data >> 10) & 3);
            paddingBit = ((data >> 9) & 1) != 0;
            channelMode = (byte) ((data >> 6) & 3);
        } else {
            throw new Exception("Invalid frame sync word");
        }
    }

    /**
     * Getter for frame sync word data
     *
     * @return Frame sync word data
     */
    public int getData() {
        return data;
    }

    /**
     * Whether stereo playback mode is used
     *
     * @return <code>true</code> if stereo mode is used, <code>false</code> otherwise
     */
    public boolean isStereo() {
        return (channelMode != 3);
    }

    /**
     * Whether MP3 has protection bit
     *
     * @return <code>true</code> if MP3 has protection bit, <code>false</code> otherwise
     */
    public boolean isProtected() {
        return protectionBit;
    }

    /**
     * Getter for bitrate
     *
     * @return File bitrate
     */
    public int getBitRate() {
        int result;
        switch (audioVersionId) {
            case 1:
                // Unknown
                return -1;
            case 0:
            case 2:
                // Version 2 or 2.5
                if (layerDescription == 3) {
                    // Layer 1
                    result = BITRATES[3][bitRateIndex];
                } else if (layerDescription == 2 || layerDescription == 1) {
                    // Layer 2 or 3
                    result = BITRATES[4][bitRateIndex];
                } else {
                    // Unknown layer
                    return -1;
                }
                break;
            case 3:
                // Version 1
                if (layerDescription == 3) {
                    // Layer 1
                    result = BITRATES[0][bitRateIndex];
                } else if (layerDescription == 2) {
                    // Layer 2
                    result = BITRATES[1][bitRateIndex];
                } else if (layerDescription == 1) {
                    // Layer 3
                    result = BITRATES[2][bitRateIndex];
                } else {
                    // Unknown layer
                    return -1;
                }
                break;
            default:
                // Unknown version
                return -1;
        }
        return result * 1000;
    }

    /**
     * Getter for sample rate
     *
     * @return Sampling rate
     */
    public int getSampleRate() {
        return SAMPLERATES[audioVersionId][samplingRateIndex];
    }

    /**
     * Calculate the size of a MP3 frame for this header.
     * 
     * @return size of the frame including the header
     */
    public int frameSize() {
        switch (layerDescription) {
            case 3:
                // Layer 1
                return (12 * getBitRate() / getSampleRate() + (paddingBit ? 1 : 0)) * 4;
            case 2:
            case 1:
                // Layer 2 and 3
                if (audioVersionId == 3) {
                    // MPEG 1
                    return 144 * getBitRate() / getSampleRate() + (paddingBit ? 1 : 0);
                } else {
                    // MPEG 2 or 2.5
                    return 72 * getBitRate() / getSampleRate() + (paddingBit ? 1 : 0);
                }
            default:
                // Unknown
                return -1;
        }
    }

    /**
     * Return the duration of the frame for this header.
     * 
     * @return The duration in milliseconds
     */
    public double frameDuration() {
        switch (layerDescription) {
            case 3:
                // Layer 1
                return 384 / (getSampleRate() * 0.001);
            case 2:
            case 1:
                if (audioVersionId == 3) {
                    // MPEG 1, Layer 2 and 3
                    return 1152 / (getSampleRate() * 0.001);
                } else {
                    // MPEG 2 or 2.5, Layer 2 and 3
                    return 576 / (getSampleRate() * 0.001);
                }
            default:
                // Unknown
                return -1;
        }
    }

}
