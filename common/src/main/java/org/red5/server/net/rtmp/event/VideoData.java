/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.rtmp.event;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.codec.VideoCodec;
import org.red5.codec.VideoFrameType;
import org.red5.codec.VideoPacketType;
import org.red5.io.IoConstants;
import org.red5.server.api.stream.IStreamPacket;
import org.red5.server.stream.IStreamData;
import org.red5.util.ByteNibbler;

/**
 * Video data event
 */
public class VideoData extends BaseEvent implements IoConstants, IStreamData<VideoData>, IStreamPacket {

    private static final long serialVersionUID = 5538859593815804830L;

    /**
     * Video data
     */
    protected IoBuffer data;

    /**
     * Data type
     */
    private final byte dataType = TYPE_VIDEO_DATA;

    /**
     * Codec id
     */
    private byte codecId = -1;

    /**
     * Configuration flag
     */
    private boolean config;

    /**
     * Enhanced flag
     */
    private boolean enhanced;

    /**
     * Frame type, unknown by default
     */
    private VideoFrameType frameType;

    /**
     * Packet type
     */
    private VideoPacketType packetType;

    /**
     * Video codec
     */
    //protected transient IVideoStreamCodec codec;

    /** Constructs a new VideoData. */
    public VideoData() {
        this(IoBuffer.allocate(0).flip());
    }

    /**
     * Create video data event with given data buffer
     *
     * @param data
     *            Video data
     */
    public VideoData(IoBuffer data) {
        super(Type.STREAM_DATA);
        setData(data);
    }

    /**
     * Create video data event with given data buffer
     *
     * @param data
     *            Video data
     * @param copy
     *            true to use a copy of the data or false to use reference
     */
    public VideoData(IoBuffer data, boolean copy) {
        super(Type.STREAM_DATA);
        if (copy) {
            byte[] array = new byte[data.remaining()];
            data.mark();
            data.get(array);
            data.reset();
            setData(array);
        } else {
            setData(data);
        }
    }

    /** {@inheritDoc} */
    @Override
    public byte getDataType() {
        return dataType;
    }

    public IoBuffer getData() {
        return data;
    }

    public void setData(IoBuffer data) {
        if (codecId == -1 && data.remaining() > 0) {
            data.mark();
            byte flg = data.get();
            enhanced = ByteNibbler.isBitSet(flg, 7);
            if (enhanced) {
                frameType = VideoFrameType.valueOf((flg & 0b01110000) >> 4);
                packetType = VideoPacketType.valueOf(flg & IoConstants.MASK_VIDEO_CODEC);
                if (data.remaining() > 3 && frameType != VideoFrameType.COMMAND_FRAME && packetType != VideoPacketType.Multitrack) {
                    int fourcc = data.getInt();
                    VideoCodec vc = VideoCodec.valueOfByFourCc(fourcc);
                    codecId = vc != null ? vc.getId() : -1;
                }
            } else {
                codecId = (byte) (flg & IoConstants.MASK_VIDEO_CODEC);
                frameType = VideoFrameType.valueOf((flg & MASK_VIDEO_FRAMETYPE) >> 4);
                packetType = VideoPacketType.valueOf(data.get());
            }
            data.reset();
        }

        this.data = data;
    }

    public void setData(byte[] data) {
        // set some properties if we can
        if (codecId == -1 && data.length > 0) {
            byte flg = data[0];
            // check for enhanced bit
            enhanced = ByteNibbler.isBitSet(flg, 7);
            if (enhanced) {
                // enhanced handling
                frameType = VideoFrameType.valueOf((flg & 0b01110000) >> 4);
                packetType = VideoPacketType.valueOf(flg & IoConstants.MASK_VIDEO_CODEC);
                if (data.length > 4 && frameType != VideoFrameType.COMMAND_FRAME && packetType != VideoPacketType.Multitrack) {
                    int fourcc = ((data[1] & 0xff) << 24 | (data[2] & 0xff) << 16 | (data[3] & 0xff) << 8 | data[4] & 0xff);
                    VideoCodec vc = VideoCodec.valueOfByFourCc(fourcc);
                    codecId = vc != null ? vc.getId() : -1;
                }
            } else {
                codecId = (byte) (flg & IoConstants.MASK_VIDEO_CODEC);
                frameType = VideoFrameType.valueOf((flg & MASK_VIDEO_FRAMETYPE) >> 4);
                packetType = VideoPacketType.valueOf(data[1]);
            }
            config = (packetType == VideoPacketType.SequenceStart);
        }
        setData(IoBuffer.wrap(data));
    }

    public int getCodecId() {
        return codecId;
    }

    public boolean isConfig() {
        return config;
    }

    /**
     * Returns the video frame type.
     *
     * @return video frame type
     */
    public VideoFrameType getFrameType() {
        return frameType;
    }

    public boolean isKeyFrame() {
        return frameType == VideoFrameType.KEYFRAME;
    }

    /**
     * Returns the video packet type.
     *
     * @return video packet type
     */
    public VideoPacketType getVideoPacketType() {
        return packetType;
    }

    public boolean isEndOfSequence() {
        return packetType == VideoPacketType.SequenceEnd;
    }

    public boolean isEnhanced() {
        return enhanced;
    }

    public void reset() {
        releaseInternal();
    }

    /** {@inheritDoc} */
    @Override
    protected void releaseInternal() {
        if (data != null) {
            final IoBuffer localData = data;
            // null out the data first so we don't accidentally
            // return a valid reference first
            data = null;
            localData.clear();
            localData.free();
        }
        //codec = null;
        codecId = -1;
        config = false;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        frameType = (VideoFrameType) in.readObject();
        byte[] byteBuf = (byte[]) in.readObject();
        if (byteBuf != null) {
            setData(byteBuf);
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        out.writeObject(frameType);
        if (data != null) {
            if (data.hasArray()) {
                out.writeObject(data.array());
            } else {
                byte[] array = new byte[data.remaining()];
                data.mark();
                data.get(array);
                data.reset();
                out.writeObject(array);
            }
        } else {
            out.writeObject(null);
        }
    }

    /**
     * Duplicate this message / event.
     *
     * @return duplicated event
     */
    public VideoData duplicate() throws IOException, ClassNotFoundException {
        VideoData result = new VideoData();
        // serialize
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        writeExternal(oos);
        oos.close();
        // convert to byte array
        byte[] buf = baos.toByteArray();
        baos.close();
        // create input streams
        ByteArrayInputStream bais = new ByteArrayInputStream(buf);
        ObjectInputStream ois = new ObjectInputStream(bais);
        // deserialize
        result.readExternal(ois);
        ois.close();
        bais.close();
        // clone the header if there is one
        if (header != null) {
            result.setHeader(header.clone());
        }
        result.setSourceType(sourceType);
        result.setSource(source);
        result.setTimestamp(timestamp);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return String.format("Video - ts: %s length: %s", getTimestamp(), (data != null ? data.limit() : '0'));
    }

}
