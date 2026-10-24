/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.stream.consumer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.red5.io.ITagWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Size, duration and free space limits of one recording file, shared by the file consumers.
 * <br>
 * Size and duration count the content already in the file when appending, so a file cannot grow past its limits over several append
 * sessions. A value of 0 disables a limit.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
final class RecordingLimits {

    private static final Logger log = LoggerFactory.getLogger(RecordingLimits.class);

    // how often, in checks, the free space check runs
    private static final int FREE_SPACE_CHECK_INTERVAL = 100;

    /**
     * Maximum bytes of tag data in the recording file; 0 means unlimited.
     */
    private long maxBytes;

    /**
     * Maximum duration of the recording file in milliseconds, measured by stream timestamps; 0 means unlimited.
     */
    private long maxDurationMs;

    /**
     * Minimum usable space to keep free on the recording volume; 0 disables the check.
     */
    private long minFreeDiskBytes;

    // checks left until the next free space check, the first check always looks at the volume
    private int checksUntilSpaceCheck;

    /**
     * Checks the recording against its limits.
     *
     * @param writer
     *            writer of the recording
     * @param path
     *            recording file
     * @param timestamp
     *            timestamp of the last tag written, relative to the start of this recording session
     * @return reason the recording has to stop, or null while it is within its limits
     */
    String check(ITagWriter writer, Path path, int timestamp) {
        if (maxBytes > 0) {
            long bytes = writer.getExistingBytes() + writer.getBytesWritten();
            if (bytes >= maxBytes) {
                return String.format("Recording reached the %d byte limit", maxBytes);
            }
        }
        if (maxDurationMs > 0) {
            long duration = writer.getExistingDuration() + (long) timestamp;
            if (duration >= maxDurationMs) {
                return String.format("Recording reached the %d ms duration limit", maxDurationMs);
            }
        }
        if (minFreeDiskBytes > 0 && --checksUntilSpaceCheck <= 0) {
            checksUntilSpaceCheck = FREE_SPACE_CHECK_INTERVAL;
            // the directory always exists, the file itself is moved aside by the writer when appending
            Path dir = path.toAbsolutePath().getParent();
            try {
                long usable = Files.getFileStore(dir).getUsableSpace();
                if (usable < minFreeDiskBytes) {
                    return String.format("Recording volume has %d usable bytes, below the %d byte minimum", usable, minFreeDiskBytes);
                }
            } catch (IOException e) {
                log.warn("Could not check free space for {}", path, e);
            }
        }
        return null;
    }

    long getMaxBytes() {
        return maxBytes;
    }

    void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    long getMaxDurationMs() {
        return maxDurationMs;
    }

    void setMaxDurationMs(long maxDurationMs) {
        this.maxDurationMs = maxDurationMs;
    }

    long getMinFreeDiskBytes() {
        return minFreeDiskBytes;
    }

    void setMinFreeDiskBytes(long minFreeDiskBytes) {
        this.minFreeDiskBytes = minFreeDiskBytes;
    }

}
