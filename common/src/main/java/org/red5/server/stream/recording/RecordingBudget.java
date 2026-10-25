/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.stream.recording;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import org.red5.server.api.IConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Accounts the recordings of applications: an aggregate byte budget per record directory, optional per-principal quotas, attribution of
 * each recording, and an optional retention policy.
 * <br>
 * The recording listener looks up a bean named {@value #BEAN_NAME} in the application context, so the server-wide bean in
 * red5-common.xml applies to every application, and an application can declare its own bean with that id in its red5-web.xml. All
 * limits are off by default.
 * <br>
 * <b>Application budget</b>: {@link #setMaxApplicationBytes(long)} caps the bytes of all regular files under an application's record
 * directory (its {@code streams} directory by default, including the directories of its sub-scopes); files are counted wherever they
 * came from, so video-on-demand content in the directory counts too. A record or append publish is refused while the directory is at
 * the budget, and a recording in progress stops, with NetStream.Record.Failed, once the directory reaches it. Accounting is kept cheap:
 * the directory is walked, without following links, when a recording starts and when one stops, leaving out the files of the
 * recordings in progress; while recording, each recording adds the size of its file as the file consumer writes it. No walk is made
 * while the budget is 0.
 * <br>
 * <b>Per-principal quotas</b>: an {@link IRecordingPrincipalQuota} hook identifies the principal of a publishing connection and its
 * quota; the bytes each principal records are counted in memory.
 * <br>
 * <b>Attribution</b>: every recording is a {@link RecordingSession} holding the application, stream, file, connection id, remote
 * address and principal, and its bytes and duration; the hook is told when it starts and stops, and one INFO line is logged when it
 * stops.
 * <br>
 * <b>Retention</b>: an {@link IRecordingRetentionPolicy}, such as {@link OldestFirstRetentionPolicy}, is applied to the record
 * directory when a recording stops and, if {@link #setRetentionIntervalMs(long)} is set, on a schedule. The schedule covers the
 * directories the budget has seen a recording in, the one set with {@link #setRecordDirectory(String)}, and any passed to
 * {@link #applyRetention(Path)}.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class RecordingBudget {

    private static final Logger log = LoggerFactory.getLogger(RecordingBudget.class);

    /**
     * Name of the bean the recording listener looks up in the application context.
     */
    public static final String BEAN_NAME = "recordingBudget";

    /**
     * Maximum bytes of all regular files in an application's record directory; 0 means unlimited.
     */
    private volatile long maxApplicationBytes;

    /**
     * Per-principal quota hook, or null for none.
     */
    private volatile IRecordingPrincipalQuota principalQuota;

    /**
     * Retention policy, or null for none.
     */
    private volatile IRecordingRetentionPolicy retentionPolicy;

    /**
     * Interval of the scheduled retention runs in milliseconds; 0 runs retention only when a recording stops.
     */
    private long retentionIntervalMs;

    /**
     * Record directory known from configuration, for scheduled retention before any recording.
     */
    private String recordDirectory;

    private final ConcurrentMap<Path, DirectoryUsage> directories = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, AtomicLong> principals = new ConcurrentHashMap<>();

    private ScheduledExecutorService retentionExecutor;

    private boolean destroyed;

    /**
     * Registers the configured record directory and starts the scheduled retention, when configured. Optional; the first recording
     * does the same.
     */
    public void init() {
        if (recordDirectory != null && !recordDirectory.isBlank()) {
            getDirectory(Paths.get(recordDirectory));
        }
        scheduleRetention();
    }

    /**
     * Stops the scheduled retention.
     */
    public synchronized void destroy() {
        destroyed = true;
        if (retentionExecutor != null) {
            retentionExecutor.shutdownNow();
            retentionExecutor = null;
        }
    }

    /**
     * Accounts a recording that is about to start, before any existing file is replaced.
     *
     * @param recordDirectory
     *            the application's record directory
     * @param application
     *            name of the application
     * @param conn
     *            publishing connection, or null
     * @param streamName
     *            name of the recorded stream
     * @param file
     *            recording file
     * @param append
     *            true when appending to an existing file
     * @return the session to hand to the file consumer and to pass to {@link #stop(RecordingSession)}
     * @throws RecordingRefusedException
     *             when the application budget or the principal's quota is used up
     */
    public RecordingSession start(Path recordDirectory, String application, IConnection conn, String streamName, Path file, boolean append) {
        DirectoryUsage directory = getDirectory(recordDirectory);
        String connectionId = null, remoteAddress = null, principal = null;
        if (conn != null) {
            connectionId = conn.getSessionId();
            remoteAddress = conn.getRemoteAddress();
        }
        AtomicLong principalUsage = null;
        long maxPrincipalBytes = 0L;
        final IRecordingPrincipalQuota quota = principalQuota;
        if (quota != null) {
            principal = quota.getPrincipal(conn);
            if (principal != null) {
                principalUsage = principals.computeIfAbsent(principal, p -> new AtomicLong(Math.max(0L, quota.getUsedBytes(p))));
                maxPrincipalBytes = quota.getMaxBytes(principal);
                if (maxPrincipalBytes > 0 && principalUsage.get() >= maxPrincipalBytes) {
                    throw refuse(String.format("Recording quota of %d bytes used up for %s", maxPrincipalBytes, principal), application, streamName, connectionId, remoteAddress);
                }
            }
        }
        // what an appended file holds counts towards the budget; a file that is replaced does not
        long existingBytes = 0L;
        if (append) {
            try {
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    existingBytes = Files.size(file);
                }
            } catch (IOException e) {
                log.warn("Could not read the size of {}", file, e);
            }
        }
        RecordingSession session = new RecordingSession(this, directory, application, streamName, file.toAbsolutePath().normalize(), append, connectionId, remoteAddress, principal, principalUsage, maxPrincipalBytes, existingBytes);
        long maxBytes = maxApplicationBytes;
        synchronized (directory) {
            directory.register(session);
            if (maxBytes > 0) {
                directory.measure();
                if (directory.getUsedBytes() >= maxBytes) {
                    directory.unregister(session, false);
                    throw refuse(String.format("Application recording budget of %d bytes used up, %d bytes in %s", maxBytes, directory.getUsedBytes(), directory.path), application, streamName, connectionId, remoteAddress);
                }
            }
        }
        log.debug("Recording started - {}", session);
        if (quota != null) {
            try {
                quota.recordingStarted(session);
            } catch (Exception e) {
                log.warn("Recording quota hook failed on start of {}", file, e);
            }
        }
        return session;
    }

    private RecordingRefusedException refuse(String reason, String application, String streamName, String connectionId, String remoteAddress) {
        log.warn("Recording refused - application: {} stream: {} connection: {} remote: {}: {}", application, streamName, connectionId, remoteAddress, reason);
        return new RecordingRefusedException(reason);
    }

    /**
     * Accounts a recording that has stopped and its file closed: logs its attribution, tells the quota hook, and applies the retention
     * policy. Calling it again for the same session does nothing.
     *
     * @param session
     *            the recording
     */
    public void stop(RecordingSession session) {
        DirectoryUsage directory = session.getDirectory();
        synchronized (directory) {
            synchronized (session) {
                if (!session.markStopped()) {
                    return;
                }
                directory.unregister(session, true);
            }
            if (maxApplicationBytes > 0) {
                directory.measure();
            }
        }
        log.info("Recording stopped - {}", session);
        IRecordingPrincipalQuota quota = principalQuota;
        if (quota != null) {
            try {
                quota.recordingStopped(session);
            } catch (Exception e) {
                log.warn("Recording quota hook failed on stop of {}", session.getFile(), e);
            }
        }
        applyRetention(directory, session.getFile());
    }

    /**
     * Applies the retention policy to a record directory now, and has scheduled retention cover it from now on.
     *
     * @param recordDirectory
     *            record directory
     * @return the files deleted
     */
    public List<Path> applyRetention(Path recordDirectory) {
        return applyRetention(getDirectory(recordDirectory), null);
    }

    /**
     * Applies the retention policy to every record directory the budget knows; this is what the schedule runs.
     */
    public void applyRetention() {
        for (DirectoryUsage directory : directories.values()) {
            try {
                applyRetention(directory, null);
            } catch (Exception e) {
                log.warn("Retention failed for {}", directory.path, e);
            }
        }
    }

    private List<Path> applyRetention(DirectoryUsage directory, Path stopped) {
        IRecordingRetentionPolicy policy = retentionPolicy;
        if (policy == null) {
            return Collections.emptyList();
        }
        // recordings cannot start in the directory while the policy runs
        synchronized (directory) {
            List<Path> deleted;
            try {
                deleted = policy.apply(directory.path, directory.inUse(stopped));
            } catch (RuntimeException e) {
                log.warn("Retention policy failed for {}", directory.path, e);
                return Collections.emptyList();
            }
            if (!deleted.isEmpty() && maxApplicationBytes > 0) {
                directory.measure();
            }
            return deleted;
        }
    }

    /**
     * Returns the bytes used in a record directory, measuring it now.
     *
     * @param recordDirectory
     *            record directory
     * @return bytes of the files in the directory and of the recordings in progress
     */
    public long getUsedBytes(Path recordDirectory) {
        DirectoryUsage directory = getDirectory(recordDirectory);
        synchronized (directory) {
            directory.measure();
            return directory.getUsedBytes();
        }
    }

    /**
     * Returns the bytes a principal has recorded, as counted by this budget.
     *
     * @param principal
     *            principal
     * @return bytes, 0 for a principal not seen
     */
    public long getPrincipalBytes(String principal) {
        AtomicLong usage = principals.get(principal);
        return usage != null ? usage.get() : 0L;
    }

    private DirectoryUsage getDirectory(Path recordDirectory) {
        Path key = recordDirectory.toAbsolutePath().normalize();
        DirectoryUsage directory = directories.get(key);
        if (directory == null) {
            directory = directories.computeIfAbsent(key, DirectoryUsage::new);
            scheduleRetention();
        }
        return directory;
    }

    private synchronized void scheduleRetention() {
        if (retentionExecutor == null && !destroyed && retentionPolicy != null && retentionIntervalMs > 0) {
            retentionExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "RecordingRetention");
                thread.setDaemon(true);
                return thread;
            });
            retentionExecutor.scheduleWithFixedDelay(this::applyRetention, retentionIntervalMs, retentionIntervalMs, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Returns the maximum bytes of all regular files in an application's record directory; 0 means unlimited.
     *
     * @return maximum bytes
     */
    public long getMaxApplicationBytes() {
        return maxApplicationBytes;
    }

    /**
     * Sets the maximum bytes of all regular files in an application's record directory, recordings in progress included; 0 means
     * unlimited.
     *
     * @param maxApplicationBytes
     *            maximum bytes
     */
    public void setMaxApplicationBytes(long maxApplicationBytes) {
        this.maxApplicationBytes = maxApplicationBytes;
    }

    /**
     * Returns the per-principal quota hook.
     *
     * @return hook, or null
     */
    public IRecordingPrincipalQuota getPrincipalQuota() {
        return principalQuota;
    }

    /**
     * Sets the per-principal quota hook.
     *
     * @param principalQuota
     *            hook, or null for none
     */
    public void setPrincipalQuota(IRecordingPrincipalQuota principalQuota) {
        this.principalQuota = principalQuota;
    }

    /**
     * Returns the retention policy.
     *
     * @return policy, or null
     */
    public IRecordingRetentionPolicy getRetentionPolicy() {
        return retentionPolicy;
    }

    /**
     * Sets the retention policy applied when a recording stops and on the retention schedule.
     *
     * @param retentionPolicy
     *            policy, or null for none
     */
    public void setRetentionPolicy(IRecordingRetentionPolicy retentionPolicy) {
        this.retentionPolicy = retentionPolicy;
    }

    /**
     * Returns the interval of the scheduled retention runs in milliseconds.
     *
     * @return interval, 0 when retention only runs as recordings stop
     */
    public long getRetentionIntervalMs() {
        return retentionIntervalMs;
    }

    /**
     * Sets the interval of the scheduled retention runs in milliseconds; 0, the default, runs retention only when a recording stops.
     *
     * @param retentionIntervalMs
     *            interval
     */
    public void setRetentionIntervalMs(long retentionIntervalMs) {
        this.retentionIntervalMs = retentionIntervalMs;
    }

    /**
     * Returns the configured record directory.
     *
     * @return record directory, or null
     */
    public String getRecordDirectory() {
        return recordDirectory;
    }

    /**
     * Sets a record directory the scheduled retention covers from {@link #init()} on, before any recording is made in it.
     *
     * @param recordDirectory
     *            record directory
     */
    public void setRecordDirectory(String recordDirectory) {
        this.recordDirectory = recordDirectory;
    }

    /**
     * Returns whether a file belongs to a recording: the recording file itself or the working files the FLV writer keeps next to it
     * while recording ({@code name.flv.ser}, {@code name.flv.info} and, when appending, {@code name.old}).
     */
    static boolean belongsTo(Path candidate, Path recording) {
        Path parent = candidate.getParent();
        if (parent == null || !parent.equals(recording.getParent())) {
            return false;
        }
        String name = candidate.getFileName().toString();
        String recordingName = recording.getFileName().toString();
        if (name.equals(recordingName) || name.startsWith(recordingName + '.')) {
            return true;
        }
        int dot = recordingName.lastIndexOf('.');
        return dot > 0 && name.equals(recordingName.substring(0, dot) + ".old");
    }

    /**
     * Resolves a file's directory to its real path, so it compares with paths found by walking the real record directory.
     */
    static Path realFile(Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent != null) {
            try {
                return parent.toRealPath().resolve(absolute.getFileName());
            } catch (IOException e) {
                // the directory does not exist yet
            }
        }
        return absolute;
    }

    /**
     * Usage of one record directory: the bytes of its files, measured by walking it, plus the bytes of the recordings in progress in it,
     * which the walk leaves out. Walks, registration and retention synchronize on the instance; the writers only add to an atomic
     * counter.
     */
    static final class DirectoryUsage {

        final Path path;

        // bytes of the files found by the last walk, leaving out those of recordings in progress
        private volatile long measuredBytes;

        // bytes of the files of the recordings in progress
        private final AtomicLong activeBytes = new AtomicLong();

        private final Set<RecordingSession> sessions = ConcurrentHashMap.newKeySet();

        DirectoryUsage(Path path) {
            this.path = path;
        }

        long getUsedBytes() {
            return measuredBytes + activeBytes.get();
        }

        void addActiveBytes(long delta) {
            activeBytes.addAndGet(delta);
        }

        void register(RecordingSession session) {
            synchronized (session) {
                if (sessions.add(session)) {
                    activeBytes.addAndGet(session.getFileBytes());
                }
            }
        }

        /**
         * Removes a session; a stopped recording's file stays counted until the next walk measures it.
         */
        void unregister(RecordingSession session, boolean written) {
            synchronized (session) {
                if (sessions.remove(session)) {
                    activeBytes.addAndGet(-session.getFileBytes());
                    if (written) {
                        measuredBytes += session.getFileBytes();
                    }
                }
            }
        }

        /**
         * Walks the directory and sums the regular files outside the recordings in progress; the caller holds the lock.
         */
        void measure() {
            if (!Files.isDirectory(path)) {
                measuredBytes = 0L;
                return;
            }
            final List<Path> active = activeFiles();
            final long[] total = new long[1];
            try {
                Files.walkFileTree(path.toRealPath(), EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (attrs.isRegularFile() && !matchesAny(file, active)) {
                            total[0] += attrs.size();
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException e) {
                        return FileVisitResult.CONTINUE;
                    }

                });
                measuredBytes = total[0];
            } catch (IOException e) {
                log.warn("Could not measure record directory {}", path, e);
            }
        }

        /**
         * Returns a test for the files of the recordings in progress and, when given, of a recording that just stopped.
         */
        Predicate<Path> inUse(Path stopped) {
            final List<Path> files = activeFiles();
            if (stopped != null) {
                files.add(realFile(stopped));
            }
            return file -> matchesAny(file, files);
        }

        private List<Path> activeFiles() {
            List<Path> files = new ArrayList<>();
            for (RecordingSession session : sessions) {
                files.add(realFile(session.getFile()));
            }
            return files;
        }

        private static boolean matchesAny(Path file, List<Path> recordings) {
            for (Path recording : recordings) {
                if (belongsTo(file, recording)) {
                    return true;
                }
            }
            return false;
        }

    }

}
