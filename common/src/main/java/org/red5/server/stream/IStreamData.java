/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.stream;

import java.io.IOException;

import org.apache.mina.core.buffer.IoBuffer;

/**
 * Stream data packet
 *
 * @param <T>
 *            type of the stream data
 * @author mondain
 */
public interface IStreamData<T> {

    /**
     * Getter for property 'data'.
     *
     * @return Value for property 'data'
     */
    IoBuffer getData();

    /**
     * Creates a byte accurate copy.
     *
     * @return duplicate of the current data item
     * @throws java.io.IOException
     *             on error
     * @throws java.lang.ClassNotFoundException
     *             on class not found
     */
    IStreamData<T> duplicate() throws IOException, ClassNotFoundException;

    /**
     * Getter for property 'dataType'.
     *
     * @return Value for property 'dataType'
     */
    byte getDataType();

    /**
     * Set the audio codec reference.
     *
     * @param codec
     */
    //default void setAudioCodecReference(IAudioStreamCodec codec) {
    //}

    /**
     * Set the video codec reference.
     *
     * @param codec
     */
    //default void setVideoCodecReference(IVideoStreamCodec codec) {
    //}

}
