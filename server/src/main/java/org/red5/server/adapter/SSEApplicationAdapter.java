/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.adapter;

import java.util.List;

import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.IConnection;
import org.red5.server.api.Red5;
import org.red5.server.api.scope.IScope;
import org.red5.server.net.sse.ISSEService;
import org.red5.server.net.sse.SSEConnection;
import org.red5.server.net.sse.SSEEvent;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Application adapter that provides Server-Sent Events integration.
 * This adapter extends MultiThreadedApplicationAdapter and provides
 * convenient methods for sending SSE events based on Red5 application events.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class SSEApplicationAdapter extends MultiThreadedApplicationAdapter {

    private static Logger log = Red5LoggerFactory.getLogger(SSEApplicationAdapter.class);

    @Autowired(required = false)
    private ISSEService sseService;

    @Override
    public boolean appStart(IScope app) {
        log.info("SSE-enabled application starting: {}", app.getName());

        // Notify SSE clients about application start
        if (sseService != null) {
            sseService.broadcastEventToScope(app, "app.start", "Application " + app.getName() + " has started");
        }

        return super.appStart(app);
    }

    @Override
    public void appStop(IScope app) {
        log.info("SSE-enabled application stopping: {}", app.getName());

        // Notify SSE clients about application stop
        if (sseService != null) {
            sseService.broadcastEventToScope(app, "app.stop", "Application " + app.getName() + " is stopping");
        }

        super.appStop(app);
    }

    @Override
    public boolean appConnect(IConnection conn, Object[] params) {
        log.debug("Client connected to SSE-enabled app: {}", conn.getRemoteAddress());

        // Notify SSE clients about new connection
        if (sseService != null) {
            sseService.broadcastEventToScope(conn.getScope(), "user.connect", "New user connected from " + conn.getRemoteAddress());
        }

        return super.appConnect(conn, params);
    }

    @Override
    public void appDisconnect(IConnection conn) {
        log.debug("Client disconnected from SSE-enabled app: {}", conn.getRemoteAddress());

        // Notify SSE clients about disconnection
        if (sseService != null) {
            sseService.broadcastEventToScope(conn.getScope(), "user.disconnect", "User disconnected: " + conn.getRemoteAddress());
        }

        super.appDisconnect(conn);
    }

    /**
     * Broadcasts a message to all SSE clients in the application scope.
     *
     * @param message The message to broadcast
     * @return The number of successful sends
     */
    public int broadcastSSEMessage(String message) {
        if (sseService != null) {
            IConnection conn = Red5.getConnectionLocal();
            IScope currentScope = conn != null ? conn.getScope() : null;
            if (currentScope != null) {
                return sseService.broadcastToScope(currentScope, message);
            }
        }
        log.warn("SSE service not available for broadcasting or no current scope");
        return 0;
    }

    /**
     * Broadcasts an event to all SSE clients in the application scope.
     *
     * @param event The event type
     * @param message The message to broadcast
     * @return The number of successful sends
     */
    public int broadcastSSEEvent(String event, String message) {
        if (sseService != null) {
            IConnection conn = Red5.getConnectionLocal();
            IScope currentScope = conn != null ? conn.getScope() : null;
            if (currentScope != null) {
                return sseService.broadcastEventToScope(currentScope, event, message);
            }
        }
        log.warn("SSE service not available for broadcasting or no current scope");
        return 0;
    }

    /**
     * Broadcasts an SSE event to all clients in the application scope.
     *
     * @param sseEvent The SSE event to broadcast
     * @return The number of successful sends
     */
    public int broadcastSSEEvent(SSEEvent sseEvent) {
        if (sseService != null) {
            IConnection conn = Red5.getConnectionLocal();
            IScope currentScope = conn != null ? conn.getScope() : null;
            if (currentScope != null) {
                return sseService.broadcastEventToScope(currentScope, sseEvent);
            }
        }
        log.warn("SSE service not available for broadcasting or no current scope");
        return 0;
    }

    /**
     * Gets all active SSE connections.
     *
     * @return List of active SSE connections
     */
    public List<SSEConnection> getSSEConnections() {
        if (sseService != null) {
            return (List<SSEConnection>) sseService.getAllConnections();
        }
        log.warn("SSE service not available");
        return List.of();
    }

    /**
     * Gets the number of active SSE connections in this application scope.
     *
     * @return number of SSE connections
     */
    public long getSSEConnectionCount() {
        if (sseService != null) {
            IConnection conn = Red5.getConnectionLocal();
            IScope currentScope = conn != null ? conn.getScope() : null;
            if (currentScope != null) {
                return sseService.getConnectionsInScope(currentScope);
            }
        }
        return 0;
    }

    /**
     * Sends a message to a specific SSE connection.
     *
     * @param connectionId The connection ID
     * @param message The message to send
     * @return true if sent successfully
     */
    public boolean sendSSEToConnection(String connectionId, String message) {
        if (sseService != null) {
            return sseService.sendToConnection(connectionId, message);
        }
        log.warn("SSE service not available for sending to connection");
        return false;
    }

    /**
     * Sends an event to a specific SSE connection.
     *
     * @param connectionId The connection ID
     * @param event The event type
     * @param message The message to send
     * @return true if sent successfully
     */
    public boolean sendSSEEventToConnection(String connectionId, String event, String message) {
        if (sseService != null) {
            return sseService.sendEventToConnection(connectionId, event, message);
        }
        log.warn("SSE service not available for sending to connection");
        return false;
    }

    /**
     * Gets the SSE service instance.
     *
     * @return the SSE service, or null if not available
     */
    public ISSEService getSseService() {
        return sseService;
    }

    /**
     * Sets the SSE service instance.
     *
     * @param sseService the SSE service to set
     */
    public void setSseService(ISSEService sseService) {
        this.sseService = sseService;
    }
}