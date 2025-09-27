/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.sse;

import java.util.Collection;

import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.IScopeService;

/**
 * Interface for Server-Sent Events service operations.
 * This interface provides methods for managing SSE connections and broadcasting events.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public interface ISSEService extends IScopeService {

    /** Constant <code>BEAN_NAME="sseService"</code> */
    public static String BEAN_NAME = "sseService";

    /**
     * Broadcasts a message to all connected SSE clients.
     *
     * @param message The message to broadcast
     * @return The number of successful sends
     */
    int broadcastMessage(String message);

    /**
     * Broadcasts an event to all connected SSE clients.
     *
     * @param event The event type
     * @param message The message to broadcast
     * @return The number of successful sends
     */
    int broadcastEvent(String event, String message);

    /**
     * Broadcasts an SSE event to all connected clients.
     *
     * @param sseEvent The SSE event to broadcast
     * @return The number of successful sends
     */
    int broadcastEvent(SSEEvent sseEvent);

    /**
     * Broadcasts a message to all clients in a specific scope.
     *
     * @param scope The scope to broadcast to
     * @param message The message to broadcast
     * @return The number of successful sends
     */
    int broadcastToScope(IScope scope, String message);

    /**
     * Broadcasts an event to all clients in a specific scope.
     *
     * @param scope The scope to broadcast to
     * @param event The event type
     * @param message The message to broadcast
     * @return The number of successful sends
     */
    int broadcastEventToScope(IScope scope, String event, String message);

    /**
     * Broadcasts an SSE event to all clients in a specific scope.
     *
     * @param scope The scope to broadcast to
     * @param sseEvent The SSE event to broadcast
     * @return The number of successful sends
     */
    int broadcastEventToScope(IScope scope, SSEEvent sseEvent);

    /**
     * Sends a message to a specific connection.
     *
     * @param connectionId The connection ID
     * @param message The message to send
     * @return true if the message was sent successfully
     */
    boolean sendToConnection(String connectionId, String message);

    /**
     * Sends an event to a specific connection.
     *
     * @param connectionId The connection ID
     * @param event The event type
     * @param message The message to send
     * @return true if the event was sent successfully
     */
    boolean sendEventToConnection(String connectionId, String event, String message);

    /**
     * Sends an SSE event to a specific connection.
     *
     * @param connectionId The connection ID
     * @param sseEvent The SSE event to send
     * @return true if the event was sent successfully
     */
    boolean sendEventToConnection(String connectionId, SSEEvent sseEvent);

    /**
     * Gets all active SSE connections.
     *
     * @return Collection of all active connections
     */
    Collection<SSEConnection> getAllConnections();

    /**
     * Gets the number of active connections.
     *
     * @return number of active connections
     */
    int getConnectionCount();

    /**
     * Gets the number of connections in a specific scope.
     *
     * @param scope The scope to count connections for
     * @return The number of connections in the scope
     */
    long getConnectionsInScope(IScope scope);

    /**
     * Gets a specific connection by ID.
     *
     * @param connectionId The connection ID
     * @return The connection, or null if not found
     */
    SSEConnection getConnection(String connectionId);

    /**
     * Closes a specific connection.
     *
     * @param connectionId The connection ID to close
     * @return true if the connection was found and closed
     */
    boolean closeConnection(String connectionId);
}