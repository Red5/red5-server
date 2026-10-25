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
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Built-in retention policy deleting the oldest recordings of an application once they are older than a maximum age, or while their
 * total size is above a maximum. Both limits are 0 by default, which disables the policy.
 * <br>
 * The record directory may also hold video-on-demand content, so the policy is deliberately narrow:
 * <ul>
 * <li>only regular files whose name ends with one of the configured extensions ({@code .flv} by default) are considered, and only they
 * count towards the total size;</li>
 * <li>the directory tree is walked without following symbolic links, so a link, and anything a link points to, is never considered and
 * the policy never leaves the directory; the record directory itself is resolved once, so it may be a link an administrator set up;</li>
 * <li>files of recordings in progress, the recording that just stopped, and any file modified within the last {@code minIdleMs} (a
 * file some other writer may still be writing) are never deleted;</li>
 * <li>every deletion is logged.</li>
 * </ul>
 * Files are ordered by last modification time, oldest first.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class OldestFirstRetentionPolicy implements IRecordingRetentionPolicy {

    private static final Logger log = LoggerFactory.getLogger(OldestFirstRetentionPolicy.class);

    /**
     * Maximum age of a recording in milliseconds, by last modification time; 0 disables the age limit.
     */
    private long maxAgeMs;

    /**
     * Maximum total bytes of the recordings in the directory; 0 disables the size limit.
     */
    private long maxTotalBytes;

    /**
     * Extensions of the files the policy may delete, lower case with a leading dot.
     */
    private List<String> extensions = List.of(".flv");

    /**
     * Files modified within this many milliseconds are never deleted.
     */
    private long minIdleMs = 60_000L;

    /** {@inheritDoc} */
    @Override
    public List<Path> apply(Path recordDirectory, Predicate<Path> inUse) {
        if ((maxAgeMs <= 0 && maxTotalBytes <= 0) || extensions.isEmpty()) {
            return Collections.emptyList();
        }
        final Path root;
        try {
            if (!Files.isDirectory(recordDirectory)) {
                return Collections.emptyList();
            }
            root = recordDirectory.toRealPath();
        } catch (IOException e) {
            log.warn("Retention could not resolve record directory {}", recordDirectory, e);
            return Collections.emptyList();
        }
        List<Candidate> candidates = new ArrayList<>();
        try {
            // without FOLLOW_LINKS links are reported as links, never as the files or directories they point to
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile() && matches(file)) {
                        candidates.add(new Candidate(file, attrs.size(), attrs.lastModifiedTime().toMillis()));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    log.debug("Retention skipped {}: {}", file, e.getMessage());
                    return FileVisitResult.CONTINUE;
                }

            });
        } catch (IOException e) {
            log.warn("Retention could not walk record directory {}", root, e);
            return Collections.emptyList();
        }
        candidates.sort(Comparator.comparingLong((Candidate c) -> c.modified).thenComparing(c -> c.path));
        long total = 0L;
        for (Candidate candidate : candidates) {
            total += candidate.size;
        }
        long now = System.currentTimeMillis();
        List<Path> deleted = new ArrayList<>();
        for (Candidate candidate : candidates) {
            boolean expired = maxAgeMs > 0 && now - candidate.modified > maxAgeMs;
            boolean oversized = maxTotalBytes > 0 && total > maxTotalBytes;
            if (!expired && !oversized) {
                // the remaining files are newer and the total only shrinks, so nothing else is due
                break;
            }
            if (inUse.test(candidate.path) || now - candidate.modified < minIdleMs) {
                continue;
            }
            if (delete(root, candidate, expired ? "older than " + maxAgeMs + " ms" : "recordings above " + maxTotalBytes + " bytes")) {
                total -= candidate.size;
                deleted.add(candidate.path);
            }
        }
        return deleted;
    }

    private boolean matches(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String extension : extensions) {
            if (name.endsWith(extension) && name.length() > extension.length()) {
                return true;
            }
        }
        return false;
    }

    private boolean delete(Path root, Candidate candidate, String reason) {
        Path file = candidate.path;
        try {
            // check again right before deleting: still a regular file, not a link, inside the directory
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attrs.isRegularFile() || !file.normalize().startsWith(root)) {
                log.warn("Retention skipped {}, it is no longer a regular file in {}", file, root);
                return false;
            }
            Files.delete(file);
            log.info("Retention deleted recording {} ({} bytes, modified {}): {}", file, candidate.size, attrs.lastModifiedTime(), reason);
            return true;
        } catch (IOException e) {
            log.warn("Retention could not delete recording {}", file, e);
            return false;
        }
    }

    /**
     * Returns the maximum age of a recording in milliseconds; 0 disables the age limit.
     *
     * @return maximum age
     */
    public long getMaxAgeMs() {
        return maxAgeMs;
    }

    /**
     * Sets the maximum age of a recording in milliseconds, by last modification time; 0 disables the age limit.
     *
     * @param maxAgeMs
     *            maximum age
     */
    public void setMaxAgeMs(long maxAgeMs) {
        this.maxAgeMs = maxAgeMs;
    }

    /**
     * Returns the maximum total bytes of the recordings in the directory; 0 disables the size limit.
     *
     * @return maximum total bytes
     */
    public long getMaxTotalBytes() {
        return maxTotalBytes;
    }

    /**
     * Sets the maximum total bytes of the recordings in the directory, counting only files with the configured extensions; the oldest
     * are deleted until the total is at or below it. 0 disables the size limit.
     *
     * @param maxTotalBytes
     *            maximum total bytes
     */
    public void setMaxTotalBytes(long maxTotalBytes) {
        this.maxTotalBytes = maxTotalBytes;
    }

    /**
     * Returns the extensions of the files the policy may delete.
     *
     * @return extensions, lower case with a leading dot
     */
    public List<String> getExtensions() {
        return extensions;
    }

    /**
     * Sets the extensions of the files the policy may delete, such as {@code .flv}; matching ignores case. An empty list disables the
     * policy.
     *
     * @param extensions
     *            extensions, with or without the leading dot
     */
    public void setExtensions(List<String> extensions) {
        List<String> normalized = new ArrayList<>();
        if (extensions != null) {
            for (String extension : extensions) {
                if (extension != null && !extension.isBlank()) {
                    String ext = extension.trim().toLowerCase(Locale.ROOT);
                    normalized.add(ext.startsWith(".") ? ext : "." + ext);
                }
            }
        }
        this.extensions = List.copyOf(normalized);
    }

    /**
     * Returns how recently modified a file may be and still be deleted, in milliseconds.
     *
     * @return minimum idle time
     */
    public long getMinIdleMs() {
        return minIdleMs;
    }

    /**
     * Sets the time in milliseconds a file must have gone unmodified before it may be deleted, which protects files written by
     * something the budget does not track; 60 seconds by default.
     *
     * @param minIdleMs
     *            minimum idle time
     */
    public void setMinIdleMs(long minIdleMs) {
        this.minIdleMs = minIdleMs;
    }

    private static final class Candidate {

        final Path path;

        final long size;

        final long modified;

        Candidate(Path path, long size, long modified) {
            this.path = path;
            this.size = size;
            this.modified = modified;
        }

    }

}
