/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.rtmp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.red5.server.BaseConnection;
import org.red5.server.net.IConnectionManager;
import org.red5.server.net.rtmpt.RTMPTConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Responsible for management and creation of RTMP based connections.
 *
 * @author The Red5 Project
 */
public class RTMPConnManager implements IConnectionManager<BaseConnection>, ApplicationContextAware, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RTMPConnManager.class);

    protected static IConnectionManager<BaseConnection> instance;

    protected static ApplicationContext applicationContext;

    protected ScheduledExecutorService executor;

    protected ScheduledFuture<?> checkerFuture;

    protected ConcurrentMap<String, BaseConnection> connMap = new ConcurrentHashMap<>();

    protected AtomicInteger conns = new AtomicInteger();

    protected boolean debug;

    public static IConnectionManager<BaseConnection> getInstance() {
        if (instance == null) {
            log.trace("Connection manager instance does not exist");
            if (applicationContext != null && applicationContext.containsBean("rtmpConnManager")) {
                log.trace("Connection manager bean exists");
                instance = (RTMPConnManager) applicationContext.getBean("rtmpConnManager");
            } else {
                log.trace("Connection manager bean doesnt exist, creating new instance");
                instance = new RTMPConnManager();
            }
        }
        return instance;
    }

    public void createConnectionChecker() {
        executor = Executors.newScheduledThreadPool(1, new CustomizableThreadFactory("ConnectionChecker-"));
        // create a scheduled job to check for dead or hung connections
        checkerFuture = executor.scheduleAtFixedRate(() -> {
            // count the connections that need closing
            int closedConnections = 0;
            // get all the current connections
            Collection<BaseConnection> allConns = getAllConnections();
            log.debug("Checking {} connections", allConns.size());
            for (BaseConnection conn : allConns) {
                if (conn instanceof RTMPMinaConnection) {
                    ((RTMPMinaConnection) conn).dumpInfo();
                }
                String sessionId = conn.getSessionId();
                if (conn.isDisconnected()) {
                    removeConnection(sessionId);
                } else if (conn.isIdle()) {
                    if (!conn.isClosed()) {
                        log.debug("Connection {} is not closed", conn.getSessionId());
                    } else {
                        closedConnections++;
                    }
                }
            }
            // if there is more than one connection that needed to be closed, request a GC to clean up memory.
            if (closedConnections > 0) {
                System.gc();
            }
        }, 7000, 30000, TimeUnit.MILLISECONDS);
    }

    /** {@inheritDoc} */
    @Override
    public BaseConnection createConnection(Class<?> connCls) {
        BaseConnection conn = null;
        if (RTMPConnection.class.isAssignableFrom(connCls)) {
            try {
                // create connection
                conn = createConnectionInstance(connCls);
                // add to local map
                connMap.put(conn.getSessionId(), conn);
                log.trace("Connections: {}", conns.incrementAndGet());
                // set the scheduler
                if (applicationContext.containsBean("rtmpScheduler")) {
                    ((RTMPConnection) conn).setScheduler((ThreadPoolTaskScheduler) applicationContext.getBean("rtmpScheduler"));
                }
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
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Returns a connection for a given session id.
     *
     * @param sessionId session id
     * @return connection if found and null otherwise
     */
    public BaseConnection getConnectionBySessionId(String sessionId) {
        log.trace("Getting connection by session id: {}", sessionId);
        BaseConnection conn = connMap.get(sessionId);
        if (conn == null && log.isDebugEnabled()) {
            log.debug("Connection not found for {}", sessionId);
            if (log.isTraceEnabled()) {
                log.trace("Connections ({}) {}", connMap.size(), connMap.values());
            }
        }
        return conn;
    }

    /** {@inheritDoc} */
    @Override
    public BaseConnection removeConnection(BaseConnection conn) {
        return removeConnection(conn.getSessionId());
    }

    /** {@inheritDoc} */
    @Override
    public BaseConnection removeConnection(String sessionId) {
        log.trace("Removing connection with session id: {}", sessionId);
        if (log.isTraceEnabled()) {
            log.trace("Connections ({}) at pre-remove: {}", connMap.size(), connMap.values());
        }
        // remove from map
        BaseConnection conn = connMap.remove(sessionId);
        if (conn != null) {
            log.trace("Connections: {}", conns.decrementAndGet());
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
     * @param cls
     *            class
     * @return connection
     * @throws Exception
     *             on error
     */
    public RTMPConnection createConnectionInstance(Class<?> cls) throws Exception {
        RTMPConnection conn = null;
        if (cls == RTMPMinaConnection.class) {
            conn = (RTMPMinaConnection) applicationContext.getBean(RTMPMinaConnection.class);
        } else if (cls == RTMPTConnection.class) {
            conn = (RTMPTConnection) applicationContext.getBean(RTMPTConnection.class);
        } else {
            conn = (RTMPConnection) cls.getDeclaredConstructor().newInstance();
        }
        return conn;
    }

    /**
     * @param debug
     *            the debug to set
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        RTMPConnManager.applicationContext = applicationContext;
    }

    @Override
    public void destroy() throws Exception {
        if (checkerFuture != null && !checkerFuture.isDone()) {
            checkerFuture.cancel(true);
        }
        executor.shutdownNow();
    }

}
