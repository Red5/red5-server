package org.red5.codec;

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.io.IoConstants;
import org.red5.util.ByteNibbler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract video codec implementation.
 *
 */
public class AbstractVideo implements IVideoStreamCodec {

    protected static Logger log = LoggerFactory.getLogger("Video");

    protected static boolean isTrace = log.isTraceEnabled(), isDebug = log.isDebugEnabled();

    // tracks for multitrack video, if size = 1, theres only one track
    protected ConcurrentMap<Integer, IVideoStreamCodec> tracks = new ConcurrentSkipListMap<>();

    // multitrack flag
    protected boolean multitrack;

    // track codec - this is temporary when used for multitrack video
    protected IVideoStreamCodec trackCodec;

    // codec enum
    protected VideoCodec codec;

    // whether or not to employ enhanced codec handling
    protected boolean enhanced;

    protected AvMultitrackType multitrackType;

    protected VideoFrameType frameType;

    protected VideoPacketType packetType;

    /** Current timestamp for the stored keyframe */
    protected int keyframeTimestamp;

    /**
     * Storage for key frames
     */
    protected final CopyOnWriteArrayList<FrameData> keyframes = new CopyOnWriteArrayList<>();

    /**
     * Storage for frames buffered since last key frame
     */
    protected CopyOnWriteArrayList<FrameData> interframes;

    /**
     * Number of frames buffered since last key frame
     */
    protected final AtomicInteger numInterframes = new AtomicInteger(0);

    /**
     * Whether or not to buffer interframes. Default is false.
     */
    protected boolean bufferInterframes;

    // track id
    protected int trackId = 0;

    // track length in bytes
    protected int trackSize = 0;

    // video codec specific attributes
    protected transient ConcurrentMap<String, String> attributes = new ConcurrentHashMap<>();

    @Override
    public VideoCodec getCodec() {
        return codec;
    }

    @Override
    public String getName() {
        return codec.name();
    }

    @Override
    public void setTrackId(int trackId) {
        this.trackId = trackId;
    }

    @Override
    public int getTrackId() {
        return trackId;
    }

    @Override
    public void reset() {
    }

    /**
     * Soft reset, clears keyframes and interframes, but does not reset the codec.
     */
    protected void softReset() {
        keyframes.clear();
        if (interframes != null) {
            interframes.clear();
        }
        numInterframes.set(0);
    }

    @Override
    public boolean canDropFrames() {
        return false;
    }

    @Override
    public boolean canHandleData(IoBuffer data) {
        boolean result = false;
        if (data != null && data.limit() > 0) {
            // get the first byte
            byte flg = data.get();
            // determine if we've got an enhanced codec
            enhanced = ByteNibbler.isBitSet(flg, 7); // network order so its rtl
            // for frame type we need get 3 bits
            int ft = ((flg & 0b01110000) >> 4);
            frameType = VideoFrameType.valueOf(ft);
            // the codec id for enhanced is handled via addData
            if (enhanced) {
                log.info("Codec is enhanced, codec id is determined via subsequent addData calls, frame type: {}", frameType);
                result = true;
            } else {
                log.info("Codec is not enhanced, codec id: {} frame type: {}", codec.getId(), frameType);
                result = ((flg & IoConstants.MASK_VIDEO_CODEC) == codec.getId());
            }
        }
        return result;
    }

    @Override
    public boolean addData(IoBuffer data) {
        // not an ideal implementation, but it works when there's no timestamp supplied
        return addData(data, (keyframeTimestamp + 1));
    }

    @SuppressWarnings("incomplete-switch")
    @Override
    public boolean addData(IoBuffer data, int timestamp) {
        boolean result = false;
        if (data != null && data.hasRemaining()) {
            boolean processVideoBody = false;
            @SuppressWarnings("unused")
            VideoCommand command = null;
            // mark
            data.mark();
            // get the first byte
            byte flg = data.get();
            // determine if we've got an enhanced codec
            enhanced = ByteNibbler.isBitSet(flg, 7); // network order so its rtl
            // for frame type we need get 3 bits
            int ft = ((flg & 0b01110000) >> 4);
            frameType = VideoFrameType.valueOf(ft);
            log.debug("Frame type: {}", frameType);
            if (enhanced) {
                // we are going to process the video body only if we're enhanced
                processVideoBody = true;
                // The UB[4] bits are interpreted as VideoPacketType instead of codec id
                packetType = VideoPacketType.valueOf(flg & IoConstants.MASK_VIDEO_CODEC);
                log.debug("Enhanced codec handling, packet type: {}", packetType);
                if (packetType != VideoPacketType.Metadata && frameType == VideoFrameType.COMMAND_FRAME) {
                    // get the command
                    command = VideoCommand.valueOf(data.get());
                    // should be no body to process from here
                    processVideoBody = false;
                } else if (packetType == VideoPacketType.Multitrack) {
                    // set the multitrack flag
                    multitrack = true;
                    // set up for reading more bits
                    ByteNibbler nibbler = new ByteNibbler(data.get());
                    multitrackType = AvMultitrackType.valueOf((byte) nibbler.nibble(4));
                    // Fetch VideoPacketType for all video tracks in the message
                    // This fetch MUST not result in a AudioPacketType.Multitrack
                    packetType = VideoPacketType.valueOf(nibbler.nibble(4));
                    if (multitrackType != AvMultitrackType.ManyTracksManyCodecs) {
                        // The tracks are encoded with the same codec identified by the FOURCC
                        trackCodec = getTrackCodec(data);
                    } else {
                        // The tracks are encoded with the same codec identified by the FOURCC
                        trackCodec = getTrackCodec(data);
                    }
                }
                log.debug("Multitrack: {} multitrackType: {} packetType: {}", multitrack, multitrackType, packetType);
                // read all the data
                while (processVideoBody && data.hasRemaining()) {
                    // handle multiple tracks
                    if (multitrack) {
                        // handle tracks that each have their own codec
                        if (multitrackType == AvMultitrackType.ManyTracksManyCodecs) {
                            // The tracks are encoded with their own codec identified by the FOURCC
                            trackCodec = getTrackCodec(data);
                        }
                        // track ordering
                        // For identifying the highest priority (a.k.a., default track) or highest quality track, it is RECOMMENDED
                        // to use trackId set to zero. For tracks of lesser priority or quality, use multiple instances of trackId
                        // with ascending numerical values. The concept of priority or quality can have multiple interpretations,
                        // including but not limited to bitrate, resolution, default angle, and language. This recommendation
                        // serves as a guideline intended to standardize track numbering across various applications.
                        trackId = data.get();
                        if (multitrackType != AvMultitrackType.OneTrack) {
                            // The 'sizeOfVideoTrack' specifies the size in bytes of the current track that is being processed.
                            // This size starts counting immediately after the position where the 'sizeOfVideoTrack' value is
                            // located. You can use this value as an offset to locate the next video track in a multitrack system.
                            // The data pointer is positioned immediately after this field. Depending on the MultiTrack type, the
                            // offset points to either a 'fourCc' or a 'trackId.'
                            trackSize = (data.get() & 0xff) << 16 | (data.get() & 0xff) << 8 | data.get() & 0xff;
                        }
                        // we're multitrack and multicodec so update track id
                        if (multitrackType == AvMultitrackType.ManyTracksManyCodecs) {
                            trackCodec.setTrackId(trackId);
                        }
                    } else if (packetType != VideoPacketType.Metadata) { // no fourcc for metadata non-multitrack
                        // track codec is null if we're not multitrack or command frame
                        trackCodec = getTrackCodec(data);
                    }
                    switch (packetType) {
                        case CodedFramesX: // pass coded data without comp time offset
                            break;
                        case CodedFrames: // pass coded data
                            break;
                        case SequenceStart: // start of sequence
                            break;
                        case MPEG2TSSequenceStart: // start of MPEG2TS sequence
                            break;
                        case SequenceEnd: // end of sequence
                            break;
                        case Metadata: // metadata
                            // The body does not contain video data; instead, it consists of AMF-encoded metadata. The
                            // metadata is represented by a series of [name, value] pairs. Currently, the only defined
                            // [name, value] pair is ["colorInfo", Object]. See the Metadata Frame section for more
                            // details on this object.
                            // For a deeper understanding of the encoding, please refer to the descriptions of SCRIPTDATA
                            // and SCRIPTDATAVALUE in the [FLV] file specification.
                            break;
                    }
                    // check for multiple tracks
                    if (multitrack && trackSize > 0) {
                        data.skip(trackSize);
                        continue;
                    }
                    // break out of the loop
                    break;
                }
                result = true;
            } else {
                // read the first byte verify the codec matches
                result = ((flg & IoConstants.MASK_VIDEO_CODEC) == codec.getId());
                if (frameType == VideoFrameType.COMMAND_FRAME) {
                    // get the command
                    command = VideoCommand.valueOf(data.get());
                }
            }
            // reset
            data.reset();
        }
        return result;
    }

    /**
     * Get the track codec for the given data.
     *
     * @param data
     * @return Video codec
     */
    protected IVideoStreamCodec getTrackCodec(IoBuffer data) {
        Integer fourcc = data.getInt();
        log.debug("Fourcc: {} pos: {}", fourcc, data.position());
        if (!tracks.containsKey(fourcc)) {
            // create a new codec instance
            trackCodec = VideoCodec.valueOfByFourCc(fourcc).newInstance();
            tracks.put(fourcc, trackCodec);
        } else {
            trackCodec = tracks.get(fourcc);
        }
        log.debug("Track codec: {}", trackCodec);
        return trackCodec;
    }

    @Override
    public boolean addData(IoBuffer data, int timestamp, boolean amf) {
        return false;
    }

    @Override
    public IoBuffer getDecoderConfiguration() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public IoBuffer getKeyframe() {
        if (keyframes.isEmpty()) {
            return null;
        }
        return keyframes.get(0).getFrame();
    }

    /** {@inheritDoc} */
    @Override
    public FrameData[] getKeyframes() {
        return keyframes.toArray(new FrameData[0]);
    }

    /** {@inheritDoc} */
    @Override
    public int getNumInterframes() {
        return numInterframes.get();
    }

    /** {@inheritDoc} */
    @Override
    public FrameData getInterframe(int index) {
        int interframeCount = numInterframes.get();
        log.trace("Interframe count: {} index: {}", interframeCount, index);
        if (interframes != null && index < interframeCount) {
            return interframes.get(index);
        }
        return null;
    }

    public boolean isBufferInterframes() {
        return bufferInterframes;
    }

    public void setBufferInterframes(boolean bufferInterframes) {
        this.bufferInterframes = bufferInterframes;
        if (bufferInterframes && interframes == null) {
            interframes = new CopyOnWriteArrayList<>();
        }
    }

    @Override
    public boolean isEnhanced() {
        return enhanced;
    }

    @Override
    public AvMultitrackType getMultitrackType() {
        return multitrackType;
    }

    @Override
    public VideoFrameType getFrameType() {
        return frameType;
    }

    @Override
    public VideoPacketType getPacketType() {
        return packetType;
    }

    /**
     * Sets an attribute directly on the codec instance.
     *
     * @param key
     * @param value
     */
    public void setAttribute(String key, String value) {
        attributes.put(key, value);
    }

    /**
     * Returns the attribute for a given key.
     *
     * @param key
     * @return String value
     */
    public String getAttribute(String key) {
        return attributes.get(key);
    }

    /**
     * Returns a track codec for the given track index. This is only good for a single track as it will return the
     * first codec found. In a multi-track scenario, the proper look-up should be done by the track's fourcc.
     *
     * @param trackIndex
     * @return Video codec at the index or null if no track exist
     */
    public VideoCodec getTrackCodec(int trackIndex) {
        VideoCodec result = null;
        if (!tracks.isEmpty()) {
            for (Entry<Integer, IVideoStreamCodec> trakCodec : tracks.entrySet()) {
                result = trakCodec.getValue().getCodec();
                break;
            }
        }
        return result;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((codec == null) ? 0 : codec.hashCode());
        result = prime * result + trackId;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AbstractVideo other = (AbstractVideo) obj;
        if (codec != other.codec)
            return false;
        if (trackId != other.trackId)
            return false;
        return true;
    }

    @Override
    public String toString() {
        if (enhanced) {
            return "Video [codec=" + codec + ", multitrackType=" + multitrackType + ", trackId=" + trackId + ", frameType=" + frameType + ", packetType=" + packetType + "]";
        }
        return "Video [codec=" + codec + ", frameType=" + frameType + ", not enhanced]";
    }

}
