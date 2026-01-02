/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.httpflv;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * Manager for HTTP-FLV connection lifecycle, keep-alive, and monitoring.
 * This class provides centralized management of HTTP-FLV connections including:
 * - Connection timeout detection and cleanup
 * - Keep-alive mechanism for long-lived connections
 * - Statistics collection and monitoring
 * - Graceful shutdown handling
 *
 * Can be configured as a Spring bean for application-wide management.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class HTTPFLVManager implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(HTTPFLVManager.class);

    /** Bean name for Spring configuration */
    public static final String BEAN_NAME = "httpflv.manager";

    /** Managed connections by connection ID */
    private final Map<String, HTTPFLVConnection> connections = new ConcurrentHashMap<>();

    /** Scheduler for keep-alive and timeout tasks */
    private ScheduledExecutorService scheduler;

    /** Keep-alive task future */
    private ScheduledFuture<?> keepAliveTask;

    /** Timeout check task future */
    private ScheduledFuture<?> timeoutCheckTask;

    /** Whether the manager is running */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Total connections managed */
    private final AtomicLong totalConnectionsManaged = new AtomicLong();

    /** Total connections timed out */
    private final AtomicLong totalConnectionsTimedOut = new AtomicLong();

    /** Connection timeout in milliseconds (default 30 seconds) */
    private long connectionTimeout = 30000;

    /** Keep-alive interval in milliseconds (default 15 seconds) */
    private long keepAliveInterval = 15000;

    /** Timeout check interval in milliseconds (default 5 seconds) */
    private long timeoutCheckInterval = 5000;

    /** Whether keep-alive is enabled */
    private boolean keepAliveEnabled = true;

    /** Reference to HTTPFLVService for coordination */
    private HTTPFLVService httpFlvService;

    /**
     * Initializes the manager after properties are set.
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        start();
    }

    /**
     * Cleans up resources when the bean is destroyed.
     */
    @Override
    public void destroy() throws Exception {
        stop();
    }

    /**
     * Starts the manager and scheduled tasks.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting HTTP-FLV Manager");
            scheduler = Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "HTTPFLVManager-Worker");
                t.setDaemon(true);
                return t;
            });
            // Start timeout check task
            timeoutCheckTask = scheduler.scheduleAtFixedRate(this::checkTimeouts, timeoutCheckInterval, timeoutCheckInterval, TimeUnit.MILLISECONDS);
            log.debug("Timeout check task started with interval: {}ms", timeoutCheckInterval);
            // Start keep-alive task if enabled
            if (keepAliveEnabled) {
                keepAliveTask = scheduler.scheduleAtFixedRate(this::sendKeepAlives, keepAliveInterval, keepAliveInterval, TimeUnit.MILLISECONDS);
                log.debug("Keep-alive task started with interval: {}ms", keepAliveInterval);
            }
            log.info("HTTP-FLV Manager started (timeout: {}ms, keepAlive: {}ms)", connectionTimeout, keepAliveInterval);
        }
    }

    /**
     * Stops the manager and all scheduled tasks.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping HTTP-FLV Manager");
            // Cancel scheduled tasks
            if (keepAliveTask != null) {
                keepAliveTask.cancel(false);
                keepAliveTask = null;
            }
            if (timeoutCheckTask != null) {
                timeoutCheckTask.cancel(false);
                timeoutCheckTask = null;
            }
            // Shutdown scheduler
            if (scheduler != null) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                scheduler = null;
            }
            // Close all connections
            closeAllConnections();
            log.info("HTTP-FLV Manager stopped");
        }
    }

    /**
     * Registers a connection with the manager.
     *
     * @param connection the connection to register
     */
    public void registerConnection(HTTPFLVConnection connection) {
        if (connection == null || !running.get()) {
            return;
        }
        connections.put(connection.getConnectionId(), connection);
        totalConnectionsManaged.incrementAndGet();
        log.debug("Registered connection with manager: {}", connection.getConnectionId());
    }

    /**
     * Unregisters a connection from the manager.
     *
     * @param connectionId the connection ID
     * @return the removed connection, or null if not found
     */
    public HTTPFLVConnection unregisterConnection(String connectionId) {
        HTTPFLVConnection connection = connections.remove(connectionId);
        if (connection != null) {
            log.debug("Unregistered connection from manager: {}", connectionId);
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
     * Checks for timed out connections and closes them.
     */
    private void checkTimeouts() {
        if (!running.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<String> timedOut = new ArrayList<>();
        for (Map.Entry<String, HTTPFLVConnection> entry : connections.entrySet()) {
            HTTPFLVConnection connection = entry.getValue();
            if (!connection.isConnected()) {
                timedOut.add(entry.getKey());
                continue;
            }
            long lastActivity = connection.getLastActivity();
            if (now - lastActivity > connectionTimeout) {
                log.debug("Connection timed out: {} (last activity: {}ms ago)", entry.getKey(), now - lastActivity);
                timedOut.add(entry.getKey());
                totalConnectionsTimedOut.incrementAndGet();
            }
        }
        // Close timed out connections
        for (String connectionId : timedOut) {
            HTTPFLVConnection connection = connections.remove(connectionId);
            if (connection != null) {
                connection.close();
                // Also unregister from service if available
                if (httpFlvService != null) {
                    httpFlvService.unregisterConnection(connectionId);
                }
            }
        }
        if (!timedOut.isEmpty()) {
            log.debug("Closed {} timed out connections", timedOut.size());
        }
    }

    /**
     * Sends keep-alive to all connections.
     * For HTTP-FLV, we don't actually send data (no SSE comments),
     * but we check if connections are still alive.
     */
    private void sendKeepAlives() {
        if (!running.get()) {
            return;
        }
        List<String> dead = new ArrayList<>();
        for (Map.Entry<String, HTTPFLVConnection> entry : connections.entrySet()) {
            HTTPFLVConnection connection = entry.getValue();
            if (!connection.isConnected()) {
                dead.add(entry.getKey());
            }
        }
        // Clean up dead connections
        for (String connectionId : dead) {
            HTTPFLVConnection connection = connections.remove(connectionId);
            if (connection != null) {
                connection.close();
                if (httpFlvService != null) {
                    httpFlvService.unregisterConnection(connectionId);
                }
            }
        }
        if (!dead.isEmpty()) {
            log.debug("Cleaned up {} dead connections during keep-alive check", dead.size());
        }
    }

    /**
     * Closes all managed connections.
     */
    public void closeAllConnections() {
        int count = connections.size();
        connections.values().forEach(HTTPFLVConnection::close);
        connections.clear();
        if (count > 0) {
            log.debug("Closed {} connections", count);
        }
    }

    /**
     * Gets the count of active connections.
     *
     * @return active connection count
     */
    public int getActiveConnectionCount() {
        return connections.size();
    }

    /**
     * Gets the total connections managed since start.
     *
     * @return total connections managed
     */
    public long getTotalConnectionsManaged() {
        return totalConnectionsManaged.get();
    }

    /**
     * Gets the total connections that timed out.
     *
     * @return total timed out connections
     */
    public long getTotalConnectionsTimedOut() {
        return totalConnectionsTimedOut.get();
    }

    /**
     * Checks if the manager is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Gets statistics about the manager.
     *
     * @return map of statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("running", running.get());
        stats.put("activeConnections", getActiveConnectionCount());
        stats.put("totalConnectionsManaged", getTotalConnectionsManaged());
        stats.put("totalConnectionsTimedOut", getTotalConnectionsTimedOut());
        stats.put("connectionTimeout", connectionTimeout);
        stats.put("keepAliveInterval", keepAliveInterval);
        stats.put("keepAliveEnabled", keepAliveEnabled);
        return stats;
    }

    // Getters and setters for configuration

    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public long getKeepAliveInterval() {
        return keepAliveInterval;
    }

    public void setKeepAliveInterval(long keepAliveInterval) {
        this.keepAliveInterval = keepAliveInterval;
    }

    public long getTimeoutCheckInterval() {
        return timeoutCheckInterval;
    }

    public void setTimeoutCheckInterval(long timeoutCheckInterval) {
        this.timeoutCheckInterval = timeoutCheckInterval;
    }

    public boolean isKeepAliveEnabled() {
        return keepAliveEnabled;
    }

    public void setKeepAliveEnabled(boolean keepAliveEnabled) {
        this.keepAliveEnabled = keepAliveEnabled;
    }

    public HTTPFLVService getHttpFlvService() {
        return httpFlvService;
    }

    public void setHttpFlvService(HTTPFLVService httpFlvService) {
        this.httpFlvService = httpFlvService;
    }

}
