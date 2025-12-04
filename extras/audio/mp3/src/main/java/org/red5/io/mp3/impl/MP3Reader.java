/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.mp3.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.mp3.AudioFrame;
import org.apache.tika.parser.mp3.Mp3Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.red5.io.IKeyFrameMetaCache;
import org.red5.io.IStreamableFile;
import org.red5.io.ITag;
import org.red5.io.ITagReader;
import org.red5.io.IoConstants;
import org.red5.io.amf.Output;
import org.red5.io.flv.IKeyFrameDataAnalyzer;
import org.red5.io.flv.impl.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Read MP3 files
 */
public class MP3Reader implements ITagReader, IKeyFrameDataAnalyzer {

    protected static Logger log = LoggerFactory.getLogger(MP3Reader.class);

    /**
     * File
     */
    private File file;

    /**
     * File input stream
     */
    private FileInputStream fis;

    /**
     * Last read tag object
     */
    private ITag tag;

    /**
     * Previous tag size
     */
    private int prevSize;

    /**
     * Current time
     */
    private double currentTime;

    /**
     * Frame metadata
     */
    private KeyFrameMeta frameMeta;

    /**
     * Positions and time map
     */
    private HashMap<Long, Float> posTimeMap;

    private int dataRate;

    /**
     * File duration
     */
    private long duration;

    /**
     * Frame cache
     */
    static private IKeyFrameMetaCache frameCache;

    /**
     * Holder for ID3 meta data
     */
    private MetaData metaData;

    /**
     * Container for metadata and any other tags that should be sent prior to media data.
     */
    private LinkedList<ITag> firstTags = new LinkedList<>();

    private long fileSize;

    private final Semaphore lock = new Semaphore(1, true);

    private FileChannel fileChannel;

    private LinkedList<AudioFrame> frameList;

    private int frameIndex;

    private int frameCount;

    MP3Reader() {
        // Only used by the bean startup code to initialize the frame cache
    }

    /**
     * Creates reader from file input stream
     *
     * @param file
     *            file input
     * @throws IOException
     *             on IO error
     */
    public MP3Reader(File file) throws IOException {
        // XXX(paul) check for IOUtils before proceeding as tika libs may not be available
        // import org.apache.tika.io.IOUtils;
        // "org.apache.tika.io.IOUtils"
        // "org.apache.poi.util.IOUtils"
        this.file = file;
        fis = new FileInputStream(file);
        try {
            // parse the ID3 info
            BodyContentHandler handler = new BodyContentHandler();
            Metadata metadata = new Metadata();
            // MP3 parser
            Mp3Parser parser = new Mp3Parser();
            parser.parse(fis, handler, metadata, null);
            log.debug("Contents of the document: {}", handler.toString());
            // create meta data holder
            metaData = new MetaData();
            String val = null;
            String[] metadataNames = metadata.names();
            for (String name : metadataNames) {
                val = metadata.get(name);
                log.debug("Meta name: {} value: {}", name, val);
                if ("xmpDM:artist".equals(name)) {
                    metaData.setArtist(val);
                } else if ("xmpDM:album".equals(name)) {
                    metaData.setAlbum(val);
                } else if ("title".equals(name)) {
                    metaData.setSongName(val);
                } else if ("xmpDM:genre".equals(name)) {
                    metaData.setGenre(val);
                } else if ("xmpDM:logComment".equals(name)) {
                    metaData.setComment(val);
                } else if ("xmpDM:trackNumber".equals(name)) {
                    metaData.setTrack(val);
                } else if ("xmpDM:releaseDate".equals(name)) {
                    metaData.setYear(val);
                } else if ("xmpDM:duration".equals(name) || "duration".equals(name)) {
                    metaData.setDuration(val);
                } else if ("xmpDM:audioSampleRate".equals(name) || "samplerate".equals(name)) {
                    metaData.setSampleRate(val);
                } else if ("channels".equals(name)) {
                    metaData.setChannels(val);
                }
            }
            /*
             * //send album image if included List<Artwork> tagFieldList = idTag.getArtworkList(); if (tagFieldList == null || tagFieldList.isEmpty()) { log.debug("No cover art was found"); }
             * else { Artwork imageField = tagFieldList.get(0); log.debug("Picture type: {}", imageField.getPictureType()); FrameBodyAPIC imageFrameBody = new FrameBodyAPIC();
             * imageFrameBody.setImageData(imageField.getBinaryData()); if (!imageFrameBody.isImageUrl()) { byte[] imageBuffer = (byte[])
             * imageFrameBody.getObjectValue(DataTypes.OBJ_PICTURE_DATA); //set the cover image on the metadata metaData.setCovr(imageBuffer); // Create tag for onImageData event IoBuffer buf
             * = IoBuffer.allocate(imageBuffer.length); buf.setAutoExpand(true); Output out = new Output(buf); out.writeString("onImageData"); Map<Object, Object> props = new HashMap<Object,
             * Object>(); props.put("trackid", 1); props.put("data", imageBuffer); out.writeMap(props); buf.flip(); //Ugh i hate flash sometimes!! //Error #2095: flash.net.NetStream was unable
             * to invoke callback onImageData. ITag result = new Tag(IoConstants.TYPE_METADATA, 0, buf.limit(), null, 0); result.setBody(buf); //add to first frames firstTags.add(result); } }
             * } else { log.info("File did not contain ID3v2 data: {}", file.getName()); }
             */
        } catch (Exception e) {
            log.error("MP3Reader {}", e);
        }
        // ensure we have a valid sample rate
        checkValidHeader();
        // get the total bytes / file size
        fileSize = file.length();
        log.debug("File size: {}", fileSize);
        // analyze keyframes data
        analyzeKeyFrames();
        // create file metadata object
        firstTags.addFirst(createFileMeta());
        log.trace("File input stream - open: {} position: {}", fis.getChannel().isOpen(), fis.getChannel().position());
        // create a channel for reading
        fileChannel = fis.getChannel();
    }

    /**
     * A MP3 stream never has video.
     *
     * @return always returns <code>false</code>
     */
    @Override
    public boolean hasVideo() {
        return false;
    }

    public void setFrameCache(IKeyFrameMetaCache frameCache) {
        MP3Reader.frameCache = frameCache;
    }

    /**
     * Check if the file can be played back with Flash. Supported sample rates are 44KHz, 22KHz, 11KHz and 5.5KHz
     */
    private void checkValidHeader() {
        if (metaData == null || metaData.getSampleRate() == null) {
            log.warn("Sample rate metadata missing; skipping validation");
            return;
        }
        int sampleRate = 0;
        try {
            sampleRate = Integer.parseInt(metaData.getSampleRate());
        } catch (NumberFormatException nfe) {
            log.warn("Invalid sample rate metadata: {}", metaData.getSampleRate());
            return;
        }
        switch (sampleRate) {
            case 48000:
            case 44100:
            case 22050:
            case 11025:
            case 5513:
                break;
            default:
                throw new RuntimeException("Unsupported sample rate: " + sampleRate);
        }
    }

    /**
     * Creates file metadata object
     *
     * @return Tag
     */
    private ITag createFileMeta() {
        log.debug("createFileMeta");
        // create tag for onMetaData event
        IoBuffer in = IoBuffer.allocate(1024);
        in.setAutoExpand(true);
        Output out = new Output(in);
        out.writeString("onMetaData");
        Map<Object, Object> props = new HashMap<Object, Object>();
        props.put("audiocodecid", IoConstants.FLAG_FORMAT_MP3);
        props.put("canSeekToEnd", true);
        // set id3 meta data if it exists
        if (metaData != null) {
            if (metaData.artist != null) {
                props.put("artist", metaData.artist);
            }
            if (metaData.album != null) {
                props.put("album", metaData.album);
            }
            if (metaData.songName != null) {
                props.put("songName", metaData.songName);
            }
            if (metaData.genre != null) {
                props.put("genre", metaData.genre);
            }
            if (metaData.year != null) {
                props.put("year", metaData.year);
            }
            if (metaData.track != null) {
                props.put("track", metaData.track);
            }
            if (metaData.comment != null) {
                props.put("comment", metaData.comment);
            }
            if (metaData.duration != null) {
                props.put("duration", metaData.duration);
            }
            if (metaData.channels != null) {
                props.put("channels", metaData.channels);
            }
            if (metaData.sampleRate != null) {
                props.put("samplerate", metaData.sampleRate);
            }
            if (metaData.hasCoverImage()) {
                Map<Object, Object> covr = new HashMap<>(1);
                covr.put("covr", new Object[] { metaData.getCovr() });
                props.put("tags", covr);
            }
            //clear meta for gc
            metaData = null;
        }
        log.debug("Metadata properties map: {}", props);
        // check for duration
        if (!props.containsKey("duration")) {
            // generate it from framemeta
            if (frameMeta != null) {
                props.put("duration", frameMeta.timestamps[frameMeta.timestamps.length - 1] / 1000.0);
            } else {
                log.debug("Frame meta was null");
            }
        }
        // set datarate
        if (dataRate > 0) {
            props.put("audiodatarate", dataRate);
        }
        out.writeMap(props);
        in.flip();
        // meta-data
        ITag result = new Tag(IoConstants.TYPE_METADATA, 0, in.limit(), null, prevSize);
        result.setBody(in);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public IStreamableFile getFile() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public int getOffset() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public long getBytesRead() {
        try {
            return fileChannel != null ? fileChannel.position() : 0;
        } catch (IOException e) {
        }
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public long getDuration() {
        return duration;
    }

    /**
     * Get the total readable bytes in a file or ByteBuffer.
     *
     * @return Total readable bytes
     */
    @Override
    public long getTotalBytes() {
        return fileSize;
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasMoreTags() {
        log.debug("hasMoreTags");
        return fileChannel.isOpen() && frameIndex < frameCount;
    }

    /** {@inheritDoc} */
    @Override
    public ITag readTag() {
        log.debug("readTag");
        try {
            lock.acquire();
            if (!firstTags.isEmpty()) {
                // return first tags before media data
                return firstTags.removeFirst();
            }
            AudioFrame frame = frameList.get(frameIndex++);
            if (frame == null) {
                return null;
            }
            int frameSize = frame.getLength();
            log.trace("Frame size: {}", frameSize);
            if (frameSize == 0) {
                return null;
            }
            tag = new Tag(IoConstants.TYPE_AUDIO, (int) currentTime, frameSize + 1, null, prevSize);
            prevSize = frameSize + 1;
            currentTime += frame.getDuration();
            IoBuffer body = IoBuffer.allocate(tag.getBodySize());
            body.setAutoExpand(true);
            byte tagType = (IoConstants.FLAG_FORMAT_MP3 << 4) | (IoConstants.FLAG_SIZE_16_BIT << 1);
            switch (frame.getSampleRate()) {
                case 48000:
                    tagType |= IoConstants.FLAG_RATE_48_KHZ << 2;
                    break;
                case 44100:
                    tagType |= IoConstants.FLAG_RATE_44_KHZ << 2;
                    break;
                case 22050:
                    tagType |= IoConstants.FLAG_RATE_22_KHZ << 2;
                    break;
                case 11025:
                    tagType |= IoConstants.FLAG_RATE_11_KHZ << 2;
                    break;
                default:
                    tagType |= IoConstants.FLAG_RATE_5_5_KHZ << 2;
            }
            tagType |= (frame.getChannels() > 1 ? IoConstants.FLAG_TYPE_STEREO : IoConstants.FLAG_TYPE_MONO);
            body.put(tagType);
            // read the header and data after fixing the position
            log.trace("Allocating {} buffer", frameSize);
            if (frameSize > 0) {
                ByteBuffer in = ByteBuffer.allocate(frameSize).order(ByteOrder.BIG_ENDIAN);
                fileChannel.read(in);
                in.flip();
                body.put(in);
                body.flip();
                tag.setBody(body);
            } else {
                log.warn("Buffer size was invalid: {}", frameSize);
            }
        } catch (InterruptedException e) {
            log.warn("Exception acquiring lock", e);
        } catch (Exception e) {
            log.warn("Exception reading tag", e);
        } finally {
            lock.release();
        }
        return tag;
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        if (posTimeMap != null) {
            posTimeMap.clear();
        }
        try {
            fis.close();
            fileChannel.close();
        } catch (IOException e) {
            log.error("Exception on close", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void decodeHeader() {
    }

    /** {@inheritDoc} */
    @Override
    public void position(long pos) {
        if (pos == Long.MAX_VALUE) {
            // seek at EOF
            currentTime = duration;
        }
        if (posTimeMap != null && posTimeMap.containsKey(pos)) {
            try {
                fileChannel.position(pos);
                currentTime = posTimeMap.get(pos);
            } catch (IOException e) {
                log.warn("Setting position to: {} failed", pos, e);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public KeyFrameMeta analyzeKeyFrames() {
        log.debug("analyzeKeyFrames");
        if (frameMeta != null) {
            return frameMeta;
        }
        try {
            lock.acquire();
            // check for cached frame information
            if (frameCache != null) {
                frameMeta = frameCache.loadKeyFrameMeta(file);
                if (frameMeta != null && frameMeta.duration > 0) {
                    // frame data loaded, create other mappings
                    duration = frameMeta.duration;
                    frameMeta.audioOnly = true;
                    posTimeMap = new HashMap<>();
                    for (int i = 0; i < frameMeta.positions.length; i++) {
                        posTimeMap.put(frameMeta.positions[i], (float) frameMeta.timestamps[i]);
                    }
                    return frameMeta;
                }
            }
            // rewind to the beginning using a channel
            FileChannel channel = fis.getChannel();
            log.debug("Position: {}", channel.position());
            channel.position(0);
            // create an internal parsing stream
            MP3Stream stream = new MP3Stream(fis);
            // frame holder
            frameList = new LinkedList<>();
            // position and timestamp lists
            List<Long> positionList = new ArrayList<>();
            List<Float> timestampList = new ArrayList<>();
            dataRate = 0;
            long rate = 0;
            float time = 0f;
            // read the first frame and move on to all the following ones
            AudioFrame frame = stream.nextFrame();
            while (frame != null) {
                long pos = channel.position() - 4;
                if (pos + frame.getLength() > fileSize) {
                    // last frame is incomplete
                    log.trace("Last frame was incomplete");
                    break;
                }
                // save frame ref
                frameList.add(frame);
                // add the position for this frame
                positionList.add(pos);
                // add the timestamp for this frame
                timestampList.add(time);
                // get the bitrate
                rate += frame.getBitRate() / 1000;
                // get the duration
                time += frame.getDuration();
                // increase the frame counter
                frameCount++;
                // skip current frame
                stream.skipFrame();
                // move to next frame
                frame = stream.nextFrame();
            }
            // reset the file input stream position
            channel.position(0);
            log.trace("Finished with frame count: {}", frameCount);
            duration = (long) time;
            if (frameCount > 0) {
                dataRate = (int) (rate / frameCount);
            } else {
                dataRate = 0;
                log.warn("No frames were read from {}; data rate set to 0", file.getName());
            }
            posTimeMap = new HashMap<>();
            frameMeta = new KeyFrameMeta();
            frameMeta.duration = duration;
            frameMeta.positions = new long[positionList.size()];
            frameMeta.timestamps = new int[timestampList.size()];
            frameMeta.audioOnly = true;
            for (int i = 0; i < frameMeta.positions.length; i++) {
                frameMeta.positions[i] = positionList.get(i);
                frameMeta.timestamps[i] = timestampList.get(i).intValue();
                posTimeMap.put(positionList.get(i), timestampList.get(i));
            }
            if (frameCache != null) {
                frameCache.saveKeyFrameMeta(file, frameMeta);
            }
        } catch (InterruptedException e) {
            log.warn("Exception acquiring lock", e);
        } catch (Exception e) {
            log.warn("Exception analyzing frames", e);
        } finally {
            lock.release();
        }
        log.debug("Analysis complete");
        if (log.isTraceEnabled()) {
            log.trace("{}", frameMeta);
        }
        return frameMeta;
    }

    /**
     * Simple holder for id3 meta data
     */
    static class MetaData {
        String album;

        String artist;

        String genre;

        String songName;

        String track;

        String year;

        String comment;

        String duration;

        String sampleRate;

        String channels;

        byte[] covr = null;

        public String getAlbum() {
            return album;
        }

        public void setAlbum(String album) {
            this.album = album;
        }

        public String getArtist() {
            return artist;
        }

        public void setArtist(String artist) {
            this.artist = artist;
        }

        public String getGenre() {
            return genre;
        }

        public void setGenre(String genre) {
            this.genre = genre;
        }

        public String getSongName() {
            return songName;
        }

        public void setSongName(String songName) {
            this.songName = songName;
        }

        public String getTrack() {
            return track;
        }

        public void setTrack(String track) {
            this.track = track;
        }

        public String getYear() {
            return year;
        }

        public void setYear(String year) {
            this.year = year;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        public String getDuration() {
            return duration;
        }

        public void setDuration(String duration) {
            this.duration = duration;
        }

        public String getSampleRate() {
            return sampleRate;
        }

        public void setSampleRate(String sampleRate) {
            this.sampleRate = sampleRate;
        }

        public String getChannels() {
            return channels;
        }

        public void setChannels(String channels) {
            this.channels = channels;
        }

        public byte[] getCovr() {
            return covr;
        }

        public void setCovr(byte[] covr) {
            this.covr = covr;
            log.debug("Cover image array size: {}", covr.length);
        }

        public boolean hasCoverImage() {
            return covr != null;
        }

    }

}
