/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.stream.recording;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

/**
 * Policy deciding which recordings in an application's record directory are deleted, set on the application's {@link RecordingBudget}.
 * <br>
 * The budget applies the policy when a recording stops and, when configured, on a schedule. The record directory may also hold
 * video-on-demand content, so an implementation must only delete what it is sure is an expired recording, must stay inside the
 * directory, and must never delete a file for which {@code inUse} is true. {@link OldestFirstRetentionPolicy} is the built-in policy.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public interface IRecordingRetentionPolicy {

    /**
     * Applies the policy to a record directory.
     *
     * @param recordDirectory
     *            the application's record directory
     * @param inUse
     *            true for the files of recordings in progress, and of the recording that just stopped, which must not be deleted
     * @return the files deleted
     */
    List<Path> apply(Path recordDirectory, Predicate<Path> inUse);

}
