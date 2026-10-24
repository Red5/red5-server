package org.red5.net.websocket.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.ServletContext;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.red5.net.websocket.WSConstants;
import org.red5.net.websocket.WebSocketPlugin;
import org.red5.net.websocket.WebSocketScope;
import org.red5.net.websocket.WebSocketScopeManager;
import org.red5.server.adapter.MultiThreadedApplicationAdapter;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.ScopeType;
import org.red5.server.plugin.PluginRegistry;
import org.red5.server.scope.Scope;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.web.context.WebApplicationContext;

/**
 * Verifies that WebSocket handshakes are bound to the application scope of the web application that serves them, and that hosts serving the
 * same context path keep separate containers (issue #478).
 */
public class ApplicationBindingTest {

    // unique to this test so the plugin's static container map does not collide with other tests
    private static final String APP = "wsbinding478";

    private static final String PATH = "/" + APP;

    // containers are kept in a static map, so each test uses its own host names
    private static final AtomicInteger RUN = new AtomicInteger();

    private long pingInterval;

    private WebSocketPlugin plugin;

    private String hostA, hostB;

    private IScope appA, appB;

    private WebSocketScopeManager managerA, managerB;

    @Before
    public void setUp() {
        // no pinger threads for these tests
        pingInterval = WebSocketScopeManager.websocketPingInterval;
        WebSocketScopeManager.websocketPingInterval = -1;
        int run = RUN.incrementAndGet();
        hostA = "Catalina/hostA-" + run;
        hostB = "Catalina/hostB-" + run;
        plugin = new WebSocketPlugin();
        PluginRegistry.register(plugin);
        // same-named applications in two global scopes, as two hosts would have
        appA = new Scope(new Scope(null, ScopeType.GLOBAL, "hostA", false), ScopeType.APPLICATION, APP, false);
        appB = new Scope(new Scope(null, ScopeType.GLOBAL, "hostB", false), ScopeType.APPLICATION, APP, false);
        managerA = register(appA);
        managerB = register(appB);
    }

    @After
    public void tearDown() {
        plugin.removeManager(appA);
        plugin.removeManager(appB);
        PluginRegistry.unregister(plugin);
        WebSocketScopeManager.websocketPingInterval = pingInterval;
    }

    @Test
    public void testSameNamedApplicationsHaveSeparateManagers() {
        assertNotNull(managerA);
        assertNotNull(managerB);
        assertNotSame(managerA, managerB);
        assertSame(appA, managerA.getApplication());
        assertSame(appB, managerB.getApplication());
    }

    @Test
    public void testContainersAreKeptPerHost() {
        DefaultWsServerContainer containerA = container(hostA, appA, Map.of());
        DefaultWsServerContainer containerB = container(hostB, appB, Map.of());
        assertNotSame(containerA, containerB);
        assertNotSame(configurator(containerA), configurator(containerB));
        // the same host and context path returns the existing container
        assertSame(containerA, WebSocketPlugin.getWsServerContainerInstance(containerA.getServletContext()));
        assertSame(containerA, WebSocketPlugin.getWsServerContainer(containerA.getServletContext()));
        assertSame(containerB, WebSocketPlugin.getWsServerContainer(containerB.getServletContext()));
    }

    @Test
    public void testHandshakeUsesOwnApplication() {
        DefaultWsServerContainer containerA = container(hostA, appA, Map.of());
        DefaultWsServerContainer containerB = container(hostB, appB, Map.of());
        DefaultServerEndpointConfigurator configuratorA = bind(containerA);
        DefaultServerEndpointConfigurator configuratorB = bind(containerB);
        assertSame(appA, configuratorA.getApplicationScope());
        assertSame(appB, configuratorB.getApplicationScope());
        Map<String, Object> propsA = handshake(containerA, PATH);
        assertSame(managerA, propsA.get(WSConstants.WS_MANAGER));
        assertSame(appA, ((WebSocketScope) propsA.get(WSConstants.WS_SCOPE)).getScope());
        Map<String, Object> propsB = handshake(containerB, PATH + "/");
        assertSame(managerB, propsB.get(WSConstants.WS_MANAGER));
        assertSame(appB, ((WebSocketScope) propsB.get(WSConstants.WS_SCOPE)).getScope());
    }

    @Test
    public void testNoFallbackToSameNamedApplication() {
        DefaultWsServerContainer containerB = container(hostB, appB, Map.of());
        bind(containerB);
        // once application B has no manager, its handshakes must not be served by application A
        plugin.removeManager(appB);
        Map<String, Object> props = handshake(containerB, PATH);
        assertNull(props.get(WSConstants.WS_MANAGER));
        assertNull(props.get(WSConstants.WS_SCOPE));
        // an endpoint that is not bound to any application is not resolved by name either
        DefaultServerEndpointConfigurator unbound = new DefaultServerEndpointConfigurator();
        ServerEndpointConfig sec = ServerEndpointConfig.Builder.create(DefaultWebSocketEndpoint.class, PATH).configurator(unbound).build();
        WsPerSessionServerEndpointConfig perSession = new WsPerSessionServerEndpointConfig(sec);
        unbound.modifyHandshake(perSession, request(PATH), response());
        assertNull(perSession.getUserProperties().get(WSConstants.WS_MANAGER));
    }

    @Test
    public void testOriginSettingsComeFromOwnApplication() {
        // same context path on two hosts, each with its own origin context-params (issue #470)
        DefaultWsServerContainer containerA = container(hostA, appA, Map.of("crossOriginPolicy", "true", "allowedOrigins", "https://a.example.com"));
        DefaultWsServerContainer containerB = container(hostB, appB, Map.of("crossOriginPolicy", "true", "allowedOrigins", " https://b.example.com , b.example.net:5080 "));
        DefaultServerEndpointConfigurator configuratorA = bind(containerA);
        DefaultServerEndpointConfigurator configuratorB = bind(containerB);
        assertSame(appA, configuratorA.getApplicationScope());
        assertSame(appB, configuratorB.getApplicationScope());
        assertTrue(configuratorA.checkOrigin("https://a.example.com"));
        assertFalse(configuratorA.checkOrigin("https://b.example.com"));
        // browsers always send an Origin, so a missing one is accepted by default (issue #489)
        assertTrue(configuratorA.checkOrigin(null));
        assertTrue(configuratorB.checkOrigin("https://b.example.com"));
        assertTrue(configuratorB.checkOrigin("http://b.example.net:5080"));
        assertFalse(configuratorB.checkOrigin("https://a.example.com"));
        // an application without the context-params keeps the plugin defaults: checking on, same-origin only (issue #489)
        IScope appC = new Scope(new Scope(null, ScopeType.GLOBAL, "hostC", false), ScopeType.APPLICATION, APP, false);
        DefaultServerEndpointConfigurator configuratorC = configurator(container(hostA.replace("hostA", "hostC"), appC, Map.of()));
        assertTrue(configuratorC.isCrossOriginPolicy());
        assertFalse(configuratorC.checkOrigin("https://a.example.com", "c.example.com"));
        assertTrue(configuratorC.checkOrigin("https://c.example.com", "c.example.com"));
        assertTrue(configuratorC.checkOrigin(null));
    }

    @Test
    public void testDeprecatedPathLookupIsAmbiguous() {
        // the name-based lookup can only return one of the two applications, which is why handshakes no longer use it
        @SuppressWarnings("deprecation")
        WebSocketScopeManager byName = plugin.getManager(PATH);
        assertTrue(byName == managerA || byName == managerB);
    }

    private WebSocketScopeManager register(IScope appScope) {
        MultiThreadedApplicationAdapter app = new MultiThreadedApplicationAdapter();
        app.setScope(appScope);
        plugin.setApplication(app);
        // as the plugin does for a new application scope
        new WebSocketScope(appScope).register();
        return plugin.getManager(appScope);
    }

    private static DefaultServerEndpointConfigurator configurator(DefaultWsServerContainer container) {
        return (DefaultServerEndpointConfigurator) container.findMapping(PATH).getConfig().getConfigurator();
    }

    private static DefaultServerEndpointConfigurator bind(DefaultWsServerContainer container) {
        DefaultServerEndpointConfigurator configurator = configurator(container);
        configurator.bindApplicationScope(container.getServletContext());
        return configurator;
    }

    private static Map<String, Object> handshake(DefaultWsServerContainer container, String path) {
        ServerEndpointConfig sec = container.findMapping(PATH).getConfig();
        WsPerSessionServerEndpointConfig perSession = new WsPerSessionServerEndpointConfig(sec);
        sec.getConfigurator().modifyHandshake(perSession, request(path), response());
        return perSession.getUserProperties();
    }

    /**
     * Creates the server container for a web application on the given host, with a Spring context holding its web.scope bean, as
     * TomcatLoader sets up.
     */
    static DefaultWsServerContainer container(String virtualServerName, IScope appScope, Map<String, String> initParams) {
        StaticApplicationContext appCtx = new StaticApplicationContext();
        appCtx.getBeanFactory().registerSingleton("web.scope", appScope);
        appCtx.refresh();
        Map<String, Object> attributes = new ConcurrentHashMap<>();
        attributes.put(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, appCtx);
        ServletContext servletContext = servletContext(virtualServerName, PATH, attributes, initParams);
        return (DefaultWsServerContainer) WebSocketPlugin.getWsServerContainerInstance(servletContext);
    }

    private static ServletContext servletContext(String virtualServerName, String contextPath, Map<String, Object> attributes, Map<String, String> initParams) {
        return (ServletContext) Proxy.newProxyInstance(ApplicationBindingTest.class.getClassLoader(), new Class<?>[] { ServletContext.class }, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getContextPath":
                    return contextPath;
                case "getVirtualServerName":
                    return virtualServerName;
                case "getAttribute":
                    return attributes.get(args[0]);
                case "setAttribute":
                    if (args[1] == null) {
                        attributes.remove(args[0]);
                    } else {
                        attributes.put((String) args[0], args[1]);
                    }
                    return null;
                case "removeAttribute":
                    attributes.remove(args[0]);
                    return null;
                case "getAttributeNames":
                    return Collections.enumeration(attributes.keySet());
                case "getInitParameter":
                    return initParams.get(args[0]);
                case "getInitParameterNames":
                    return Collections.enumeration(initParams.keySet());
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return "ServletContext[" + virtualServerName + contextPath + "]";
                default:
                    return defaultValue(method.getReturnType());
            }
        });
    }

    private static HandshakeRequest request(String path) {
        return (HandshakeRequest) Proxy.newProxyInstance(ApplicationBindingTest.class.getClassLoader(), new Class<?>[] { HandshakeRequest.class }, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getRequestURI":
                    return URI.create(path);
                case "getHeaders":
                case "getParameterMap":
                    return Collections.emptyMap();
                case "toString":
                    return "HandshakeRequest[" + path + "]";
                default:
                    return defaultValue(method.getReturnType());
            }
        });
    }

    private static HandshakeResponse response() {
        Map<String, List<String>> headers = new HashMap<>();
        return (HandshakeResponse) Proxy.newProxyInstance(ApplicationBindingTest.class.getClassLoader(), new Class<?>[] { HandshakeResponse.class }, (proxy, method, args) -> {
            if ("getHeaders".equals(method.getName())) {
                return headers;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        } else if (type == int.class) {
            return 0;
        } else if (type == long.class) {
            return 0L;
        }
        return null;
    }

}
