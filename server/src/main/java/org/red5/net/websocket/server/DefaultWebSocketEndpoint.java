/*
 * RED5 Open Source Flash Server - https://github.com/red5 Copyright 2006-2018 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.net.websocket.server;

import java.io.EOFException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Optional;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.PongMessage;
import javax.websocket.Session;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.net.websocket.WSConstants;
import org.red5.net.websocket.WebSocketConnection;
import org.red5.net.websocket.WebSocketScope;
import org.red5.net.websocket.WebSocketScopeManager;
import org.red5.net.websocket.model.WSMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default WebSocket endpoint.
 *
 * @author Paul Gregoire
 */
public class DefaultWebSocketEndpoint extends Endpoint {

    private final Logger log = LoggerFactory.getLogger(DefaultWebSocketEndpoint.class);

    // websocket manager for this path / endpoint
    private WebSocketScopeManager manager;

    // websocket scope where connections connect
    private WebSocketScope scope;

    /**
     * TODO: Currently, Tomcat uses an Endpoint instance once - however the java doc of endpoint says: "Each instance of a websocket endpoint is guaranteed not to be called by more
     * than one thread at a time per active connection." This could mean that after calling onClose(), the instance could be reused for another connection so onOpen() will get
     * called (possibly from another thread).<br>
     * If this is the case, we would need a variable holder for the variables that are accessed by the Room thread, and read the reference to the holder at the beginning of onOpen,
     * onMessage, onClose methods to ensure the room thread always gets the correct instance of the variable holder.
     */

    private ThreadLocal<WebSocketConnection> connectionLocal = new ThreadLocal<>();

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        log.trace("Session {} opened", session.getId());
        // Set maximum messages size to 10,000 bytes
        session.setMaxTextMessageBufferSize(10000);
        session.addMessageHandler(stringHandler);
        session.addMessageHandler(binaryHandler);
        session.addMessageHandler(pongHandler);
        // get the manager
        manager = (WebSocketScopeManager) config.getUserProperties().get(WSConstants.WS_MANAGER);
        // get ws scope from user props
        scope = (WebSocketScope) config.getUserProperties().get(WSConstants.WS_SCOPE);
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        log.trace("Session {} closed", session.getId());
        // get the connection; try scope first then session
        WebSocketConnection conn = Optional.ofNullable(scope.getConnectionBySessionId(session.getId())).orElse((WebSocketConnection) session.getUserProperties().get(WSConstants.WS_CONNECTION));
        if (conn != null) {
            // close the ws conn
            conn.close();
            // remove the connection
            manager.removeConnection(conn);
        } else {
            log.debug("Connection for id: {} was not found in the scope: {}", session.getId(), scope.getPath());
        }
    }

    @Override
    public void onError(Session session, Throwable t) {
        // Most likely cause is a user closing their browser. Check to see if the root cause is EOF and if it is ignore it.
        // Protect against infinite loops.
        int count = 0;
        Throwable root = t;
        while (root.getCause() != null && count < 20) {
            root = root.getCause();
            count++;
        }
        if (root instanceof EOFException) {
            // Assume this is triggered by the user closing their browser and ignore it.
            if (log.isDebugEnabled()) {
                log.warn("EOF exception", root);
            }
        } else if (!session.isOpen() && root instanceof IOException) {
            // IOException after close. Assume this is a variation of the user closing their browser (or refreshing very quickly) and ignore it.
            if (log.isDebugEnabled()) {
                log.warn("IO exception when not opened", root);
            }
        } else {
            log.warn("onError: {}", t.toString(), t);
            onClose(session, new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, t.getMessage()));
        }
    }

    public WebSocketConnection getConnectionLocal() {
        return connectionLocal.get();
    }

    public void setConnectionLocal(WebSocketConnection connection) {
        this.connectionLocal.set(connection);
    }

    private final MessageHandler.Whole<String> stringHandler = new MessageHandler.Whole<String>() {

        @Override
        public void onMessage(final String message) {
            if (log.isTraceEnabled()) {
                log.trace("Message received {}", message);
            }
            final WebSocketConnection conn = connectionLocal.get();
            try {
                // update the byte received counter
                conn.updateReadBytes(message.getBytes().length);
                // create a websocket message and add the current connection for listener access
                WSMessage wsMessage = new WSMessage(message);
                wsMessage.setConnection(conn);
                // fire the message off to the scope for handling
                scope.onMessage(wsMessage);
            } catch (UnsupportedEncodingException e) {
                log.warn("Exception on message", e);
            }
        }

    };

    private final MessageHandler.Whole<ByteBuffer> binaryHandler = new MessageHandler.Whole<ByteBuffer>() {

        @Override
        public void onMessage(ByteBuffer message) {
            if (log.isTraceEnabled()) {
                log.trace("Message received {}", message);
            }
            final WebSocketConnection conn = connectionLocal.get();
            // update the byte received counter
            conn.updateReadBytes(message.limit());
            // create a websocket message and add the current connection for listener access
            WSMessage wsMessage = new WSMessage();
            wsMessage.setPayload(IoBuffer.wrap(message));
            wsMessage.setConnection(conn);
            // fire the message off to the scope for handling
            scope.onMessage(wsMessage);
        }

    };

    private final MessageHandler.Whole<PongMessage> pongHandler = new MessageHandler.Whole<PongMessage>() {

        @Override
        public void onMessage(PongMessage message) {
            if (log.isTraceEnabled()) {
                log.trace("Pong received {}", message);
            }
            // update the byte received counter
            connectionLocal.get().updateReadBytes(1);
        }

    };

}