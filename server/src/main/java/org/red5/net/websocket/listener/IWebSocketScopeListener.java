/*
 * RED5 Open Source Flash Server - https://github.com/red5 Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.net.websocket.listener;

import org.red5.net.websocket.WebSocketConnection;
import org.red5.net.websocket.WebSocketScope;

/**
 * <p>IWebSocketScopeListener interface.</p>
 *
 * @author mondain
 */
public interface IWebSocketScopeListener {

    /**
     * <p>scopeCreated.</p>
     *
     * @param wsScope a {@link org.red5.net.websocket.WebSocketScope} object
     */
    void scopeCreated(WebSocketScope wsScope);

    /**
     * <p>scopeAdded.</p>
     *
     * @param wsScope a {@link org.red5.net.websocket.WebSocketScope} object
     */
    void scopeAdded(WebSocketScope wsScope);

    /**
     * <p>scopeRemoved.</p>
     *
     * @param wsScope a {@link org.red5.net.websocket.WebSocketScope} object
     */
    void scopeRemoved(WebSocketScope wsScope);

    /**
     * <p>connectionAdded.</p>
     *
     * @param wsScope a {@link org.red5.net.websocket.WebSocketScope} object
     * @param wsConn a {@link org.red5.net.websocket.WebSocketConnection} object
     */
    void connectionAdded(WebSocketScope wsScope, WebSocketConnection wsConn);

    /**
     * <p>connectionRemoved.</p>
     *
     * @param wsScope a {@link org.red5.net.websocket.WebSocketScope} object
     * @param wsConn a {@link org.red5.net.websocket.WebSocketConnection} object
     */
    void connectionRemoved(WebSocketScope wsScope, WebSocketConnection wsConn);

    /**
     * XXX(paul) maybe add this for recv update earlier than onMessage callback.
     *
     * String message received on the given connection and scope.
     *
     * @param wsScope
     * @param wsConn
     * @param message
     */
    // void receivedMessage(WebSocketScope wsScope, WebSocketConnection wsConn, String message);

}
