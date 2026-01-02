/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.net.websocket.flv;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.red5.net.websocket.WebSocketConnection;
import org.red5.net.websocket.WebSocketScope;
import org.red5.net.websocket.listener.IWebSocketDataListener;
import org.red5.net.websocket.model.WSMessage;
import org.red5.server.api.scope.IBroadcastScope;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.api.stream.IClientBroadcastStream;
import org.red5.server.net.httpflv.HTTPFLVService;
import org.red5.server.net.httpflv.StreamConfiguration;
import org.red5.server.stream.IProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WebSocket data listener for FLV streaming.
 * Handles WebSocket connections requesting FLV stream data.
 *
 * This listener responds to WebSocket connections with the "flv" protocol
 * and streams live FLV data to connected clients. Compatible with flv.js
 * WebSocket mode.
 *
 * URL format: ws://server:port/app/stream.flv
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class WebSocketFLVDataListener implements IWebSocketDataListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketFLVDataListener.class);

    /** Protocol identifier for FLV WebSocket connections */
    public static final String FLV_PROTOCOL = "flv";

    /** Bean name for Spring configuration */
    public static final String BEAN_NAME = "websocket.flv.listener";

    /** Protocol this listener handles */
    private String protocol = FLV_PROTOCOL;

    /** Active FLV connections by WebSocket session ID */
    private final Map<String, WebSocketFLVConnection> connections = new ConcurrentHashMap<>();

    /** Reference to HTTP-FLV service for shared configuration */
    private HTTPFLVService httpFlvService;

    /** WebSocket scope reference */
    private WebSocketScope webSocketScope;

    /** Total connections managed */
    private final AtomicLong totalConnections = new AtomicLong(0);

    /** Whether the listener is enabled */
    private boolean enabled = true;

    @Override
    public String getProtocol() {
        return protocol;
    }

    @Override
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    @Override
    public void onWSConnect(WebSocketConnection conn) {
        if (!enabled) {
            log.debug("WebSocket-FLV listener is disabled, ignoring connection");
            return;
        }
        log.debug("WebSocket-FLV connection request from: {}", conn.getSessionId());
        // Parse stream name from path
        String path = conn.getPath();
        String streamName = parseStreamName(path);
        if (streamName == null || streamName.isEmpty()) {
            log.warn("No stream name found in WebSocket path: {}", path);
            conn.close();
            return;
        }
        log.debug("Requested stream: {} from path: {}", streamName, path);
        // Get the scope
        IScope scope = getScope(conn);
        if (scope == null) {
            log.warn("No scope available for WebSocket-FLV connection");
            conn.close();
            return;
        }
        // Find the broadcast stream
        IBroadcastStream broadcastStream = getBroadcastStream(scope, streamName);
        if (broadcastStream == null) {
            log.debug("Stream not found: {} in scope: {}", streamName, scope.getName());
            conn.close();
            return;
        }
        // Check service status if available
        if (httpFlvService != null && !httpFlvService.isEnabled()) {
            log.debug("HTTP-FLV service is disabled");
            conn.close();
            return;
        }
        // Create WebSocket-FLV connection
        WebSocketFLVConnection flvConnection = new WebSocketFLVConnection(conn, scope, streamName);
        // Initialize connection (sends FLV header)
        if (!flvConnection.initialize()) {
            log.warn("Failed to initialize WebSocket-FLV connection: {}", conn.getSessionId());
            conn.close();
            return;
        }
        // Send initial stream configuration (metadata, codec config, GOP cache)
        if (httpFlvService != null) {
            StreamConfiguration streamConfig = httpFlvService.getStreamConfiguration(streamName);
            streamConfig.updateFromStream(broadcastStream);
            flvConnection.sendInitialData(streamConfig);
        }
        // Store connection
        connections.put(conn.getSessionId(), flvConnection);
        totalConnections.incrementAndGet();
        // Subscribe to broadcast stream
        flvConnection.subscribe(broadcastStream);
        log.info("Established WebSocket-FLV connection: {} for stream: {} in scope: {}", conn.getSessionId(), streamName, scope.getName());
    }

    @Override
    public void onWSDisconnect(WebSocketConnection conn) {
        String sessionId = conn.getSessionId();
        WebSocketFLVConnection flvConnection = connections.remove(sessionId);
        if (flvConnection != null) {
            flvConnection.close();
            log.debug("WebSocket-FLV connection disconnected: {}", sessionId);
        }
    }

    @Override
    public void onWSMessage(WSMessage message) {
        // FLV streaming is server-to-client only, ignore incoming messages
        log.trace("Ignoring incoming WebSocket message for FLV connection");
    }

    @Override
    public void stop() {
        log.info("Stopping WebSocket-FLV listener");
        // Close all active connections
        connections.values().forEach(WebSocketFLVConnection::close);
        connections.clear();
    }

    /**
     * Parses the stream name from the WebSocket path.
     * Expected format: /app/stream.flv or /stream.flv
     *
     * @param path the WebSocket path
     * @return the stream name without .flv extension, or null if not found
     */
    private String parseStreamName(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        // Remove leading slash
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        // Handle paths like "live/stream.flv" or just "stream.flv"
        // We want to extract just the stream name part
        String streamName = path;
        // If path contains slashes, take the last part
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            streamName = path.substring(lastSlash + 1);
        }
        // Remove .flv extension if present
        if (streamName.toLowerCase().endsWith(".flv")) {
            streamName = streamName.substring(0, streamName.length() - 4);
        }
        return streamName.isEmpty() ? null : streamName;
    }

    /**
     * Gets the scope for the WebSocket connection.
     *
     * @param conn the WebSocket connection
     * @return the scope, or null if not available
     */
    private IScope getScope(WebSocketConnection conn) {
        // Try to get scope from WebSocketScope
        if (webSocketScope != null) {
            return webSocketScope.getScope();
        }
        // Try to get from connection's WebSocketScope
        WebSocketScope wsScope = conn.getScope();
        if (wsScope != null) {
            return wsScope.getScope();
        }
        return null;
    }

    /**
     * Gets the broadcast stream for the given scope and stream name.
     *
     * @param scope the scope to search in
     * @param streamName the stream name
     * @return the broadcast stream, or null if not found
     */
    private IBroadcastStream getBroadcastStream(IScope scope, String streamName) {
        // First try to get the broadcast scope directly
        IBroadcastScope broadcastScope = scope.getBroadcastScope(streamName);
        if (broadcastScope != null) {
            IClientBroadcastStream clientStream = broadcastScope.getClientBroadcastStream();
            if (clientStream != null) {
                log.debug("Found broadcast stream: {} via broadcast scope", streamName);
                return clientStream;
            }
        }
        // Try via provider service to check if stream exists
        IProviderService providerService = (IProviderService) scope.getContext().getBean(IProviderService.BEAN_NAME);
        if (providerService != null) {
            IProviderService.INPUT_TYPE inputType = providerService.lookupProviderInput(scope, streamName, 0);
            if (inputType == IProviderService.INPUT_TYPE.LIVE) {
                // Re-check broadcast scope after confirming stream is live
                broadcastScope = scope.getBroadcastScope(streamName);
                if (broadcastScope != null) {
                    IClientBroadcastStream clientStream = broadcastScope.getClientBroadcastStream();
                    if (clientStream != null) {
                        log.debug("Found broadcast stream: {} via provider service lookup", streamName);
                        return clientStream;
                    }
                }
            } else if (inputType == IProviderService.INPUT_TYPE.LIVE_WAIT) {
                log.debug("Stream {} not yet publishing, input type: {}", streamName, inputType);
            } else {
                log.debug("Stream {} not found, input type: {}", streamName, inputType);
            }
        }
        return null;
    }

    /**
     * Gets a connection by WebSocket session ID.
     *
     * @param sessionId the WebSocket session ID
     * @return the FLV connection, or null if not found
     */
    public WebSocketFLVConnection getConnection(String sessionId) {
        return connections.get(sessionId);
    }

    /**
     * Gets the count of active connections.
     *
     * @return active connection count
     */
    public int getConnectionCount() {
        return connections.size();
    }

    /**
     * Gets the total connections managed since start.
     *
     * @return total connections
     */
    public long getTotalConnections() {
        return totalConnections.get();
    }

    /**
     * Checks if the listener is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether the listener is enabled.
     *
     * @param enabled true to enable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Gets the HTTP-FLV service reference.
     *
     * @return HTTP-FLV service
     */
    public HTTPFLVService getHttpFlvService() {
        return httpFlvService;
    }

    /**
     * Sets the HTTP-FLV service reference for shared configuration.
     *
     * @param httpFlvService the HTTP-FLV service
     */
    public void setHttpFlvService(HTTPFLVService httpFlvService) {
        this.httpFlvService = httpFlvService;
    }

    /**
     * Gets the WebSocket scope reference.
     *
     * @return WebSocket scope
     */
    public WebSocketScope getWebSocketScope() {
        return webSocketScope;
    }

    /**
     * Sets the WebSocket scope reference.
     *
     * @param webSocketScope the WebSocket scope
     */
    public void setWebSocketScope(WebSocketScope webSocketScope) {
        this.webSocketScope = webSocketScope;
    }

}
