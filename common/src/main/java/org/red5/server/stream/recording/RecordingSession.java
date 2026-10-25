/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.stream.recording;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One recording accounted by a {@link RecordingBudget}: who made it, where it is written and how much it has written.
 * <br>
 * The attribution (application, stream, file, connection, remote address and principal) is fixed when the recording starts; bytes and
 * duration are updated by the file consumer after each tag it writes. The session is handed to the
 * {@link IRecordingPrincipalQuota} hook when the recording starts and stops.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class RecordingSession {

    private final RecordingBudget budget;

    private final RecordingBudget.DirectoryUsage directory;

    private final String application;

    private final String streamName;

    private final Path file;

    private final boolean append;

    private final String connectionId;

    private final String remoteAddress;

    private final String principal;

    private final long startTime = System.currentTimeMillis();

    // bytes the principal has recorded in this application, shared by its sessions; null without a principal
    private final AtomicLong principalUsage;

    // maximum bytes of the principal, asked once when the recording starts; 0 means unlimited
    private final long maxPrincipalBytes;

    private final AtomicBoolean stopped = new AtomicBoolean();

    private volatile long stopTime;

    // size of the recording file counting what it held before when appending
    private volatile long fileBytes;

    // bytes written by this recording session
    private volatile long bytesWritten;

    // duration of this recording session in milliseconds, by stream timestamps
    private volatile long durationMs;

    private volatile String failure;

    RecordingSession(RecordingBudget budget, RecordingBudget.DirectoryUsage directory, String application, String streamName, Path file, boolean append, String connectionId, String remoteAddress, String principal, AtomicLong principalUsage, long maxPrincipalBytes, long fileBytes) {
        this.budget = budget;
        this.directory = directory;
        this.application = application;
        this.streamName = streamName;
        this.file = file;
        this.append = append;
        this.connectionId = connectionId;
        this.remoteAddress = remoteAddress;
        this.principal = principal;
        this.principalUsage = principalUsage;
        this.maxPrincipalBytes = maxPrincipalBytes;
        this.fileBytes = fileBytes;
    }

    /**
     * Updates the size of the recording and checks the application budget and the principal's quota. Called by the file consumer
     * after each tag it writes, from its one writing thread.
     *
     * @param fileBytes
     *            bytes in the recording file, counting what it held before when appending
     * @param written
     *            bytes written by this recording session
     * @param duration
     *            duration of this recording session in milliseconds
     * @return reason the recording has to stop, or null while it is within the budget and quota
     */
    public synchronized String update(long fileBytes, long written, long duration) {
        if (stopped.get()) {
            // the bytes of a stopped session are part of the directory measurement
            return null;
        }
        directory.addActiveBytes(fileBytes - this.fileBytes);
        this.fileBytes = fileBytes;
        if (principalUsage != null) {
            principalUsage.addAndGet(written - bytesWritten);
        }
        bytesWritten = written;
        durationMs = duration;
        long maxApplicationBytes = budget.getMaxApplicationBytes();
        if (maxApplicationBytes > 0) {
            long used = directory.getUsedBytes();
            if (used >= maxApplicationBytes) {
                return String.format("Application recording budget of %d bytes reached", maxApplicationBytes);
            }
        }
        if (maxPrincipalBytes > 0 && principalUsage.get() >= maxPrincipalBytes) {
            return String.format("Recording quota of %d bytes reached for %s", maxPrincipalBytes, principal);
        }
        return null;
    }

    /**
     * Marks the session stopped; returns false if it already was.
     */
    boolean markStopped() {
        if (stopped.compareAndSet(false, true)) {
            stopTime = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    RecordingBudget.DirectoryUsage getDirectory() {
        return directory;
    }

    /**
     * Returns the name of the application recording.
     *
     * @return application name
     */
    public String getApplication() {
        return application;
    }

    /**
     * Returns the name of the recorded stream.
     *
     * @return stream name
     */
    public String getStreamName() {
        return streamName;
    }

    /**
     * Returns the recording file.
     *
     * @return file
     */
    public Path getFile() {
        return file;
    }

    /**
     * Returns whether the recording appends to an existing file.
     *
     * @return true when appending
     */
    public boolean isAppend() {
        return append;
    }

    /**
     * Returns the session id of the publishing connection, or null for a recording made without a connection.
     *
     * @return connection id
     */
    public String getConnectionId() {
        return connectionId;
    }

    /**
     * Returns the remote address of the publishing connection, or null for a recording made without a connection.
     *
     * @return remote address
     */
    public String getRemoteAddress() {
        return remoteAddress;
    }

    /**
     * Returns the principal of the publishing connection, as identified by the {@link IRecordingPrincipalQuota} hook, or null.
     *
     * @return principal
     */
    public String getPrincipal() {
        return principal;
    }

    /**
     * Returns when the recording started, in epoch milliseconds.
     *
     * @return start time
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Returns when the recording stopped, in epoch milliseconds, or 0 while it is recording.
     *
     * @return stop time
     */
    public long getStopTime() {
        return stopTime;
    }

    /**
     * Returns whether the recording has stopped.
     *
     * @return true once stopped
     */
    public boolean isStopped() {
        return stopped.get();
    }

    /**
     * Returns the bytes written by this recording session.
     *
     * @return bytes written
     */
    public long getBytesWritten() {
        return bytesWritten;
    }

    /**
     * Returns the bytes in the recording file, counting what it held before when appending.
     *
     * @return file bytes
     */
    public long getFileBytes() {
        return fileBytes;
    }

    /**
     * Returns the duration of this recording session in milliseconds, measured by stream timestamps.
     *
     * @return duration
     */
    public long getDurationMs() {
        return durationMs;
    }

    /**
     * Returns why the recording stopped writing early, such as a reached limit, or null.
     *
     * @return failure reason
     */
    public String getFailure() {
        return failure;
    }

    /**
     * Sets why the recording stopped writing early.
     *
     * @param failure
     *            failure reason
     */
    public void setFailure(String failure) {
        this.failure = failure;
    }

    @Override
    public String toString() {
        return String.format("application: %s stream: %s file: %s append: %b connection: %s remote: %s principal: %s bytes: %d file bytes: %d duration: %d ms%s", application, streamName, file, append, connectionId, remoteAddress, principal, bytesWritten, fileBytes, durationMs, failure != null ? " failure: " + failure : "");
    }

}
