/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io;

/**
 * Constants found in FLV files / streams.
 *
 */
public interface IoConstants {
    /**
     * Video data
     */
    public static final byte TYPE_VIDEO = 0x09;

    /**
     * Audio data
     */
    public static final byte TYPE_AUDIO = 0x08;

    /**
     * Metadata
     */
    public static final byte TYPE_METADATA = 0x12;

    /**
     * Encryption
     */
    public static final byte TYPE_ENCRYPTED = 0x20;

    /**
     * Encrypted audio data
     */
    public static final byte TYPE_ENCRYPTED_AUDIO = TYPE_AUDIO + TYPE_ENCRYPTED;

    /**
     * Encrypted video data
     */
    public static final byte TYPE_ENCRYPTED_VIDEO = TYPE_VIDEO + TYPE_ENCRYPTED;

    /**
     * Encrypted meta data
     */
    public static final byte TYPE_ENCRYPTED_METADATA = TYPE_METADATA + TYPE_ENCRYPTED;

    /**
     * Mask sound type
     */
    public static final byte MASK_SOUND_TYPE = 0x01;

    /**
     * Mono mode
     */
    public static final byte FLAG_TYPE_MONO = 0x00;

    /**
     * Stereo mode
     */
    public static final byte FLAG_TYPE_STEREO = 0x01;

    /**
     * Mask sound size
     */
    public static final byte MASK_SOUND_SIZE = 0x02;

    /**
     * 8 bit flag size
     */
    public static final byte FLAG_SIZE_8_BIT = 0x00;

    /**
     * 16 bit flag size
     */
    public static final byte FLAG_SIZE_16_BIT = 0x01;

    /**
     * Mask sound rate
     */
    public static final byte MASK_SOUND_RATE = 0x0C;

    /**
     * 5.5 KHz rate flag
     */
    public static final byte FLAG_RATE_5_5_KHZ = 0x00;

    /**
     * 11 KHz rate flag
     */
    public static final byte FLAG_RATE_11_KHZ = 0x01;

    /**
     * 22 KHz rate flag
     */
    public static final byte FLAG_RATE_22_KHZ = 0x02;

    /**
     * 44 KHz rate flag
     */
    public static final byte FLAG_RATE_44_KHZ = 0x03;

    /**
     * 48 KHz rate flag
     */
    public static final byte FLAG_RATE_48_KHZ = 0x04;

    /**
     * Mask sound format (unsigned)
     */
    public static final int MASK_SOUND_FORMAT = 0xf0;

    /**
     * Mask video codec
     */
    public static final byte MASK_VIDEO_CODEC = 0x0f;

    /**
     * Video frametype flag
     */
    public static final int MASK_VIDEO_FRAMETYPE = 0xf0;

}
