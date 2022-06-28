/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2016 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.mp4.impl;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
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

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.mina.core.buffer.IoBuffer;
import org.mp4parser.Box;
import org.mp4parser.Container;
import org.mp4parser.IsoFile;
import org.mp4parser.boxes.adobe.ActionMessageFormat0SampleEntryBox;
import org.mp4parser.boxes.apple.AppleWaveBox;
import org.mp4parser.boxes.iso14496.part1.objectdescriptors.AudioSpecificConfig;
import org.mp4parser.boxes.iso14496.part1.objectdescriptors.DecoderConfigDescriptor;
import org.mp4parser.boxes.iso14496.part1.objectdescriptors.DecoderSpecificInfo;
import org.mp4parser.boxes.iso14496.part1.objectdescriptors.ESDescriptor;
import org.mp4parser.boxes.iso14496.part12.AbstractMediaHeaderBox;
import org.mp4parser.boxes.iso14496.part12.ChunkOffset64BitBox;
import org.mp4parser.boxes.iso14496.part12.ChunkOffsetBox;
import org.mp4parser.boxes.iso14496.part12.CompositionTimeToSample;
import org.mp4parser.boxes.iso14496.part12.HandlerBox;
import org.mp4parser.boxes.iso14496.part12.MediaBox;
import org.mp4parser.boxes.iso14496.part12.MediaDataBox;
import org.mp4parser.boxes.iso14496.part12.MediaHeaderBox;
import org.mp4parser.boxes.iso14496.part12.MediaInformationBox;
import org.mp4parser.boxes.iso14496.part12.MovieBox;
import org.mp4parser.boxes.iso14496.part12.MovieExtendsBox;
import org.mp4parser.boxes.iso14496.part12.MovieFragmentBox;
import org.mp4parser.boxes.iso14496.part12.MovieFragmentHeaderBox;
import org.mp4parser.boxes.iso14496.part12.MovieFragmentRandomAccessBox;
import org.mp4parser.boxes.iso14496.part12.MovieHeaderBox;
import org.mp4parser.boxes.iso14496.part12.SampleDependencyTypeBox;
import org.mp4parser.boxes.iso14496.part12.SampleDescriptionBox;
import org.mp4parser.boxes.iso14496.part12.SampleSizeBox;
import org.mp4parser.boxes.iso14496.part12.SampleTableBox;
import org.mp4parser.boxes.iso14496.part12.SampleToChunkBox;
import org.mp4parser.boxes.iso14496.part12.SoundMediaHeaderBox;
import org.mp4parser.boxes.iso14496.part12.SyncSampleBox;
import org.mp4parser.boxes.iso14496.part12.TimeToSampleBox;
import org.mp4parser.boxes.iso14496.part12.TrackBox;
import org.mp4parser.boxes.iso14496.part12.TrackExtendsBox;
import org.mp4parser.boxes.iso14496.part12.TrackFragmentBox;
import org.mp4parser.boxes.iso14496.part12.TrackFragmentHeaderBox;
import org.mp4parser.boxes.iso14496.part12.TrackHeaderBox;
import org.mp4parser.boxes.iso14496.part12.TrackRunBox;
import org.mp4parser.boxes.iso14496.part12.VideoMediaHeaderBox;
import org.mp4parser.boxes.iso14496.part14.ESDescriptorBox;
import org.mp4parser.boxes.iso14496.part15.AvcConfigurationBox;
import org.mp4parser.boxes.iso14496.part15.AvcDecoderConfigurationRecord;
import org.mp4parser.boxes.sampleentry.AudioSampleEntry;
import org.mp4parser.boxes.sampleentry.SampleEntry;
import org.mp4parser.boxes.sampleentry.VisualSampleEntry;
import org.mp4parser.tools.Path;
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

    /**
     * Provider of boxes
     */
    private IsoFile isoFile;

    /** Mapping between file position and timestamp in ms. */
    private HashMap<Integer, Long> timePosMap;

    private HashMap<Integer, Long> samplePosMap;

    /** Whether or not the clip contains a video track */
    private boolean hasVideo = false;

    /** Whether or not the clip contains an audio track */
    private boolean hasAudio = false;

    //default video codec
    private String videoCodecId = "avc1";

    //default audio codec
    private String audioCodecId = "mp4a";

    //decoder bytes / configs
    private byte[] audioDecoderBytes;

    private byte[] videoDecoderBytes;

    // duration in milliseconds
    private long duration;

    // movie time scale
    private long timeScale;

    private int width;

    private int height;

    //audio sample rate kHz
    private double audioTimeScale;

    private int audioChannels;

    //default to aac lc
    private int audioCodecType = 1;

    private long videoSampleCount;

    private double fps;

    private double videoTimeScale;

    private int avcLevel;

    private int avcProfile;

    private String formattedDuration;

    //samples to chunk mappings
    private List<SampleToChunkBox.Entry> videoSamplesToChunks;

    private List<SampleToChunkBox.Entry> audioSamplesToChunks;

    //keyframe - sample numbers
    private long[] syncSamples;

    //samples
    private long[] videoSamples;

    private long[] audioSamples;

    private long audioSampleSize;

    //chunk offsets
    private long[] videoChunkOffsets;

    private long[] audioChunkOffsets;

    //sample duration
    private long videoSampleDuration = 125;

    private long audioSampleDuration = 1024;

    //keep track of current frame / sample
    private int currentFrame = 0;

    private int prevFrameSize = 0;

    private int prevVideoTS = -1;

    private List<MP4Frame> frames = new ArrayList<MP4Frame>();

    private long audioCount;

    private long videoCount;

    // composition time to sample entries
    private List<CompositionTimeToSample.Entry> compositionTimes;

    /**
     * Container for metadata and any other tags that should be sent prior to media data.
     */
    private LinkedList<ITag> firstTags = new LinkedList<ITag>();

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
    public MP4Reader(File f) throws IOException {
        if (null == f) {
            log.warn("Reader was passed a null file");
            log.debug("{}", ToStringBuilder.reflectionToString(this));
        }
        if (f.exists() && f.canRead()) {
            // create a datasource / channel
            dataSource = Files.newByteChannel(Paths.get(f.toURI()));
            // instance an iso file from mp4parser
            isoFile = new IsoFile(dataSource);
            //decode all the info that we want from the atoms
            decodeHeader();
            //analyze the samples/chunks and build the keyframe meta data
            analyzeFrames();
            //add meta data
            firstTags.add(createFileMeta());
            //create / add the pre-streaming (decoder config) tags
            createPreStreamingTags(0, false);
        } else {
            log.warn("Reader was passed an unreadable or non-existant file");
        }
    }

    /**
     * This handles the moov atom being at the beginning or end of the file, so the mdat may also be before or after the moov atom.
     */
    @Override
    public void decodeHeader() {
        try {
            // we want a moov and an mdat, anything else will throw the invalid file type error
            MovieBox moov = isoFile.getBoxes(MovieBox.class).get(0);
            if (log.isDebugEnabled()) {
                log.debug("moov children: {}", moov.getBoxes().size());
                dumpBox(moov);
            }
            // get the movie header
            MovieHeaderBox mvhd = moov.getMovieHeaderBox();
            // get the timescale and duration
            timeScale = mvhd.getTimescale();
            duration = mvhd.getDuration();
            log.debug("Time scale {} Duration {}", timeScale, duration);
            double lengthInSeconds = (double) duration / timeScale;
            log.debug("Seconds {}", lengthInSeconds);
            // look at the tracks
            log.debug("Tracks: {}", moov.getTrackCount());
            List<TrackBox> tracks = moov.getBoxes(TrackBox.class); // trak
            for (TrackBox trak : tracks) {
                if (log.isDebugEnabled()) {
                    log.debug("trak children: {}", trak.getBoxes().size());
                    dumpBox(trak);
                }
                TrackHeaderBox tkhd = trak.getTrackHeaderBox(); // tkhd
                log.debug("Track id: {}", tkhd.getTrackId());
                if (tkhd != null && tkhd.getWidth() > 0) {
                    width = (int) tkhd.getWidth();
                    height = (int) tkhd.getHeight();
                    log.debug("Width {} x Height {}", width, height);
                }
                MediaBox mdia = trak.getMediaBox(); // mdia
                long scale = 0;
                boolean isAudio = false, isVideo = false;
                if (mdia != null) {
                    if (log.isDebugEnabled()) {
                        log.debug("mdia children: {}", mdia.getBoxes().size());
                        dumpBox(mdia);
                    }
                    MediaHeaderBox mdhd = mdia.getMediaHeaderBox(); // mdhd
                    if (mdhd != null) {
                        log.debug("Media data header atom found");
                        // this will be for either video or audio depending media info
                        scale = mdhd.getTimescale();
                        log.debug("Time scale {}", scale);
                    }
                    HandlerBox hdlr = mdia.getHandlerBox(); // hdlr
                    if (hdlr != null) {
                        String hdlrType = hdlr.getHandlerType();
                        if ("vide".equals(hdlrType)) {
                            hasVideo = true;
                            if (scale > 0) {
                                videoTimeScale = scale * 1.0;
                                log.debug("Video time scale: {}", videoTimeScale);
                            }
                        } else if ("soun".equals(hdlrType)) {
                            hasAudio = true;
                            if (scale > 0) {
                                audioTimeScale = scale * 1.0;
                                log.debug("Audio time scale: {}", audioTimeScale);
                            }
                        } else {
                            log.debug("Unhandled handler type: {}", hdlrType);
                        }
                    }
                    MediaInformationBox minf = mdia.getMediaInformationBox();
                    if (minf != null) {
                        if (log.isDebugEnabled()) {
                            log.debug("minf children: {}", minf.getBoxes().size());
                            dumpBox(minf);
                        }
                        AbstractMediaHeaderBox abs = minf.getMediaHeaderBox();
                        if (abs != null) {
                            if (abs instanceof SoundMediaHeaderBox) { // smhd
                                //SoundMediaHeaderBox smhd = (SoundMediaHeaderBox) abs;
                                log.debug("Sound header atom found");
                                isAudio = true;
                            } else if (abs instanceof VideoMediaHeaderBox) { // vmhd
                                //VideoMediaHeaderBox vmhd = (VideoMediaHeaderBox) abs;
                                log.debug("Video header atom found");
                                isVideo = true;
                            } else {
                                log.debug("Unhandled media header box: {}", abs.getType());
                            }
                        } else {
                            log.debug("Null media header box");
                        }
                    }
                }
                SampleTableBox stbl = trak.getSampleTableBox(); // mdia/minf/stbl
                if (stbl != null) {
                    if (log.isDebugEnabled()) {
                        log.debug("stbl children: {}", stbl.getBoxes().size());
                        dumpBox(stbl);
                    }
                    SampleDescriptionBox stsd = stbl.getSampleDescriptionBox(); // stsd
                    if (stsd != null) {
                        //stsd: mp4a, avc1, mp4v
                        //String type = stsd.getType();
                        if (log.isDebugEnabled()) {
                            log.debug("stsd children: {}", stsd.getBoxes().size());
                            dumpBox(stsd);
                        }
                        SampleEntry entry = stsd.getSampleEntry();
                        if (entry != null) {
                            log.debug("Sample entry type: {}", entry.getType());
                            // determine if audio or video and process from there
                            if (entry instanceof AudioSampleEntry) {
                                processAudioBox(stbl, (AudioSampleEntry) entry, scale);
                            } else if (entry instanceof VisualSampleEntry) {
                                processVideoBox(stbl, (VisualSampleEntry) entry, scale);
                            }
                        } else {
                            log.debug("Sample entry was null");
                            if (isVideo) {
                                processVideoBox(stbl, scale);
                            } else if (isAudio) {
                                processAudioBox(stbl, scale);
                            }
                        }
                    }
                }
            }
            //calculate FPS
            fps = (videoSampleCount * timeScale) / (double) duration;
            log.debug("FPS calc: ({} * {}) / {}", new Object[] { videoSampleCount, timeScale, duration });
            log.debug("FPS: {}", fps);
            //real duration
            StringBuilder sb = new StringBuilder();
            double videoTime = ((double) duration / (double) timeScale);
            log.debug("Video time: {}", videoTime);
            int minutes = (int) (videoTime / 60);
            if (minutes > 0) {
                sb.append(minutes);
                sb.append('.');
            }
            //formatter for seconds / millis
            NumberFormat df = DecimalFormat.getInstance();
            df.setMaximumFractionDigits(2);
            sb.append(df.format((videoTime % 60)));
            formattedDuration = sb.toString();
            log.debug("Time: {}", formattedDuration);

            List<MediaDataBox> mdats = isoFile.getBoxes(MediaDataBox.class);
            if (mdats != null && !mdats.isEmpty()) {
                log.debug("mdat count: {}", mdats.size());
            }
            // handle fragmentation
            boolean fragmented = false;
            // detect whether or not this movie contains fragments first
            List<MovieFragmentBox> moofs = isoFile.getBoxes(MovieFragmentBox.class); // moof
            if (moofs != null && !moofs.isEmpty()) {
                log.info("Movie contains {} framents", moofs.size());
                fragmented = true;
                for (MovieFragmentBox moof : moofs) {
                    dumpBox(moof);
                    MovieFragmentHeaderBox mfhd = moof.getBoxes(MovieFragmentHeaderBox.class).get(0);
                    if (mfhd != null) {
                        log.debug("Sequence: {}", mfhd.getSequenceNumber());
                    }
                    List<TrackFragmentBox> trafs = moof.getBoxes(TrackFragmentBox.class);
                    for (TrackFragmentBox traf : trafs) {
                        TrackFragmentHeaderBox tfhd = traf.getTrackFragmentHeaderBox();
                        log.debug("tfhd: {}", tfhd);
                    }
                    List<TrackExtendsBox> trexs = moof.getBoxes(TrackExtendsBox.class);
                    for (TrackExtendsBox trex : trexs) {
                        log.debug("trex - track id: {} duration: {} sample size: {}", trex.getTrackId(), trex.getDefaultSampleDuration(), trex.getDefaultSampleSize());
                    }
                    //List<Long> syncSamples = moof.getSyncSamples(sdtp);
                    if (compositionTimes == null) {
                        compositionTimes = new ArrayList<>();
                    }
                    LinkedList<Integer> dataOffsets = new LinkedList<>();
                    LinkedList<Long> sampleSizes = new LinkedList<>();
                    List<TrackRunBox> truns = moof.getTrackRunBoxes();
                    log.info("Fragment contains {} TrackRunBox entries", truns.size());
                    for (TrackRunBox trun : truns) {
                        log.debug("trun - {}", trun);
                        //videoSamplesToChunks
                        if (trun.isDataOffsetPresent()) {
                            dataOffsets.add(trun.getDataOffset());
                        }
                        videoSampleCount += trun.getSampleCount();
                        List<TrackRunBox.Entry> recs = trun.getEntries();
                        log.info("TrackRunBox contains {} entries", recs.size());
                        for (TrackRunBox.Entry rec : recs) {
                            log.info("Entry: {}", rec);
                            if (trun.isSampleCompositionTimeOffsetPresent()) {
                                CompositionTimeToSample.Entry ctts = new CompositionTimeToSample.Entry((int) trun.getSampleCount(), (int) rec.getSampleCompositionTimeOffset());
                                compositionTimes.add(ctts);
                            }
                            sampleSizes.add(rec.getSampleSize());
                            if (trun.isSampleDurationPresent()) {
                                videoSampleDuration += rec.getSampleDuration();
                            }
                        }
                    }
                    // SampleToChunkBox.Entry

                    log.info("Video duration: {}", videoSampleDuration);
                    videoSamples = new long[sampleSizes.size()];
                    for (int i = 0; i < videoSamples.length; i++) {
                        videoSamples[i] = sampleSizes.remove();
                    }
                    log.info("Video samples: {}", Arrays.toString(videoSamples));
                    videoChunkOffsets = new long[dataOffsets.size()];
                    for (int i = 0; i < videoChunkOffsets.length; i++) {
                        videoChunkOffsets[i] = dataOffsets.remove();
                    }
                    log.info("Video chunk offsets: {}", Arrays.toString(videoChunkOffsets));
                }
            }
            if (isoFile.getBoxes(MovieFragmentRandomAccessBox.class).size() > 0) { // mfra
                log.info("Movie contains frament random access info");
            }
            if (isoFile.getBoxes(ActionMessageFormat0SampleEntryBox.class).size() > 0) {
                log.info("Movie contains AMF entries");
            }
            // if we have fragments, we should have an mvex
            if (fragmented) {
                MovieExtendsBox mvex = moov.getBoxes(MovieExtendsBox.class).get(0); // mvex
                dumpBox(mvex);
                List<TrackExtendsBox> trexs = mvex.getBoxes(TrackExtendsBox.class);
                for (TrackExtendsBox trex : trexs) {
                    log.debug("trex - track id: {} duration: {} sample size: {}", trex.getTrackId(), trex.getDefaultSampleDuration(), trex.getDefaultSampleSize());
                }
            }
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
    public static void dumpBox(Container box) {
        log.debug("Dump box: {}", box);
        for (Box bx : box.getBoxes()) {
            log.debug("{} child: {}", box, bx.getType());
        }
    }

    /**
     * Process the video information contained in the atoms.
     *
     * @param stbl
     * @param vse
     *            VisualSampleEntry
     * @param scale
     *            timescale
     */
    private void processVideoBox(SampleTableBox stbl, VisualSampleEntry vse, long scale) {
        // get codec
        String codecName = vse.getType();
        //set the video codec here - may be avc1 or mp4v
        setVideoCodecId(codecName);
        if ("avc1".equals(codecName)) {
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
        } else if ("mp4v".equals(codecName)) {
            if (vse.getBoxes(ESDescriptorBox.class).size() > 0) {
                // look for esds
                ESDescriptorBox esds = vse.getBoxes(ESDescriptorBox.class).get(0);
                if (esds != null) {
                    ESDescriptor descriptor = esds.getEsDescriptor();
                    //log.debug("ES descriptor: {}", descriptor);
                    if (descriptor != null) {
                        DecoderConfigDescriptor decConf = descriptor.getDecoderConfigDescriptor();
                        if (decConf != null) {
                            DecoderSpecificInfo decInfo = decConf.getDecoderSpecificInfo();
                            ByteBuffer byteBuffer = decInfo.serialize();
                            videoDecoderBytes = new byte[byteBuffer.limit()];
                            byteBuffer.get(videoDecoderBytes);
                        }
                    }
                }
            }
        } else {
            log.debug("Unrecognized video codec: {} compressor name: {}", codecName, vse.getCompressorname());
        }
        processVideoStbl(stbl, scale);
    }

    /**
     * Process the video information contained in the atoms.
     *
     * @param stbl
     * @param scale
     *            timescale
     */
    private void processVideoBox(SampleTableBox stbl, long scale) {
        AvcConfigurationBox avcC = (AvcConfigurationBox) Path.getPath(isoFile, "/moov/trak/mdia/minf/stbl/stsd/drmi/avcC");
        if (avcC != null) {
            long videoConfigContentSize = avcC.getContentSize();
            log.debug("AVCC size: {}", videoConfigContentSize);
            //			ByteBuffer byteBuffer = ByteBuffer.allocate((int) videoConfigContentSize);
            //			avc1.avcDecoderConfigurationRecord.getContent(byteBuffer);
            //			byteBuffer.flip();
            //			videoDecoderBytes = new byte[byteBuffer.limit()];
            //			byteBuffer.get(videoDecoderBytes);
        } else {
            log.warn("avcC atom not found");
        }
        processVideoStbl(stbl, scale);
    }

    /**
     * Process an stbl atom with containing video information.
     *
     * @param stbl
     * @param scale
     */
    private void processVideoStbl(SampleTableBox stbl, long scale) {
        // stsc - has Records
        SampleToChunkBox stsc = stbl.getSampleToChunkBox(); // stsc
        if (stsc != null) {
            log.debug("Sample to chunk atom found");
            videoSamplesToChunks = stsc.getEntries();
            log.debug("Video samples to chunks: {}", videoSamplesToChunks.size());
            for (SampleToChunkBox.Entry s2c : videoSamplesToChunks) {
                log.info("Entry: {}", s2c);
            }
        }
        // stsz - has Samples
        SampleSizeBox stsz = stbl.getSampleSizeBox(); // stsz
        if (stsz != null) {
            log.debug("Sample size atom found");
            videoSamples = stsz.getSampleSizes();
            // if sample size is 0 then the table must be checked due to variable sample sizes
            log.debug("Sample size: {}", stsz.getSampleSize());
            videoSampleCount = stsz.getSampleCount();
            log.debug("Sample count: {}", videoSampleCount);
        }
        // stco - has Chunks
        ChunkOffsetBox stco = stbl.getChunkOffsetBox(); // stco / co64
        if (stco != null) {
            log.debug("Chunk offset atom found");
            videoChunkOffsets = stco.getChunkOffsets();
            log.debug("Chunk count: {}", videoChunkOffsets.length);
        } else {
            // co64 - has Chunks
            List<ChunkOffset64BitBox> stblBoxes = stbl.getBoxes(ChunkOffset64BitBox.class);
            if (stblBoxes != null && !stblBoxes.isEmpty()) {
                ChunkOffset64BitBox co64 = stblBoxes.get(0);
                if (co64 != null) {
                    log.debug("Chunk offset (64) atom found");
                    videoChunkOffsets = co64.getChunkOffsets();
                    log.debug("Chunk count: {}", videoChunkOffsets.length);
                    // double the timescale for video, since it seems to run at
                    // half-speed when co64 is used (seems hacky)
                    //videoTimeScale = scale * 2.0;
                    //log.debug("Video time scale: {}", videoTimeScale);
                }
            }
        }
        // stss - has Sync - no sync means all samples are keyframes
        SyncSampleBox stss = stbl.getSyncSampleBox(); // stss
        if (stss != null) {
            log.debug("Sync sample atom found");
            syncSamples = stss.getSampleNumber();
            log.debug("Keyframes: {}", syncSamples.length);
        }
        // stts - has TimeSampleRecords
        TimeToSampleBox stts = stbl.getTimeToSampleBox(); // stts
        if (stts != null) {
            log.debug("Time to sample atom found");
            List<TimeToSampleBox.Entry> records = stts.getEntries();
            log.debug("Video time to samples: {}", records.size());
            // handle instance where there are no actual records (bad f4v?)
            if (records.size() > 0) {
                TimeToSampleBox.Entry rec = records.get(0);
                log.debug("Samples = {} delta = {}", rec.getCount(), rec.getDelta());
                //if we have 1 record it means all samples have the same duration
                videoSampleDuration = rec.getDelta();
            }
        }
        // ctts - (composition) time to sample
        CompositionTimeToSample ctts = stbl.getCompositionTimeToSample(); // ctts
        if (ctts != null) {
            log.debug("Composition time to sample atom found");
            compositionTimes = ctts.getEntries();
            log.debug("Record count: {}", compositionTimes.size());
            if (log.isTraceEnabled()) {
                for (CompositionTimeToSample.Entry rec : compositionTimes) {
                    double offset = rec.getOffset();
                    if (scale > 0d) {
                        offset = (offset / (double) scale) * 1000.0;
                        rec.setOffset((int) offset);
                    }
                    log.trace("Samples = {} offset = {}", rec.getCount(), rec.getOffset());
                }
            }
        }
        // sdtp - sample dependency type
        SampleDependencyTypeBox sdtp = stbl.getSampleDependencyTypeBox(); // sdtp
        if (sdtp != null) {
            log.debug("Independent and disposable samples atom found");
            List<SampleDependencyTypeBox.Entry> recs = sdtp.getEntries();
            for (SampleDependencyTypeBox.Entry rec : recs) {
                log.debug("{}", rec);
            }
        }
    }

    /**
     * Process the audio information contained in the atoms.
     *
     * @param stbl
     * @param ase
     *            AudioSampleEntry
     * @param scale
     *            timescale
     */
    private void processAudioBox(SampleTableBox stbl, AudioSampleEntry ase, long scale) {
        // get codec
        String codecName = ase.getType();
        // set the audio codec here - may be mp4a or...
        setAudioCodecId(codecName);
        log.debug("Sample size: {}", ase.getSampleSize());
        long ats = ase.getSampleRate();
        // skip invalid audio time scale
        if (ats > 0) {
            audioTimeScale = ats * 1.0;
        }
        log.debug("Sample rate (audio time scale): {}", audioTimeScale);
        audioChannels = ase.getChannelCount();
        log.debug("Channels: {}", audioChannels);
        if (ase.getBoxes(ESDescriptorBox.class).size() > 0) {
            // look for esds
            ESDescriptorBox esds = ase.getBoxes(ESDescriptorBox.class).get(0);
            if (esds == null) {
                log.debug("esds not found in default path");
                // check for decompression param atom
                AppleWaveBox wave = ase.getBoxes(AppleWaveBox.class).get(0);
                if (wave != null) {
                    log.debug("wave atom found");
                    // wave/esds
                    esds = wave.getBoxes(ESDescriptorBox.class).get(0);
                    if (esds == null) {
                        log.debug("esds not found in wave");
                        // mp4a/esds
                        //AC3SpecificBox mp4a = wave.getBoxes(AC3SpecificBox.class).get(0);
                        //esds = mp4a.getBoxes(ESDescriptorBox.class).get(0);
                    }
                }
            }
            //mp4a: esds
            if (esds != null) {
                // http://stackoverflow.com/questions/3987850/mp4-atom-how-to-discriminate-the-audio-codec-is-it-aac-or-mp3
                ESDescriptor descriptor = esds.getEsDescriptor();
                if (descriptor != null) {
                    DecoderConfigDescriptor configDescriptor = descriptor.getDecoderConfigDescriptor();
                    AudioSpecificConfig audioInfo = configDescriptor.getAudioSpecificInfo();
                    if (audioInfo != null) {
                        audioDecoderBytes = audioInfo.getConfigBytes();
                        /*
                         * the first 5 (0-4) bits tell us about the coder used for aacaot/aottype http://wiki.multimedia.cx/index.php?title=MPEG-4_Audio 0 - NULL 1 - AAC Main (a deprecated AAC profile
                         * from MPEG-2) 2 - AAC LC or backwards compatible HE-AAC 3 - AAC Scalable Sample Rate 4 - AAC LTP (a replacement for AAC Main, rarely used) 5 - HE-AAC explicitly signaled
                         * (Non-backward compatible) 23 - Low Delay AAC 29 - HE-AACv2 explicitly signaled 32 - MP3on4 Layer 1 33 - MP3on4 Layer 2 34 - MP3on4 Layer 3
                         */
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
                        log.debug("Audio coder type: {} {} id: {}", new Object[] { audioCoderType, Integer.toBinaryString(audioCoderType), audioCodecId });
                    } else {
                        log.debug("Audio specific config was not found");
                        DecoderSpecificInfo info = configDescriptor.getDecoderSpecificInfo();
                        if (info != null) {
                            log.debug("Decoder info found: {}", info.getTag());
                            // qcelp == 5
                        }
                    }
                } else {
                    log.debug("No ES descriptor found");
                }
            }
        } else {
            log.debug("Audio sample entry had no descriptor");
        }
        processAudioStbl(stbl, scale);
    }

    /**
     * Process the audio information contained in the atoms.
     *
     * @param stbl
     * @param scale
     *            timescale
     */
    private void processAudioBox(SampleTableBox stbl, long scale) {
        processAudioStbl(stbl, scale);
    }

    private void processAudioStbl(SampleTableBox stbl, long scale) {
        //stsc - has Records
        SampleToChunkBox stsc = stbl.getSampleToChunkBox(); // stsc
        if (stsc != null) {
            log.debug("Sample to chunk atom found");
            audioSamplesToChunks = stsc.getEntries();
            log.debug("Audio samples to chunks: {}", audioSamplesToChunks.size());
            // handle instance where there are no actual records (bad f4v?)
        }
        //stsz - has Samples
        SampleSizeBox stsz = stbl.getSampleSizeBox(); // stsz
        if (stsz != null) {
            log.debug("Sample size atom found");
            audioSamples = stsz.getSampleSizes();
            log.debug("Samples: {}", audioSamples.length);
            // if sample size is 0 then the table must be checked due to variable sample sizes
            audioSampleSize = stsz.getSampleSize();
            log.debug("Sample size: {}", audioSampleSize);
            long audioSampleCount = stsz.getSampleCount();
            log.debug("Sample count: {}", audioSampleCount);
        }
        //stco - has Chunks
        ChunkOffsetBox stco = stbl.getChunkOffsetBox(); // stco / co64
        if (stco != null) {
            log.debug("Chunk offset atom found");
            audioChunkOffsets = stco.getChunkOffsets();
            log.debug("Chunk count: {}", audioChunkOffsets.length);
        } else {
            //co64 - has Chunks
            ChunkOffset64BitBox co64 = stbl.getBoxes(ChunkOffset64BitBox.class).get(0);
            if (co64 != null) {
                log.debug("Chunk offset (64) atom found");
                audioChunkOffsets = co64.getChunkOffsets();
                log.debug("Chunk count: {}", audioChunkOffsets.length);
            }
        }
        //stts - has TimeSampleRecords
        TimeToSampleBox stts = stbl.getTimeToSampleBox(); // stts
        if (stts != null) {
            log.debug("Time to sample atom found");
            List<TimeToSampleBox.Entry> records = stts.getEntries();
            log.debug("Audio time to samples: {}", records.size());
            // handle instance where there are no actual records (bad f4v?)
            if (records.size() > 0) {
                TimeToSampleBox.Entry rec = records.get(0);
                log.debug("Samples = {} delta = {}", rec.getCount(), rec.getDelta());
                //if we have 1 record it means all samples have the same duration
                audioSampleDuration = rec.getDelta();
            }
        }
        // sdtp - sample dependency type
        SampleDependencyTypeBox sdtp = stbl.getSampleDependencyTypeBox(); // sdtp
        if (sdtp != null) {
            log.debug("Independent and disposable samples atom found");
            List<SampleDependencyTypeBox.Entry> recs = sdtp.getEntries();
            for (SampleDependencyTypeBox.Entry rec : recs) {
                log.debug("{}", rec);
            }
        }
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
                    long samplePos = frame.getOffset();
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
                        dataSource.position(samplePos);
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
        timePosMap = new HashMap<Integer, Long>();
        samplePosMap = new HashMap<Integer, Long>();
        // tag == sample
        int sample = 1;
        // position
        Long pos = null;
        // if audio-only, skip this
        if (videoSamplesToChunks != null) {
            // handle composite times
            int compositeIndex = 0;
            CompositionTimeToSample.Entry compositeTimeEntry = null;
            if (compositionTimes != null && !compositionTimes.isEmpty()) {
                compositeTimeEntry = compositionTimes.remove(0);
            }
            for (int i = 0; i < videoSamplesToChunks.size(); i++) {
                SampleToChunkBox.Entry record = videoSamplesToChunks.get(i);
                long firstChunk = record.getFirstChunk();
                long lastChunk = videoChunkOffsets.length;
                if (i < videoSamplesToChunks.size() - 1) {
                    SampleToChunkBox.Entry nextRecord = videoSamplesToChunks.get(i + 1);
                    lastChunk = nextRecord.getFirstChunk() - 1;
                }
                for (long chunk = firstChunk; chunk <= lastChunk; chunk++) {
                    long sampleCount = record.getSamplesPerChunk();
                    pos = videoChunkOffsets[(int) (chunk - 1)];
                    while (sampleCount > 0) {
                        //log.debug("Position: {}", pos);
                        samplePosMap.put(sample, pos);
                        //calculate ts
                        double ts = (videoSampleDuration * (sample - 1)) / videoTimeScale;
                        //check to see if the sample is a keyframe
                        boolean keyframe = false;
                        //some files appear not to have sync samples
                        if (syncSamples != null) {
                            keyframe = ArrayUtils.contains(syncSamples, sample);
                            if (seekPoints == null) {
                                seekPoints = new LinkedList<Integer>();
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
                        //size of the sample
                        int size = (int) videoSamples[sample - 1];
                        //create a frame
                        MP4Frame frame = new MP4Frame();
                        frame.setKeyFrame(keyframe);
                        frame.setOffset(pos);
                        frame.setSize(size);
                        frame.setTime(ts);
                        frame.setType(TYPE_VIDEO);
                        //set time offset value from composition records
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
                        //inc and dec stuff
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
            //add the audio frames / samples / chunks
            sample = 1;
            for (int i = 0; i < audioSamplesToChunks.size(); i++) {
                SampleToChunkBox.Entry record = audioSamplesToChunks.get(i);
                long firstChunk = record.getFirstChunk();
                long lastChunk = audioChunkOffsets.length;
                if (i < audioSamplesToChunks.size() - 1) {
                    SampleToChunkBox.Entry nextRecord = audioSamplesToChunks.get(i + 1);
                    lastChunk = nextRecord.getFirstChunk() - 1;
                }
                for (long chunk = firstChunk; chunk <= lastChunk; chunk++) {
                    long sampleCount = record.getSamplesPerChunk();
                    pos = audioChunkOffsets[(int) (chunk - 1)];
                    while (sampleCount > 0) {
                        //calculate ts
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
                                    dataSource.position(pos);
                                    // create buffer to store bytes so we can check them
                                    ByteBuffer dst = ByteBuffer.allocate(6);
                                    // read the data
                                    dataSource.read(dst);
                                    // flip it
                                    dst.flip();
                                    // reset the position
                                    dataSource.position(position);
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
                        //create a frame
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
            audioSamplesToChunks.clear();
            audioSamplesToChunks = null;
        }
        if (videoSamplesToChunks != null) {
            videoChunkOffsets = null;
            videoSamplesToChunks.clear();
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
