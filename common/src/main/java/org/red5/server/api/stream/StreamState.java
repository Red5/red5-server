/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.api.stream;

/**
 * Represents all the states that a stream may be in at a requested point in time.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public enum StreamState {

    /** The stream has been created but not yet initialized. */
    INIT,
    /** The stream has been un-initialized / torn down. */
    UNINIT,
    /** The stream is open and ready for use. */
    OPEN,
    /** The stream has been closed. */
    CLOSED,
    /** The stream has been started. */
    STARTED,
    /** The stream has been stopped. */
    STOPPED,
    /** The stream is currently being published (broadcast). */
    PUBLISHING,
    /** The stream is currently being played back. */
    PLAYING,
    /** The stream playback or publish has been paused. */
    PAUSED,
    /** The stream playback or publish has been resumed after a pause. */
    RESUMED,
    /** The stream has reached its end. */
    END,
    /** A seek operation is in progress on the stream. */
    SEEK;

}
