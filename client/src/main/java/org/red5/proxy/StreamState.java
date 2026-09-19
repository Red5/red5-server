/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2015 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.proxy;

/**
 * <p>StreamState class.</p>
 *
 * @author mondain
 */
public enum StreamState {

    /** The stream has not yet been set up. */
    UNINITIALIZED,
    /** The stream is stopped and not currently active. */
    STOPPED,
    /** The underlying connection to the server is being established. */
    CONNECTING,
    /** A stream is being created on the connection. */
    STREAM_CREATING,
    /** A publish request has been sent and is in progress. */
    PUBLISHING,
    /** The stream has been successfully published. */
    PUBLISHED,
    /** The stream has been unpublished. */
    UNPUBLISHED;

}
