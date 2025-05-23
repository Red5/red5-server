/*
 * RED5 Open Source Flash Server - https://github.com/red5 Copyright 2006-2018 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.net.websocket.listener;

import org.red5.net.websocket.WebSocketConnection;
import org.red5.net.websocket.model.WSMessage;

/**
 * Listener for WebSocket events.
 *
 * @author mondain
 */
public interface IWebSocketDataListener {

    /**
     * Returns the protocol for which this listener is interested.
     *
     * @return protocol
     */
    public String getProtocol();

    /**
     * Sets the protocol for which this listener is interested.
     *
     * @param protocol a {@link java.lang.String} object
     */
    public void setProtocol(String protocol);

    /**
     * Dispatch message.
     *
     * @param message a {@link org.red5.net.websocket.model.WSMessage} object
     */
    public void onWSMessage(WSMessage message);

    /**
     * Connect a WebSocket client.
     *
     * @param conn
     *            WebSocketConnection
     */
    public void onWSConnect(WebSocketConnection conn);

    /**
     * Disconnect WebSocket client.
     *
     * @param conn
     *            WebSocketConnection
     */
    public void onWSDisconnect(WebSocketConnection conn);

    /**
     * Stops the listener.
     */
    public void stop();

}
