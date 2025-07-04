/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.scope;

import java.beans.ConstructorProperties;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import javax.management.openmbean.CompositeData;

import org.apache.commons.lang3.StringUtils;
import org.red5.server.Server;
import org.red5.server.api.IClient;
import org.red5.server.api.IConnection;
import org.red5.server.api.IContext;
import org.red5.server.api.IServer;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.persistence.PersistenceUtils;
import org.red5.server.api.scope.IBasicScope;
import org.red5.server.api.scope.IBroadcastScope;
import org.red5.server.api.scope.IGlobalScope;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.IScopeAware;
import org.red5.server.api.scope.IScopeHandler;
import org.red5.server.api.scope.ScopeType;
import org.red5.server.api.statistics.IScopeStatistics;
import org.red5.server.api.statistics.support.StatisticsCounter;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.api.stream.IClientBroadcastStream;
import org.red5.server.exception.ScopeException;
import org.red5.server.jmx.mxbeans.ScopeMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.jmx.export.annotation.ManagedResource;

/**
 * The scope object. <br>
 * A stateful object shared between a group of clients connected to the same context path. Scopes are arranged in a hierarchical way, so a
 * scope always has a parent unless its a "global" scope. If a client is connected to a scope then they are also connected to its parent
 * scope. The scope object is used to access resources, shared object, streams, etc. <br>
 * Scope layout:
 *
 * <pre>
 *  /Global scope - Contains application scopes
 *      /Application scope - Contains room, shared object, and stream scopes
 *          /Room scope - Contains other room, shared object, and / or stream scopes
 *              /Shared object scope - Contains shared object
 *              /Broadcast stream scope - Contains a broadcast stream
 * </pre>
 *
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 * @author Nathan Smith (nathgs@gmail.com)
 */
@ManagedResource(objectName = "org.red5.server:type=Scope", description = "Scope")
public class Scope extends BasicScope implements IScope, IScopeStatistics, ScopeMXBean {

    /** Constant <code>log</code> */
    protected static Logger log = LoggerFactory.getLogger(Scope.class);

    /** Constant <code>isDebug=log.isDebugEnabled()</code> */
    /** Constant <code>isTrace=log.isTraceEnabled()</code> */
    protected static boolean isDebug = log.isDebugEnabled(), isTrace = log.isTraceEnabled();

    /**
     * Unset flag constant
     */
    private static final int UNSET = -1;

    /**
     * Timestamp the scope was created.
     */
    private long creationTime;

    /**
     * Scope nesting depth, unset by default
     */
    private int depth = UNSET;

    /**
     * Whether scope is enabled
     */
    private boolean enabled = true;

    /**
     * Whether scope is running
     */
    private boolean running;

    /**
     * Auto-start flag
     */
    private boolean autoStart = true;

    /**
     * Scope context
     */
    private transient IContext context;

    /**
     * Scope handler
     */
    private transient IScopeHandler handler;

    /**
     * Registered service handlers for this scope. The map is created on-demand only if it's accessed for writing.
     */
    private transient volatile ConcurrentMap<String, Object> serviceHandlers;

    /**
     * Child scopes
     */
    private final transient ConcurrentScopeSet children;

    /**
     * Connected clients map
     */
    private final transient CopyOnWriteArraySet<IClient> clients;

    /**
     * Statistics about connections to the scope.
     */
    protected final transient StatisticsCounter connectionStats = new StatisticsCounter();

    /**
     * Statistics about sub-scopes.
     */
    protected final transient StatisticsCounter subscopeStats = new StatisticsCounter();

    /**
     * Mbean object name.
     */
    protected ObjectName oName;

    {
        creationTime = System.currentTimeMillis();
    }

    /**
     * Creates a scope
     */
    @ConstructorProperties(value = { "" })
    public Scope() {
        super(null, ScopeType.UNDEFINED, null, false);
        children = new ConcurrentScopeSet();
        clients = new CopyOnWriteArraySet<IClient>();
    }

    /**
     * Creates scope via parameters.
     *
     * @param parent scope parent
     * @param type scope type
     * @param name scope name
     * @param persistent persist or not
     */
    public Scope(IScope parent, ScopeType type, String name, boolean persistent) {
        super(parent, type, name, persistent);
        children = new ConcurrentScopeSet();
        clients = new CopyOnWriteArraySet<IClient>();
    }

    /**
     * {@inheritDoc}
     *
     * Add child scope to this scope
     */
    public boolean addChildScope(IBasicScope scope) {
        log.debug("Add child: {}", scope);
        boolean added = false;
        if (scope.isValid()) {
            try {
                if (!children.contains(scope)) {
                    log.debug("Adding child scope: {} to {}", scope, this);
                    added = children.add(scope);
                    if (added) {
                        // post notification
                        ((Server) getServer()).notifyBasicScopeAdded(scope);
                    }
                } else {
                    log.warn("Child scope already exists");
                }
            } catch (Exception e) {
                log.warn("Exception on add subscope", e);
            }
        } else {
            log.warn("Invalid scope rejected: {}", scope);
        }
        if (added && scope.getStore() == null) {
            // if child scope has no persistence store, use same class as parent
            try {
                if (scope instanceof Scope) {
                    ((Scope) scope).setPersistenceClass(persistenceClass);
                }
            } catch (Exception error) {
                log.error("Could not set persistence class", error);
            }
        }
        return added;
    }

    /**
     * {@inheritDoc}
     *
     * Connect to scope
     */
    public boolean connect(IConnection conn) {
        return connect(conn, null);
    }

    /**
     * Connect to scope with parameters. To successfully connect to scope it must have handler that will accept this connection with given
     * set of parameters. Client associated with connection is added to scope clients set, connection is registered as scope event listener.
     *
     * @param conn
     *            Connection object
     * @param params
     *            Parameters passed with connection
     * @return true on success, false otherwise
     */
    public boolean connect(IConnection conn, Object[] params) {
        log.debug("Connect - scope: {} connection: {}", this, conn);
        if (enabled) {
            if (hasParent() && !parent.connect(conn, params)) {
                log.debug("Connection to parent failed");
                return false;
            }
            if (hasHandler() && !getHandler().connect(conn, this, params)) {
                log.debug("Connection to handler failed");
                return false;
            }
            if (!conn.isConnected()) {
                log.debug("Connection is not connected");
                // timeout while connecting client
                return false;
            }
            final IClient client = conn.getClient();
            // we would not get this far if there is no handler
            if (hasHandler() && !getHandler().join(client, this)) {
                return false;
            }
            // checking the connection again? why?
            if (!conn.isConnected()) {
                // timeout while connecting client
                return false;
            }
            // add the client and event listener
            if (clients.add(client) && addEventListener(conn)) {
                log.debug("Added client");
                // increment conn stats
                connectionStats.increment();
                // get connected scope
                IScope connScope = conn.getScope();
                log.trace("Connection scope: {}", connScope);
                if (this.equals(connScope)) {
                    final IServer server = getServer();
                    if (server instanceof Server) {
                        ((Server) server).notifyConnected(conn);
                    }
                }
                return true;
            }
        } else {
            log.debug("Connection failed, scope is disabled");
        }
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * Create child scope of room type, with the given name.
     */
    public boolean createChildScope(String name) {
        // quick lookup by name
        log.debug("createChildScope: {}", name);
        if (children.hasName(name)) {
            log.debug("Scope: {} already exists, children: {}", name, children.getNames());
        } else {
            return addChildScope(new Scope(this, ScopeType.ROOM, name, false));
        }
        return false;
    }

    /**
     * Destroys scope
     *
     * @throws java.lang.Exception
     *             on error
     */
    public void destroy() throws Exception {
        log.debug("Destroy scope");
        if (hasParent()) {
            parent.removeChildScope(this);
        }
        if (hasHandler()) {
            // Because handler can be null when there is a parent handler
            getHandler().stop(this);
        }
        // kill all child scopes
        children.forEach(child -> {
            removeChildScope(child);
            if (child instanceof Scope) {
                ((Scope) child).uninit();
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * Disconnect connection from scope
     */
    public void disconnect(IConnection conn) {
        log.debug("Disconnect: {}", conn);
        // call disconnect handlers in reverse order of connection. ie. roomDisconnect is called before appDisconnect.
        final IClient client = conn.getClient();
        // null client can happen if connection didn't fully connect to the scope or its been nulled out
        if (client != null) {
            // remove it if it exists
            if (clients.remove(client)) {
                log.debug("Removed client");
                // get connected scope
                IScope connScope = conn.getScope();
                log.trace("Disconnection scope: {}", connScope);
                IScopeHandler handler = getHandler();
                if (handler != null) {
                    try {
                        handler.disconnect(conn, this);
                    } catch (Exception e) {
                        log.warn("Error while executing \"disconnect\" for connection {} on handler {}. {}", new Object[] { conn, handler, e });
                    }
                    try {
                        // there may be a timeout here ?
                        handler.leave(client, this);
                    } catch (Exception e) {
                        log.warn("Error while executing \"leave\" for client {} on handler {}. {}", new Object[] { conn, handler, e });
                    }
                }
                if (this.equals(connScope)) {
                    final IServer server = getServer();
                    if (server instanceof Server) {
                        ((Server) server).notifyDisconnected(conn);
                    }
                }
            }
        }
        // remove listener
        removeEventListener(conn);
        // disconnect from parent
        if (hasParent()) {
            parent.disconnect(conn);
        }
        // decrement conn stats
        connectionStats.decrement();
    }

    /** {@inheritDoc} */
    @Override
    public void dispatchEvent(IEvent event) {
        getClientConnections().forEach(conn -> {
            try {
                conn.dispatchEvent(event);
            } catch (RuntimeException e) {
                log.error("Exception during dispatching event: {}", event, e);
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * @return a int
     */
    public int getActiveClients() {
        return clients.size();
    }

    /**
     * {@inheritDoc}
     *
     * @return a int
     */
    public int getActiveConnections() {
        return connectionStats.getCurrent();
    }

    /**
     * {@inheritDoc}
     *
     * @return a int
     */
    public int getActiveSubscopes() {
        return subscopeStats.getCurrent();
    }

    /**
     * {@inheritDoc}
     *
     * Return the broadcast scope for a given name.
     */
    public IBroadcastScope getBroadcastScope(String name) {
        return (IBroadcastScope) children.getBasicScope(ScopeType.BROADCAST, name);
    }

    /**
     * {@inheritDoc}
     *
     * Return the broadcast streams for this scope.
     */
    @Override
    public Set<IBroadcastStream> getBroadcastStreams() {
        Set<IBroadcastStream> streams = new HashSet<>();
        children.getBasicScopes(ScopeType.BROADCAST).stream().filter(bs -> ((IBroadcastScope) bs).getClientBroadcastStream() != null).forEach(bs -> streams.add(((IBroadcastScope) bs).getClientBroadcastStream()));
        return streams;
    }

    /**
     * {@inheritDoc}
     *
     * Return base scope with given name.
     */
    public IBasicScope getBasicScope(String name) {
        return children.getBasicScope(ScopeType.UNDEFINED, name);
    }

    /**
     * {@inheritDoc}
     *
     * Return base scope of given type with given name.
     */
    public IBasicScope getBasicScope(ScopeType type, String name) {
        return children.getBasicScope(type, name);
    }

    /**
     * {@inheritDoc}
     *
     * Return basic scope names matching given type.
     */
    public Set<String> getBasicScopeNames(ScopeType type) {
        if (type != null) {
            // if its broadcast type then also check aliases
            if (type == ScopeType.BROADCAST) {
                final Set<String> broadcastNames = new HashSet<>();
                Set<IBasicScope> broadcastScopes = children.stream().filter(child -> child.getType().equals(type)).collect(Collectors.toSet());
                broadcastScopes.forEach(bs -> {
                    // add the streams name
                    broadcastNames.add(bs.getName());
                    // add any aliases
                    IClientBroadcastStream stream = ((IBroadcastScope) bs).getClientBroadcastStream();
                    if (stream != null) {
                        // publish alias if it exists
                        String nameAlias = stream.getAlias();
                        if (nameAlias != null) {
                            broadcastNames.add(nameAlias);
                        }
                        // subscribe aliases
                        if (stream.hasAlias()) {
                            broadcastNames.addAll(stream.getAliases());
                        }
                    }
                });
                return broadcastNames;
            } else {
                return children.stream().filter(child -> child.getType().equals(type)).map(IBasicScope::getName).collect(Collectors.toSet());
            }
        }
        return getScopeNames();
    }

    /**
     * Return current thread context classloader.
     *
     * @return Classloader for thread context
     */
    public ClassLoader getClassLoader() {
        return getContext().getClassLoader();
    }

    /**
     * Return set of clients.
     *
     * @return Set of clients bound to scope
     */
    public Set<IClient> getClients() {
        return clients;
    }

    /**
     * {@inheritDoc}
     *
     * @return a {@link java.util.Collection} object
     */
    @Deprecated
    public Collection<Set<IConnection>> getConnections() {
        Collection<Set<IConnection>> result = new ArrayList<Set<IConnection>>(3);
        result.add(getClientConnections());
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * @return a {@link java.util.Set} object
     */
    public Set<IConnection> getClientConnections() {
        Set<IConnection> result = new HashSet<IConnection>(3);
        log.debug("Client count: {}", clients.size());
        for (IClient cli : clients) {
            Set<IConnection> set = cli.getConnections();
            log.debug("Client connection count: {}", set.size());
            if (set.size() > 1) {
                log.warn("Client connections exceeded expected single count; size: {}", set.size());
            }
            for (IConnection conn : set) {
                result.add(conn);
            }
        }
        return result;
    }

    /** {@inheritDoc} */
    @Deprecated
    public Set<IConnection> lookupConnections(IClient client) {
        HashSet<IConnection> result = new HashSet<IConnection>(1);
        if (clients.contains(client)) {
            for (IClient cli : clients) {
                if (cli.equals(client)) {
                    Set<IConnection> set = cli.getConnections();
                    if (set.size() > 1) {
                        log.warn("Client connections exceeded expected single count; size: {}", set.size());
                    }
                    result.add(set.iterator().next());
                    break;
                }
            }
        }
        return result;
    }

    /** {@inheritDoc} */
    public IConnection lookupConnection(IClient client) {
        for (IClient cli : clients) {
            if (cli.equals(client)) {
                Set<IConnection> set = cli.getConnections();
                if (set.size() > 1) {
                    log.warn("Client connections exceeded expected single count; size: {}", set.size());
                }
                return set.iterator().next();
            }
        }
        return null;
    }

    /**
     * Return scope context. If scope doesn't have context, parent's context is returns, and so forth.
     *
     * @return Scope context or parent context
     */
    public IContext getContext() {
        if (!hasContext() && hasParent()) {
            //log.debug("returning parent context");
            return parent.getContext();
        } else {
            //log.debug("returning context");
            return context;
        }
    }

    /**
     * Return scope context path
     *
     * @return Scope context path
     */
    public String getContextPath() {
        if (hasContext()) {
            return "";
        } else if (hasParent()) {
            return parent.getContextPath() + '/' + name;
        } else {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return a long
     */
    public long getCreationTime() {
        return creationTime;
    }

    /**
     * {@inheritDoc}
     *
     * return scope depth
     */
    @Override
    public int getDepth() {
        if (depth == UNSET) {
            if (hasParent()) {
                depth = parent.getDepth() + 1;
            } else {
                depth = 0;
            }
        }
        return depth;
    }

    /**
     * Return scope handler or parent's scope handler if this scope doesn't have one.
     *
     * @return Scope handler (or parent's one)
     */
    public IScopeHandler getHandler() {
        log.trace("getHandler from {}", name);
        if (handler != null) {
            return handler;
        } else if (hasParent()) {
            return getParent().getHandler();
        } else {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return a int
     */
    public int getMaxClients() {
        return connectionStats.getMax();
    }

    /**
     * {@inheritDoc}
     *
     * @return a int
     */
    public int getMaxConnections() {
        return connectionStats.getMax();
    }

    /**
     * {@inheritDoc}
     *
     * @return a int
     */
    public int getMaxSubscopes() {
        return subscopeStats.getMax();
    }

    /**
     * {@inheritDoc}
     *
     * Return parent scope
     */
    @Override
    public IScope getParent() {
        return parent;
    }

    /**
     * {@inheritDoc}
     *
     * Return scope path calculated from parent path and parent scope name
     */
    @Override
    public String getPath() {
        if (hasParent()) {
            return parent.getPath() + '/' + parent.getName();
        } else {
            return "";
        }
    }

    /**
     * {@inheritDoc}
     *
     * Return resource located at given path
     */
    @SuppressWarnings("null")
    public Resource getResource(String path) {
        if (hasContext()) {
            return context.getResource(path);
        }
        return getContext().getResource(getContextPath() + '/' + path);
    }

    /**
     * {@inheritDoc}
     *
     * Return array of resources from path string, usually used with pattern path
     */
    @SuppressWarnings("null")
    public Resource[] getResources(String path) throws IOException {
        if (hasContext()) {
            return context.getResources(path);
        }
        return getContext().getResources(getContextPath() + '/' + path);
    }

    /**
     * {@inheritDoc}
     *
     * Return child scope by name
     */
    public IScope getScope(String name) {
        IBasicScope child = children.getBasicScope(ScopeType.UNDEFINED, name);
        log.debug("Child of {}: {}", this.name, child);
        if (child != null) {
            if (child instanceof IScope) {
                return (IScope) child;
            }
            log.warn("Requested scope: {} is not of IScope type: {}", name, child.getClass().getName());
        }
        return null;
    }

    /**
     * Return child scope names iterator
     *
     * @return Child scope names iterator
     */
    public Set<String> getScopeNames() {
        log.debug("Children: {}", children);
        return children.getNames();
    }

    /**
     * {@inheritDoc}
     *
     * Return service handler by name
     */
    public Object getServiceHandler(String name) {
        Map<String, Object> serviceHandlers = getServiceHandlers(false);
        if (serviceHandlers == null) {
            return null;
        }
        return serviceHandlers.get(name);
    }

    /**
     * Return set of service handler names. Removing entries from the set unregisters the corresponding service handler.
     *
     * @return Set of service handler names
     */
    @SuppressWarnings("unchecked")
    public Set<String> getServiceHandlerNames() {
        Map<String, Object> serviceHandlers = getServiceHandlers(false);
        if (serviceHandlers == null) {
            return Collections.EMPTY_SET;
        }
        return serviceHandlers.keySet();
    }

    /**
     * Return map of service handlers. The map is created if it doesn't exist yet.
     *
     * @return Map of service handlers
     */
    protected Map<String, Object> getServiceHandlers() {
        return getServiceHandlers(true);
    }

    /**
     * Return map of service handlers and optionally created it if it doesn't exist.
     *
     * @param allowCreate
     *            Should the map be created if it doesn't exist?
     * @return Map of service handlers
     */
    protected Map<String, Object> getServiceHandlers(boolean allowCreate) {
        if (serviceHandlers == null) {
            if (allowCreate) {
                serviceHandlers = new ConcurrentHashMap<String, Object>(3, 0.9f, 1);
            }
        }
        return serviceHandlers;
    }

    /**
     * {@inheritDoc}
     *
     * @return a {@link org.red5.server.api.statistics.IScopeStatistics} object
     */
    public IScopeStatistics getStatistics() {
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @return a int
     */
    @Deprecated
    public int getTotalClients() {
        return connectionStats.getTotal();
    }

    /**
     * {@inheritDoc}
     *
     * @return a int
     */
    public int getTotalConnections() {
        return connectionStats.getTotal();
    }

    /**
     * {@inheritDoc}
     *
     * @return a int
     */
    public int getTotalSubscopes() {
        return subscopeStats.getTotal();
    }

    /**
     * {@inheritDoc}
     *
     * Handles event. To be implemented in subclasses.
     */
    @Override
    public boolean handleEvent(IEvent event) {
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * Check whether scope has child scope with given name
     */
    public boolean hasChildScope(String name) {
        log.debug("Has child scope? {} in {}", name, this);
        return children.hasName(name);
    }

    /**
     * {@inheritDoc}
     *
     * Check whether scope has child scope with given name and type
     */
    public boolean hasChildScope(ScopeType type, String name) {
        log.debug("Has child scope? {} in {}", name, this);
        return children.getBasicScope(type, name) != null;
    }

    /**
     * Check if scope has a context
     *
     * @return true if scope has context, false otherwise
     */
    public boolean hasContext() {
        return context != null;
    }

    /**
     * Check if scope or it's parent has handler
     *
     * @return true if scope or it's parent scope has a handler, false otherwise
     */
    public boolean hasHandler() {
        return (handler != null || (hasParent() && getParent().hasHandler()));
    }

    /**
     * {@inheritDoc}
     *
     * Check if scope has parent scope
     */
    @Override
    public boolean hasParent() {
        return (parent != null);
    }

    /**
     * Initialization actions, start if autostart is set to true.
     */
    public void init() {
        log.debug("Init scope: {} parent: {}", name, (parent != null ? parent.getName() : "no-parent"));
        if (hasParent()) {
            if (!parent.hasChildScope(name)) {
                if (parent.addChildScope(this)) {
                    log.debug("Scope added to parent");
                } else {
                    log.warn("Scope not added to parent");
                    //throw new ScopeException("Scope not added to parent");
                    return;
                }
            } else {
                throw new ScopeException("Scope already exists in parent");
            }
        } else {
            log.debug("Scope has no parent");
        }
        if (autoStart) {
            start();
        }
    }

    /**
     * Uninitialize scope and unregister from parent.
     */
    public void uninit() {
        log.debug("Un-init scope: {}", name);
        children.forEach(child -> {
            if (child instanceof Scope) {
                ((Scope) child).uninit();
            }
        });
        stop();
        setEnabled(false);
        if (hasParent()) {
            if (parent.hasChildScope(name)) {
                parent.removeChildScope(this);
            }
        }
    }

    /**
     * Check if scope is enabled
     *
     * @return true if scope is enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Here for JMX only, uses isEnabled()
     *
     * @return a boolean
     */
    public boolean getEnabled() {
        return isEnabled();
    }

    /**
     * Check if scope is in running state
     *
     * @return true if scope is in running state, false otherwise
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Here for JMX only, uses isEnabled()
     *
     * @return a boolean
     */
    public boolean getRunning() {
        return isRunning();
    }

    /**
     * {@inheritDoc}
     *
     * Register service handler by name
     */
    public void registerServiceHandler(String name, Object handler) {
        Map<String, Object> serviceHandlers = getServiceHandlers();
        serviceHandlers.put(name, handler);
    }

    /**
     * {@inheritDoc}
     *
     * Removes child scope
     */
    public void removeChildScope(IBasicScope scope) {
        log.debug("removeChildScope: {}", scope);
        // remove from parent
        if (children.remove(scope)) {
            // post notification
            ((Server) getServer()).notifyBasicScopeRemoved(scope);
            if (scope instanceof Scope) {
                unregisterJMX();
            }
        }
    }

    /**
     * Removes all the child scopes
     */
    public void removeChildren() {
        log.trace("removeChildren of {}", name);
        children.forEach(child -> removeChildScope(child));
    }

    /**
     * {@inheritDoc}
     *
     * Setter for autostart flag
     */
    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    /**
     * {@inheritDoc}
     *
     * Setter for child load path. Should be implemented in subclasses?
     */
    public void setChildLoadPath(String pattern) {

    }

    /**
     * Setter for context
     *
     * @param context
     *            Context object
     */
    public void setContext(IContext context) {
        log.debug("Set context: {}", context);
        this.context = context;
    }

    /**
     * {@inheritDoc}
     *
     * Set scope depth
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * {@inheritDoc}
     *
     * Enable or disable scope by setting enable flag
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Setter for scope event handler
     *
     * @param handler
     *            Event handler
     */
    public void setHandler(IScopeHandler handler) {
        log.debug("setHandler: {} on {}", handler, name);
        this.handler = handler;
        if (handler instanceof IScopeAware) {
            ((IScopeAware) handler).setScope(this);
        }
    }

    /**
     * {@inheritDoc}
     *
     * Setter for scope name
     */
    @Override
    public final void setName(String name) {
        log.debug("Set name: {}", name);
        if (this.name == null && StringUtils.isNotBlank(name)) {
            // reset of the name is no longer allowed
            this.name = name;
            // unregister from jmx
            if (oName != null) {
                unregisterJMX();
            }
            // register
            registerJMX();
        } else {
            log.info("Scope {} name reset to: {} disallowed", this.name, name);
        }
    }

    /**
     * Setter for parent scope
     *
     * @param parent
     *            Parent scope
     */
    public void setParent(IScope parent) {
        log.debug("Set parent scope: {}", parent);
        this.parent = parent;
    }

    /**
     * {@inheritDoc}
     *
     * Set scope persistence class
     */
    public void setPersistenceClass(String persistenceClass) throws Exception {
        this.persistenceClass = persistenceClass;
        if (persistenceClass != null) {
            store = PersistenceUtils.getPersistenceStore(this, persistenceClass);
        }
    }

    /**
     * Starts scope
     *
     * @return true if scope has handler and it's start method returned true, false otherwise
     */
    public boolean start() {
        log.debug("Start scope");
        boolean result = false;
        if (enabled && !running) {
            // check for any handlers
            if (handler != null) {
                log.debug("Scope {} has a handler {}", this.getName(), handler);
            } else {
                log.debug("{} has no handler, adding parent handler", this);
                handler = parent.getHandler();
            }
            try {
                // if we dont have a handler of our own dont try to start it
                if (handler != null) {
                    result = handler.start(this);
                } else {
                    // always start scopes without handlers
                    log.debug("{} has no handler of its own, allowing start", this);
                    result = true;
                }
            } catch (Throwable e) {
                log.error("Could not start scope {}", this, e);
            } finally {
                // post notification
                ((Server) getServer()).notifyScopeCreated(this);
            }
            running = result;
        }
        return result;
    }

    /**
     * Stops scope
     */
    public void stop() {
        log.debug("stop: {}", name);
        if (enabled && running && handler != null) {
            try {
                // if we dont have a handler of our own dont try to stop it
                handler.stop(this);
            } catch (Throwable e) {
                log.error("Could not stop scope {}", this, e);
            } finally {
                // post notification
                ((Server) getServer()).notifyScopeRemoved(this);
            }
            // remove all children
            removeChildren();
        }
        running = false;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "Scope [name=" + getName() + ", path=" + getPath() + ", type=" + type + ", autoStart=" + autoStart + ", creationTime=" + creationTime + ", depth=" + getDepth() + ", enabled=" + enabled + ", running=" + running + "]";
    }

    /**
     * {@inheritDoc}
     *
     * Unregisters service handler by name
     */
    public void unregisterServiceHandler(String name) {
        Map<String, Object> serviceHandlers = getServiceHandlers(false);
        if (serviceHandlers != null) {
            serviceHandlers.remove(name);
        }
    }

    /**
     * Return the server instance connected to this scope.
     *
     * @return the server instance
     */
    public IServer getServer() {
        if (hasParent()) {
            final IScope parent = getParent();
            if (parent instanceof Scope) {
                return ((Scope) parent).getServer();
            } else if (parent instanceof IGlobalScope) {
                return ((IGlobalScope) parent).getServer();
            }
        }
        return null;
    }

    //for debugging
    /**
     * <p>dump.</p>
     */
    public void dump() {
        if (isTrace) {
            log.trace("Scope: {} {}", this.getClass().getName(), this);
            log.trace("Running: {}", running);
            if (hasParent()) {
                log.trace("Parent: {}", parent);
                Set<String> names = parent.getBasicScopeNames(null);
                log.trace("Sibling count: {}", names.size());
                for (String sib : names) {
                    log.trace("Siblings - {}", sib);
                }
                names = null;
            }
            log.trace("Handler: {} child count: {}", handler, children.size());
            children.forEach(child -> {
                log.trace("Child: {}", child);
            });
        }
    }

    /**
     * <p>registerJMX.</p>
     */
    protected void registerJMX() {
        // register with jmx
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            String cName = this.getClass().getName();
            if (cName.indexOf('.') != -1) {
                cName = cName.substring(cName.lastIndexOf('.')).replaceFirst("[\\.]", "");
            }
            oName = new ObjectName(String.format("org.red5.server:type=%s,name=%s", cName, name));
            // don't reregister
            if (!mbs.isRegistered(oName)) {
                mbs.registerMBean(new StandardMBean(this, ScopeMXBean.class, true), oName);
            }
        } catch (Exception e) {
            log.warn("Error on jmx registration", e);
        }
    }

    /**
     * <p>unregisterJMX.</p>
     */
    protected void unregisterJMX() {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        if (oName != null && mbs.isRegistered(oName)) {
            try {
                mbs.unregisterMBean(oName);
            } catch (Exception e) {
                log.warn("Exception unregistering: {}", oName, e);
            }
            oName = null;
        }
    }

    /**
     * Allows for reconstruction via CompositeData.
     *
     * @param cd
     *            composite data
     * @return Scope class instance
     */
    public static Scope from(CompositeData cd) {
        IScope parent = null;
        ScopeType type = ScopeType.UNDEFINED;
        String name = null;
        boolean persistent = false;
        if (cd.containsKey("parent")) {
            parent = (IScope) cd.get("parent");
        }
        if (cd.containsKey("type")) {
            type = (ScopeType) cd.get("type");
        }
        if (cd.containsKey("name")) {
            name = (String) cd.get("name");
        }
        if (cd.containsKey("persistent")) {
            persistent = (Boolean) cd.get("persistent");
        }
        return new Scope(parent, type, name, persistent);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((getPath() == null) ? 0 : getPath().hashCode());
        result = prime * result + getDepth();
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Scope other = (Scope) obj;
        if (hashCode() != other.hashCode()) {
            return false;
        }
        return true;
    }

    private final class ConcurrentScopeSet extends ConcurrentSkipListSet<IBasicScope> {

        private static final long serialVersionUID = 283917025588555L;

        @Override
        public boolean add(IBasicScope scope) {
            log.debug("Add child scope: {}", scope);
            boolean added = false;
            // check #1
            if (!contains(scope)) {
                log.debug("Adding child scope: {} to {}", (((IBasicScope) scope).getName()), this);
                if (hasHandler()) {
                    // get the handler for the scope to which we are adding this new scope
                    IScopeHandler hdlr = getHandler();
                    // add the scope to the handler
                    if (!hdlr.addChildScope(scope)) {
                        log.warn("Failed to add child scope: {} to {}", scope, this);
                        return added;
                    }
                } else {
                    log.trace("No handler found for {}", this);
                }
                try {
                    // check #2 for entry
                    if (!contains(scope)) {
                        // add the entry
                        added = super.add(scope);
                        if (added) {
                            subscopeStats.increment();
                        } else {
                            log.debug("Subscope was not added");
                        }
                    } else {
                        log.debug("Subscope already exists");
                    }
                } catch (Exception e) {
                    log.warn("Exception on add", e);
                }
                if (added && scope instanceof Scope) {
                    // cast it
                    Scope scp = (Scope) scope;
                    // start the scope
                    if (scp.start()) {
                        log.debug("Child scope started");
                    } else {
                        log.debug("Failed to start child scope: {} in {}", scope, this);
                    }
                }
            }
            return added;
        }

        @Override
        public boolean remove(Object scope) {
            log.debug("Remove child scope: {}", scope);
            boolean removed = false;
            // remove the entry, ensure removed value is equal to the given object
            if (super.remove(scope)) {
                subscopeStats.decrement();
                if (hasHandler()) {
                    IScopeHandler hdlr = getHandler();
                    log.debug("Removing child scope: {}", (((IBasicScope) scope).getName()));
                    hdlr.removeChildScope((IBasicScope) scope);
                    if (scope instanceof Scope) {
                        // stop the scope
                        ((Scope) scope).stop();
                    }
                } else {
                    log.trace("No handler found for {}", this);
                }
                removed = true;
            } else {
                log.debug("Scope was not removed or was not found");
            }
            return removed;
        }

        /**
         * Returns the scope names.
         *
         * @return names
         */
        public Set<String> getNames() {
            Set<String> names = new HashSet<String>();
            stream().forEach(child -> names.add(child.getName()));
            return names;
        }

        /**
         * Returns whether or not a named scope exists.
         *
         * @return true if a matching scope is found, false otherwise
         */
        public boolean hasName(String name) {
            if (isDebug) {
                log.debug("hasName: {}", name);
            }
            if (name != null) {
                return stream().filter(child -> child.getName().equals(name)).findFirst().isPresent();
            } else {
                log.info("Invalid scope name, null is not allowed");
            }
            return false;
        }

        /**
         * Returns child scopes for a given type.
         *
         * @param type
         *            Scope type
         * @return set of scopes matching type
         */
        public Set<IBasicScope> getBasicScopes(ScopeType type) {
            return stream().filter(child -> child.getType().equals(type)).collect(Collectors.toUnmodifiableSet());
        }

        /**
         * Returns a child scope for a given name and type.
         *
         * @param type
         *            Scope type
         * @param name
         *            Scope name
         * @return scope
         */
        public IBasicScope getBasicScope(ScopeType type, String name) {
            Optional<IBasicScope> scope = null;
            // skip type check?
            if (ScopeType.UNDEFINED.equals(type)) {
                scope = stream().filter(child -> child.getName().equals(name)).findFirst();
            } else {
                // if its broadcast type then allow an alias match in addition to the name match
                if (ScopeType.BROADCAST.equals(type)) {
                    // checks publish and subscribe aliases
                    for (IBasicScope child : this) {
                        // ensure type is broadcast type, since we'll pull out a cbs
                        if (child.getType().equals(type)) {
                            String childName = child.getName();
                            IClientBroadcastStream cbs = ((IBroadcastScope) child).getClientBroadcastStream();
                            if (cbs != null) {
                                String pubName = cbs.getPublishedName();
                                if (childName.equals(name)) {
                                    log.debug("Scope found by name: {} on {}", name, pubName);
                                    return child;
                                } else if (cbs.containsAlias(name)) {
                                    log.debug("Scope found with alias: {} on {}", name, pubName);
                                    return child;
                                } else {
                                    log.debug("No match for name or alias of {} on published stream: {}", name, pubName);
                                }
                            } else {
                                //log.debug("Broadcast scope: {} has no stream attached", name);
                                if (childName.equals(name)) {
                                    log.debug("Scope found by name: {} but has no stream", name);
                                    return child;
                                }
                            }
                        }
                    }
                } else {
                    scope = stream().filter(child -> child.getType().equals(type) && child.getName().equals(name)).findFirst();
                }
            }
            if (scope != null && scope.isPresent()) {
                return scope.get();
            }
            return null;
        }

    }

}
