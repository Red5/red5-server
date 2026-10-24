package org.red5.net.websocket.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;

import org.junit.Before;
import org.junit.Test;
import org.red5.net.websocket.WebSocketPlugin;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.ScopeType;
import org.red5.server.scope.Scope;

/**
 * Verifies that a web application's WebSocket server container is removed when its context is destroyed, so a redeployed context gets a
 * new container with its own settings (issue #493).
 */
public class ContainerLifecycleTest {

    // containers are kept in a static map, so each test uses its own host name
    private static final AtomicInteger RUN = new AtomicInteger();

    private String host;

    private IScope app;

    @Before
    public void setUp() {
        host = "Catalina/lifecycle-" + RUN.incrementAndGet();
        app = new Scope(new Scope(null, ScopeType.GLOBAL, "lifecycle", false), ScopeType.APPLICATION, "wsbinding478", false);
    }

    @Test
    public void testContextDestroyedRemovesContainer() {
        DefaultWsServerContainer container = ApplicationBindingTest.container(host, app, Map.of());
        ServletContext ctx = container.getServletContext();
        assertSame(container, WebSocketPlugin.getWsServerContainer(ctx));
        new WsContextListener().contextDestroyed(new ServletContextEvent(ctx));
        assertNull("the destroyed context's container must be removed", WebSocketPlugin.getWsServerContainer(ctx));
    }

    @Test
    public void testFilterDestroyRemovesContainer() throws Exception {
        DefaultWsServerContainer container = ApplicationBindingTest.container(host, app, Map.of());
        ServletContext ctx = container.getServletContext();
        WsFilter filter = new WsFilter();
        filter.init(filterConfig(ctx));
        filter.destroy();
        assertNull("the destroyed filter's container must be removed", WebSocketPlugin.getWsServerContainer(ctx));
    }

    @Test
    public void testRedeployGetsNewContainerWithNewSettings() {
        DefaultWsServerContainer first = ApplicationBindingTest.container(host, app, Map.of("crossOriginPolicy", "true", "allowedOrigins", "https://old.example.com"));
        new WsContextListener().contextDestroyed(new ServletContextEvent(first.getServletContext()));
        // the same host and context path deployed again, with changed settings
        DefaultWsServerContainer second = ApplicationBindingTest.container(host, app, Map.of("crossOriginPolicy", "true", "allowedOrigins", "https://new.example.com"));
        assertNotSame("a redeployed context must not reuse the destroyed context's container", first, second);
        assertSame(second, WebSocketPlugin.getWsServerContainer(second.getServletContext()));
        DefaultServerEndpointConfigurator configurator = configurator(second);
        assertTrue(configurator.checkOrigin("https://new.example.com"));
        assertFalse("the destroyed context's settings must not carry over", configurator.checkOrigin("https://old.example.com"));
    }

    @Test
    public void testLateDestroyLeavesNewerContainer() {
        DefaultWsServerContainer first = ApplicationBindingTest.container(host, app, Map.of());
        ServletContext firstCtx = first.getServletContext();
        assertNotNull(WebSocketPlugin.removeWsServerContainer(firstCtx));
        DefaultWsServerContainer second = ApplicationBindingTest.container(host, app, Map.of());
        // a second destroy notification for the old context, as both the filter and the listener send one
        assertNull(WebSocketPlugin.removeWsServerContainer(firstCtx));
        assertSame("the newer context's container must stay registered", second, WebSocketPlugin.getWsServerContainer(second.getServletContext()));
    }

    private static DefaultServerEndpointConfigurator configurator(DefaultWsServerContainer container) {
        return (DefaultServerEndpointConfigurator) container.findMapping("/wsbinding478").getConfig().getConfigurator();
    }

    private static FilterConfig filterConfig(ServletContext ctx) {
        return (FilterConfig) Proxy.newProxyInstance(ContainerLifecycleTest.class.getClassLoader(), new Class<?>[] { FilterConfig.class }, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getServletContext":
                    return ctx;
                case "getInitParameterNames":
                    return Collections.emptyEnumeration();
                default:
                    return null;
            }
        });
    }

}
