package org.red5.server.net.mediabunny.fmp4;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Base class for write-only ISO BMFF boxes (ISO/IEC 14496-12). A box knows its payload size up front, so a whole tree is
 * written into a single buffer allocation.
 */
abstract class Box {

    private final byte[] type;

    Box(String type) {
        this.type = type.getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * @return payload size in bytes, excluding the 8-byte box header
     */
    abstract int payloadSize();

    abstract void writePayload(ByteBuffer buffer);

    /**
     * @return total box size in bytes, including the 8-byte box header
     */
    final int size() {
        return 8 + payloadSize();
    }

    final void write(ByteBuffer buffer) {
        buffer.putInt(size());
        buffer.put(type, 0, 4);
        writePayload(buffer);
    }

    /**
     * Writes version and flags for a full box.
     */
    static void putVersionFlags(ByteBuffer buffer, int version, int flags) {
        buffer.putInt((version << 24) | (flags & 0xFFFFFF));
    }

    /**
     * Serializes a sequence of top-level boxes into one array.
     */
    static byte[] serialize(Box... boxes) {
        int total = 0;
        for (Box box : boxes) {
            total += box.size();
        }
        ByteBuffer buffer = ByteBuffer.allocate(total);
        for (Box box : boxes) {
            box.write(buffer);
        }
        return buffer.array();
    }

}
