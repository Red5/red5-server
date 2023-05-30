/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2013 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.client.net.rtmp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.red5.client.net.rtmpt.RTMPTClientConnection;
import org.red5.server.BaseConnection;
import org.red5.server.api.Red5;
import org.red5.server.net.IConnectionManager;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.RTMPMinaConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Responsible for management and creation of RTMP based connections.
 *
 * @author The Red5 Project
 */
public class RTMPClientConnManager implements IConnectionManager<BaseConnection> {

    private static final Logger log = LoggerFactory.getLogger(RTMPClientConnManager.class);

    private static int maxHandshakeTimeout = 7000;

    private static int maxInactivity = 60000;

    private static int pingInterval = 0;

    private static int executorQueueCapacity = 32;

    // whether or not to use the ThreadPoolTaskExecutor for incoming messages
    protected static boolean enableTaskExecutor;

    protected static IConnectionManager<BaseConnection> instance;

    protected ConcurrentMap<String, BaseConnection> connMap = new ConcurrentHashMap<>();

    protected AtomicInteger conns = new AtomicInteger();

    public static IConnectionManager<BaseConnection> getInstance() {
        if (instance == null) {
            instance = new RTMPClientConnManager();
        }
        return instance;
    }

    /** {@inheritDoc} */
    @Override
    public BaseConnection createConnection(Class<?> connCls) {
        BaseConnection conn = null;
        if (BaseConnection.class.isAssignableFrom(connCls)) {
            try {
                // create connection
                conn = createConnectionInstance(connCls);
                // add to local map
                connMap.put(conn.getSessionId(), conn);
                log.trace("Connections: {}", conns.incrementAndGet());
                log.trace("Connection created: {}", conn);
            } catch (Exception ex) {
                log.warn("Exception creating connection", ex);
            }
        }
        return conn;
    }

    /** {@inheritDoc} */
    @Override
    public BaseConnection createConnection(Class<?> connCls, String sessionId) {
        BaseConnection conn = null;
        if (BaseConnection.class.isAssignableFrom(connCls)) {
            try {
                // create connection
                conn = createConnectionInstance(connCls);
                // set the session id
                if (conn instanceof RTMPTClientConnection) {
                    ((RTMPTClientConnection) conn).setSessionId(sessionId);
                }
                // add to local map
                connMap.put(conn.getSessionId(), conn);
                log.trace("Connections: {} created: {}", conns.incrementAndGet(), conn);
            } catch (Exception ex) {
                log.warn("Exception creating connection", ex);
            }
        }
        return conn;
    }

    /**
     * Returns a connection for a given session id.
     *
     * @param sessionId session id
     * @return connection if found and null otherwise
     */
    @Override
    public BaseConnection getConnectionBySessionId(String sessionId) {
        log.debug("Getting connection by session id: {}", sessionId);
        if (connMap.containsKey(sessionId)) {
            return connMap.get(sessionId);
        } else {
            log.warn("Connection not found for {}", sessionId);
            if (log.isTraceEnabled()) {
                log.trace("Connections ({}) {}", connMap.size(), connMap.values());
            }
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public BaseConnection removeConnection(BaseConnection conn) {
        log.trace("Removing connection: {}", conn);
        return removeConnection(conn.getSessionId());
    }

    /** {@inheritDoc} */
    @Override
    public BaseConnection removeConnection(String sessionId) {
        log.debug("Removing connection with session id: {}", sessionId);
        if (log.isTraceEnabled()) {
            log.trace("Connections ({}) at pre-remove: {}", connMap.size(), connMap.values());
        }
        // remove from map
        BaseConnection conn = connMap.remove(sessionId);
        if (conn != null) {
            log.trace("Connections: {}", conns.decrementAndGet());
            Red5.setConnectionLocal(null);
        }
        return conn;
    }

    /** {@inheritDoc} */
    @Override
    public Collection<BaseConnection> getAllConnections() {
        ArrayList<BaseConnection> list = new ArrayList<>(connMap.size());
        list.addAll(connMap.values());
        return list;
    }

    /** {@inheritDoc} */
    @Override
    public Collection<BaseConnection> removeConnections() {
        final List<BaseConnection> list = new ArrayList<>(connMap.size());
        connMap.values().forEach(conn -> {
            removeConnection(conn.getSessionId());
            list.add(conn);
        });
        return list;
    }

    /**
     * Creates a connection instance based on the supplied type.
     *
     * @param cls class
     * @return connection
     * @throws Exception thrown
     */
    public RTMPConnection createConnectionInstance(Class<?> cls) throws Exception {
        RTMPConnection conn = null;
        if (cls == RTMPMinaConnection.class) {
            conn = (RTMPMinaConnection) cls.getDeclaredConstructor().newInstance();
        } else if (cls == RTMPTClientConnection.class) {
            conn = (RTMPTClientConnection) cls.getDeclaredConstructor().newInstance();
        } else {
            conn = (RTMPConnection) cls.getDeclaredConstructor().newInstance();
        }
        conn.setMaxHandshakeTimeout(maxHandshakeTimeout);
        conn.setMaxInactivity(maxInactivity);
        conn.setPingInterval(pingInterval);
        if (enableTaskExecutor) {
            // setup executor
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setDaemon(true);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(executorQueueCapacity);
            executor.initialize();
            conn.setExecutor(executor);
        }
        return conn;
    }

    public static void setMaxHandshakeTimeout(int maxHandshakeTimeout) {
        RTMPClientConnManager.maxHandshakeTimeout = maxHandshakeTimeout;
    }

    public static void setMaxInactivity(int maxInactivity) {
        RTMPClientConnManager.maxInactivity = maxInactivity;
    }

    public static void setPingInterval(int pingInterval) {
        RTMPClientConnManager.pingInterval = pingInterval;
    }

    public static void setExecutorQueueCapacity(int executorQueueCapacity) {
        RTMPClientConnManager.executorQueueCapacity = executorQueueCapacity;
    }

    public static void setEnableTaskExecutor(boolean enableTaskExecutor) {
        RTMPClientConnManager.enableTaskExecutor = enableTaskExecutor;
    }

}
