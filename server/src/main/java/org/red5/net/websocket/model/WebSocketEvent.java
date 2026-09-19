/*
 * RED5 Open Source Flash Server - https://github.com/red5 Copyright 2006-2018 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.net.websocket.model;

/**
 * WebSocket event enumeration.
 *
 * @author Paul Gregoire
 */
public enum WebSocketEvent {

    /** A new WebSocket scope was created. */
    SCOPE_CREATED,
    /** A WebSocket scope was added to its parent/manager. */
    SCOPE_ADDED,
    /** A WebSocket scope was removed from its parent/manager. */
    SCOPE_REMOVED,
    /** A WebSocket connection was added to a scope. */
    CONNECTION_ADDED,
    /** A WebSocket connection was removed from a scope. */
    CONNECTION_REMOVED;

}
