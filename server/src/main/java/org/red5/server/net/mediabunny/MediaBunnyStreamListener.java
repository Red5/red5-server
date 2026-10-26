package org.red5.server.net.mediabunny;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.codec.AudioCodec;
import org.red5.codec.VideoCodec;
import org.red5.codec.VideoFrameType;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.api.stream.IStreamListener;
import org.red5.server.api.stream.IStreamPacket;
import org.red5.codec.IAudioStreamCodec;
import org.red5.codec.IStreamCodecInfo;
import org.red5.codec.IVideoStreamCodec;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.net.rtmp.event.VideoData;
import org.red5.codec.AudioPacketType;
import org.red5.codec.VideoPacketType;
import org.red5.server.net.mediabunny.fmp4.SampleFlags;
import org.red5.server.net.mediabunny.fmp4.Fmp4FragmentBuilder;
import org.red5.server.net.mediabunny.fmp4.Fmp4InitSegmentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Converts AVC/AAC RTMP packets into CMAF fMP4 fragments.
 */
public class MediaBunnyStreamListener implements IStreamListener {

    private static final Logger log = LoggerFactory.getLogger(MediaBunnyStreamListener.class);

    private static final long VIDEO_TIMESCALE = 90000;

    private static final long DEFAULT_VIDEO_DURATION = 3000;

    @SuppressWarnings("unused")
    private static final long DEFAULT_AUDIO_DURATION = 1024;

    private static final int AAC_FRAME_SAMPLES = 1024;

    private static final long VIDEO_FRAGMENT_TARGET = 9000;

    private static final long AUDIO_FRAGMENT_TARGET = 4800;

    private static final int AUDIO_JITTER_FRAMES = 6;

    private final String streamKey;

    private final MediaBunnyStreamRegistry registry;

    private final Fmp4FragmentBuilder fragmentBuilder = new Fmp4FragmentBuilder();

    private byte[] avcConfig;

    private byte[] ascConfig;

    private byte[] avcCBox;

    private byte[] esdsBox;

    private int width;

    private int height;

    private int audioSampleRate;

    private int audioChannels;

    private boolean initSegmentSent;

    private boolean audioSeen;

    private boolean videoSeen;

    private boolean expectedAudio;

    private boolean expectedVideo;

    private boolean videoKeyframeSeen;

    private long lastVideoTimestamp = -1;

    private long lastAudioTimestamp = -1;

    private long fragmentSequence = 0;

    private long videoDecodeTime = 0;

    private long audioDecodeTime = 0;

    private final TrackBuffer videoBuffer = new TrackBuffer();

    private final TrackBuffer audioBuffer = new TrackBuffer();

    private boolean audioFragmentSent;

    MediaBunnyStreamListener(String streamKey, MediaBunnyStreamRegistry registry) {
        log.debug("Creating MediaBunnyStreamListener for stream: {}", streamKey);
        this.streamKey = streamKey;
        this.registry = registry;
    }

    /**
     * Sets which tracks are expected to be present in the stream, used to decide when the output is complete.
     *
     * @param expectVideo
     *            whether a video track is expected
     * @param expectAudio
     *            whether an audio track is expected
     */
    public void setExpectedTracks(boolean expectVideo, boolean expectAudio) {
        this.expectedVideo = expectVideo;
        this.expectedAudio = expectAudio;
        log.info("Expected tracks for stream {} video={} audio={}", streamKey, expectVideo, expectAudio);
    }

    /**
     * Seeds the expected tracks and pushes any available decoder configuration data from an existing stream's codec info, so
     * late-joining consumers of this listener receive the required configuration before media data.
     *
     * @param codecInfo
     *            the stream's codec information to seed from, or {@code null} if none is available
     */
    public void seedFromCodecInfo(IStreamCodecInfo codecInfo) {
        if (codecInfo == null) {
            log.debug("No codec info available to seed for stream {}", streamKey);
            return;
        }
        setExpectedTracks(codecInfo.hasVideo(), codecInfo.hasAudio());
        IVideoStreamCodec videoCodec = codecInfo.getVideoCodec();
        if (videoCodec != null) {
            IoBuffer config = videoCodec.getDecoderConfiguration();
            if (config != null && config.remaining() > 0) {
                log.info("Seeding video codec config for stream {} codec={}", streamKey, videoCodec.getName());
                handleVideo(new VideoData(config.asReadOnlyBuffer()));
            } else {
                log.debug("Video codec config not available for stream {} codec={}", streamKey, videoCodec.getName());
            }
        } else {
            log.debug("No video codec present in codec info for stream {}", streamKey);
        }
        IAudioStreamCodec audioCodec = codecInfo.getAudioCodec();
        if (audioCodec != null) {
            IoBuffer config = audioCodec.getDecoderConfiguration();
            if (config != null && config.remaining() > 0) {
                log.info("Seeding audio codec config for stream {} codec={}", streamKey, audioCodec.getName());
                handleAudio(new AudioData(config.asReadOnlyBuffer()));
            } else {
                log.debug("Audio codec config not available for stream {} codec={}", streamKey, audioCodec.getName());
            }
        } else {
            log.debug("No audio codec present in codec info for stream {}", streamKey);
        }
    }

    @Override
    public void packetReceived(IBroadcastStream stream, IStreamPacket packet) {
        log.debug("Packet received for stream {}: {}", streamKey, packet.getClass().getSimpleName());
        if (packet instanceof VideoData) {
            handleVideo((VideoData) packet);
        } else if (packet instanceof AudioData) {
            handleAudio((AudioData) packet);
        }
    }

    @Override
    public void onClosed() {
        log.info("Closing MediaBunnyStreamListener for stream {}", streamKey);
        flushVideoBuffer();
        flushAudioBuffer();
        registry.onStreamClosed(streamKey);
    }

    private void handleVideo(VideoData video) {
        if (video.getCodecId() != VideoCodec.AVC.getId()) {
            if (log.isDebugEnabled()) {
                log.debug("Ignoring non-AVC video packet for stream {} codecId={}", streamKey, video.getCodecId());
            }
            return;
        }
        videoSeen = true;
        byte[] payload = toBytes(video.getData());
        if (payload.length < 5) {
            return;
        }
        VideoPacketType packetType = video.getVideoPacketType();
        if (packetType == VideoPacketType.SequenceStart) {
            log.info("Received AVC sequence start for stream {} codecId={} payloadBytes={}", streamKey, video.getCodecId(), payload.length);
            avcConfig = Arrays.copyOfRange(payload, 5, payload.length);
            log.info("AVC config prefix for stream {}: {}", streamKey, hexPrefix(avcConfig, 32));
            avcCBox = buildBox("avcC", avcConfig);
            int[] dimensions = parseAvcDimensions(avcConfig);
            if (dimensions != null) {
                width = dimensions[0];
                height = dimensions[1];
            } else {
                width = 640;
                height = 360;
                log.warn("AVC SPS dimensions not found, defaulting to {}x{}", width, height);
            }
            trySendInitSegment();
            return;
        }
        if (packetType != VideoPacketType.CodedFrames && packetType != VideoPacketType.CodedFramesX) {
            return;
        }
        if (!initSegmentSent) {
            trySendInitSegment();
            if (!initSegmentSent) {
                return;
            }
        }
        if (!videoKeyframeSeen && video.getFrameType() != VideoFrameType.KEYFRAME) {
            // Drop pre-keyframe samples to satisfy decoder expectations
            return;
        }
        long timestamp = video.getTimestamp();
        long duration = computeDuration(timestamp, lastVideoTimestamp, DEFAULT_VIDEO_DURATION, VIDEO_TIMESCALE);
        lastVideoTimestamp = timestamp;
        long compositionOffset = packetType == VideoPacketType.CodedFrames ? toTimescale(parseCompositionTime(payload), VIDEO_TIMESCALE) : 0;

        byte[] sampleData = Arrays.copyOfRange(payload, 5, payload.length);
        SampleFlags flags = video.getFrameType() == VideoFrameType.KEYFRAME ? SampleFlags.createSyncSampleFlags() : SampleFlags.createNonSyncSampleFlags();

        if (video.getFrameType() == VideoFrameType.KEYFRAME) {
            if (!videoKeyframeSeen) {
                // Reset timing on first keyframe to align fragment with decoder expectations
                videoKeyframeSeen = true;
                videoDecodeTime = 0;
                videoBuffer.reset();
                fragmentSequence = 0;
                // Align audio start with video start
                audioDecodeTime = 0;
                audioBuffer.reset();
                audioFragmentSent = false;
            } else if (!videoBuffer.samples.isEmpty()) {
                flushVideoBuffer();
            }
            videoBuffer.startsWithKeyframe = true;
        } else if (!videoKeyframeSeen) {
            return;
        }
        addSample(videoBuffer, sampleData, duration, flags, compositionOffset, videoDecodeTime);
        videoDecodeTime += duration;

        if (videoBuffer.bufferedDuration >= VIDEO_FRAGMENT_TARGET) {
            flushVideoBuffer();
        }
    }

    private void handleAudio(AudioData audio) {
        if (audio.getCodecId() != AudioCodec.AAC.getId()) {
            if (log.isDebugEnabled()) {
                log.debug("Ignoring non-AAC audio packet for stream {} codecId={}", streamKey, audio.getCodecId());
            }
            return;
        }
        audioSeen = true;
        byte[] payload = toBytes(audio.getData());
        if (payload.length < 2) {
            return;
        }
        AudioPacketType packetType = audio.getPacketType();
        if (log.isDebugEnabled()) {
            log.debug("Audio packet type for stream {}: {}", streamKey, packetType);
        }
        if (packetType == AudioPacketType.SequenceStart) {
            log.info("Received AAC sequence start for stream {} codecId={} payloadBytes={}", streamKey, audio.getCodecId(), payload.length);
            ascConfig = Arrays.copyOfRange(payload, 2, payload.length);
            log.info("AAC config prefix for stream {}: {}", streamKey, hexPrefix(ascConfig, 16));
            AudioSpecificConfig asc = parseAudioSpecificConfig(ascConfig);
            audioSampleRate = asc.sampleRate;
            audioChannels = asc.channelConfig;
            esdsBox = buildEsdsBox(ascConfig);
            trySendInitSegment();
            return;
        }
        if (expectedVideo && !videoKeyframeSeen) {
            // Hold audio samples until we have a video keyframe to align against
            return;
        }
        if (packetType != AudioPacketType.CodedFrames) {
            return;
        }
        if (!initSegmentSent) {
            trySendInitSegment();
            if (!initSegmentSent) {
                return;
            }
        }
        long timestamp = audio.getTimestamp();
        long timescale = audioSampleRate > 0 ? audioSampleRate : 48000;
        long duration = computeAacDuration(timestamp, lastAudioTimestamp, timescale);
        lastAudioTimestamp = timestamp;
        byte[] sampleData = Arrays.copyOfRange(payload, 2, payload.length);
        SampleFlags flags = SampleFlags.createSyncSampleFlags();

        addSample(audioBuffer, sampleData, duration, flags, null, audioDecodeTime);
        audioDecodeTime += duration;

        long target = AUDIO_FRAGMENT_TARGET;
        if (audioSampleRate > 0) {
            target = Math.min(AAC_FRAME_SAMPLES * 5L, AUDIO_FRAGMENT_TARGET);
        }
        if (log.isDebugEnabled()) {
            log.debug("Audio buffer for stream {} samples={} bufferedDuration={} target={}", streamKey, audioBuffer.samples.size(), audioBuffer.bufferedDuration, target);
        }
        if (!audioFragmentSent) {
            if (audioBuffer.samples.size() >= AUDIO_JITTER_FRAMES) {
                flushAudioBuffer();
                audioFragmentSent = true;
                return;
            } else {
                return;
            }
        }
        if (audioBuffer.bufferedDuration >= target) {
            flushAudioBuffer();
            audioFragmentSent = true;
        }
    }

    private void trySendInitSegment() {
        if (initSegmentSent) {
            return;
        }
        boolean videoReady = avcCBox != null && width > 0 && height > 0;
        boolean audioReady = esdsBox != null && audioSampleRate > 0 && audioChannels > 0;
        if (expectedVideo && !videoReady) {
            return;
        }
        if (expectedAudio && !audioReady) {
            return;
        }
        if (videoSeen && !videoReady) {
            return;
        }
        if (audioSeen && !audioReady) {
            return;
        }
        if (!videoReady && !audioReady) {
            return;
        }
        log.info("Building init segment for stream {} videoReady={} audioReady={} width={} height={} audioSampleRate={} audioChannels={}", streamKey, videoReady, audioReady, width, height, audioSampleRate, audioChannels);
        Fmp4InitSegmentBuilder builder = new Fmp4InitSegmentBuilder();
        if (videoReady) {
            builder.addVideoTrack(new Fmp4InitSegmentBuilder.VideoTrackConfig(1, VIDEO_TIMESCALE, "avc1", avcCBox, width, height));
        }
        if (audioReady) {
            builder.addAudioTrack(new Fmp4InitSegmentBuilder.AudioTrackConfig(2, audioSampleRate, "mp4a", esdsBox, audioChannels, audioSampleRate, 16));
        }
        try {
            byte[] initSegment = builder.build();
            log.info("Init segment built for stream {} ({} bytes) prefix={}", streamKey, initSegment.length, hexPrefix(initSegment, 24));
            initSegmentSent = true;
            registry.onInitSegment(streamKey, initSegment);
        } catch (Exception e) {
            log.warn("Failed to build init segment for {}", streamKey, e);
        }
    }

    private static byte[] toBytes(IoBuffer buffer) {
        if (buffer == null) {
            return new byte[0];
        }
        byte[] data = new byte[buffer.remaining()];
        buffer.mark();
        buffer.get(data);
        buffer.reset();
        return data;
    }

    private static long computeDuration(long timestamp, long lastTimestamp, long defaultDuration, long timescale) {
        if (lastTimestamp >= 0 && timestamp > lastTimestamp) {
            long deltaMs = timestamp - lastTimestamp;
            long duration = toTimescale(deltaMs, timescale);
            return duration > 0 ? duration : defaultDuration;
        }
        return defaultDuration;
    }

    private static long computeAacDuration(long timestamp, long lastTimestamp, long timescale) {
        if (lastTimestamp >= 0 && timestamp > lastTimestamp) {
            long deltaMs = timestamp - lastTimestamp;
            double samplesExact = (deltaMs * (double) timescale) / 1000.0;
            long frames = Math.max(1, Math.round(samplesExact / AAC_FRAME_SAMPLES));
            long duration = frames * AAC_FRAME_SAMPLES;
            return Math.min(duration, AAC_FRAME_SAMPLES * 6L);
        }
        return AAC_FRAME_SAMPLES;
    }

    private static long toTimescale(long ms, long timescale) {
        return (ms * timescale) / 1000;
    }

    private void addSample(TrackBuffer buffer, byte[] data, long duration, SampleFlags flags, Long cto, long decodeTime) {
        if (buffer.samples.isEmpty()) {
            buffer.fragmentStartDecodeTime = decodeTime;
        }
        buffer.media.write(data, 0, data.length);
        buffer.samples.add(new Fmp4FragmentBuilder.SampleData(duration, data.length, flags, cto));
        buffer.bufferedDuration += duration;
    }

    private void flushVideoBuffer() {
        flushBuffer(videoBuffer, ++fragmentSequence, 1, true);
    }

    private void flushAudioBuffer() {
        flushBuffer(audioBuffer, ++fragmentSequence, 2, false);
    }

    private void flushBuffer(TrackBuffer buffer, long sequence, long trackId, boolean video) {
        if (buffer.samples.isEmpty()) {
            return;
        }
        boolean keyframe = buffer.startsWithKeyframe;
        Fmp4FragmentBuilder.FragmentConfig config = new Fmp4FragmentBuilder.FragmentConfig().setSequenceNumber(sequence).setTrackId(trackId).setBaseDecodeTime(buffer.fragmentStartDecodeTime).setMediaData(buffer.media.toByteArray()).setSamples(new ArrayList<>(buffer.samples));
        byte[] fragment = fragmentBuilder.buildFragment(config);
        if (log.isDebugEnabled()) {
            log.debug("Built {} fragment for stream {} ({} bytes, keyframe={})", video ? "VIDEO" : "AUDIO", streamKey, fragment.length, keyframe);
        }
        if (keyframe && video) {
            registry.onKeyframeFragment(streamKey, fragment);
        } else {
            registry.onFragment(streamKey, fragment);
        }
        buffer.reset();
    }

    private static int parseCompositionTime(byte[] payload) {
        int value = ((payload[2] & 0xff) << 16) | ((payload[3] & 0xff) << 8) | (payload[4] & 0xff);
        if ((value & 0x00800000) != 0) {
            value |= 0xff000000;
        }
        return value;
    }

    private static byte[] buildBox(String type, byte[] payload) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        int size = 8 + payload.length;
        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.putInt(size);
        buffer.put(typeBytes, 0, 4);
        buffer.put(payload);
        return buffer.array();
    }

    private static byte[] buildEsdsBox(byte[] asc) {
        byte[] decSpecific = buildDescriptor((byte) 0x05, asc);
        byte[] decoderConfig = buildDecoderConfig(decSpecific);
        byte[] slConfig = buildDescriptor((byte) 0x06, new byte[] { 0x02 });

        byte[] esPayload = concat(new byte[] { 0x00, 0x02, 0x00 }, // ES_ID=2, flags=0
                decoderConfig, slConfig);
        byte[] esDescriptor = buildDescriptor((byte) 0x03, esPayload);

        byte[] fullBox = concat(new byte[] { 0, 0, 0, 0 }, esDescriptor);
        return buildBox("esds", fullBox);
    }

    private static String hexPrefix(byte[] data, int maxBytes) {
        if (data == null || data.length == 0) {
            return "<empty>";
        }
        int limit = Math.min(data.length, maxBytes);
        StringBuilder sb = new StringBuilder(limit * 2 + 6);
        for (int i = 0; i < limit; i++) {
            sb.append(String.format("%02x", data[i]));
        }
        if (data.length > limit) {
            sb.append("...");
        }
        return sb.toString();
    }

    private static byte[] buildDecoderConfig(byte[] decSpecific) {
        byte[] header = new byte[] { 0x40, // object type indication (MPEG-4 Audio)
                0x15, // stream type (audio) << 2 | 1
                0x00, 0x00, 0x00, // buffer size DB
                0x00, 0x00, 0x00, 0x00, // max bitrate
                0x00, 0x00, 0x00, 0x00 // avg bitrate
        };
        byte[] payload = concat(header, decSpecific);
        return buildDescriptor((byte) 0x04, payload);
    }

    private static byte[] buildDescriptor(byte tag, byte[] payload) {
        byte[] size = encodeDescriptorSize(payload.length);
        return concat(new byte[] { tag }, size, payload);
    }

    private static byte[] encodeDescriptorSize(int size) {
        int value = size;
        byte[] encoded = new byte[4];
        int count = 0;
        do {
            byte b = (byte) (value & 0x7f);
            value >>= 7;
            if (value > 0) {
                b |= 0x80;
            }
            encoded[count++] = b;
        } while (value > 0 && count < encoded.length);
        return Arrays.copyOf(encoded, count);
    }

    private static byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] array : arrays) {
            total += array.length;
        }
        byte[] out = new byte[total];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, out, offset, array.length);
            offset += array.length;
        }
        return out;
    }

    private static int[] parseAvcDimensions(byte[] avcConfig) {
        if (avcConfig == null || avcConfig.length < 7) {
            return null;
        }
        int spsCount = avcConfig[5] & 0x1f;
        int offset = 6;
        for (int i = 0; i < spsCount; i++) {
            if (offset + 2 > avcConfig.length) {
                break;
            }
            int spsLength = ((avcConfig[offset] & 0xff) << 8) | (avcConfig[offset + 1] & 0xff);
            offset += 2;
            if (offset + spsLength > avcConfig.length) {
                break;
            }
            byte[] sps = Arrays.copyOfRange(avcConfig, offset, offset + spsLength);
            int[] dims = parseSps(sps);
            if (dims != null) {
                return dims;
            }
            offset += spsLength;
        }
        return null;
    }

    private static int[] parseSps(byte[] sps) {
        byte[] cleaned = removeEmulationPrevention(sps);
        int offset = 0;
        if (cleaned.length > 0 && (cleaned[0] & 0x1f) == 7) {
            // Skip NAL header byte if present
            offset = 1;
        }
        if (cleaned.length - offset <= 0) {
            return null;
        }
        BitReader reader = new BitReader(Arrays.copyOfRange(cleaned, offset, cleaned.length));
        int profileIdc = reader.readBits(8);
        reader.readBits(8); // constraint flags + reserved
        reader.readBits(8); // level idc
        reader.readUE(); // seq_parameter_set_id

        int chromaFormatIdc = 1;
        if (isExtendedProfile(profileIdc)) {
            chromaFormatIdc = reader.readUE();
            if (chromaFormatIdc == 3) {
                reader.readBits(1);
            }
            reader.readUE();
            reader.readUE();
            reader.readBits(1);
            if (reader.readBits(1) == 1) {
                int count = chromaFormatIdc != 3 ? 8 : 12;
                for (int i = 0; i < count; i++) {
                    if (reader.readBits(1) == 1) {
                        skipScalingList(reader, i < 6 ? 16 : 64);
                    }
                }
            }
        }

        reader.readUE(); // log2_max_frame_num_minus4
        int picOrderCntType = reader.readUE();
        if (picOrderCntType == 0) {
            reader.readUE();
        } else if (picOrderCntType == 1) {
            reader.readBits(1);
            reader.readSE();
            reader.readSE();
            int count = reader.readUE();
            for (int i = 0; i < count; i++) {
                reader.readSE();
            }
        }
        reader.readUE(); // max_num_ref_frames
        reader.readBits(1);
        int picWidthInMbsMinus1 = reader.readUE();
        int picHeightInMapUnitsMinus1 = reader.readUE();
        int frameMbsOnlyFlag = reader.readBits(1);
        if (frameMbsOnlyFlag == 0) {
            reader.readBits(1);
        }
        reader.readBits(1);
        int frameCropLeft = 0;
        int frameCropRight = 0;
        int frameCropTop = 0;
        int frameCropBottom = 0;
        if (reader.readBits(1) == 1) {
            frameCropLeft = reader.readUE();
            frameCropRight = reader.readUE();
            frameCropTop = reader.readUE();
            frameCropBottom = reader.readUE();
        }

        int width = (picWidthInMbsMinus1 + 1) * 16;
        int height = (picHeightInMapUnitsMinus1 + 1) * 16 * (2 - frameMbsOnlyFlag);

        int cropUnitX = 1;
        int cropUnitY = 2 - frameMbsOnlyFlag;
        if (chromaFormatIdc == 1) {
            cropUnitX = 2;
            cropUnitY *= 2;
        } else if (chromaFormatIdc == 2) {
            cropUnitX = 2;
        }
        width -= (frameCropLeft + frameCropRight) * cropUnitX;
        height -= (frameCropTop + frameCropBottom) * cropUnitY;
        return new int[] { width, height };
    }

    private static byte[] removeEmulationPrevention(byte[] data) {
        byte[] cleaned = new byte[data.length];
        int count = 0;
        for (int i = 0; i < data.length; i++) {
            if (i > 1 && data[i] == 0x03 && data[i - 1] == 0x00 && data[i - 2] == 0x00) {
                continue;
            }
            cleaned[count++] = data[i];
        }
        return Arrays.copyOf(cleaned, count);
    }

    private static boolean isExtendedProfile(int profileIdc) {
        return profileIdc == 100 || profileIdc == 110 || profileIdc == 122 || profileIdc == 244 || profileIdc == 44 || profileIdc == 83 || profileIdc == 86 || profileIdc == 118 || profileIdc == 128 || profileIdc == 138 || profileIdc == 139 || profileIdc == 134;
    }

    private static void skipScalingList(BitReader reader, int size) {
        int lastScale = 8;
        int nextScale = 8;
        for (int i = 0; i < size; i++) {
            if (nextScale != 0) {
                int deltaScale = reader.readSE();
                nextScale = (lastScale + deltaScale + 256) % 256;
            }
            lastScale = nextScale == 0 ? lastScale : nextScale;
        }
    }

    private static AudioSpecificConfig parseAudioSpecificConfig(byte[] asc) {
        BitReader reader = new BitReader(asc);
        int audioObjectType = reader.readBits(5);
        int samplingIndex = reader.readBits(4);
        int sampleRate;
        if (samplingIndex == 15) {
            sampleRate = reader.readBits(24);
        } else {
            sampleRate = samplingIndex < AAC_SAMPLE_RATES.length ? AAC_SAMPLE_RATES[samplingIndex] : 48000;
        }
        int channelConfig = reader.readBits(4);
        if (audioObjectType == 5 || audioObjectType == 29) {
            samplingIndex = reader.readBits(4);
            if (samplingIndex == 15) {
                sampleRate = reader.readBits(24);
            } else {
                sampleRate = AAC_SAMPLE_RATES[samplingIndex];
            }
        }
        return new AudioSpecificConfig(sampleRate, channelConfig);
    }

    private static final int[] AAC_SAMPLE_RATES = new int[] { 96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350 };

    private static class AudioSpecificConfig {
        private final int sampleRate;

        private final int channelConfig;

        private AudioSpecificConfig(int sampleRate, int channelConfig) {
            this.sampleRate = sampleRate;
            this.channelConfig = channelConfig;
        }
    }

    private static class BitReader {
        private final byte[] data;

        private int bitPos;

        BitReader(byte[] data) {
            this.data = data;
        }

        int readBits(int count) {
            int value = 0;
            for (int i = 0; i < count; i++) {
                int bytePos = bitPos >> 3;
                int bitOffset = 7 - (bitPos & 7);
                int bit = (data[bytePos] >> bitOffset) & 1;
                value = (value << 1) | bit;
                bitPos++;
            }
            return value;
        }

        int readUE() {
            int zeros = 0;
            while (readBits(1) == 0 && bitPos < data.length * 8) {
                zeros++;
            }
            int value = (1 << zeros) - 1 + readBits(zeros);
            return value;
        }

        int readSE() {
            int value = readUE();
            int sign = ((value & 1) == 0) ? -1 : 1;
            return sign * ((value + 1) >> 1);
        }
    }

    private static class TrackBuffer {
        private final ByteArrayOutputStream media = new ByteArrayOutputStream();

        private final List<Fmp4FragmentBuilder.SampleData> samples = new ArrayList<>();

        private long bufferedDuration;

        private long fragmentStartDecodeTime;

        private boolean startsWithKeyframe;

        private void reset() {
            media.reset();
            samples.clear();
            bufferedDuration = 0;
            fragmentStartDecodeTime = 0;
            startsWithKeyframe = false;
        }
    }
}
