/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.stream.recording;

import org.red5.server.api.IConnection;

/**
 * Hook an application implements to put recordings under per-principal quotas, set on its {@link RecordingBudget}.
 * <br>
 * The hook identifies the principal (an authenticated user, an API key, a customer; whatever the application authenticates) of a
 * publishing connection and returns how many bytes that principal may record. The budget counts the bytes each principal records, in
 * memory for as long as the budget lives, starting from {@link #getUsedBytes(String)}, refuses a recording when the principal's quota
 * is used up and stops a recording when it reaches the quota, with the same NetStream.Record status as the other recording limits.
 * Every method has a default, so an implementation only overrides what it needs; with the defaults no principal is identified and no
 * quota applies.
 * <br>
 * An application that keeps usage across restarts stores it from {@link #recordingStopped(RecordingSession)} and returns it from
 * {@link #getUsedBytes(String)}. Deleting recordings, by hand or with a retention policy, does not credit the principal.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public interface IRecordingPrincipalQuota {

    /**
     * Returns the principal of a publishing connection. Called when a recording starts; the connection is null for a recording started
     * without one, such as a server-side stream.
     *
     * @param conn
     *            publishing connection, may be null
     * @return principal, or null when the recording is not subject to a per-principal quota
     */
    default String getPrincipal(IConnection conn) {
        return null;
    }

    /**
     * Returns the maximum bytes a principal may record. Called when each of the principal's recordings starts.
     *
     * @param principal
     *            principal
     * @return maximum bytes, 0 means unlimited
     */
    default long getMaxBytes(String principal) {
        return 0L;
    }

    /**
     * Returns the bytes a principal recorded before the budget started counting, such as usage the application persisted. Called once
     * per principal, when the budget first sees it.
     *
     * @param principal
     *            principal
     * @return bytes already used
     */
    default long getUsedBytes(String principal) {
        return 0L;
    }

    /**
     * Called when a recording has been allowed to start.
     *
     * @param session
     *            the recording
     */
    default void recordingStarted(RecordingSession session) {
    }

    /**
     * Called when a recording has stopped, with its final bytes and duration and the reason it stopped early, if it did.
     *
     * @param session
     *            the recording
     */
    default void recordingStopped(RecordingSession session) {
    }

}
