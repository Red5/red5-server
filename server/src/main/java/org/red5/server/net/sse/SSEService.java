/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.sse;

import java.util.Collection;

import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.scope.IScope;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Implementation of the SSE service interface.
 * This service provides a high-level API for working with Server-Sent Events
 * and delegates to the SSEManager for actual connection management.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class SSEService implements ISSEService {

    private static Logger log = Red5LoggerFactory.getLogger(SSEService.class);

    @Autowired
    private SSEManager sseManager;

    @Override
    public int broadcastMessage(String message) {
        log.debug("Broadcasting message to all connections: {}", message);
        return sseManager.broadcastMessage(message);
    }

    @Override
    public int broadcastEvent(String event, String message) {
        log.debug("Broadcasting event '{}' to all connections: {}", event, message);
        return sseManager.broadcastEvent(event, message);
    }

    @Override
    public int broadcastEvent(SSEEvent sseEvent) {
        log.debug("Broadcasting SSE event to all connections: {}", sseEvent);
        return sseManager.broadcastEvent(sseEvent.getEvent(), sseEvent.getData());
    }

    @Override
    public int broadcastToScope(IScope scope, String message) {
        log.debug("Broadcasting message to scope '{}': {}", scope != null ? scope.getName() : "null", message);
        return sseManager.broadcastToScope(scope, message);
    }

    @Override
    public int broadcastEventToScope(IScope scope, String event, String message) {
        log.debug("Broadcasting event '{}' to scope '{}': {}", event, scope != null ? scope.getName() : "null", message);
        return sseManager.broadcastEventToScope(scope, event, message);
    }

    @Override
    public int broadcastEventToScope(IScope scope, SSEEvent sseEvent) {
        log.debug("Broadcasting SSE event to scope '{}': {}", scope != null ? scope.getName() : "null", sseEvent);
        return sseManager.broadcastEventToScope(scope, sseEvent.getEvent(), sseEvent.getData());
    }

    @Override
    public boolean sendToConnection(String connectionId, String message) {
        log.debug("Sending message to connection '{}': {}", connectionId, message);
        return sseManager.sendToConnection(connectionId, message);
    }

    @Override
    public boolean sendEventToConnection(String connectionId, String event, String message) {
        log.debug("Sending event '{}' to connection '{}': {}", event, connectionId, message);
        return sseManager.sendEventToConnection(connectionId, event, message);
    }

    @Override
    public boolean sendEventToConnection(String connectionId, SSEEvent sseEvent) {
        log.debug("Sending SSE event to connection '{}': {}", connectionId, sseEvent);
        SSEConnection connection = sseManager.getConnection(connectionId);
        if (connection != null) {
            return connection.sendEvent(sseEvent.getId(), sseEvent.getEvent(), sseEvent.getData(), sseEvent.getRetry());
        }
        return false;
    }

    @Override
    public Collection<SSEConnection> getAllConnections() {
        return sseManager.getAllConnections();
    }

    @Override
    public int getConnectionCount() {
        return sseManager.getConnectionCount();
    }

    @Override
    public long getConnectionsInScope(IScope scope) {
        return sseManager.getConnectionsInScope(scope);
    }

    @Override
    public SSEConnection getConnection(String connectionId) {
        return sseManager.getConnection(connectionId);
    }

    @Override
    public boolean closeConnection(String connectionId) {
        log.debug("Closing connection: {}", connectionId);
        return sseManager.closeConnection(connectionId);
    }

    /**
     * Sets the SSE manager.
     *
     * @param sseManager the SSE manager to set
     */
    public void setSseManager(SSEManager sseManager) {
        this.sseManager = sseManager;
    }

    /**
     * Gets the SSE manager.
     *
     * @return the SSE manager
     */
    public SSEManager getSseManager() {
        return sseManager;
    }
}