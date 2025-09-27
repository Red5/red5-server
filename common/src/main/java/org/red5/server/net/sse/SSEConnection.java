/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.sse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.red5.server.api.scope.IScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Represents a Server-Sent Events connection to a client.
 * This class manages the lifecycle of an SSE connection including sending events,
 * handling connection state, and proper cleanup.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class SSEConnection {

    private static Logger log = LoggerFactory.getLogger(SSEConnection.class);

    private final String connectionId;

    private final AsyncContext asyncContext;

    private final HttpServletResponse response;

    private final IScope scope;

    private final AtomicBoolean connected = new AtomicBoolean(true);

    private final AtomicLong lastEventId = new AtomicLong(0);

    private volatile long lastActivity;

    /**
     * Creates a new SSE connection.
     *
     * @param connectionId Unique identifier for this connection
     * @param asyncContext The async servlet context
     * @param response The HTTP response
     * @param scope The Red5 scope this connection belongs to
     */
    public SSEConnection(String connectionId, AsyncContext asyncContext, HttpServletResponse response, IScope scope) {
        this.connectionId = connectionId;
        this.asyncContext = asyncContext;
        this.response = response;
        this.scope = scope;
        this.lastActivity = System.currentTimeMillis();
        // Set up SSE headers
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Headers", "Cache-Control");
        log.debug("Created SSE connection: {} for scope: {}", connectionId, scope.getName());
    }

    /**
     * Sends a simple message event.
     *
     * @param message The message to send
     * @return true if the message was sent successfully
     */
    public boolean sendMessage(String message) {
        return sendEvent(null, null, message, null);
    }

    /**
     * Sends a message event with a specific event type.
     *
     * @param event The event type
     * @param message The message to send
     * @return true if the message was sent successfully
     */
    public boolean sendEvent(String event, String message) {
        return sendEvent(null, event, message, null);
    }

    /**
     * Sends a complete SSE event with all optional fields.
     *
     * @param id The event ID (optional)
     * @param event The event type (optional)
     * @param data The event data
     * @param retry The retry timeout in milliseconds (optional)
     * @return true if the event was sent successfully
     */
    public boolean sendEvent(String id, String event, String data, Integer retry) {
        if (isConnected()) {
            try {
                StringBuilder eventBuilder = new StringBuilder();
                if (id != null) {
                    eventBuilder.append("id: ").append(id).append("\n");
                } else {
                    // Auto-generate ID if not provided
                    eventBuilder.append("id: ").append(lastEventId.incrementAndGet()).append("\n");
                }
                if (event != null) {
                    eventBuilder.append("event: ").append(event).append("\n");
                }
                if (retry != null) {
                    eventBuilder.append("retry: ").append(retry).append("\n");
                }
                if (data != null) {
                    // Handle multi-line data
                    String[] lines = data.split("\n");
                    for (String line : lines) {
                        eventBuilder.append("data: ").append(line).append("\n");
                    }
                }
                eventBuilder.append("\n"); // End of event
                byte[] eventBytes = eventBuilder.toString().getBytes(StandardCharsets.UTF_8);
                OutputStream outputStream = response.getOutputStream();
                if (outputStream != null) {
                    outputStream.write(eventBytes);
                    outputStream.flush();
                    lastActivity = System.currentTimeMillis();
                    log.trace("Sent SSE event to connection {}: {}", connectionId, eventBuilder.toString().trim());
                    return true;
                } else {
                    log.debug("Output stream is null for connection {}", connectionId);
                }
            } catch (IOException e) {
                log.debug("Failed to send SSE event to connection {}: {}", connectionId, e.getMessage());
                close();
            }
        } else {
            log.debug("Connection {} is not connected, cannot send event", connectionId);
        }
        return false;
    }

    /**
     * Sends a keep-alive comment to maintain the connection.
     *
     * @return true if the keep-alive was sent successfully
     */
    public boolean sendKeepAlive() {
        if (isConnected()) {
            try {
                OutputStream outputStream = response.getOutputStream();
                if (outputStream != null) {
                    outputStream.write(": keep-alive\n\n".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                    lastActivity = System.currentTimeMillis();
                    log.trace("Sent keep-alive to connection {}", connectionId);
                    return true;
                } else {
                    log.debug("Output stream is null for connection {}", connectionId);
                }
            } catch (IOException e) {
                log.debug("Failed to send keep-alive to connection {}: {}", connectionId, e.getMessage());
                close();
            }
        }
        return false;
    }

    /**
     * Closes the SSE connection.
     */
    public void close() {
        if (connected.compareAndSet(true, false)) {
            log.debug("Closing SSE connection: {}", connectionId);
            try {
                if (asyncContext != null) {
                    asyncContext.complete();
                }
            } catch (Exception e) {
                log.debug("Error completing async context for connection {}: {}", connectionId, e.getMessage());
            }
        }
    }

    /**
     * Checks if the connection is still active.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * Gets the connection ID.
     *
     * @return connection ID
     */
    public String getConnectionId() {
        return connectionId;
    }

    /**
     * Gets the scope this connection belongs to.
     *
     * @return the scope
     */
    public IScope getScope() {
        return scope;
    }

    /**
     * Gets the last activity timestamp.
     *
     * @return last activity time in milliseconds
     */
    public long getLastActivity() {
        return lastActivity;
    }

    /**
     * Gets the current event ID counter.
     *
     * @return current event ID
     */
    public long getCurrentEventId() {
        return lastEventId.get();
    }

    @Override
    public String toString() {
        return "SSEConnection{" + "connectionId='" + connectionId + '\'' + ", scope=" + (scope != null ? scope.getName() : "null") + ", connected=" + connected.get() + ", lastEventId=" + lastEventId.get() + ", lastActivity=" + lastActivity + '}';
    }
}