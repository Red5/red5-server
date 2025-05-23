/*
 * RED5 Open Source Flash Server - https://github.com/red5 Copyright 2006-2018 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.net.websocket.listener;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Set;

import org.red5.net.websocket.WebSocketConnection;
import org.red5.net.websocket.WebSocketPlugin;
import org.red5.net.websocket.WebSocketScope;
import org.red5.net.websocket.WebSocketScopeManager;
import org.red5.net.websocket.model.WSMessage;
import org.red5.server.plugin.PluginRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default WebSocket data listener. In this default implementation, all messages are echoed back to every connection in the current scope.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class DefaultWebSocketDataListener extends WebSocketDataListener {

    private static final Logger log = LoggerFactory.getLogger(DefaultWebSocketDataListener.class);

    /** {@inheritDoc} */
    @Override
    public void onWSConnect(WebSocketConnection conn) {
        log.info("Connect: {}", conn);
    }

    /** {@inheritDoc} */
    @Override
    public void onWSDisconnect(WebSocketConnection conn) {
        log.info("Disconnect: {}", conn);
    }

    /** {@inheritDoc} */
    @Override
    public void onWSMessage(WSMessage message) {
        // assume we have text
        String msg = new String(message.getPayload().array());
        log.info("onWSMessage: {}", msg);
        // get the path
        String path = message.getPath();
        // just echo back the message
        WebSocketScopeManager manager = ((WebSocketPlugin) PluginRegistry.getPlugin(WebSocketPlugin.NAME)).getManager(path);
        if (manager != null) {
            // get the ws scope
            WebSocketScope wsScope = manager.getScope(path);
            Set<WebSocketConnection> conns = wsScope.getConns();
            for (WebSocketConnection conn : conns) {
                log.debug("Echoing to {}", conn);
                try {
                    conn.send(msg);
                } catch (UnsupportedEncodingException e) {
                    log.warn("Encoding issue with the message data: {}", message, e);
                } catch (IOException e) {
                    log.warn("IO exception", e);
                }
            }
        } else {
            log.info("No manager found for path: {}", path);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void stop() {
        log.info("Stop");
    }

}
