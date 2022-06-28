/*
 * RED5 Open Source Flash Server - https://github.com/red5 Copyright 2006-2018 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.net.websocket;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Future;

import org.red5.net.websocket.listener.DefaultWebSocketDataListener;
import org.red5.net.websocket.listener.IWebSocketDataListener;
import org.red5.net.websocket.listener.IWebSocketScopeListener;
import org.red5.net.websocket.model.WebSocketEvent;
import org.red5.server.api.IContext;
import org.red5.server.api.scope.IScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages websocket scopes and listeners.
 *
 * @author Toda Takahiko
 * @author Paul Gregoire
 */
public class WebSocketScopeManager {

    private static final Logger log = LoggerFactory.getLogger(WebSocketScopeManager.class);

    // reference to the owning application scope
    private IScope appScope;

    // all the ws scopes that the manager is responsible for
    private ConcurrentMap<String, WebSocketScope> scopes = new ConcurrentHashMap<>();

    // scope listeners
    private CopyOnWriteArraySet<IWebSocketScopeListener> scopeListeners = new CopyOnWriteArraySet<>();

    // currently active rooms
    private CopyOnWriteArraySet<String> activeRooms = new CopyOnWriteArraySet<>();

    // whether or not to copy listeners from parent to child on create
    protected boolean copyListeners = true;

    public void addListener(IWebSocketScopeListener listener) {
        scopeListeners.add(listener);
    }

    public void removeListener(IWebSocketScopeListener listener) {
        scopeListeners.remove(listener);
    }

    /**
     * Returns the enable state of a given path.
     *
     * @param path
     *            scope / context path
     * @return enabled if registered as active and false otherwise
     */
    public boolean isEnabled(String path) {
        if (path.startsWith("/")) {
            // start after the leading slash
            int roomSlashPos = path.indexOf('/', 1);
            if (roomSlashPos == -1) {
                // check application level scope
                path = path.substring(1);
            } else {
                // check room level scope
                path = path.substring(1, roomSlashPos);
            }
        }
        boolean enabled = activeRooms.contains(path);
        log.debug("Enabled check on path: {} enabled: {}", path, enabled);
        return enabled;
    }

    /**
     * Adds a scope to the enabled applications.
     *
     * @param scope
     *            the application scope
     */
    public void addScope(IScope scope) {
        String app = scope.getName();
        // add the name to the collection (no '/' prefix)
        activeRooms.add(app);
        // check the context for a predefined websocket scope
        IContext ctx = scope.getContext();
        if (ctx != null && ctx.hasBean("webSocketScopeDefault")) {
            log.debug("WebSocket scope found in context");
            WebSocketScope wsScope = (WebSocketScope) scope.getContext().getBean("webSocketScopeDefault");
            if (wsScope != null) {
                log.trace("Default WebSocketScope has {} listeners", wsScope.getListeners().size());
            }
            // add to scopes
            scopes.put(String.format("/%s", app), wsScope);
        } else {
            log.debug("Creating a new scope");
            // add a default scope and listener if none are defined
            WebSocketScope wsScope = new WebSocketScope();
            wsScope.setScope(scope);
            wsScope.setPath(String.format("/%s", app));
            if (wsScope.getListeners().isEmpty()) {
                log.debug("adding default listener");
                wsScope.addListener(new DefaultWebSocketDataListener());
            }
            notifyListeners(WebSocketEvent.SCOPE_CREATED, wsScope);
            // add to scopes
            addWebSocketScope(wsScope);
        }
    }

    /**
     * Removes the application scope.
     *
     * @param scope
     *            the application scope
     */
    public void removeApplication(IScope scope) {
        activeRooms.remove(scope.getName());
    }

    /**
     * Adds a websocket scope.
     *
     * @param webSocketScope
     * @return true if added and false otherwise
     */
    public boolean addWebSocketScope(WebSocketScope webSocketScope) {
        String path = webSocketScope.getPath();
        if (scopes.putIfAbsent(path, webSocketScope) == null) {
            log.info("addWebSocketScope: {}", webSocketScope);
            notifyListeners(WebSocketEvent.SCOPE_ADDED, webSocketScope);
            return true;
        }
        return false;
    }

    /**
     * Removes a websocket scope.
     *
     * @param webSocketScope
     * @return true if removed and false otherwise
     */
    public boolean removeWebSocketScope(WebSocketScope webSocketScope) {
        log.info("removeWebSocketScope: {}", webSocketScope);
        WebSocketScope wsScope = scopes.remove(webSocketScope.getPath());
        if (wsScope != null) {
            notifyListeners(WebSocketEvent.SCOPE_REMOVED, wsScope);
            return true;
        }
        return false;
    }

    /**
     * Add the connection on scope.
     *
     * @param conn
     *            WebSocketConnection
     */
    public void addConnection(WebSocketConnection conn) {
        WebSocketScope scope = getScope(conn);
        scope.addConnection(conn);
    }

    /**
     * Remove connection from scope.
     *
     * @param conn
     *            WebSocketConnection
     */
    public void removeConnection(WebSocketConnection conn) {
        if (conn != null) {
            WebSocketScope scope = getScope(conn);
            if (scope != null) {
                scope.removeConnection(conn);
                if (!scope.isValid()) {
                    // scope is not valid. delete this.
                    removeWebSocketScope(scope);
                }
            }
        }
    }

    /**
     * Add the listener on scope via its path.
     *
     * @param listener
     *            IWebSocketDataListener
     * @param path
     */
    public void addListener(IWebSocketDataListener listener, String path) {
        log.trace("addListener: {}", listener);
        WebSocketScope scope = getScope(path);
        if (scope != null) {
            scope.addListener(listener);
        } else {
            log.info("Scope not found for path: {}", path);
        }
    }

    /**
     * Remove listener from scope via its path.
     *
     * @param listener
     *            IWebSocketDataListener
     * @param path
     */
    public void removeListener(IWebSocketDataListener listener, String path) {
        log.trace("removeListener: {}", listener);
        WebSocketScope scope = getScope(path);
        if (scope != null) {
            scope.removeListener(listener);
            if (!scope.isValid()) {
                // scope is not valid. delete this
                removeWebSocketScope(scope);
            }
        } else {
            log.info("Scope not found for path: {}", path);
        }
    }

    /**
     * Create a web socket scope. Use the IWebSocketScopeListener interface to configure the created scope.
     *
     * @param path
     */
    public void makeScope(String path) {
        log.debug("makeScope: {}", path);
        WebSocketScope wsScope = null;
        if (!scopes.containsKey(path)) {
            // new websocket scope
            wsScope = new WebSocketScope();
            wsScope.setPath(path);
            notifyListeners(WebSocketEvent.SCOPE_CREATED, wsScope);
            addWebSocketScope(wsScope);
            log.debug("Use the IWebSocketScopeListener interface to be notified of new scopes");
        } else {
            log.debug("Scope already exists: {}", path);
        }
    }

    /**
     * Create a web socket scope from a server IScope. Use the IWebSocketScopeListener interface to configure the created scope.
     *
     * @param scope
     */
    public void makeScope(IScope scope) {
        log.debug("makeScope: {}", scope);
        String path = scope.getContextPath();
        WebSocketScope wsScope = null;
        if (!scopes.containsKey(path)) {
            // add the name to the collection (no '/' prefix)
            activeRooms.add(scope.getName());
            // new websocket scope for the server scope
            wsScope = new WebSocketScope();
            wsScope.setPath(path);
            wsScope.setScope(scope);
            notifyListeners(WebSocketEvent.SCOPE_CREATED, wsScope);
            addWebSocketScope(wsScope);
            log.debug("Use the IWebSocketScopeListener interface to be notified of new scopes");
        } else {
            log.debug("Scope already exists: {}", path);
        }
    }

    /**
     * Get the corresponding scope.
     *
     * @param path
     *            scope path
     * @return scope
     */
    public WebSocketScope getScope(String path) {
        log.debug("getScope: {}", path);
        WebSocketScope scope = scopes.get(path);
        // if we dont find a scope, go for default
        if (scope == null) {
            scope = scopes.get("default");
        }
        log.debug("Returning: {}", scope);
        return scope;
    }

    /**
     * Notifies listeners of scope lifecycle events.
     *
     * @param event
     * @param wsScope
     */
    private void notifyListeners(WebSocketEvent event, WebSocketScope wsScope) {
        @SuppressWarnings("unused")
        Future<?> notifier = WebSocketPlugin.submit(() -> {
            scopeListeners.forEach(listener -> {
                switch (event) {
                    case SCOPE_CREATED:
                        listener.scopeCreated(wsScope);
                        break;
                    case SCOPE_ADDED:
                        listener.scopeAdded(wsScope);
                        break;
                    case SCOPE_REMOVED:
                        listener.scopeRemoved(wsScope);
                        break;
                }
            });
        });
    }

    /**
     * Get the corresponding scope, if none exists, make new one.
     *
     * @param conn
     * @return wsScope
     */
    private WebSocketScope getScope(WebSocketConnection conn) {
        if (log.isTraceEnabled()) {
            log.trace("Scopes: {}", scopes);
        }
        log.debug("getScope: {}", conn);
        // ensure that there is a "default" websocket scope
        String path = conn.getPath();
        if (!scopes.containsKey(path)) {
            // check for default scope
            if (!scopes.containsKey("default")) {
                WebSocketScope defaultWSScope = new WebSocketScope();
                defaultWSScope.setPath(path);
                notifyListeners(WebSocketEvent.SCOPE_CREATED, defaultWSScope);
                addWebSocketScope(defaultWSScope);
                //log.debug("Use the IWebSocketScopeListener interface to be notified of new scopes");
            } else {
                path = "default";
            }
        }
        WebSocketScope wsScope = Optional.ofNullable(conn.getScope()).orElse(scopes.get(path));
        log.debug("Returning: {}", wsScope);
        return wsScope;
    }

    /**
     * Stops this manager and the scopes contained within.
     */
    public void stop() {
        for (WebSocketScope scope : scopes.values()) {
            scope.unregister();
        }
    }

    /**
     * Set the application scope for this manager.
     *
     * @param appScope
     * @return true if added and false otherwise
     */
    public boolean setApplication(IScope appScope) {
        log.debug("Application scope: {}", appScope);
        this.appScope = appScope;
        // add the name to the collection (no '/' prefix)
        return activeRooms.add(appScope.getName());
    }

    public void setCopyListeners(boolean copy) {
        this.copyListeners = copy;
    }

    @Override
    public String toString() {
        return String.format("App scope: %s%nActive rooms: %s%nWS scopes: %s%nWS listeners: %s%n", appScope, activeRooms, scopes, scopeListeners);
    }

}
