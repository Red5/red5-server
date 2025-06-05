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
import org.red5.codec.AudioCodec;
import org.red5.codec.AudioPacketType;
import org.red5.io.IoConstants;
import org.red5.server.api.stream.IStreamPacket;
import org.red5.server.stream.IStreamData;

/**
 * <p>AudioData class.</p>
 *
 * @author mondain
 */
public class AudioData extends BaseEvent implements IStreamData<AudioData>, IStreamPacket {

    private static final long serialVersionUID = -4102940670913999407L;

    /**
     * Data type
     */
    private final byte dataType = TYPE_AUDIO_DATA;

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
     * Packet type
     */
    private AudioPacketType packetType;

    /**
     * Audio codec
     */
    //protected transient IAudioStreamCodec codec;

    /**
     * Constructs a new AudioData.
     */
    public AudioData() {
        this(IoBuffer.allocate(0).flip());
    }

    /**
     * <p>Constructor for AudioData.</p>
     *
     * @param data a {@link org.apache.mina.core.buffer.IoBuffer} object
     */
    public AudioData(IoBuffer data) {
        super(Type.STREAM_DATA);
        setData(data);
    }

    /**
     * Create audio data event with given data buffer
     *
     * @param data
     *            Audio data
     * @param copy
     *            true to use a copy of the data or false to use reference
     */
    public AudioData(IoBuffer data, boolean copy) {
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

    /**
     * <p>Getter for the field <code>data</code>.</p>
     *
     * @return a {@link org.apache.mina.core.buffer.IoBuffer} object
     */
    public IoBuffer getData() {
        return data;
    }

    /**
     * <p>Setter for the field <code>data</code>.</p>
     *
     * @param data a {@link org.apache.mina.core.buffer.IoBuffer} object
     */
    public void setData(IoBuffer data) {
        // set some properties if we can
        if (codecId == -1 && data.remaining() > 0) {
            data.mark();
            byte flg = data.get();
            codecId = (byte) ((flg & IoConstants.MASK_SOUND_FORMAT) >> 4);
            enhanced = (codecId == AudioCodec.ExHeader.getId());
            if (enhanced) {
                packetType = AudioPacketType.valueOf(flg & 0x0f);
            } else if (codecId == 10 && data.remaining() > 0) {
                flg = data.get();
                packetType = AudioPacketType.valueOf(flg);
            }
            data.reset();
            config = (packetType == AudioPacketType.SequenceStart);
        }
        this.data = data;
    }

    /**
     * <p>Setter for the field <code>data</code>.</p>
     *
     * @param data an array of {@link byte} objects
     */
    public void setData(byte[] data) {
        // set some properties if we can
        if (codecId == -1 && data.length > 0) {
            codecId = (byte) ((data[0] & IoConstants.MASK_SOUND_FORMAT) >> 4);
            enhanced = (codecId == AudioCodec.ExHeader.getId());
            if (enhanced) {
                packetType = AudioPacketType.valueOf(data[0] & 0x0f);
            } else if (codecId == AudioCodec.AAC.getId() && data.length > 1) {
                packetType = AudioPacketType.valueOf(data[1]);
            }
            config = (packetType == AudioPacketType.SequenceStart);
        }
        setData(IoBuffer.wrap(data));
    }

    /**
     * <p>Getter for the field <code>codecId</code>.</p>
     *
     * @return a int
     */
    public int getCodecId() {
        return codecId;
    }

    /**
     * <p>isConfig.</p>
     *
     * @return a boolean
     */
    public boolean isConfig() {
        return config;
    }

    /**
     * Returns the audio packet type.
     *
     * @return audio packet type
     */
    public AudioPacketType getPacketType() {
        return packetType;
    }

    /**
     * <p>isEndOfSequence.</p>
     *
     * @return a boolean
     */
    public boolean isEndOfSequence() {
        return packetType == AudioPacketType.SequenceEnd;
    }

    /**
     * <p>isEnhanced.</p>
     *
     * @return a boolean
     */
    public boolean isEnhanced() {
        return enhanced;
    }

    /**
     * <p>reset.</p>
     */
    public void reset() {
        releaseInternal();
    }

    /** {@inheritDoc} */
    @Override
    protected void releaseInternal() {
        if (data != null) {
            data.free();
            data = null;
        }
        //codec = null;
        codecId = -1;
        config = false;
    }

    /** {@inheritDoc} */
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        byte[] byteBuf = (byte[]) in.readObject();
        if (byteBuf != null) {
            setData(byteBuf);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
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

    @Override
    public BaseEvent forkedDuplicate() {

        AudioData fork = new AudioData(super.concurrentDataCopy());
        fork.setTimestamp(this.timestamp);
        if (header != null) {
            fork.setHeader(header.clone());
        }
        fork.setSource(this.getSource());
        fork.setSourceType(this.getSourceType());
        return fork;
    }

    /**
     * Duplicate this message / event.
     *
     * @return duplicated event
     * @throws java.io.IOException if any.
     * @throws java.lang.ClassNotFoundException if any.
     */
    public AudioData duplicate() throws IOException, ClassNotFoundException {
        AudioData result = new AudioData();
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
        return String.format("Audio - ts: %s length: %s", getTimestamp(), (data != null ? data.limit() : '0'));
    }

}
