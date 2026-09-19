/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2015 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.client.net.rtmp;

/**
 * Represents the current state of the client.
 *
 * @author mondain
 */
public enum ClientState {

    /** Client has not yet been initialized. */
    UNINIT,
    /** Client is in the process of establishing a connection to the server. */
    CONNECTING,
    /** Client has successfully connected to the server. */
    CONNECTED,
    /** Client is in the process of creating a stream. */
    STREAM_CREATING,
    /** Client is actively publishing a stream. */
    PUBLISHING,
    /** Client has stopped publishing a stream. */
    UNPUBLISHED,
    /** Client is actively playing a stream. */
    PLAYING,
    /** Client has stopped playback. */
    STOPPED,
    /** Client has disconnected from the server. */
    DISCONNECTED;

}
