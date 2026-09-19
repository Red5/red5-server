/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.so;

/**
 * One update event for a shared object received through a connection.
 *
 * @author mondain
 */
public interface ISharedObjectEvent {

    /**
     * The kind of shared object update or notification an event represents.
     */
    enum Type {
        /** A client connected to the shared object, server-side notification. */
        SERVER_CONNECT,
        /** A client disconnected from the shared object, server-side notification. */
        SERVER_DISCONNECT,
        /** An attribute was set on the shared object, server-side notification. */
        SERVER_SET_ATTRIBUTE,
        /** An attribute was deleted from the shared object, server-side notification. */
        SERVER_DELETE_ATTRIBUTE,
        /** A message/method call was sent to the shared object, server-side notification. */
        SERVER_SEND_MESSAGE,
        /** Request to clear all data/attributes of the shared object. */
        CLIENT_CLEAR_DATA,
        /** Request to delete a single attribute from the shared object. */
        CLIENT_DELETE_ATTRIBUTE,
        /** Notification that data was deleted from the shared object. */
        CLIENT_DELETE_DATA,
        /** Initial data sent to a client upon connecting to the shared object. */
        CLIENT_INITIAL_DATA,
        /** A status event/message reported to the client. */
        CLIENT_STATUS,
        /** Notification that data was updated on the shared object. */
        CLIENT_UPDATE_DATA,
        /** Request to update (set) a single attribute on the shared object. */
        CLIENT_UPDATE_ATTRIBUTE,
        /** A message/method call sent by a client to the shared object handlers. */
        CLIENT_SEND_MESSAGE
    };

    /**
     * Returns the type of the event.
     *
     * @return the type of the event
     */
    public Type getType();

    /**
     * Returns the key of the event.
     *
     * Depending on the type this contains:
     * <ul>
     * <li>the attribute name to set for SET_ATTRIBUTE</li>
     * <li>the attribute name to delete for DELETE_ATTRIBUTE</li>
     * <li>the handler name to call for SEND_MESSAGE</li>
     * </ul>
     * In all other cases the key is null
     *
     * @return the key of the event
     */
    public String getKey();

    /**
     * Returns the value of the event.
     *
     * Depending on the type this contains:
     * <ul>
     * <li>the attribute value to set for SET_ATTRIBUTE</li>
     * <li>a list of parameters to pass to the handler for SEND_MESSAGE</li>
     * </ul>
     * In all other cases the value is null
     *
     * @return the value of the event
     */
    public Object getValue();

}
