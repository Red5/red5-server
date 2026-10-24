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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
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
import org.red5.server.adapter.StatefulScopeWrappingAdapter;
import org.red5.server.api.listeners.ScopeListenerAdapter;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.ScopeType;
import org.red5.server.plugin.PluginRegistry;
import org.red5.server.plugin.Red5Plugin;
import org.red5.server.util.ScopeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

/**
 * WebSocketPlugin - centralized WebSocket configuration and locator. <br>
 * This plugin will be called by Red5 plugin launcher to associate application components with WebSockets.
 * <p>
 * Each web application gets its own {@link DefaultWsServerContainer} and {@link DefaultServerEndpointConfigurator}, keyed by the
 * application's host and context path, and each application scope gets its own {@link WebSocketScopeManager}, keyed by the scope instance.
 * A handshake is bound to the manager of the application scope that serves the request, never to another application that merely shares
 * its name.
 * <p>
 * Origin checking: the plugin's <code>crossOriginPolicy</code> and <code>allowedOrigins</code> properties are server-wide defaults. A web
 * application overrides them with web.xml context-params (or servlet context attributes) of the same names:
 * <ul>
 * <li><code>crossOriginPolicy</code> - <code>true</code> to check the Origin header of WebSocket handshakes, <code>false</code> to accept
 * any origin. Checking is off by default.</li>
 * <li><code>allowedOrigins</code> - comma-separated origins, such as <code>https://example.com,https://example.com:8443</code>. An entry
 * with a scheme must match the Origin's scheme, host and port exactly, with default ports filled in. An entry without a scheme, such as
 * <code>example.com</code> or <code>example.com:5080</code>, matches that exact host, and port if given, under any scheme. Sub-domains and
 * suffixes never match. <code>*</code> allows any origin.</li>
 * </ul>
 * While checking is enabled a handshake without an Origin header is rejected; while it is disabled such handshakes are accepted. The HTTP
 * principal, session and cookies of the upgrade request are carried into the WebSocket connection, so with checking off a page on any site
 * can open a connection with the user's credentials. Applications that authenticate WebSocket users through the HTTP session, cookies or
 * container login must enable <code>crossOriginPolicy</code> and list their own origins in <code>allowedOrigins</code>.
 *
 * @author Paul Gregoire
 */
public class WebSocketPlugin extends Red5Plugin {

    private static Logger log = LoggerFactory.getLogger(WebSocketPlugin.class);

    /** Constant <code>NAME="WebSocketPlugin"</code> */
    public final static String NAME = "WebSocketPlugin";

    // Shared executor
    private static ExecutorService executor = Executors.newCachedThreadPool();

    // Same origin policy enable/disabled; never read, see setSameOriginPolicy
    private static boolean sameOriginPolicy;

    // Origin checking enable/disabled, the server-wide default (off)
    private static boolean crossOriginPolicy;

    // Allowed origins, the server-wide default
    private static String[] allowedOrigins = new String[] { "*" };

    // holds application scopes, by instance, and their associated websocket scope manager
    private static ConcurrentMap<ScopeKey, WebSocketScopeManager> managerMap = new ConcurrentHashMap<>();

    // holds DefaultWsServerContainer instances keyed by their host identity and servlet context path
    private static ConcurrentMap<String, DefaultWsServerContainer> containerMap = new ConcurrentHashMap<>();

    private ScopeListenerAdapter scopeListener;

    /**
     * <p>Constructor for WebSocketPlugin.</p>
     */
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
        containerMap.values().forEach(WebSocketPlugin::destroyContainer);
        containerMap.clear();
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
            WebSocketScopeManager manager = getManager(scope);
            if (manager == null) {
                // get the application adapter
                MultiThreadedApplicationAdapter app = (MultiThreadedApplicationAdapter) scope.getHandler();
                log.info("Creating WebSocketScopeManager for {}", app);
                // set the application in the plugin to create a websocket scope manager for it
                setApplication(app);
                // get the new manager
                manager = getManager(scope);
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
            WebSocketScopeManager manager = getManager(appScope);
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
     * @param task a {@link java.lang.Runnable} object
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
     * @param path a {@link java.lang.String} object
     * @return IScope
     * @deprecated an application name does not identify an application: the same name may be deployed on several hosts or global scopes,
     *             and this returns whichever one is found first. Use the scope bound to the request instead, see
     *             {@link #resolveApplicationScope(ServletContext)} and {@link DefaultServerEndpointConfigurator#getApplicationScope()}.
     */
    @Deprecated
    public IScope getApplicationScope(String path) {
        // set a reference to the application scope so we can create room scopes
        String applicationScopeName = path.split("\\/")[1];
        log.debug("Looking for application scope: {}", applicationScopeName);
        return managerMap.keySet().stream().map(ScopeKey::scope).filter(scope -> (ScopeUtils.isApp(scope) && scope.getName().equals(applicationScopeName))).findFirst().orElse(null);
    }

    /**
     * Returns the application scope of a web application: its <code>web.scope</code> bean, or else the scope of its
     * <code>web.handler</code> bean, from the Spring application context stored in the servlet context. Returns null when that context
     * has not been created yet or defines neither bean.
     *
     * @param servletContext servlet context of the web application
     * @return application scope or null
     */
    public static IScope resolveApplicationScope(ServletContext servletContext) {
        if (servletContext == null) {
            return null;
        }
        Object attr = servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
        if (attr instanceof ApplicationContext appCtx) {
            if (appCtx.containsBean("web.scope")) {
                Object webScope = appCtx.getBean("web.scope");
                if (webScope instanceof IScope scope) {
                    return scope;
                }
            }
            if (appCtx.containsBean("web.handler")) {
                // use one level higher than MultiThreadedApplicationAdapter since we only need the scope
                Object handler = appCtx.getBean("web.handler");
                if (handler instanceof StatefulScopeWrappingAdapter app) {
                    return app.getScope();
                }
            }
        }
        return null;
    }

    /**
     * Returns a WebSocketScopeManager for a given application scope. The scope must be the registered application scope instance itself;
     * another scope that is merely equal to it, such as a same-named application in another global scope, does not match.
     *
     * @param scope a {@link org.red5.server.api.scope.IScope} object
     * @return WebSocketScopeManager if registered for the given scope and null otherwise
     */
    public WebSocketScopeManager getManager(IScope scope) {
        if (scope == null) {
            return null;
        }
        return managerMap.get(new ScopeKey(scope));
    }

    /**
     * Returns a WebSocketScopeManager for a given path.
     *
     * @param path a {@link java.lang.String} object
     * @return WebSocketScopeManager if registered for the given path and null otherwise
     * @deprecated an application name does not identify an application: the same name may be deployed on several hosts or global scopes,
     *             and this returns whichever manager is found first. Use {@link #getManager(IScope)} with the application scope bound to
     *             the request, or {@link WebSocketConnection#getScope()} for an open connection.
     */
    @Deprecated
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
            for (Entry<ScopeKey, WebSocketScopeManager> entry : managerMap.entrySet()) {
                IScope appScope = entry.getKey().scope();
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
        if (scope == null) {
            return null;
        }
        return managerMap.remove(new ScopeKey(scope));
    }

    /**
     * Returns a DefaultWsServerContainer for a given path.
     *
     * @param path a {@link java.lang.String} object
     * @return DefaultWsServerContainer
     * @deprecated containers are kept per host and context path, so a context path alone is ambiguous when several hosts serve it. This
     *             accepts a full container key (host identity followed by the context path) or a context path served by a single host, and
     *             returns null when several hosts serve the context path. Use {@link #getWsServerContainer(ServletContext)}.
     */
    @Deprecated
    public DefaultWsServerContainer getWsServerContainer(String path) {
        log.debug("getWsServerContainer: {}", path);
        DefaultWsServerContainer container = containerMap.get(path);
        if (container == null) {
            Set<DefaultWsServerContainer> matches = containerMap.values().stream().filter(c -> path.equals(normalizeContextPath(c.getServletContext().getContextPath()))).collect(Collectors.toSet());
            if (matches.size() == 1) {
                container = matches.iterator().next();
            } else if (matches.size() > 1) {
                log.warn("Context path {} is served by {} hosts, look the container up by its servlet context instead", path, matches.size());
            }
        }
        return container;
    }

    /**
     * Returns the DefaultWsServerContainer of a web application, if one has been created.
     *
     * @param servletContext servlet context of the web application
     * @return DefaultWsServerContainer or null
     */
    public static DefaultWsServerContainer getWsServerContainer(ServletContext servletContext) {
        return containerMap.get(containerKey(servletContext));
    }

    /**
     * Removes the server container of a web application that is being destroyed and shuts it down, closing its open sessions, so a
     * redeployed context gets a new container with its own settings. A container that was already replaced by one for a newer servlet
     * context under the same key is left in place.
     *
     * @param servletContext servlet context of the web application being destroyed
     * @return the removed container, or null if this servlet context had none
     */
    public static DefaultWsServerContainer removeWsServerContainer(ServletContext servletContext) {
        String key = containerKey(servletContext);
        DefaultWsServerContainer container = containerMap.get(key);
        if (container != null && container.getServletContext() == servletContext && containerMap.remove(key, container)) {
            log.debug("Removing websocket server container: {}", key);
            if (servletContext.getAttribute(Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE) == container) {
                servletContext.removeAttribute(Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE);
            }
            destroyContainer(container);
            return container;
        }
        return null;
    }

    private static void destroyContainer(DefaultWsServerContainer container) {
        try {
            container.destroy();
        } catch (Exception e) {
            log.warn("Exception destroying websocket server container", e);
        }
    }

    /**
     * Returns the key of a web application's server container: its host identity followed by its context path, or the context path alone
     * when no host identity is available. The host identity is the servlet context's virtual server name, which Tomcat reports as
     * engine/host and which, unlike the red5.host.id attribute, is already available when the container is created; red5.host.id is the
     * fallback.
     *
     * @param servletContext servlet context of the web application
     * @return container key
     */
    static String containerKey(ServletContext servletContext) {
        String path = normalizeContextPath(servletContext.getContextPath());
        String hostId = null;
        try {
            hostId = servletContext.getVirtualServerName();
        } catch (UnsupportedOperationException e) {
            log.trace("Virtual server name is not available", e);
        }
        if (StringUtils.isBlank(hostId)) {
            Object attr = servletContext.getAttribute("red5.host.id");
            hostId = attr != null ? attr.toString() : null;
        }
        return StringUtils.isBlank(hostId) ? path : hostId + path;
    }

    private static String normalizeContextPath(String path) {
        // handle root
        return (path == null || path.isEmpty()) ? "/" : path;
    }

    /** {@inheritDoc} */
    @Override
    public void setApplication(MultiThreadedApplicationAdapter application) {
        log.info("WebSocketPlugin application: {}", application);
        // get the app scope
        final IScope appScope = application.getScope();
        // atomically create and initialize manager to prevent race condition
        // where getManager() could return a manager before setApplication() is called
        managerMap.computeIfAbsent(new ScopeKey(appScope), key -> {
            WebSocketScopeManager manager = new WebSocketScopeManager();
            manager.setApplication(key.scope());
            return manager;
        });
        super.setApplication(application);
    }

    /**
     * <p>isSameOriginPolicy.</p>
     *
     * @return a boolean
     * @deprecated no origin check reads this setting; see {@link #setSameOriginPolicy(boolean)}
     */
    @Deprecated
    public static boolean isSameOriginPolicy() {
        return sameOriginPolicy;
    }

    /**
     * <p>Setter for the field <code>sameOriginPolicy</code>.</p>
     *
     * @param sameOriginPolicy a boolean
     * @deprecated this setting has never been read and has no effect. Origin checking is controlled by
     *             {@link #setCrossOriginPolicy(boolean)} and {@link #setAllowedOrigins(String[])}, or per application by the
     *             <code>crossOriginPolicy</code> and <code>allowedOrigins</code> context-params; to accept only the application's own site,
     *             enable crossOriginPolicy and list that origin in allowedOrigins. The setter remains so existing Spring configurations
     *             still load.
     */
    @Deprecated
    public void setSameOriginPolicy(boolean sameOriginPolicy) {
        WebSocketPlugin.sameOriginPolicy = sameOriginPolicy;
    }

    /**
     * Returns a per-application setting from the servlet context init-parameters (web.xml context-param), falling back to a servlet
     * context attribute of the same name.
     *
     * @param servletContext servlet context
     * @param name setting name
     * @return value or null when not set
     */
    private static String contextSetting(ServletContext servletContext, String name) {
        String value = servletContext.getInitParameter(name);
        if (value == null) {
            Object attr = servletContext.getAttribute(name);
            if (attr != null) {
                value = attr.toString();
            }
        }
        return value;
    }

    /**
     * Returns the server-wide default for Origin checking.
     *
     * @return a boolean
     */
    public static boolean isCrossOriginPolicy() {
        return crossOriginPolicy;
    }

    /**
     * Sets the server-wide default for Origin checking, used by web applications that do not set the <code>crossOriginPolicy</code>
     * context-param. While enabled, handshakes whose Origin header is missing or not allowed are rejected with 403. Off by default.
     *
     * @param crossOriginPolicy a boolean
     */
    public void setCrossOriginPolicy(boolean crossOriginPolicy) {
        WebSocketPlugin.crossOriginPolicy = crossOriginPolicy;
    }

    /**
     * Returns the server-wide default allowed origins.
     *
     * @return an array of {@link java.lang.String} objects
     */
    public static String[] getAllowedOrigins() {
        return allowedOrigins;
    }

    /**
     * Sets the server-wide default allowed origins, used by web applications that do not set the <code>allowedOrigins</code>
     * context-param; see the class description for the matching rules. Defaults to <code>*</code>, any origin.
     *
     * @param allowedOrigins an array of {@link java.lang.String} objects
     */
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
     * @param servletContext a {@link jakarta.servlet.ServletContext} object
     * @return WsServerContainer
     */
    public static ServerContainer getWsServerContainerInstance(ServletContext servletContext) {
        String path = normalizeContextPath(servletContext.getContextPath());
        // key by host and context path so hosts serving the same context path keep their own container and origin settings
        String key = containerKey(servletContext);
        log.info("getWsServerContainerInstance: {} key: {}", path, key);
        DefaultWsServerContainer container;
        if (containerMap.containsKey(key)) {
            container = containerMap.get(key);
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
            // per-application override from a context-param or servlet context attribute
            DefaultServerEndpointConfigurator endpointConfigurator = (DefaultServerEndpointConfigurator) configurator;
            String crossOriginSetting = contextSetting(servletContext, "crossOriginPolicy");
            if (crossOriginSetting != null) {
                endpointConfigurator.setCrossOriginPolicy(Boolean.parseBoolean(crossOriginSetting.trim()));
            }
            String allowedOriginsSetting = contextSetting(servletContext, "allowedOrigins");
            if (allowedOriginsSetting != null) {
                endpointConfigurator.setAllowedOrigins(Stream.of(allowedOriginsSetting.split(",")).map(String::trim).filter(o -> !o.isEmpty()).toArray(String[]::new));
            }
            if (!endpointConfigurator.isCrossOriginPolicy()) {
                log.info("WebSocket Origin checking is disabled for {}; browsers on any site can open authenticated connections, set the crossOriginPolicy and allowedOrigins context-params to restrict them", path);
            } else if (Arrays.asList(endpointConfigurator.getAllowedOrigins()).contains("*")) {
                log.warn("WebSocket Origin checking for {} allows any origin (*), which is unsafe when HTTP sessions or credentials are used", path);
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
            containerMap.put(key, container);
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

    /**
     * Map key that compares application scopes by instance. Scope equality is derived from the name, type, path and depth hash codes,
     * which is not strict enough to keep two applications apart.
     */
    private record ScopeKey(IScope scope) {

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ScopeKey other && other.scope == scope;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(scope);
        }

    }

}
