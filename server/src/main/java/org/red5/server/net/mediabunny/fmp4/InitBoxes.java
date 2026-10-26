package org.red5.server.net.mediabunny.fmp4;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Leaf boxes of an fMP4 initialization segment (ftyp + moov) - ISO/IEC 14496-12. Only the version 0 forms are written,
 * with zero creation/modification times and durations, as fragmented streams carry timing in the fragments.
 */
final class InitBoxes {

    // unity transformation matrix used by mvhd and tkhd
    private static final int[] MATRIX = { 0x00010000, 0, 0, 0, 0x00010000, 0, 0, 0, 0x40000000 };

    private InitBoxes() {
    }

    private static void putMatrix(ByteBuffer buffer) {
        for (int value : MATRIX) {
            buffer.putInt(value);
        }
    }

    /**
     * File Type Box (ftyp) - Section 4.3.
     */
    static final class FtypBox extends Box {

        private final String majorBrand;

        private final long minorVersion;

        private final List<String> compatibleBrands;

        FtypBox(String majorBrand, long minorVersion, List<String> compatibleBrands) {
            super("ftyp");
            this.majorBrand = majorBrand;
            this.minorVersion = minorVersion;
            this.compatibleBrands = compatibleBrands;
        }

        @Override
        int payloadSize() {
            return 4 + 4 + compatibleBrands.size() * 4;
        }

        @Override
        void writePayload(ByteBuffer buffer) {
            buffer.put(majorBrand.getBytes(StandardCharsets.US_ASCII), 0, 4);
            buffer.putInt((int) minorVersion);
            for (String brand : compatibleBrands) {
                buffer.put(brand.getBytes(StandardCharsets.US_ASCII), 0, 4);
            }
        }

    }

    /**
     * Movie Header Box (mvhd) - Section 8.2.2.
     */
    static final class MvhdBox extends Box {

        private final long timescale;

        private final long nextTrackId;

        MvhdBox(long timescale, long nextTrackId) {
            super("mvhd");
            this.timescale = timescale;
            this.nextTrackId = nextTrackId;
        }

        @Override
        int payloadSize() {
            return 100;
        }

        @Override
        void writePayload(ByteBuffer buffer) {
            putVersionFlags(buffer, 0, 0);
            buffer.putInt(0); // creation_time
            buffer.putInt(0); // modification_time
            buffer.putInt((int) timescale);
            buffer.putInt(0); // duration
            buffer.putInt(0x00010000); // rate 1.0
            buffer.putShort((short) 0x0100); // volume 1.0
            buffer.putShort((short) 0); // reserved
            buffer.putInt(0); // reserved
            buffer.putInt(0); // reserved
            putMatrix(buffer);
            for (int i = 0; i < 6; i++) {
                buffer.putInt(0); // pre_defined
            }
            buffer.putInt((int) nextTrackId);
        }

    }

    /**
     * Track Header Box (tkhd) - Section 8.3.2. Flags mark the track enabled, in movie and in preview.
     */
    static final class TkhdBox extends Box {

        private final long trackId;

        private final int volume;

        private final int width;

        private final int height;

        /**
         * @param trackId track id
         * @param volume 8.8 fixed point volume, 0 for video
         * @param width width in pixels, 0 for audio
         * @param height height in pixels, 0 for audio
         */
        TkhdBox(long trackId, int volume, int width, int height) {
            super("tkhd");
            this.trackId = trackId;
            this.volume = volume;
            this.width = width;
            this.height = height;
        }

        @Override
        int payloadSize() {
            return 84;
        }

        @Override
        void writePayload(ByteBuffer buffer) {
            putVersionFlags(buffer, 0, 0x000007);
            buffer.putInt(0); // creation_time
            buffer.putInt(0); // modification_time
            buffer.putInt((int) trackId);
            buffer.putInt(0); // reserved
            buffer.putInt(0); // duration
            buffer.putInt(0); // reserved
            buffer.putInt(0); // reserved
            buffer.putShort((short) 0); // layer
            buffer.putShort((short) 0); // alternate_group
            buffer.putShort((short) volume);
            buffer.putShort((short) 0); // reserved
            putMatrix(buffer);
            buffer.putInt(width << 16); // 16.16 fixed point
            buffer.putInt(height << 16); // 16.16 fixed point
        }

    }

    /**
     * Media Header Box (mdhd) - Section 8.4.2. Language is 'und'.
     */
    static final class MdhdBox extends Box {

        private final long timescale;

        MdhdBox(long timescale) {
            super("mdhd");
            this.timescale = timescale;
        }

        @Override
        int payloadSize() {
            return 24;
        }

        @Override
        void writePayload(ByteBuffer buffer) {
            putVersionFlags(buffer, 0, 0);
            buffer.putInt(0); // creation_time
            buffer.putInt(0); // modification_time
            buffer.putInt((int) timescale);
            buffer.putInt(0); // duration
            buffer.putShort((short) 0x55C4); // language 'und'
            buffer.putShort((short) 0); // pre_defined
        }

    }

    /**
     * Handler Reference Box (hdlr) - Section 8.4.3.
     */
    static final class HdlrBox extends Box {

        private final String handlerType;

        private final byte[] name;

        HdlrBox(String handlerType, String name) {
            super("hdlr");
            this.handlerType = handlerType;
            this.name = name.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        int payloadSize() {
            return 24 + name.length + 1;
        }

        @Override
        void writePayload(ByteBuffer buffer) {
            putVersionFlags(buffer, 0, 0);
            buffer.putInt(0); // pre_defined
            buffer.put(handlerType.getBytes(StandardCharsets.US_ASCII), 0, 4);
            buffer.putInt(0); // reserved
            buffer.putInt(0); // reserved
            buffer.putInt(0); // reserved
            buffer.put(name);
            buffer.put((byte) 0); // null terminator
        }

    }

    /**
     * Video Media Header Box (vmhd) - Section 12.1.2.
     */
    static final class VmhdBox extends Box {

        VmhdBox() {
            super("vmhd");
        }

        @Override
        int payloadSize() {
            return 12;
        }

        @Override
        void writePayload(ByteBuffer buffer) {
            putVersionFlags(buffer, 0, 1); // flags must be 1
            buffer.putShort((short) 0); // graphicsmode
            buffer.putShort((short) 0); // opcolor[0]
            buffer.putShort((short) 0); // opcolor[1]
            buffer.putShort((short) 0); // opcolor[2]
        }

    }

    /**
     * Sound Media Header Box (smhd) - Section 12.2.2.
     */
    static final class SmhdBox extends Box {

        SmhdBox() {
            super("smhd");
        }

        @Override
        int payloadSize() {
            return 8;
        }

        @Override
        void writePayload(ByteBuffer buffer) {
            putVersionFlags(buffer, 0, 0);
            buffer.putShort((short) 0); // balance
            buffer.putShort((short) 0); // reserved
        }

    }

    /**
     * Data Reference Box (dref) - Section 8.7.2, holding one self-contained 'url ' entry.
     */
    static final class DrefBox extends Box {

        DrefBox() {
            super("dref");
        }

        @Override
        int payloadSize() {
            return 4 + 4 + 12;
        }

        @Override
        void writePayload(ByteBuffer buffer) {
            putVersionFlags(buffer, 0, 0);
            buffer.putInt(1); // entry_count
            buffer.putInt(12); // url box size
            buffer.put("url ".getBytes(StandardCharsets.US_ASCII));
            putVersionFlags(buffer, 0, 1); // media data is in the same file
        }

    }

    /**
     * Sample table box with no entries (stts, stsc, stsz, stco); fragmented files describe samples in trun instead.
     */
    static final class EmptyTableBox extends Box {

        private final int payloadSize;

        private EmptyTableBox(String type, int payloadSize) {
            super(type);
            this.payloadSize = payloadSize;
        }

        /**
         * @return stts, stsc, stsz and stco boxes in stbl order
         */
        static Box[] all() {
            // version/flags + entry_count; stsz has an extra sample_size field
            return new Box[] { new EmptyTableBox("stts", 8), new EmptyTableBox("stsc", 8), new EmptyTableBox("stsz", 12), new EmptyTableBox("stco", 8) };
        }

        @Override
        int payloadSize() {
            return payloadSize;
        }

        @Override
        void writePayload(ByteBuffer buffer) {
            buffer.put(new byte[payloadSize]);
        }

    }

    /**
     * Sample Description Box (stsd) - Section 8.5.2, holding a single sample entry.
     */
    static final class StsdBox extends Box {

        private final Box entry;

        StsdBox(Box entry) {
            super("stsd");
            this.entry = entry;
        }

        @Override
        int payloadSize() {
            return 4 + 4 + entry.size();
        }

        @Override
        void writePayload(ByteBuffer buffer) {
            putVersionFlags(buffer, 0, 0);
            buffer.putInt(1); // entry_count
            entry.write(buffer);
        }

    }

    /**
     * Visual sample entry - Section 12.1.3, followed by the codec configuration box (e.g. avcC).
     */
    static final class VisualSampleEntry extends Box {

        private final int width;

        private final int height;

        private final byte[] codecConfig;

        VisualSampleEntry(String codecFourcc, int width, int height, byte[] codecConfig) {
            super(codecFourcc);
            this.width = width;
            this.height = height;
            this.codecConfig = codecConfig;
        }

        @Override
        int payloadSize() {
            return 8 + 70 + (codecConfig != null ? codecConfig.length : 0);
        }

        @Override
        void writePayload(ByteBuffer buffer) {
            buffer.put(new byte[6]); // reserved
            buffer.putShort((short) 1); // data_reference_index
            buffer.putShort((short) 0); // pre_defined
            buffer.putShort((short) 0); // reserved
            buffer.put(new byte[12]); // pre_defined[3]
            buffer.putShort((short) width);
            buffer.putShort((short) height);
            buffer.putInt(0x00480000); // horizresolution 72 dpi
            buffer.putInt(0x00480000); // vertresolution 72 dpi
            buffer.putInt(0); // reserved
            buffer.putShort((short) 1); // frame_count
            buffer.put(new byte[32]); // compressorname, empty
            buffer.putShort((short) 0x0018); // depth
            buffer.putShort((short) -1); // pre_defined
            if (codecConfig != null) {
                buffer.put(codecConfig);
            }
        }

    }

    /**
     * Audio sample entry - Section 12.2.3, followed by the codec configuration box (e.g. esds).
     */
    static final class AudioSampleEntry extends Box {

        private final int channelCount;

        private final int sampleSize;

        private final int sampleRate;

        private final byte[] codecConfig;

        AudioSampleEntry(String codecFourcc, int channelCount, int sampleRate, int sampleSize, byte[] codecConfig) {
            super(codecFourcc);
            this.channelCount = channelCount;
            this.sampleRate = sampleRate;
            this.sampleSize = sampleSize;
            this.codecConfig = codecConfig;
        }

        @Override
        int payloadSize() {
            return 8 + 20 + (codecConfig != null ? codecConfig.length : 0);
        }

        @Override
        void writePayload(ByteBuffer buffer) {
            buffer.put(new byte[6]); // reserved
            buffer.putShort((short) 1); // data_reference_index
            buffer.putInt(0); // reserved
            buffer.putInt(0); // reserved
            buffer.putShort((short) channelCount);
            buffer.putShort((short) sampleSize);
            buffer.putShort((short) 0); // pre_defined
            buffer.putShort((short) 0); // reserved
            buffer.putInt(sampleRate << 16); // 16.16 fixed point
            if (codecConfig != null) {
                buffer.put(codecConfig);
            }
        }

    }

    /**
     * Track Extends Box (trex) - Section 8.8.3, with sample description index 1 and zero defaults.
     */
    static final class TrexBox extends Box {

        private final long trackId;

        TrexBox(long trackId) {
            super("trex");
            this.trackId = trackId;
        }

        @Override
        int payloadSize() {
            return 24;
        }

        @Override
        void writePayload(ByteBuffer buffer) {
            putVersionFlags(buffer, 0, 0);
            buffer.putInt((int) trackId);
            buffer.putInt(1); // default_sample_description_index
            buffer.putInt(0); // default_sample_duration
            buffer.putInt(0); // default_sample_size
            buffer.putInt(0); // default_sample_flags
        }

    }

}
