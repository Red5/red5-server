/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package org.red5.io.mp4.impl;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mina.core.buffer.IoBuffer;
import org.jcodec.codecs.mpeg4.mp4.EsdsBox;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.containers.mp4.MP4TrackType;
import org.jcodec.containers.mp4.MP4Util;
import org.jcodec.containers.mp4.MP4Util.Movie;
import org.jcodec.containers.mp4.boxes.AudioSampleEntry;
import org.jcodec.containers.mp4.boxes.Box;
import org.jcodec.containers.mp4.boxes.ChunkOffsets64Box;
import org.jcodec.containers.mp4.boxes.ChunkOffsetsBox;
import org.jcodec.containers.mp4.boxes.CompositionOffsetsBox;
import org.jcodec.containers.mp4.boxes.HandlerBox;
import org.jcodec.containers.mp4.boxes.MediaBox;
import org.jcodec.containers.mp4.boxes.MediaHeaderBox;
import org.jcodec.containers.mp4.boxes.MediaInfoBox;
import org.jcodec.containers.mp4.boxes.MovieBox;
import org.jcodec.containers.mp4.boxes.MovieHeaderBox;
import org.jcodec.containers.mp4.boxes.NodeBox;
import org.jcodec.containers.mp4.boxes.SampleDescriptionBox;
import org.jcodec.containers.mp4.boxes.SampleSizesBox;
import org.jcodec.containers.mp4.boxes.SampleToChunkBox;
import org.jcodec.containers.mp4.boxes.SyncSamplesBox;
import org.jcodec.containers.mp4.boxes.TimeToSampleBox;
import org.jcodec.containers.mp4.boxes.TrackHeaderBox;
import org.jcodec.containers.mp4.boxes.TrakBox;
import org.jcodec.containers.mp4.boxes.VideoSampleEntry;
import org.jcodec.containers.mp4.boxes.WaveExtension;
import org.jcodec.containers.mp4.boxes.SampleToChunkBox.SampleToChunkEntry;
import org.jcodec.containers.mp4.boxes.TimeToSampleBox.TimeToSampleEntry;
import org.red5.io.IStreamableFile;
import org.red5.io.ITag;
import org.red5.io.ITagReader;
import org.red5.io.IoConstants;
import org.red5.io.amf.Output;
import org.red5.io.flv.IKeyFrameDataAnalyzer;
import org.red5.io.flv.impl.Tag;
import org.red5.io.mp4.MP4Frame;
import org.red5.io.utils.HexDump;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This reader is used to read the contents of an MP4 file. <br>
 * NOTE: This class is not implemented as thread-safe, the caller should ensure the thread-safety. <br>
 * New NetStream notifications <br>
 * Two new notifications facilitate the implementation of the playback components:
 * <ul>
 * <li>NetStream.Play.FileStructureInvalid: This event is sent if the player detects an MP4 with an invalid file structure. Flash Player
 * cannot play files that have invalid file structures.</li>
 * <li>NetStream.Play.NoSupportedTrackFound: This event is sent if the player does not detect any supported tracks. If there aren't any
 * supported video, audio or data tracks found, Flash Player does not play the file.</li>
 * </ul>
 *
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class MP4Reader implements IoConstants, ITagReader, IKeyFrameDataAnalyzer {

    private static Logger log = LoggerFactory.getLogger(MP4Reader.class);

    /** Audio packet prefix for the decoder frame */
    public final static byte[] PREFIX_AUDIO_CONFIG_FRAME = new byte[] { (byte) 0xaf, (byte) 0 };

    /** Audio packet prefix */
    public final static byte[] PREFIX_AUDIO_FRAME = new byte[] { (byte) 0xaf, (byte) 0x01 };

    /** Blank AAC data **/
    public final static byte[] EMPTY_AAC = { (byte) 0x21, (byte) 0x10, (byte) 0x04, (byte) 0x60, (byte) 0x8c, (byte) 0x1c };

    /** Video packet prefix for the decoder frame */
    public final static byte[] PREFIX_VIDEO_CONFIG_FRAME = new byte[] { (byte) 0x17, (byte) 0, (byte) 0, (byte) 0, (byte) 0 };

    /** Video packet prefix for key frames */
    public final static byte[] PREFIX_VIDEO_KEYFRAME = new byte[] { (byte) 0x17, (byte) 0x01 };

    /** Video packet prefix for standard frames (interframe) */
    public final static byte[] PREFIX_VIDEO_FRAME = new byte[] { (byte) 0x27, (byte) 0x01 };

    /**
     * File dataSource / channel
     */
    private SeekableByteChannel dataSource;

    /** Mapping between file position and timestamp in ms. */
    private HashMap<Integer, Long> timePosMap;

    private HashMap<Integer, Long> samplePosMap;

    /** Whether or not the clip contains a video track */
    private boolean hasVideo = false;

    /** Whether or not the clip contains an audio track */
    private boolean hasAudio = false;

    // default video codec
    private String videoCodecId = "avc1"; // hvc1

    // default audio codec
    private String audioCodecId = "mp4a";

    // decoder bytes / configs
    private byte[] audioDecoderBytes, videoDecoderBytes;

    // duration in milliseconds
    private long duration;

    // movie time scale
    private long timeScale;

    private int width;

    private int height;

    // audio sample rate kHz
    private double audioTimeScale;

    private int audioChannels;

    // default to aac lc
    private int audioCodecType = 1;

    private int videoSampleCount;

    private double fps;

    private double videoTimeScale;

    private int avcLevel;

    private int avcProfile;

    private String formattedDuration;

    // samples to chunk mappings
    private List<SampleToChunkEntry> audioSamplesToChunks, videoSamplesToChunks;

    // keyframe - sample numbers
    private int[] syncSamples;

    // samples
    private int[] audioSamples, videoSamples;

    private long audioSampleSize;

    // chunk offsets
    private long[] audioChunkOffsets, videoChunkOffsets;

    // sample duration
    private long audioSampleDuration = 1024, videoSampleDuration = 125;

    // keep track of current frame / sample
    private int currentFrame = 0;

    private int prevFrameSize = 0;

    private int prevVideoTS = -1;

    private List<MP4Frame> frames = new ArrayList<>();

    private long audioCount;

    private long videoCount;

    // composition time to sample entries
    private List<CompositionOffsetsBox.Entry> compositionTimes;

    /**
     * Container for metadata and any other tags that should be sent prior to media data.
     */
    private LinkedList<ITag> firstTags = new LinkedList<>();

    /**
     * Container for seek points in the video. These are the time stamps for the key frames or samples.
     */
    private LinkedList<Integer> seekPoints;

    private final Semaphore lock = new Semaphore(1, true);

    /** Constructs a new MP4Reader. */
    MP4Reader() {
    }

    /**
     * Creates MP4 reader from file input stream, sets up metadata generation flag.
     *
     * @param f
     *            File input stream
     * @throws IOException
     *             on IO exception
     */
    @SuppressWarnings("null")
    public MP4Reader(File f) throws IOException {
        if (null == f) {
            log.warn("Reader was passed a null file");
            log.debug("{}", ToStringBuilder.reflectionToString(this));
        }
        if (f.exists() && f.canRead()) {
            // create a datasource / channel
            dataSource = NIOUtils.readableChannel(f);
            // parse the movie
            parseMovie(dataSource);
            // analyze the samples/chunks and build the keyframe meta data
            analyzeFrames();
            // add meta data
            firstTags.add(createFileMeta());
            // create / add the pre-streaming (decoder config) tags
            createPreStreamingTags(0, false);
        } else {
            log.warn("Reader was passed an unreadable or non-existant file");
        }
    }

    /**
     * This handles the moov atom being at the beginning or end of the file, so the mdat may also be before or after the moov atom.
     */
    public void parseMovie(SeekableByteChannel dataSource) {
        try {
            // read the file
            Movie movie = MP4Util.parseFullMovieChannel(dataSource);
            // decode all the info that we want from the atoms
            MovieBox moov = movie.getMoov();
            dumpBox(moov);
            moov.getBoxes().forEach(box -> {
                if (box instanceof MovieHeaderBox) {
                    // get the timescale and duration from the movie header
                    MovieHeaderBox mvhd = (MovieHeaderBox) box;
                    timeScale = mvhd.getTimescale();
                    duration = mvhd.getDuration();
                } else {
                    log.debug("Skipping box: {}", box);
                }
            });
            double lengthInSeconds = (double) duration / timeScale;
            log.debug("Time scale {} Duration {} seconds: {}", timeScale, duration, lengthInSeconds);
            // media scale
            AtomicInteger scale = new AtomicInteger(0);
            // look at the tracks
            TrakBox[] tracks = moov.getTracks();
            log.debug("Tracks: {}", tracks.length);
            for (TrakBox trak : tracks) {
                log.debug("trak: {}", trak);
                MP4TrackType trackType = TrakBox.getTrackType(trak);
                if (trackType == MP4TrackType.SOUND) {
                    hasAudio = true;
                } else if (trackType == MP4TrackType.VIDEO) {
                    hasVideo = true;
                }
                trak.getBoxes().forEach(box -> {
                    AtomicBoolean isAudio = new AtomicBoolean(false), isVideo = new AtomicBoolean(false);
                    switch (box.getFourcc()) {
                        case "tkhd":
                            TrackHeaderBox tkhd = (TrackHeaderBox) box;
                            log.debug("Track header atom found, track id: {}", tkhd.getTrackId());
                            if (tkhd.getWidth() > 0) {
                                width = (int) tkhd.getWidth();
                                height = (int) tkhd.getHeight();
                                log.debug("Width {} x Height {}", width, height);
                            }
                            break;
                        case "mdia":
                            MediaBox mdia = (MediaBox) box;
                            mdia.getBoxes().forEach(mdbox -> {
                                if (log.isDebugEnabled()) {
                                    log.debug("mdia child: {}", mdbox);
                                }
                                if (mdbox instanceof MediaHeaderBox) {
                                    MediaHeaderBox mdhd = (MediaHeaderBox) mdbox;
                                    log.debug("Media data header atom found");
                                    // this will be for either video or audio depending media info
                                    scale.set(mdhd.getTimescale());
                                    log.debug("Time scale {}", scale);
                                } else if (mdbox instanceof HandlerBox) { // hdlr
                                    log.debug("Handler reference atom found");
                                    HandlerBox hdlr = (HandlerBox) mdbox;
                                    if (log.isDebugEnabled()) {
                                        log.debug("hdlr: {} {} {}", hdlr.getFourcc(), hdlr.getComponentType(), hdlr.getComponentSubType());
                                    }
                                    String hdlrType = hdlr.getComponentSubType();
                                    if ("vide".equals(hdlrType)) {
                                        isVideo.set(true);
                                        isAudio.set(false);
                                        if (scale.get() > 0) {
                                            videoTimeScale = scale.get() * 1.0;
                                            log.debug("Video time scale: {}", videoTimeScale);
                                        }
                                    } else if ("soun".equals(hdlrType)) {
                                        isAudio.set(true);
                                        isVideo.set(false);
                                        if (scale.get() > 0) {
                                            audioTimeScale = scale.get() * 1.0;
                                            log.debug("Audio time scale: {}", audioTimeScale);
                                        }
                                    } else {
                                        log.debug("Unhandled handler type: {}", hdlrType);
                                    }
                                } else {
                                    log.debug("Unhandled media box: {}", mdbox);
                                }
                            });
                            MediaInfoBox minf = mdia.getMinf(); // minf
                            if (minf != null) {
                                NodeBox stbl = minf.getStbl(); // mdia/minf/stbl
                                if (stbl != null) {
                                    stbl.getBoxes().forEach(sbox -> {
                                        if (log.isDebugEnabled()) {
                                            log.debug("stbl child: {}", sbox);
                                        }
                                        switch (sbox.getFourcc()) {
                                            case "stsd":
                                                SampleDescriptionBox stsd = (SampleDescriptionBox) sbox; // stsd
                                                // stsd: mp4a, avc1, mp4v
                                                stsd.getBoxes().forEach(stbox -> {
                                                    if (log.isDebugEnabled()) {
                                                        log.debug("stsd child: {}", stbox);
                                                        /*
                                                        "tag": "stsd",
                                                        "boxes": [
                                                            {
                                                            "tag": "mp4a | mp4v | avc1 | hvc1",
                                                            "boxes": [
                                                                {
                                                                "tag": "esds"
                                                                }
                                                            ]
                                                            }
                                                        ]
                                                        */
                                                    }
                                                    switch (stbox.getFourcc()) {
                                                        case "mp4a":
                                                            audioCodecId = "mp4a";
                                                            processAudioSampleEntry((AudioSampleEntry) stbox, scale.get());
                                                            break;
                                                        case "mp4v":
                                                            videoCodecId = "mp4v";
                                                            processVideoSampleEntry((VideoSampleEntry) stbox, scale.get());
                                                            break;
                                                        case "avc1":
                                                            videoCodecId = "avc1";
                                                            processVideoSampleEntry((VideoSampleEntry) stbox, scale.get());
                                                            break;
                                                        default:
                                                            log.warn("Unhandled sample description box: {}", stbox);
                                                            break;
                                                    }
                                                });
                                                break;
                                            case "stsc": // records
                                                log.debug("Sample to chunk atom found");
                                                SampleToChunkBox stsc = (SampleToChunkBox) sbox; // stsc
                                                if (isAudio.get()) {
                                                    SampleToChunkEntry[] ascEntries = stsc.getSampleToChunk();
                                                    if (log.isDebugEnabled()) {
                                                        log.debug("Audio samples to chunks: {}", ascEntries.length);
                                                        for (SampleToChunkEntry entry : ascEntries) {
                                                            log.debug("Audio s2c count: {} first: {} entry: {}", entry.getCount(), entry.getFirst(), entry.getEntry());
                                                        }
                                                    }
                                                    audioSamplesToChunks = List.of(ascEntries);
                                                    // handle instance where there are no actual records (bad f4v?)
                                                } else if (isVideo.get()) {
                                                    SampleToChunkEntry[] vscEntries = stsc.getSampleToChunk();
                                                    if (log.isDebugEnabled()) {
                                                        log.debug("Video samples to chunks: {}", vscEntries.length);
                                                        for (SampleToChunkEntry entry : vscEntries) {
                                                            log.debug("Video s2c count: {} first: {} entry: {}", entry.getCount(), entry.getFirst(), entry.getEntry());
                                                        }
                                                    }
                                                    videoSamplesToChunks = List.of(vscEntries);
                                                }
                                                break;
                                            case "stsz": // samples
                                                log.debug("Sample size atom found");
                                                SampleSizesBox stsz = (SampleSizesBox) sbox; // stsz
                                                if (isAudio.get()) {
                                                    audioSamples = stsz.getSizes();
                                                    // if sample size is 0 then the table must be checked due to variable sample sizes
                                                    log.debug("Sample size: {}", stsz.getDefaultSize());
                                                    audioSampleSize = stsz.getDefaultSize();
                                                    log.debug("Sample size: {}", audioSampleSize);
                                                    int audioSampleCount = stsz.getCount();
                                                    log.debug("Sample count: {}", audioSampleCount);
                                                } else if (isVideo.get()) {
                                                    videoSamples = stsz.getSizes();
                                                    // if sample size is 0 then the table must be checked due to variable sample sizes
                                                    log.debug("Sample size: {}", stsz.getDefaultSize());
                                                    videoSampleCount = stsz.getCount();
                                                    log.debug("Sample count: {}", videoSampleCount);
                                                }
                                                break;
                                            case "stco":
                                                log.debug("Chunk offset atom found");
                                                ChunkOffsetsBox stco = (ChunkOffsetsBox) sbox; // stco
                                                if (isAudio.get()) {
                                                    audioChunkOffsets = stco.getChunkOffsets();
                                                    log.debug("Chunk count: {}", audioChunkOffsets.length);
                                                } else if (isVideo.get()) {
                                                    videoChunkOffsets = stco.getChunkOffsets();
                                                    log.debug("Chunk count: {}", videoChunkOffsets.length);
                                                }
                                                break;
                                            case "co64":
                                                log.debug("Chunk offset (64) atom found");
                                                ChunkOffsets64Box co64 = (ChunkOffsets64Box) sbox; // co64
                                                if (isAudio.get()) {
                                                    audioChunkOffsets = co64.getChunkOffsets();
                                                    log.debug("Chunk count: {}", audioChunkOffsets.length);
                                                } else if (isVideo.get()) {
                                                    videoChunkOffsets = co64.getChunkOffsets();
                                                    log.debug("Chunk count: {}", videoChunkOffsets.length);
                                                    // double the timescale for video, since it seems to run at
                                                    // half-speed when co64 is used (seems hacky)
                                                    //videoTimeScale = scale * 2.0;
                                                    //log.debug("Video time scale: {}", videoTimeScale);
                                                }
                                            case "stss":
                                                log.debug("Sync sample atom found");
                                                SyncSamplesBox stss = (SyncSamplesBox) sbox; // stts
                                                if (isAudio.get()) {
                                                } else if (isVideo.get()) {
                                                    syncSamples = stss.getSyncSamples();
                                                    log.debug("Keyframes: {}", syncSamples.length);
                                                }
                                                break;
                                            case "stts":
                                                log.debug("Time to sample atom found");
                                                TimeToSampleBox stts = (TimeToSampleBox) sbox; // stts
                                                TimeToSampleEntry[] records = stts.getEntries();
                                                // handle instance where there are no actual records (bad f4v?)
                                                if (records.length > 0) {
                                                    TimeToSampleEntry rec = records[0];
                                                    log.debug("Samples: {} duration: {} segment duration: {}", rec.getSampleCount(), rec.getSampleDuration(), rec.getSegmentDuration());
                                                }
                                                if (isAudio.get()) {
                                                    log.debug("Audio time to samples: {}", records.length);
                                                    // if we have 1 record it means all samples have the same duration
                                                    audioSampleDuration = records[0].getSampleDuration();
                                                } else if (isVideo.get()) {
                                                    log.debug("Video time to samples: {}", records.length);
                                                    // if we have 1 record it means all samples have the same duration
                                                    videoSampleDuration = records[0].getSampleDuration();
                                                }
                                                break;
                                            case "sdtp": // sdtp - sample dependency type
                                                log.debug("Independent and disposable samples atom found");
                                                /*
                                                SamplesDependencyTypeBox sdtp = (x) sbox;
                                                if (isAudio.get()) {
                                                    List<SampleDependencyTypeBox.Entry> recs = sdtp.getEntries();
                                                    for (SampleDependencyTypeBox.Entry rec : recs) {
                                                        log.debug("{}", rec);
                                                    }
                                                } else if (isVideo.get()) {
                                                    List<SampleDependencyTypeBox.Entry> recs = sdtp.getEntries();
                                                    for (SampleDependencyTypeBox.Entry rec : recs) {
                                                        log.debug("{}", rec);
                                                    }
                                                }
                                                */
                                                break;
                                            case "ctts": // ctts - (composition) time to sample
                                                log.debug("Composition time to sample atom found");
                                                CompositionOffsetsBox ctts = (CompositionOffsetsBox) sbox;
                                                compositionTimes = List.of(ctts.getEntries());
                                                log.debug("Record count: {}", compositionTimes.size());
                                                if (log.isTraceEnabled()) {
                                                    for (CompositionOffsetsBox.Entry rec : compositionTimes) {
                                                        double offset = rec.getOffset();
                                                        if (scale.get() > 0) {
                                                            rec.offset += (offset / (double) scale.get()) * 1000.0;
                                                        }
                                                        log.trace("Samples = {} offset = {}", rec.getCount(), rec.getOffset());
                                                    }
                                                }
                                                break;
                                        }
                                    });
                                }
                            }
                            mdia.getBoxes().forEach(mbox -> {
                                if (log.isDebugEnabled()) {
                                    log.debug("mdia child: {}", mbox);
                                }
                            });
                            break;
                        default:
                            log.warn("Unhandled box: {}", box);
                            break;
                    }
                });
            }
            // calculate FPS
            fps = (videoSampleCount * timeScale) / (double) duration;
            log.debug("FPS calc: ({} * {}) / {} = {}", videoSampleCount, timeScale, duration, fps);
            // real duration
            StringBuilder sb = new StringBuilder();
            double videoTime = ((double) duration / (double) timeScale);
            log.debug("Video time: {}", videoTime);
            int minutes = (int) (videoTime / 60);
            if (minutes > 0) {
                sb.append(minutes);
                sb.append('.');
            }
            // formatter for seconds / millis
            NumberFormat df = DecimalFormat.getInstance();
            df.setMaximumFractionDigits(2);
            sb.append(df.format((videoTime % 60)));
            formattedDuration = sb.toString();
            log.debug("Time: {}", formattedDuration);
        } catch (Exception e) {
            log.error("Exception decoding header / atoms", e);
        }
    }

    /**
     * Dumps the children of a container box.
     *
     * @param box
     *            mp4 box
     */
    public static void dumpBox(NodeBox box) {
        log.debug("Dump box: {}", box);
        box.getBoxes().forEach(bx -> log.debug("{}", bx));
    }

    /**
     * Process the audio information contained in the atoms.
     *
     * @param ase
     *            AudioSampleEntry
     * @param scale
     *            timescale
     */
    private void processAudioSampleEntry(AudioSampleEntry ase, int scale) {
        log.debug("Sample size: {}", ase.getSampleSize());
        float ats = ase.getSampleRate();
        // skip invalid audio time scale
        if (ats > 0) {
            audioTimeScale = ats * 1.0;
        }
        log.debug("Sample rate (audio time scale): {}", audioTimeScale);
        audioChannels = ase.getChannelCount();
        log.debug("Channels: {}", audioChannels);
        ase.getBoxes().forEach(box -> {
            log.debug("Audio sample entry box: {}", box);
            switch (box.getFourcc()) {
                case "esds":
                    if (box.estimateSize() > 0) {

                    }
                    EsdsBox esds = Box.asBox(EsdsBox.class, box);
                    log.debug("Process {} obj: {} avg bitrate: {} max bitrate: {}", esds.getFourcc(), esds.getObjectType(), esds.getAvgBitrate(), esds.getMaxBitrate());
                    // http://stackoverflow.com/questions/3987850/mp4-atom-how-to-discriminate-the-audio-codec-is-it-aac-or-mp3
                    audioDecoderBytes = esds.getStreamInfo().array();
                    log.debug("Audio config bytes: {}", HexDump.byteArrayToHexString(audioDecoderBytes));
                    // the first 5 (0-4) bits tell us about the coder used for aacaot/aottype http://wiki.multimedia.cx/index.php?title=MPEG-4_Audio 0 - NULL 1 - AAC Main (a deprecated AAC profile
                    // from MPEG-2) 2 - AAC LC or backwards compatible HE-AAC 3 - AAC Scalable Sample Rate 4 - AAC LTP (a replacement for AAC Main, rarely used) 5 - HE-AAC explicitly signaled
                    // (Non-backward compatible) 23 - Low Delay AAC 29 - HE-AACv2 explicitly signaled 32 - MP3on4 Layer 1 33 - MP3on4 Layer 2 34 - MP3on4 Layer 3
                    //
                    byte audioCoderType = audioDecoderBytes[0];
                    //match first byte
                    switch (audioCoderType) {
                        case 0x02:
                            log.debug("Audio type AAC LC");
                        case 0x11: //ER (Error Resilient) AAC LC
                            log.debug("Audio type ER AAC LC");
                        default:
                            audioCodecType = 1; //AAC LC
                            break;
                        case 0x01:
                            log.debug("Audio type AAC Main");
                            audioCodecType = 0; //AAC Main
                            break;
                        case 0x03:
                            log.debug("Audio type AAC SBR");
                            audioCodecType = 2; //AAC LC SBR
                            break;
                        case 0x05:
                        case 0x1d:
                            log.debug("Audio type AAC HE");
                            audioCodecType = 3; //AAC HE
                            break;
                        case 0x20:
                        case 0x21:
                        case 0x22:
                            log.debug("Audio type MP3");
                            audioCodecType = 33; //MP3
                            audioCodecId = "mp3";
                            break;
                    }
                    log.debug("Audio coder type: {} {} id: {}", audioCoderType, Integer.toBinaryString(audioCoderType), audioCodecId);
                    break;
                case "wave":
                    // check for decompression param atom
                    WaveExtension wave = Box.asBox(WaveExtension.class, box);
                    log.debug("wave atom found");
                    // wave/esds
                    esds = wave.getBoxes().stream().filter(b -> b instanceof EsdsBox).map(b -> (EsdsBox) b).findFirst().orElse(null);
                    if (esds != null) {
                        log.debug("Process {} obj: {} avg bitrate: {} max bitrate: {}", esds.getFourcc(), esds.getObjectType(), esds.getAvgBitrate(), esds.getMaxBitrate());
                    } else {
                        log.debug("esds not found in wave");
                        // mp4a/esds
                        //AC3SpecificBox mp4a = wave.getBoxes(AC3SpecificBox.class).get(0);
                        //esds = mp4a.getBoxes(ESDescriptorBox.class).get(0);
                    }
                    break;
                default:
                    log.warn("Unhandled sample desc extension: {}", box);
                    break;
            }
        });
    }

    /**
     * Process the video information contained in the atoms.
     *
     * @param vse
     *            VisualSampleEntry
     * @param scale
     *            timescale
     */
    private void processVideoSampleEntry(VideoSampleEntry vse, int scale) {
        // get codec
        String compressorName = vse.getCompressorName();
        long frameCount = vse.getFrameCount();
        log.debug("Compressor: {} frame count: {}", compressorName, frameCount);
        vse.getBoxes().forEach(box -> {
            log.debug("Video sample entry box: {}", box);
            switch (box.getFourcc()) {
                case "esds": // videoCodecId = "mp4v"
                    EsdsBox esds = Box.asBox(EsdsBox.class, box);
                    log.debug("Process {} obj: {} avg bitrate: {} max bitrate: {}", esds.getFourcc(), esds.getObjectType(), esds.getAvgBitrate(), esds.getMaxBitrate());
                    videoDecoderBytes = esds.getStreamInfo().array();
                    log.debug("Video config bytes: {}", HexDump.byteArrayToHexString(videoDecoderBytes));
                    break;
                /*
                stsd child: {"tag":"avc1","boxes": [{"tag":"avcC"},{"tag":"btrt"}]}
                Compressor:  frame count: 1
                Video sample entry box: {"tag":"avcC"}
                Unhandled sample desc extension: {"tag":"avcC"}
                Video sample entry box: {"tag":"btrt"}
                Unhandled sample desc extension: {"tag":"btrt"}


                    case "avcC": // videoCodecId = "avc1"
                    AvcConfigurationBox avc1 = vse.getBoxes(AvcConfigurationBox.class).get(0);
                    avcLevel = avc1.getAvcLevelIndication();
                    log.debug("AVC level: {}", avcLevel);
                    avcProfile = avc1.getAvcProfileIndication();
                    log.debug("AVC Profile: {}", avcProfile);
                    AvcDecoderConfigurationRecord avcC = avc1.getavcDecoderConfigurationRecord();
                    if (avcC != null) {
                        long videoConfigContentSize = avcC.getContentSize();
                        log.debug("AVCC size: {}", videoConfigContentSize);
                        ByteBuffer byteBuffer = ByteBuffer.allocate((int) videoConfigContentSize);
                        avc1.avcDecoderConfigurationRecord.getContent(byteBuffer);
                        byteBuffer.flip();
                        videoDecoderBytes = new byte[byteBuffer.limit()];
                        byteBuffer.get(videoDecoderBytes);
                    } else {
                        // quicktime and ipods use a pixel aspect atom (pasp)
                        // since we have no avcC check for this and avcC may be a child
                        log.warn("avcC atom not found; we may need to modify this to support pasp atom");
                    }
                    break;
                */
                default:
                    log.warn("Unhandled sample desc extension: {}", box);
                    break;
            }
        });
    }

    /**
     * Get the total readable bytes in a file or IoBuffer.
     *
     * @return Total readable bytes
     */
    @Override
    public long getTotalBytes() {
        try {
            return dataSource.size();
        } catch (Exception e) {
            log.error("Error getTotalBytes", e);
        }
        return 0;
    }

    /**
     * Get the current position in a file or IoBuffer.
     *
     * @return Current position in a file
     */
    private long getCurrentPosition() {
        try {
            return dataSource.position();
        } catch (Exception e) {
            log.error("Error getCurrentPosition", e);
            return 0;
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasVideo() {
        return hasVideo;
    }

    /**
     * Returns the file buffer.
     *
     * @return File contents as byte buffer
     */
    public IoBuffer getFileData() {
        // TODO as of now, return null will disable cache
        // we need to redesign the cache architecture so that
        // the cache is layered underneath FLVReader not above it,
        // thus both tag cache and file cache are feasible.
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IStreamableFile getFile() {
        // TODO wondering if we need to have a reference
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOffset() {
        // XXX what's the difference from getBytesRead
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getBytesRead() {
        // XXX should summarize the total bytes read or
        // just the current position?
        return getCurrentPosition();
    }

    /** {@inheritDoc} */
    @Override
    public long getDuration() {
        return duration;
    }

    public String getVideoCodecId() {
        return videoCodecId;
    }

    public String getAudioCodecId() {
        return audioCodecId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasMoreTags() {
        return currentFrame < frames.size();
    }

    /**
     * Create tag for metadata event.
     *
     * Information mostly from http://www.kaourantin.net/2007/08/what-just-happened-to-video-on-web_20.html
     *
     * <pre>
     * 		width: Display width in pixels.
     * 		height: Display height in pixels.
     * 		duration: Duration in seconds. But unlike for FLV files this field will always be present.
     * 		videocodecid: Usually a string such as "avc1" or "VP6F", for H.264 we report 'avc1'.
     * 		audiocodecid: Usually a string such as ".mp3" or "mp4a", for AAC we report 'mp4a' and MP3 we report '.mp3'.
     * 	    avcprofile: AVC profile number, values of 66, 77, 88, 100, 110, 122 or 144; which correspond to the H.264 profiles.
     * 	    avclevel: AVC IDC level number, values between 10 and 51.
     * 	    aottype: Either 0, 1 or 2. This corresponds to AAC Main, AAC LC and SBR audio types.
     * 	    moovposition: The offset in bytes of the moov atom in a file.
     * 	    trackinfo: An array of objects containing various infomation about all the tracks in a file
     * 	      ex.
     * 	    	trackinfo[0].length: 7081
     * 	    	trackinfo[0].timescale: 600
     * 	    	trackinfo[0].sampledescription.sampletype: avc1
     * 	    	trackinfo[0].language: und
     * 	    	trackinfo[1].length: 525312
     * 	    	trackinfo[1].timescale: 44100
     * 	    	trackinfo[1].sampledescription.sampletype: mp4a
     * 	    	trackinfo[1].language: und
     *
     * 	    chapters: As mentioned above information about chapters in audiobooks.
     * 		seekpoints: Array that lists the available keyframes in a file as time stamps in milliseconds.
     * 				This is optional as the MP4 file might not contain this information. Generally speaking,
     * 				most MP4 files will include this by default. You can directly feed the values into NetStream.seek();
     * 	    videoframerate: The frame rate of the video if a monotone frame rate is used.
     * 	    		Most videos will have a monotone frame rate.
     * 	    audiosamplerate: The original sampling rate of the audio track.
     * 	    audiochannels: The original number of channels of the audio track.
     * 		progressivedownloadinfo: Object that provides information from the "pdin" atom. This is optional
     * 				and many files will not have this field.
     * 		tags: Array of key value pairs representing the information present in the "ilst" atom, which is
     * 				the equivalent of ID3 tags for MP4 files. These tags are mostly used by iTunes.
     * </pre>
     *
     * @return Metadata event tag
     */
    ITag createFileMeta() {
        log.debug("Creating onMetaData");
        // Create tag for onMetaData event
        IoBuffer buf = IoBuffer.allocate(1024);
        buf.setAutoExpand(true);
        Output out = new Output(buf);
        out.writeString("onMetaData");
        Map<Object, Object> props = new HashMap<Object, Object>();
        // Duration property
        props.put("duration", ((double) duration / (double) timeScale));
        props.put("width", width);
        props.put("height", height);
        // Video codec id
        props.put("videocodecid", videoCodecId);
        props.put("avcprofile", avcProfile);
        props.put("avclevel", avcLevel);
        props.put("videoframerate", fps);
        // Audio codec id - watch for mp3 instead of aac
        props.put("audiocodecid", audioCodecId);
        props.put("aacaot", audioCodecType);
        props.put("audiosamplerate", audioTimeScale);
        props.put("audiochannels", audioChannels);
        // position of the moov atom
        //props.put("moovposition", moovOffset);
        //props.put("chapters", ""); //this is for f4b - books
        if (seekPoints != null) {
            log.debug("Seekpoint list size: {}", seekPoints.size());
            props.put("seekpoints", seekPoints);
        }
        //tags will only appear if there is an "ilst" atom in the file
        //props.put("tags", "");
        List<Map<String, Object>> arr = new ArrayList<Map<String, Object>>(2);
        if (hasAudio) {
            Map<String, Object> audioMap = new HashMap<String, Object>(4);
            audioMap.put("timescale", audioTimeScale);
            audioMap.put("language", "und");

            List<Map<String, String>> desc = new ArrayList<Map<String, String>>(1);
            audioMap.put("sampledescription", desc);

            Map<String, String> sampleMap = new HashMap<String, String>(1);
            sampleMap.put("sampletype", audioCodecId);
            desc.add(sampleMap);

            if (audioSamples != null) {
                if (audioSampleDuration > 0) {
                    audioMap.put("length_property", audioSampleDuration * audioSamples.length);
                }
                //release some memory
                audioSamples = null;
            }
            arr.add(audioMap);
        }
        if (hasVideo) {
            Map<String, Object> videoMap = new HashMap<String, Object>(3);
            videoMap.put("timescale", videoTimeScale);
            videoMap.put("language", "und");

            List<Map<String, String>> desc = new ArrayList<Map<String, String>>(1);
            videoMap.put("sampledescription", desc);

            Map<String, String> sampleMap = new HashMap<String, String>(1);
            sampleMap.put("sampletype", videoCodecId);
            desc.add(sampleMap);
            if (videoSamples != null) {
                if (videoSampleDuration > 0) {
                    videoMap.put("length_property", videoSampleDuration * videoSamples.length);
                }
                //release some memory
                videoSamples = null;
            }
            arr.add(videoMap);
        }
        props.put("trackinfo", arr);
        //set this based on existence of seekpoints
        props.put("canSeekToEnd", (seekPoints != null));
        out.writeMap(props);
        buf.flip();
        //now that all the meta properties are done, update the duration
        duration = Math.round(duration * 1000d);
        ITag result = new Tag(IoConstants.TYPE_METADATA, 0, buf.limit(), null, 0);
        result.setBody(buf);
        return result;
    }

    /**
     * Tag sequence MetaData, Video config, Audio config, remaining audio and video
     *
     * Packet prefixes: 17 00 00 00 00 = Video extra data (first video packet) 17 01 00 00 00 = Video keyframe 27 01 00 00 00 = Video
     * interframe af 00 ... 06 = Audio extra data (first audio packet) af 01 = Audio frame
     *
     * Audio extra data(s): af 00 = Prefix 11 90 4f 14 = AAC Main = aottype 0 // 11 90 12 10 = AAC LC = aottype 1 13 90 56 e5 a5 48 00 =
     * HE-AAC SBR = aottype 2 06 = Suffix
     *
     * Still not absolutely certain about this order or the bytes - need to verify later
     */
    private void createPreStreamingTags(int timestamp, boolean clear) {
        log.debug("Creating pre-streaming tags");
        if (clear) {
            firstTags.clear();
        }
        ITag tag = null;
        IoBuffer body = null;
        if (hasVideo) {
            //video tag #1
            body = IoBuffer.allocate(41);
            body.setAutoExpand(true);
            body.put(PREFIX_VIDEO_CONFIG_FRAME); //prefix
            if (videoDecoderBytes != null) {
                //because of other processing we do this check
                if (log.isDebugEnabled()) {
                    log.debug("Video decoder bytes: {}", HexDump.byteArrayToHexString(videoDecoderBytes));
                }
                body.put(videoDecoderBytes);
            }
            tag = new Tag(IoConstants.TYPE_VIDEO, timestamp, body.position(), null, 0);
            body.flip();
            tag.setBody(body);
            //add tag
            firstTags.add(tag);
        }
        // TODO: Handle other mp4 container audio codecs like mp3
        // mp3 header magic number ((int & 0xffe00000) == 0xffe00000)
        if (hasAudio) {
            //audio tag #1
            if (audioDecoderBytes != null) {
                //because of other processing we do this check
                if (log.isDebugEnabled()) {
                    log.debug("Audio decoder bytes: {}", HexDump.byteArrayToHexString(audioDecoderBytes));
                }
                body = IoBuffer.allocate(audioDecoderBytes.length + 3);
                body.setAutoExpand(true);
                body.put(PREFIX_AUDIO_CONFIG_FRAME); //prefix
                body.put(audioDecoderBytes);
                body.put((byte) 0x06); //suffix
                tag = new Tag(IoConstants.TYPE_AUDIO, timestamp, body.position(), null, 0);
                body.flip();
                tag.setBody(body);
                //add tag
                firstTags.add(tag);
            } else {
                log.info("Audio decoder bytes were not available");
            }
        }
    }

    /**
     * Packages media data for return to providers
     */
    @Override
    public ITag readTag() {
        ITag tag = null;
        if (log.isTraceEnabled()) {
            log.trace("Read tag - prevFrameSize {} audio: {} video: {}", new Object[] { prevFrameSize, audioCount, videoCount });
        }
        // ensure there are frames before proceeding
        if (!frames.isEmpty()) {
            try {
                lock.acquire();
                //log.debug("Read tag");
                //empty-out the pre-streaming tags first
                if (!firstTags.isEmpty()) {
                    //log.debug("Returning pre-tag");
                    // Return first tags before media data
                    return firstTags.removeFirst();
                }
                //get the current frame
                MP4Frame frame = frames.get(currentFrame);
                if (frame != null) {
                    log.debug("Playback #{} {}", currentFrame, frame);
                    int sampleSize = frame.getSize();
                    int time = (int) Math.round(frame.getTime() * 1000.0);
                    //log.debug("Read tag - dst: {} base: {} time: {}", new Object[]{frameTs, baseTs, time});
                    //long samplePos = frame.getOffset();
                    //log.debug("Read tag - samplePos {}", samplePos);
                    //determine frame type and packet body padding
                    byte type = frame.getType();
                    //assume video type
                    int pad = 5;
                    if (type == TYPE_AUDIO) {
                        pad = 2;
                    }
                    //create a byte buffer of the size of the sample
                    ByteBuffer data = ByteBuffer.allocate(sampleSize + pad);
                    try {
                        //prefix is different for keyframes
                        if (type == TYPE_VIDEO) {
                            if (frame.isKeyFrame()) {
                                //log.debug("Writing keyframe prefix");
                                data.put(PREFIX_VIDEO_KEYFRAME);
                            } else {
                                //log.debug("Writing interframe prefix");
                                data.put(PREFIX_VIDEO_FRAME);
                            }
                            // match the sample with its ctts / mdhd adjustment time
                            int timeOffset = prevVideoTS != -1 ? time - prevVideoTS : 0;
                            data.put((byte) ((timeOffset >>> 16) & 0xff));
                            data.put((byte) ((timeOffset >>> 8) & 0xff));
                            data.put((byte) (timeOffset & 0xff));
                            if (log.isTraceEnabled()) {
                                byte[] prefix = new byte[5];
                                int p = data.position();
                                data.position(0);
                                data.get(prefix);
                                data.position(p);
                                log.trace("{}", prefix);
                            }
                            // track video frame count
                            videoCount++;
                            prevVideoTS = time;
                        } else {
                            //log.debug("Writing audio prefix");
                            data.put(PREFIX_AUDIO_FRAME);
                            // track audio frame count
                            audioCount++;
                        }
                        // do we need to add the mdat offset to the sample position?
                        //dataSource.position(samplePos);
                        // read from the channel
                        dataSource.read(data);
                    } catch (IOException e) {
                        log.error("Error on channel position / read", e);
                    }
                    // chunk the data
                    IoBuffer payload = IoBuffer.wrap(data.array());
                    // create the tag
                    tag = new Tag(type, time, payload.limit(), payload, prevFrameSize);
                    //log.debug("Read tag - type: {} body size: {}", (type == TYPE_AUDIO ? "Audio" : "Video"), tag.getBodySize());
                    // increment the frame number
                    currentFrame++;
                    // set the frame / tag size
                    prevFrameSize = tag.getBodySize();
                }
            } catch (InterruptedException e) {
                log.warn("Exception acquiring lock", e);
            } finally {
                lock.release();
            }
        } else {
            log.warn("No frames are available for the requested item");
        }
        //log.debug("Tag: {}", tag);
        return tag;
    }

    /**
     * Performs frame analysis and generates metadata for use in seeking. All the frames are analyzed and sorted together based on time and
     * offset.
     */
    public void analyzeFrames() {
        log.debug("Analyzing frames - video samples/chunks: {}", videoSamplesToChunks);
        // Maps positions, samples, timestamps to one another
        timePosMap = new HashMap<>();
        samplePosMap = new HashMap<>();
        // tag == sample
        int sample = 1;
        // position
        Long pos = null;
        // if audio-only, skip this
        if (videoSamplesToChunks != null) {
            // handle composite times
            int compositeIndex = 0;
            CompositionOffsetsBox.Entry compositeTimeEntry = null;
            if (compositionTimes != null && !compositionTimes.isEmpty()) {
                compositeTimeEntry = compositionTimes.remove(0);
            }
            for (int i = 0; i < videoSamplesToChunks.size(); i++) {
                SampleToChunkEntry record = videoSamplesToChunks.get(i);
                long firstChunk = record.getFirst();
                long lastChunk = videoChunkOffsets.length;
                if (i < videoSamplesToChunks.size() - 1) {
                    SampleToChunkEntry nextRecord = videoSamplesToChunks.get(i + 1);
                    lastChunk = nextRecord.getFirst() - 1;
                }
                for (long chunk = firstChunk; chunk <= lastChunk; chunk++) {
                    long sampleCount = record.getCount(); // record.getSamplesPerChunk();
                    pos = videoChunkOffsets[(int) (chunk - 1)];
                    while (sampleCount > 0) {
                        //log.debug("Position: {}", pos);
                        samplePosMap.put(sample, pos);
                        // calculate ts
                        double ts = (videoSampleDuration * (sample - 1)) / videoTimeScale;
                        // check to see if the sample is a keyframe
                        boolean keyframe = false;
                        // some files appear not to have sync samples
                        if (syncSamples != null) {
                            keyframe = ArrayUtils.contains(syncSamples, sample);
                            if (seekPoints == null) {
                                seekPoints = new LinkedList<>();
                            }
                            // get the timestamp
                            int frameTs = (int) Math.round(ts * 1000.0);
                            // add each key frames timestamp to the seek points list
                            if (keyframe) {
                                seekPoints.add(frameTs);
                            }
                            timePosMap.put(frameTs, pos);
                        } else {
                            log.debug("No sync samples available");
                        }
                        // size of the sample
                        int size = (int) videoSamples[sample - 1];
                        // create a frame
                        MP4Frame frame = new MP4Frame();
                        frame.setKeyFrame(keyframe);
                        frame.setOffset(pos);
                        frame.setSize(size);
                        frame.setTime(ts);
                        frame.setType(TYPE_VIDEO);
                        // set time offset value from composition records
                        if (compositeTimeEntry != null) {
                            // how many samples have this offset
                            int consecutiveSamples = compositeTimeEntry.getCount();
                            frame.setTimeOffset(compositeTimeEntry.getOffset());
                            // increment our count
                            compositeIndex++;
                            if (compositeIndex - consecutiveSamples == 0) {
                                // ensure there are still times available
                                if (!compositionTimes.isEmpty()) {
                                    // get the next one
                                    compositeTimeEntry = compositionTimes.remove(0);
                                }
                                // reset
                                compositeIndex = 0;
                            }
                            log.debug("Composite sample #{} {}", sample, frame);
                        }
                        // add the frame
                        frames.add(frame);
                        log.debug("Sample #{} {}", sample, frame);
                        // inc and dec stuff
                        pos += size;
                        sampleCount--;
                        sample++;
                    }
                }
            }
            log.debug("Sample position map (video): {}", samplePosMap);
        }
        // if video-only, skip this
        if (audioSamplesToChunks != null) {
            // add the audio frames / samples / chunks
            sample = 1;
            for (int i = 0; i < audioSamplesToChunks.size(); i++) {
                SampleToChunkEntry record = audioSamplesToChunks.get(i);
                long firstChunk = record.getFirst();
                long lastChunk = audioChunkOffsets.length;
                if (i < audioSamplesToChunks.size() - 1) {
                    SampleToChunkEntry nextRecord = audioSamplesToChunks.get(i + 1);
                    lastChunk = nextRecord.getFirst() - 1;
                }
                for (long chunk = firstChunk; chunk <= lastChunk; chunk++) {
                    long sampleCount = record.getCount(); // record.getSamplesPerChunk();
                    pos = audioChunkOffsets[(int) (chunk - 1)];
                    while (sampleCount > 0) {
                        // calculate ts
                        double ts = (audioSampleDuration * (sample - 1)) / audioTimeScale;
                        // sample size
                        int size = 0;
                        // if we have no samples, skip size check as its probably not aac
                        if (audioSamples.length > 0) {
                            //update sample size
                            size = (int) audioSamples[sample - 1];
                            // skip empty AAC data which is 6 bytes long
                            log.trace("Audio sample - size: {} pos: {}", size, pos);
                            if (size == 6) {
                                try {
                                    // get current pos
                                    long position = dataSource.position();
                                    // jump to data position
                                    dataSource.setPosition(pos);
                                    // create buffer to store bytes so we can check them
                                    ByteBuffer dst = ByteBuffer.allocate(6);
                                    // read the data
                                    dataSource.read(dst);
                                    // flip it
                                    dst.flip();
                                    // reset the position
                                    dataSource.setPosition(position);
                                    byte[] tmp = dst.array();
                                    log.trace("Audio bytes: {} equal: {}", HexDump.byteArrayToHexString(tmp), Arrays.equals(EMPTY_AAC, tmp));
                                    if (Arrays.equals(EMPTY_AAC, tmp)) {
                                        log.trace("Skipping empty AAC data frame");
                                        // update counts
                                        pos += size;
                                        sampleCount--;
                                        sample++;
                                        // read next
                                        continue;
                                    }
                                } catch (IOException e) {
                                    log.warn("Exception during audio analysis", e);
                                }
                            }
                        }
                        // set audio sample size
                        size = (int) (size != 0 ? size : audioSampleSize);
                        // create a frame
                        MP4Frame frame = new MP4Frame();
                        frame.setOffset(pos);
                        frame.setSize(size);
                        frame.setTime(ts);
                        frame.setType(TYPE_AUDIO);
                        frames.add(frame);
                        //log.debug("Sample #{} {}", sample, frame);
                        // update counts
                        pos += size;
                        sampleCount--;
                        sample++;
                    }
                }
            }
        }
        //sort the frames
        Collections.sort(frames);
        log.debug("Frames count: {}", frames.size());
        //log.debug("Frames: {}", frames);
        //release some memory
        if (audioSamplesToChunks != null) {
            audioChunkOffsets = null;
            audioSamplesToChunks = null;
        }
        if (videoSamplesToChunks != null) {
            videoChunkOffsets = null;
            videoSamplesToChunks = null;
        }
        if (syncSamples != null) {
            syncSamples = null;
        }
    }

    /**
     * Put the current position to pos. The caller must ensure the pos is a valid one.
     *
     * @param pos
     *            position to move to in file / channel
     */
    @Override
    public void position(long pos) {
        log.debug("Position: {}", pos);
        log.debug("Current frame: {}", currentFrame);
        int len = frames.size();
        MP4Frame frame = null;
        for (int f = 0; f < len; f++) {
            frame = frames.get(f);
            long offset = frame.getOffset();
            //look for pos to match frame offset or grab the first keyframe
            //beyond the offset
            if (pos == offset || (offset > pos && frame.isKeyFrame())) {
                //ensure that it is a keyframe
                if (!frame.isKeyFrame()) {
                    log.debug("Frame #{} was not a key frame, so trying again..", f);
                    continue;
                }
                log.info("Frame #{} found for seek: {}", f, frame);
                createPreStreamingTags((int) (frame.getTime() * 1000), true);
                currentFrame = f;
                break;
            }
            prevVideoTS = (int) (frame.getTime() * 1000);
        }
        //
        log.debug("Setting current frame: {}", currentFrame);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        log.debug("Close");
        if (dataSource != null) {
            try {
                dataSource.close();
            } catch (IOException e) {
                log.error("Channel close {}", e);
            } finally {
                if (frames != null) {
                    frames.clear();
                    frames = null;
                }
            }
        }
    }

    public void setVideoCodecId(String videoCodecId) {
        this.videoCodecId = videoCodecId;
    }

    public void setAudioCodecId(String audioCodecId) {
        this.audioCodecId = audioCodecId;
    }

    public ITag readTagHeader() {
        return null;
    }

    @Override
    public KeyFrameMeta analyzeKeyFrames() {
        KeyFrameMeta result = new KeyFrameMeta();
        result.audioOnly = hasAudio && !hasVideo;
        result.duration = duration;
        if (result.audioOnly) {
            result.positions = new long[frames.size()];
            result.timestamps = new int[frames.size()];
            result.audioOnly = true;
            for (int i = 0; i < result.positions.length; i++) {
                frames.get(i).setKeyFrame(true);
                result.positions[i] = frames.get(i).getOffset();
                result.timestamps[i] = (int) Math.round(frames.get(i).getTime() * 1000.0);
            }
        } else {
            if (seekPoints != null) {
                int seekPointCount = seekPoints.size();
                result.positions = new long[seekPointCount];
                result.timestamps = new int[seekPointCount];
                for (int idx = 0; idx < seekPointCount; idx++) {
                    final Integer ts = seekPoints.get(idx);
                    result.positions[idx] = timePosMap.get(ts);
                    result.timestamps[idx] = ts;
                }
            } else {
                log.warn("Seek points array was null");
            }
        }
        return result;
    }

}
