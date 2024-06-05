/*
 * RED5 Open Source Flash Server - https://github.com/red5 Copyright 2006-2018 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.net.websocket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpointConfig;
import jakarta.websocket.server.ServerEndpointConfig.Configurator;

import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.websocket.server.Constants;
import org.red5.net.websocket.listener.IWebSocketDataListener;
import org.red5.net.websocket.server.DefaultServerEndpointConfigurator;
import org.red5.net.websocket.server.DefaultWsServerContainer;
import org.red5.server.Server;
import org.red5.server.adapter.MultiThreadedApplicationAdapter;
import org.red5.server.api.listeners.ScopeListenerAdapter;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.ScopeType;
import org.red5.server.plugin.PluginRegistry;
import org.red5.server.plugin.Red5Plugin;
import org.red5.server.util.ScopeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WebSocketPlugin - centralized WebSocket configuration and locator. <br>
 * This plugin will be called by Red5 plugin launcher to associate application components with WebSockets.
 *
 * @author Paul Gregoire
 */
public class WebSocketPlugin extends Red5Plugin {

    private static Logger log = LoggerFactory.getLogger(WebSocketPlugin.class);

    public final static String NAME = "WebSocketPlugin";

    // Shared executor
    private static ExecutorService executor = Executors.newCachedThreadPool();

    // Same origin policy enable/disabled
    private static boolean sameOriginPolicy;

    // Cross-origin policy enable/disabled
    private static boolean crossOriginPolicy;

    // Cross-origin names
    private static String[] allowedOrigins = new String[] { "*" };

    // holds application scopes and their associated websocket scope manager
    private static ConcurrentMap<IScope, WebSocketScopeManager> managerMap = new ConcurrentHashMap<>();

    // holds DefaultWsServerContainer instances keyed by their servlet context path
    private static ConcurrentMap<String, DefaultWsServerContainer> containerMap = new ConcurrentHashMap<>();

    private ScopeListenerAdapter scopeListener;

    public WebSocketPlugin() {
        log.trace("WebSocketPlugin ctor");
    }

    /** {@inheritDoc} */
    @Override
    public void doStart() throws Exception {
        log.trace("WebSocketPlugin start");
        // add scope listener to allow creation of websocket scopes
        scopeListener = new ScopeListenerAdapter() {

            @Override
            public void notifyScopeCreated(IScope scope) {
                log.debug("Scope created: {}", scope);
                // configure the websocket scopes
                if (scope.getType() == ScopeType.APPLICATION) {
                    configureApplicationScopeWebSocket(scope);
                } else if (scope.getType() == ScopeType.ROOM) {
                    configureRoomScopeWebSocket(scope);
                }
            }

            @Override
            public void notifyScopeRemoved(IScope scope) {
                log.trace("Scope removed: {}", scope);
                if (scope.getType() == ScopeType.APPLICATION) {
                    // get and remove it at the same time, if it exists at all
                    WebSocketScopeManager manager = removeManager(scope);
                    if (manager != null) {
                        manager.stop();
                    }
                }
            }

        };
        log.info("Setting server scope listener");
        server.addListener(scopeListener);
        // process any apps/scopes that have already started before scope listener was added
        server.getGlobalScopes().forEachRemaining(gscope -> {
            log.info("Got global scope: {}", gscope.getName());
            // setup stream aware handlers
            gscope.getBasicScopeNames(ScopeType.APPLICATION).forEach(appName -> {
                log.debug("Setting up websocket for {}", appName);
                IScope appScope = (IScope) gscope.getBasicScope(ScopeType.APPLICATION, appName);
                log.debug("Configuring application scope: {}", appScope);
                configureApplicationScopeWebSocket(appScope);
            });
        });
    }

    /** {@inheritDoc} */
    @Override
    public void doStop() throws Exception {
        log.trace("WebSocketPlugin stop");
        PluginRegistry.unregister(this);
        managerMap.entrySet().forEach(entry -> {
            entry.getValue().stop();
        });
        managerMap.clear();
        executor.shutdownNow();
    }

    /**
     * Configures a websocket scope for a given application scope.
     *
     * @param scope
     *            Server application scope
     */
    private void configureApplicationScopeWebSocket(IScope scope) {
        // check to see if its already configured
        if (scope.hasAttribute(WSConstants.WS_SCOPE)) {
            log.debug("Application scope already configured: {}", scope);
        } else {
            log.debug("Configuring application scope: {}", scope);
            // get the websocket scope manager for the red5 scope
            WebSocketScopeManager manager = managerMap.get(scope);
            if (manager == null) {
                // get the application adapter
                MultiThreadedApplicationAdapter app = (MultiThreadedApplicationAdapter) scope.getHandler();
                log.info("Creating WebSocketScopeManager for {}", app);
                // set the application in the plugin to create a websocket scope manager for it
                setApplication(app);
                // get the new manager
                manager = managerMap.get(scope);
            }
            // create a websocket scope for the application
            WebSocketScope wsScope = new WebSocketScope(scope);
            // register the ws scope
            wsScope.register();
        }
    }

    /**
     * Configures a websocket scope for a given room scope.
     *
     * @param scope
     *            Server room scope
     */
    private void configureRoomScopeWebSocket(IScope scope) {
        // check to see if its already configured
        if (scope.hasAttribute(WSConstants.WS_SCOPE)) {
            log.debug("Room scope already configured: {}", scope);
        } else {
            log.debug("Configuring room scope: {}", scope);
            // get the application scope
            IScope appScope = ScopeUtils.findApplication(scope);
            // create a websocket scope for the scope
            String path = scope.getContextPath();
            log.debug("Room path: {}", path);
            WebSocketScopeManager manager = managerMap.get(appScope);
            if (manager != null) {
                WebSocketScope wsScope = manager.getScope(path);
                if (wsScope == null) {
                    manager.makeScope(scope);
                    wsScope = manager.getScope(path);
                }
                // set the scope
                wsScope.setScope(scope);
                // copy the listeners to the child
                WebSocketScope wsScopeParent = manager.getScope(appScope.getContextPath());
                for (IWebSocketDataListener listener : wsScopeParent.getListeners()) {
                    log.debug("Adding listener: {}", listener);
                    wsScope.addListener(listener);
                }
            }
        }
    }

    /**
     * Submit a task for execution.
     *
     * @param task
     * @return Future
     */
    public static Future<?> submit(Runnable task) {
        return executor.submit(task);
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return NAME;
    }

    /** {@inheritDoc} */
    @Override
    public Server getServer() {
        return super.getServer();
    }

    /**
     * Returns the application scope for a given path.
     *
     * @param path
     * @return IScope
     */
    public IScope getApplicationScope(String path) {
        // set a reference to the application scope so we can create room scopes
        String applicationScopeName = path.split("\\/")[1];
        log.debug("Looking for application scope: {}", applicationScopeName);
        return managerMap.keySet().stream().filter(scope -> (ScopeUtils.isApp(scope) && scope.getName().equals(applicationScopeName))).findFirst().get();
    }

    /**
     * Returns a WebSocketScopeManager for a given scope.
     *
     * @param scope
     * @return WebSocketScopeManager if registered for the given scope and null otherwise
     */
    public WebSocketScopeManager getManager(IScope scope) {
        return managerMap.get(scope);
    }

    /**
     * Returns a WebSocketScopeManager for a given path.
     *
     * @param path
     * @return WebSocketScopeManager if registered for the given path and null otherwise
     */
    public WebSocketScopeManager getManager(String path) {
        log.debug("getManager: {}", path);
        // determine what the app scope name is
        String[] parts = path.split("\\/");
        if (log.isTraceEnabled()) {
            log.trace("Path parts: {}", Arrays.toString(parts));
        }
        if (parts.length > 1) {
            // skip default in a path if it exists in slot #1
            String name = !"default".equals(parts[1]) ? parts[1] : ((parts.length >= 3) ? parts[2] : parts[1]);
            if (log.isTraceEnabled()) {
                log.trace("Managers: {}", managerMap.entrySet());
            }
            for (Entry<IScope, WebSocketScopeManager> entry : managerMap.entrySet()) {
                IScope appScope = entry.getKey();
                if (appScope.getName().equals(name)) {
                    log.debug("Application scope name matches path: {}", name);
                    return entry.getValue();
                } else if (log.isTraceEnabled()) {
                    log.trace("Application scope name: {} didnt match path: {}", appScope.getName(), name);
                }
            }
        }
        return null;
    }

    /**
     * Removes and returns the WebSocketScopeManager for the given scope if it exists and returns null if it does not.
     *
     * @param scope
     *            Scope for which the manager is registered
     * @return WebSocketScopeManager if registered for the given path and null otherwise
     */
    public WebSocketScopeManager removeManager(IScope scope) {
        return managerMap.remove(scope);
    }

    /**
     * Returns a DefaultWsServerContainer for a given path.
     *
     * @param path
     * @return DefaultWsServerContainer
     */
    public DefaultWsServerContainer getWsServerContainer(String path) {
        log.debug("getWsServerContainer: {}", path);
        return containerMap.get(path);
    }

    /** {@inheritDoc} */
    @Override
    public void setApplication(MultiThreadedApplicationAdapter application) {
        log.info("WebSocketPlugin application: {}", application);
        // get the app scope
        final IScope appScope = application.getScope();
        // put if not already there
        managerMap.putIfAbsent(appScope, new WebSocketScopeManager());
        // add the app scope to the manager
        managerMap.get(appScope).setApplication(appScope);
        super.setApplication(application);
    }

    public static boolean isSameOriginPolicy() {
        return sameOriginPolicy;
    }

    public void setSameOriginPolicy(boolean sameOriginPolicy) {
        WebSocketPlugin.sameOriginPolicy = sameOriginPolicy;
    }

    public static boolean isCrossOriginPolicy() {
        return crossOriginPolicy;
    }

    public void setCrossOriginPolicy(boolean crossOriginPolicy) {
        WebSocketPlugin.crossOriginPolicy = crossOriginPolicy;
    }

    public static String[] getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String[] allowedOrigins) {
        WebSocketPlugin.allowedOrigins = allowedOrigins;
        log.info("allowedOrigins: {}", Arrays.toString(WebSocketPlugin.allowedOrigins));
    }

    /**
     * Returns an new instance of the configurator.
     *
     * @return configurator
     */
    public static Configurator getWsConfiguratorInstance() {
        DefaultServerEndpointConfigurator configurator = new DefaultServerEndpointConfigurator();
        return configurator;
    }

    /**
     * Returns a new instance of WsServerContainer if one does not already exist.
     *
     * @param servletContext
     * @return WsServerContainer
     */
    public static ServerContainer getWsServerContainerInstance(ServletContext servletContext) {
        String path = servletContext.getContextPath();
        // handle root
        if (path.length() == 0) {
            path = "/";
        }
        log.info("getWsServerContainerInstance: {}", path);
        DefaultWsServerContainer container;
        if (containerMap.containsKey(path)) {
            container = containerMap.get(path);
        } else {
            // instance a server container for WS
            container = new DefaultWsServerContainer(servletContext);
            if (log.isDebugEnabled()) {
                log.debug("Attributes: {} params: {}", Collections.list(servletContext.getAttributeNames()), Collections.list(servletContext.getInitParameterNames()));
            }
            // get a configurator instance
            ServerEndpointConfig.Configurator configurator = (ServerEndpointConfig.Configurator) WebSocketPlugin.getWsConfiguratorInstance();
            // check for sub protocols
            log.debug("Checking for subprotocols");
            List<String> subProtocols = new ArrayList<>();
            Optional<Object> subProtocolsAttr = Optional.ofNullable(servletContext.getInitParameter("subProtocols"));
            if (subProtocolsAttr.isPresent()) {
                String attr = (String) subProtocolsAttr.get();
                log.debug("Subprotocols: {}", attr);
                if (StringUtils.isNotBlank(attr)) {
                    if (attr.contains(",")) {
                        // split them up
                        Stream.of(attr.split(",")).forEach(entry -> {
                            subProtocols.add(entry);
                        });
                    } else {
                        subProtocols.add(attr);
                    }
                }
            } else {
                // default to allowing any subprotocol
                subProtocols.add("*");
            }
            log.debug("Checking for CORS");
            // check for allowed origins override in this servlet context
            Optional<Object> crossOpt = Optional.ofNullable(servletContext.getAttribute("crossOriginPolicy"));
            if (crossOpt.isPresent() && Boolean.valueOf((String) crossOpt.get())) {
                Optional<String> opt = Optional.ofNullable((String) servletContext.getAttribute("allowedOrigins"));
                if (opt.isPresent()) {
                    ((DefaultServerEndpointConfigurator) configurator).setAllowedOrigins(opt.get().split(","));
                }
            }
            log.debug("Checking for endpoint override");
            // check for endpoint override and use default if not configured
            String wsEndpointClass = Optional.ofNullable((String) servletContext.getAttribute("wsEndpointClass")).orElse("org.red5.net.websocket.server.DefaultWebSocketEndpoint");
            try {
                // locate the endpoint class
                Class<?> endpointClass = Class.forName(wsEndpointClass);
                log.debug("startWebSocket - endpointPath: {} endpointClass: {}", path, wsEndpointClass);
                // build an endpoint config
                ServerEndpointConfig serverEndpointConfig = ServerEndpointConfig.Builder.create(endpointClass, path).configurator(configurator).subprotocols(subProtocols).build();
                // set the endpoint on the server container
                container.addEndpoint(serverEndpointConfig);
            } catch (Throwable t) {
                log.warn("WebSocket endpoint setup exception", t);
            }
            // store container for lookup
            containerMap.put(path, container);
            // add session listener
            servletContext.addListener(new HttpSessionListener() {

                @Override
                public void sessionCreated(HttpSessionEvent se) {
                    log.debug("sessionCreated: {}", se.getSession().getId());
                    ServletContext sc = se.getSession().getServletContext();
                    // Don't trigger WebSocket initialization if a WebSocket Server Container is already present
                    if (sc.getAttribute(Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE) == null) {
                        // grab the container using the servlet context for lookup
                        DefaultWsServerContainer serverContainer = (DefaultWsServerContainer) WebSocketPlugin.getWsServerContainerInstance(sc);
                        // set the container to the context for lookup
                        sc.setAttribute(Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE, serverContainer);
                    }
                }

                @Override
                public void sessionDestroyed(HttpSessionEvent se) {
                    log.debug("sessionDestroyed: {}", se);
                    container.closeAuthenticatedSession(se.getSession().getId());
                }

            });
        }
        // set the container to the context for lookup
        servletContext.setAttribute(Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE, container);
        return container;
    }

}
