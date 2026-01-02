/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.httpflv;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IBroadcastStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for managing HTTP-FLV streaming connections and stream configurations.
 * This service provides centralized management of:
 * - Stream configurations (codec config, metadata, GOP cache)
 * - Active connections per stream
 * - Connection statistics and monitoring
 *
 * Can be configured as a Spring bean for application-wide HTTP-FLV support.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class HTTPFLVService {

    private static final Logger log = LoggerFactory.getLogger(HTTPFLVService.class);

    /** Bean name for Spring configuration */
    public static final String BEAN_NAME = "httpflv.service";

    /** Stream configurations by stream name */
    private final Map<String, StreamConfiguration> streamConfigs = new ConcurrentHashMap<>();

    /** Active connections by connection ID */
    private final Map<String, HTTPFLVConnection> connections = new ConcurrentHashMap<>();

    /** Connections grouped by stream name */
    private final Map<String, Set<String>> connectionsByStream = new ConcurrentHashMap<>();

    /** Total connections created */
    private final AtomicLong totalConnectionsCreated = new AtomicLong();

    /** Total bytes sent */
    private final AtomicLong totalBytesSent = new AtomicLong();

    /** Whether the service is enabled */
    private boolean enabled = true;

    /** Whether GOP caching is enabled */
    private boolean gopCacheEnabled = true;

    /** Maximum GOP frames to cache per stream */
    private int maxGopFrames = 300;

    /** Maximum connections per stream (0 = unlimited) */
    private int maxConnectionsPerStream = 0;

    /**
     * Gets or creates a stream configuration for the given stream name.
     *
     * @param streamName the stream name
     * @return the stream configuration
     */
    public StreamConfiguration getStreamConfiguration(String streamName) {
        return streamConfigs.computeIfAbsent(streamName, name -> {
            StreamConfiguration config = new StreamConfiguration(name);
            config.setMaxGopFrames(maxGopFrames);
            log.debug("Created stream configuration for: {}", name);
            return config;
        });
    }

    /**
     * Updates stream configuration from a broadcast stream.
     *
     * @param stream the broadcast stream
     */
    public void updateStreamConfiguration(IBroadcastStream stream) {
        if (stream == null) {
            return;
        }
        String streamName = stream.getPublishedName();
        StreamConfiguration config = getStreamConfiguration(streamName);
        config.updateFromStream(stream);
    }

    /**
     * Removes stream configuration when stream ends.
     *
     * @param streamName the stream name
     */
    public void removeStreamConfiguration(String streamName) {
        StreamConfiguration config = streamConfigs.remove(streamName);
        if (config != null) {
            config.reset();
            log.debug("Removed stream configuration for: {}", streamName);
        }
    }

    /**
     * Registers a new connection.
     *
     * @param connection the connection to register
     * @return true if registered successfully
     */
    public boolean registerConnection(HTTPFLVConnection connection) {
        if (!enabled) {
            log.debug("HTTP-FLV service is disabled");
            return false;
        }
        String streamName = connection.getStreamName();
        // Check connection limit
        if (maxConnectionsPerStream > 0) {
            Set<String> streamConns = connectionsByStream.get(streamName);
            if (streamConns != null && streamConns.size() >= maxConnectionsPerStream) {
                log.warn("Max connections ({}) reached for stream: {}", maxConnectionsPerStream, streamName);
                return false;
            }
        }
        // Register connection
        connections.put(connection.getConnectionId(), connection);
        connectionsByStream.computeIfAbsent(streamName, k -> ConcurrentHashMap.newKeySet()).add(connection.getConnectionId());
        totalConnectionsCreated.incrementAndGet();
        log.debug("Registered HTTP-FLV connection: {} for stream: {}", connection.getConnectionId(), streamName);
        return true;
    }

    /**
     * Unregisters a connection.
     *
     * @param connectionId the connection ID
     * @return the removed connection, or null if not found
     */
    public HTTPFLVConnection unregisterConnection(String connectionId) {
        HTTPFLVConnection connection = connections.remove(connectionId);
        if (connection != null) {
            String streamName = connection.getStreamName();
            Set<String> streamConns = connectionsByStream.get(streamName);
            if (streamConns != null) {
                streamConns.remove(connectionId);
                if (streamConns.isEmpty()) {
                    connectionsByStream.remove(streamName);
                }
            }
            // Update statistics
            totalBytesSent.addAndGet(connection.getBytesSent());
            log.debug("Unregistered HTTP-FLV connection: {}", connectionId);
        }
        return connection;
    }

    /**
     * Gets a connection by ID.
     *
     * @param connectionId the connection ID
     * @return the connection, or null if not found
     */
    public HTTPFLVConnection getConnection(String connectionId) {
        return connections.get(connectionId);
    }

    /**
     * Gets all connections for a stream.
     *
     * @param streamName the stream name
     * @return collection of connections
     */
    public Collection<HTTPFLVConnection> getConnectionsForStream(String streamName) {
        Set<String> connIds = connectionsByStream.get(streamName);
        if (connIds == null || connIds.isEmpty()) {
            return Set.of();
        }
        return connIds.stream().map(connections::get).filter(c -> c != null).collect(Collectors.toList());
    }

    /**
     * Gets the count of active connections for a stream.
     *
     * @param streamName the stream name
     * @return connection count
     */
    public int getConnectionCountForStream(String streamName) {
        Set<String> connIds = connectionsByStream.get(streamName);
        return connIds != null ? connIds.size() : 0;
    }

    /**
     * Gets the total count of active connections.
     *
     * @return total connection count
     */
    public int getTotalConnectionCount() {
        return connections.size();
    }

    /**
     * Gets the total number of connections created since service start.
     *
     * @return total connections created
     */
    public long getTotalConnectionsCreated() {
        return totalConnectionsCreated.get();
    }

    /**
     * Gets the total bytes sent since service start.
     *
     * @return total bytes sent
     */
    public long getTotalBytesSent() {
        return totalBytesSent.get();
    }

    /**
     * Closes all connections for a stream.
     *
     * @param streamName the stream name
     */
    public void closeConnectionsForStream(String streamName) {
        Collection<HTTPFLVConnection> conns = getConnectionsForStream(streamName);
        for (HTTPFLVConnection conn : conns) {
            conn.close();
            unregisterConnection(conn.getConnectionId());
        }
        log.debug("Closed {} connections for stream: {}", conns.size(), streamName);
    }

    /**
     * Closes all connections.
     */
    public void closeAllConnections() {
        connections.values().forEach(HTTPFLVConnection::close);
        connections.clear();
        connectionsByStream.clear();
        log.info("Closed all HTTP-FLV connections");
    }

    /**
     * Called when a stream starts publishing.
     *
     * @param scope the scope
     * @param stream the broadcast stream
     */
    public void onStreamPublishStart(IScope scope, IBroadcastStream stream) {
        if (!enabled) {
            return;
        }
        String streamName = stream.getPublishedName();
        StreamConfiguration config = getStreamConfiguration(streamName);
        config.updateFromStream(stream);
        log.debug("Stream publish started: {} in scope: {}", streamName, scope.getName());
    }

    /**
     * Called when a stream stops publishing.
     *
     * @param scope the scope
     * @param stream the broadcast stream
     */
    public void onStreamPublishStop(IScope scope, IBroadcastStream stream) {
        String streamName = stream.getPublishedName();
        // Close all connections watching this stream
        closeConnectionsForStream(streamName);
        // Remove stream configuration
        removeStreamConfiguration(streamName);
        log.debug("Stream publish stopped: {} in scope: {}", streamName, scope.getName());
    }

    /**
     * Gets statistics for the service.
     *
     * @return map of statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("enabled", enabled);
        stats.put("gopCacheEnabled", gopCacheEnabled);
        stats.put("totalConnections", getTotalConnectionCount());
        stats.put("totalConnectionsCreated", getTotalConnectionsCreated());
        stats.put("totalBytesSent", getTotalBytesSent());
        stats.put("activeStreams", connectionsByStream.size());
        stats.put("streamConfigurations", streamConfigs.size());
        return stats;
    }

    // Getters and setters for configuration

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isGopCacheEnabled() {
        return gopCacheEnabled;
    }

    public void setGopCacheEnabled(boolean gopCacheEnabled) {
        this.gopCacheEnabled = gopCacheEnabled;
    }

    public int getMaxGopFrames() {
        return maxGopFrames;
    }

    public void setMaxGopFrames(int maxGopFrames) {
        this.maxGopFrames = maxGopFrames;
    }

    public int getMaxConnectionsPerStream() {
        return maxConnectionsPerStream;
    }

    public void setMaxConnectionsPerStream(int maxConnectionsPerStream) {
        this.maxConnectionsPerStream = maxConnectionsPerStream;
    }

}
