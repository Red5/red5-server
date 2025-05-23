/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.codec;

import org.apache.mina.core.buffer.IoBuffer;

/**
 * Represents an Audio codec and its associated decoder configuration.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public interface IAudioStreamCodec {

    /**
     * <p>getCodec.</p>
     *
     * @return the codec type.
     */
    AudioCodec getCodec();

    /**
     * <p>getName.</p>
     *
     * @return the name of the audio codec.
     */
    String getName();

    /**
     * Reset the codec to its initial state.
     */
    void reset();

    /**
     * Returns true if the codec knows how to handle the passed stream data.
     *
     * @param data
     *            some sample data to see if this codec can handle it.
     * @return can this code handle the data.
     */
    boolean canHandleData(IoBuffer data);

    /**
     * Update the state of the codec with the passed data.
     *
     * @param data
     *            data to tell the codec we're adding
     * @return true for success. false for error.
     */
    boolean addData(IoBuffer data);

    /**
     * Add audio data with a time stamp and a flag identifying the content as AMF or not.
     *
     * @param data a {@link org.apache.mina.core.buffer.IoBuffer} object
     * @param timestamp a int
     * @param amf if true, data is in AMF format otherwise its most likely from non-AMF source like RTP
     * @return true if data is added and false otherwise
     */
    boolean addData(IoBuffer data, int timestamp, boolean amf);

    /**
     * Returns information used to configure the decoder.
     *
     * @return the data for decoder setup.
     */
    default IoBuffer getDecoderConfiguration() {
        return null;
    }

    /**
     * Returns true if the codec is enhanced.
     *
     * @return true if enhanced and false otherwise
     */
    default boolean isEnhanced() {
        return false;
    }

    /**
     * Returns the multitrack type for the codec.
     *
     * @return the multitrack type
     */
    default AvMultitrackType getMultitrackType() {
        return null;
    }

    /**
     * Returns the packet type for the codec.
     *
     * @return the packet type
     */
    default AudioPacketType getPacketType() {
        return null;
    }

    /**
     * Returns the sample rate for the codec.
     *
     * @return sample rate, default is 48000
     */
    default int getSampleRate() {
        return 48000;
    }

    /**
     * Returns the sample size in bits for the codec.
     *
     * @return sample size in bits, default is 16
     */
    default int getSampleSizeInBits() {
        return 16;
    }

    /**
     * Returns the number of channels for the codec.
     *
     * @return number of channels, default is 2
     */
    default int getChannels() {
        return 2;
    }

    /**
     * Returns whether the codec is signed or not.
     *
     * @return true if signed, false otherwise
     */
    default boolean isSigned() {
        return true;
    }

    /**
     * Sets the track id.
     *
     * @param trackId a int
     */
    default void setTrackId(int trackId) {
    }

    /**
     * Returns the track id.
     *
     * @return track id
     */
    default int getTrackId() {
        return 0;
    }

}
