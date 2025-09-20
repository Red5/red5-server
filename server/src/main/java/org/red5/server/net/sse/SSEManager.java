/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.sse;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.red5.server.api.scope.IScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * Manages Server-Sent Events connections and provides broadcasting capabilities.
 * This class handles connection lifecycle, cleanup of stale connections, and
 * broadcasting messages to groups of connections.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class SSEManager implements InitializingBean, DisposableBean {

    private static Logger log = LoggerFactory.getLogger(SSEManager.class);

    private final ConcurrentHashMap<String, SSEConnection> connections = new ConcurrentHashMap<>();

    private ScheduledExecutorService executorService;

    private long connectionTimeoutMs = 300000; // 5 minutes default

    private long keepAliveIntervalMs = 30000; // 30 seconds default

    private boolean keepAliveEnabled = true;

    public SSEManager() {
        log.debug("SSEManager instantiated");
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("Starting SSE Manager");
        executorService = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "SSE-Manager");
            t.setDaemon(true);
            return t;
        });
        // Schedule cleanup task
        executorService.scheduleWithFixedDelay(this::cleanupStaleConnections, 60, 60, TimeUnit.SECONDS);
        // Schedule keep-alive task if enabled
        if (keepAliveEnabled) {
            executorService.scheduleWithFixedDelay(this::sendKeepAlives, keepAliveIntervalMs, keepAliveIntervalMs, TimeUnit.MILLISECONDS);
        }
        log.info("SSE Manager started with {} connection timeout and {} keep-alive interval", connectionTimeoutMs, keepAliveIntervalMs);
    }

    @Override
    public void destroy() throws Exception {
        log.info("Stopping SSE Manager");
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        // Close all connections
        connections.values().forEach(SSEConnection::close);
        connections.clear();
        log.info("SSE Manager stopped");
    }

    /**
     * Adds a new SSE connection.
     *
     * @param connection The SSE connection to add
     */
    public void addConnection(SSEConnection connection) {
        if (connection != null && connection.isConnected()) {
            connections.put(connection.getConnectionId(), connection);
            log.debug("Added SSE connection: {} (total: {})", connection.getConnectionId(), connections.size());
        }
    }

    /**
     * Removes an SSE connection.
     *
     * @param connectionId The connection ID to remove
     * @return The removed connection, or null if not found
     */
    public SSEConnection removeConnection(String connectionId) {
        SSEConnection removed = connections.remove(connectionId);
        if (removed != null) {
            log.debug("Removed SSE connection: {} (total: {})", connectionId, connections.size());
        }
        return removed;
    }

    /**
     * Gets an SSE connection by ID.
     *
     * @param connectionId The connection ID
     * @return The connection, or null if not found
     */
    public SSEConnection getConnection(String connectionId) {
        return connections.get(connectionId);
    }

    /**
     * Gets all active connections.
     *
     * @return Collection of all active connections
     */
    public Collection<SSEConnection> getAllConnections() {
        return connections.values();
    }

    /**
     * Gets the number of active connections.
     *
     * @return number of active connections
     */
    public int getConnectionCount() {
        return connections.size();
    }

    /**
     * Broadcasts a message to all connections.
     *
     * @param message The message to broadcast
     * @return The number of successful sends
     */
    public int broadcastMessage(String message) {
        return broadcastEvent(null, message);
    }

    /**
     * Broadcasts an event to all connections.
     *
     * @param event The event type
     * @param message The message to broadcast
     * @return The number of successful sends
     */
    public int broadcastEvent(String event, String message) {
        int successCount = 0;
        for (SSEConnection connection : connections.values()) {
            if (connection.sendEvent(event, message)) {
                successCount++;
            }
        }
        log.debug("Broadcast {} event to {}/{} connections", event != null ? event : "message", successCount, connections.size());
        return successCount;
    }

    /**
     * Broadcasts a message to all connections in a specific scope.
     *
     * @param scope The scope to broadcast to
     * @param message The message to broadcast
     * @return The number of successful sends
     */
    public int broadcastToScope(IScope scope, String message) {
        return broadcastEventToScope(scope, null, message);
    }

    /**
     * Broadcasts an event to all connections in a specific scope.
     *
     * @param scope The scope to broadcast to
     * @param event The event type
     * @param message The message to broadcast
     * @return The number of successful sends
     */
    public int broadcastEventToScope(IScope scope, String event, String message) {
        if (scope == null) {
            return 0;
        }
        int successCount = 0;
        for (SSEConnection connection : connections.values()) {
            if (scope.equals(connection.getScope()) && connection.sendEvent(event, message)) {
                successCount++;
            }
        }
        log.debug("Broadcast {} event to scope '{}': {}/{} connections", event != null ? event : "message", scope.getName(), successCount, getConnectionsInScope(scope));
        return successCount;
    }

    /**
     * Gets the number of connections in a specific scope.
     *
     * @param scope The scope to count connections for
     * @return The number of connections in the scope
     */
    public long getConnectionsInScope(IScope scope) {
        if (scope == null) {
            return 0;
        }
        return connections.values().stream().filter(conn -> scope.equals(conn.getScope())).count();
    }

    /**
     * Sends a message to a specific connection.
     *
     * @param connectionId The connection ID
     * @param message The message to send
     * @return true if the message was sent successfully
     */
    public boolean sendToConnection(String connectionId, String message) {
        SSEConnection connection = connections.get(connectionId);
        return connection != null && connection.sendMessage(message);
    }

    /**
     * Sends an event to a specific connection.
     *
     * @param connectionId The connection ID
     * @param event The event type
     * @param message The message to send
     * @return true if the event was sent successfully
     */
    public boolean sendEventToConnection(String connectionId, String event, String message) {
        SSEConnection connection = connections.get(connectionId);
        return connection != null && connection.sendEvent(event, message);
    }

    /**
     * Closes a specific connection.
     *
     * @param connectionId The connection ID to close
     * @return true if the connection was found and closed
     */
    public boolean closeConnection(String connectionId) {
        SSEConnection connection = removeConnection(connectionId);
        if (connection != null) {
            connection.close();
            return true;
        }
        return false;
    }

    /**
     * Cleans up stale connections that have exceeded the timeout.
     */
    private void cleanupStaleConnections() {
        long now = System.currentTimeMillis();
        int removedCount = 0;
        for (SSEConnection connection : connections.values()) {
            if (!connection.isConnected() || (now - connection.getLastActivity()) > connectionTimeoutMs) {
                removeConnection(connection.getConnectionId());
                connection.close();
                removedCount++;
            }
        }
        if (removedCount > 0) {
            log.debug("Cleaned up {} stale SSE connections", removedCount);
        }
    }

    /**
     * Sends keep-alive messages to all active connections.
     */
    private void sendKeepAlives() {
        int sentCount = 0;
        for (SSEConnection connection : connections.values()) {
            if (connection.sendKeepAlive()) {
                sentCount++;
            }
        }
        if (sentCount > 0) {
            log.trace("Sent keep-alive to {} connections", sentCount);
        }
    }

    /**
     * Sets the connection timeout in milliseconds.
     *
     * @param connectionTimeoutMs timeout in milliseconds
     */
    public void setConnectionTimeoutMs(long connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    /**
     * Gets the connection timeout in milliseconds.
     *
     * @return timeout in milliseconds
     */
    public long getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    /**
     * Sets the keep-alive interval in milliseconds.
     *
     * @param keepAliveIntervalMs interval in milliseconds
     */
    public void setKeepAliveIntervalMs(long keepAliveIntervalMs) {
        this.keepAliveIntervalMs = keepAliveIntervalMs;
    }

    /**
     * Gets the keep-alive interval in milliseconds.
     *
     * @return interval in milliseconds
     */
    public long getKeepAliveIntervalMs() {
        return keepAliveIntervalMs;
    }

    /**
     * Enables or disables keep-alive messages.
     *
     * @param keepAliveEnabled true to enable keep-alive
     */
    public void setKeepAliveEnabled(boolean keepAliveEnabled) {
        this.keepAliveEnabled = keepAliveEnabled;
    }

    /**
     * Checks if keep-alive is enabled.
     *
     * @return true if keep-alive is enabled
     */
    public boolean isKeepAliveEnabled() {
        return keepAliveEnabled;
    }

}