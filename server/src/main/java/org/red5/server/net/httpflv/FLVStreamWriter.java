/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.httpflv;

import java.nio.ByteBuffer;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.io.IoConstants;
import org.red5.server.api.stream.IStreamPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for building FLV stream data for HTTP-FLV delivery.
 * Converts stream packets to FLV tag format suitable for chunked HTTP transfer.
 *
 * FLV Header format (13 bytes total):
 * - Signature: "FLV" (3 bytes)
 * - Version: 0x01 (1 byte)
 * - Flags: audio/video flags (1 byte)
 * - DataOffset: 0x00000009 (4 bytes, big-endian)
 * - PreviousTagSize0: 0x00000000 (4 bytes)
 *
 * FLV Tag format (11 bytes header + data + 4 bytes prev tag size):
 * - TagType: 0x08 (audio), 0x09 (video), 0x12 (metadata) (1 byte)
 * - DataSize: (3 bytes, big-endian)
 * - Timestamp: lower 24 bits (3 bytes, big-endian)
 * - TimestampExtended: upper 8 bits (1 byte)
 * - StreamID: always 0 (3 bytes)
 * - Data: [DataSize bytes]
 * - PreviousTagSize: 11 + DataSize (4 bytes, big-endian)
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class FLVStreamWriter {

    private static final Logger log = LoggerFactory.getLogger(FLVStreamWriter.class);

    /** FLV signature bytes "FLV" */
    private static final byte[] FLV_SIGNATURE = { 0x46, 0x4C, 0x56 };

    /** FLV version 1 */
    private static final byte FLV_VERSION = 0x01;

    /** FLV header flag for audio */
    private static final byte FLAG_AUDIO = 0x04;

    /** FLV header flag for video */
    private static final byte FLAG_VIDEO = 0x01;

    /** FLV header size (signature + version + flags + data offset) */
    private static final int FLV_HEADER_SIZE = 9;

    /** FLV tag header size */
    private static final int TAG_HEADER_SIZE = 11;

    /** Previous tag size field size */
    private static final int PREV_TAG_SIZE_LENGTH = 4;

    /**
     * Creates an FLV header with both audio and video flags set.
     *
     * @return byte array containing the FLV header (13 bytes)
     */
    public static byte[] createFLVHeader() {
        return createFLVHeader(true, true);
    }

    /**
     * Creates an FLV header with specified audio/video flags.
     *
     * @param hasAudio true if stream contains audio
     * @param hasVideo true if stream contains video
     * @return byte array containing the FLV header (13 bytes)
     */
    public static byte[] createFLVHeader(boolean hasAudio, boolean hasVideo) {
        // FLV header is 9 bytes + 4 bytes for previous tag size 0 = 13 bytes
        ByteBuffer header = ByteBuffer.allocate(FLV_HEADER_SIZE + PREV_TAG_SIZE_LENGTH);
        // Signature "FLV"
        header.put(FLV_SIGNATURE);
        // Version
        header.put(FLV_VERSION);
        // Flags (audio + video)
        byte flags = 0;
        if (hasAudio) {
            flags |= FLAG_AUDIO;
        }
        if (hasVideo) {
            flags |= FLAG_VIDEO;
        }
        header.put(flags);
        // Data offset (always 9 for FLV version 1)
        header.putInt(FLV_HEADER_SIZE);
        // Previous tag size 0 (first tag)
        header.putInt(0);
        return header.array();
    }

    /**
     * Converts a stream packet to FLV tag bytes.
     *
     * @param packet the stream packet to convert
     * @return byte array containing the FLV tag, or null if packet is invalid
     */
    public static byte[] packetToFLVTag(IStreamPacket packet) {
        if (packet == null) {
            log.debug("Null packet received");
            return null;
        }
        IoBuffer data = packet.getData();
        if (data == null) {
            log.debug("Packet has null data");
            return null;
        }
        byte dataType = packet.getDataType();
        int timestamp = packet.getTimestamp();
        // Get data bytes
        int dataSize = data.remaining();
        if (dataSize == 0) {
            log.trace("Packet has zero data size");
            return null;
        }
        return createFLVTag(dataType, timestamp, data, dataSize);
    }

    /**
     * Creates an FLV tag from raw components.
     *
     * @param dataType the tag type (audio, video, or metadata)
     * @param timestamp the timestamp in milliseconds
     * @param data the tag body data
     * @param dataSize the size of the data
     * @return byte array containing the complete FLV tag
     */
    public static byte[] createFLVTag(byte dataType, int timestamp, IoBuffer data, int dataSize) {
        // Total size: tag header (11) + data + previous tag size (4)
        int totalSize = TAG_HEADER_SIZE + dataSize + PREV_TAG_SIZE_LENGTH;
        ByteBuffer tag = ByteBuffer.allocate(totalSize);
        // Tag type (1 byte)
        tag.put(dataType);
        // Data size (3 bytes, big-endian)
        tag.put((byte) ((dataSize >> 16) & 0xFF));
        tag.put((byte) ((dataSize >> 8) & 0xFF));
        tag.put((byte) (dataSize & 0xFF));
        // Timestamp (3 bytes lower, 1 byte upper for extended)
        // Lower 24 bits
        tag.put((byte) ((timestamp >> 16) & 0xFF));
        tag.put((byte) ((timestamp >> 8) & 0xFF));
        tag.put((byte) (timestamp & 0xFF));
        // Upper 8 bits (extended timestamp)
        tag.put((byte) ((timestamp >> 24) & 0xFF));
        // Stream ID (always 0, 3 bytes)
        tag.put((byte) 0);
        tag.put((byte) 0);
        tag.put((byte) 0);
        // Tag body data
        byte[] bodyBytes = new byte[dataSize];
        // Save position to restore later
        int originalPosition = data.position();
        data.get(bodyBytes);
        // Restore position for potential reuse
        data.position(originalPosition);
        tag.put(bodyBytes);
        // Previous tag size (4 bytes, big-endian)
        int previousTagSize = TAG_HEADER_SIZE + dataSize;
        tag.putInt(previousTagSize);
        return tag.array();
    }

    /**
     * Creates an FLV tag from a ByteBuffer data source.
     *
     * @param dataType the tag type (audio, video, or metadata)
     * @param timestamp the timestamp in milliseconds
     * @param data the tag body data as ByteBuffer
     * @return byte array containing the complete FLV tag
     */
    public static byte[] createFLVTag(byte dataType, int timestamp, ByteBuffer data) {
        if (data == null) {
            return null;
        }
        int dataSize = data.remaining();
        if (dataSize == 0) {
            return null;
        }
        // Total size: tag header (11) + data + previous tag size (4)
        int totalSize = TAG_HEADER_SIZE + dataSize + PREV_TAG_SIZE_LENGTH;
        ByteBuffer tag = ByteBuffer.allocate(totalSize);
        // Tag type (1 byte)
        tag.put(dataType);
        // Data size (3 bytes, big-endian)
        tag.put((byte) ((dataSize >> 16) & 0xFF));
        tag.put((byte) ((dataSize >> 8) & 0xFF));
        tag.put((byte) (dataSize & 0xFF));
        // Timestamp (3 bytes lower, 1 byte upper for extended)
        tag.put((byte) ((timestamp >> 16) & 0xFF));
        tag.put((byte) ((timestamp >> 8) & 0xFF));
        tag.put((byte) (timestamp & 0xFF));
        tag.put((byte) ((timestamp >> 24) & 0xFF));
        // Stream ID (always 0, 3 bytes)
        tag.put((byte) 0);
        tag.put((byte) 0);
        tag.put((byte) 0);
        // Tag body data
        int originalPosition = data.position();
        tag.put(data);
        data.position(originalPosition);
        // Previous tag size (4 bytes, big-endian)
        int previousTagSize = TAG_HEADER_SIZE + dataSize;
        tag.putInt(previousTagSize);
        return tag.array();
    }

    /**
     * Creates an FLV tag from raw byte array data.
     *
     * @param dataType the tag type (audio, video, or metadata)
     * @param timestamp the timestamp in milliseconds
     * @param data the tag body data as byte array
     * @return byte array containing the complete FLV tag
     */
    public static byte[] createFLVTag(byte dataType, int timestamp, byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        int dataSize = data.length;
        // Total size: tag header (11) + data + previous tag size (4)
        int totalSize = TAG_HEADER_SIZE + dataSize + PREV_TAG_SIZE_LENGTH;
        ByteBuffer tag = ByteBuffer.allocate(totalSize);
        // Tag type (1 byte)
        tag.put(dataType);
        // Data size (3 bytes, big-endian)
        tag.put((byte) ((dataSize >> 16) & 0xFF));
        tag.put((byte) ((dataSize >> 8) & 0xFF));
        tag.put((byte) (dataSize & 0xFF));
        // Timestamp (3 bytes lower, 1 byte upper for extended)
        tag.put((byte) ((timestamp >> 16) & 0xFF));
        tag.put((byte) ((timestamp >> 8) & 0xFF));
        tag.put((byte) (timestamp & 0xFF));
        tag.put((byte) ((timestamp >> 24) & 0xFF));
        // Stream ID (always 0, 3 bytes)
        tag.put((byte) 0);
        tag.put((byte) 0);
        tag.put((byte) 0);
        // Tag body data
        tag.put(data);
        // Previous tag size (4 bytes, big-endian)
        int previousTagSize = TAG_HEADER_SIZE + dataSize;
        tag.putInt(previousTagSize);
        return tag.array();
    }

    /**
     * Checks if the data type is audio.
     *
     * @param dataType the data type byte
     * @return true if audio type
     */
    public static boolean isAudio(byte dataType) {
        return dataType == IoConstants.TYPE_AUDIO;
    }

    /**
     * Checks if the data type is video.
     *
     * @param dataType the data type byte
     * @return true if video type
     */
    public static boolean isVideo(byte dataType) {
        return dataType == IoConstants.TYPE_VIDEO;
    }

    /**
     * Checks if the data type is metadata.
     *
     * @param dataType the data type byte
     * @return true if metadata type
     */
    public static boolean isMetadata(byte dataType) {
        return dataType == IoConstants.TYPE_METADATA;
    }

    /**
     * Gets a descriptive name for the data type.
     *
     * @param dataType the data type byte
     * @return string name of the type
     */
    public static String getDataTypeName(byte dataType) {
        switch (dataType) {
            case IoConstants.TYPE_AUDIO:
                return "audio";
            case IoConstants.TYPE_VIDEO:
                return "video";
            case IoConstants.TYPE_METADATA:
                return "metadata";
            default:
                return "unknown(" + dataType + ")";
        }
    }

}
