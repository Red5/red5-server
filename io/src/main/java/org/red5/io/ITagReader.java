/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io;

/**
 * <p>ITagReader interface.</p>
 *
 * @author mondain
 */
public interface ITagReader {

    /**
     * Closes the reader and free any allocated memory.
     */
    void close();

    /**
     * Decode the header of the stream;
     */
    default void decodeHeader() {
        // no-op
    }

    /**
     * Returns the amount of bytes read
     *
     * @return long
     */
    long getBytesRead();

    /**
     * Return length in seconds
     *
     * @return length in seconds
     */
    long getDuration();

    /**
     * Return the file that is loaded.
     *
     * @return the file to be loaded
     */
    IStreamableFile getFile();

    /**
     * Returns the offet length
     *
     * @return int
     */
    int getOffset();

    /**
     * Get the total readable bytes in a file or ByteBuffer
     *
     * @return Total readable bytes
     */
    long getTotalBytes();

    /**
     * Returns a boolean stating whether the FLV has more tags
     *
     * @return boolean
     */
    boolean hasMoreTags();

    /**
     * Check if the reader also has video tags.
     *
     * @return has video
     */
    boolean hasVideo();

    /**
     * Move the reader pointer to given position in file.
     *
     * @param pos
     *            File position to move to
     */
    void position(long pos);

    /**
     * Returns a Tag object
     *
     * @return Tag
     */
    ITag readTag();

}
