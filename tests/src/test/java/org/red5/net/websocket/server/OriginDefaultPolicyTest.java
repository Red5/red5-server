package org.red5.net.websocket.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.websocket.server.ServerEndpointConfig;

import org.junit.Test;
import org.red5.net.websocket.WebSocketPlugin;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.ScopeType;
import org.red5.server.scope.Scope;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Verifies that WebSocket Origin checking is on by default and accepts only same-origin handshakes unless other origins are allowed
 * (issue #489).
 */
public class OriginDefaultPolicyTest {

    private static final String HOST = "red5.example.com:5080";

    // containers are kept in a static map, so each test uses its own host name
    private static final AtomicInteger RUN = new AtomicInteger();

    @Test
    public void testPluginDefaults() {
        assertTrue("origin checking must be on by default", WebSocketPlugin.isCrossOriginPolicy());
        assertEquals("no other origin is allowed by default", 0, WebSocketPlugin.getAllowedOrigins().length);
        assertTrue(WebSocketPlugin.isAllowMissingOrigin());
    }

    @Test
    public void testDefaultAcceptsOnlySameOrigin() {
        DefaultServerEndpointConfigurator configurator = new DefaultServerEndpointConfigurator();
        assertTrue(configurator.isCrossOriginPolicy());
        assertTrue(configurator.checkOrigin("http://red5.example.com:5080", HOST));
        assertFalse(configurator.checkOrigin("https://evil.example.net", HOST));
        assertFalse(configurator.checkOrigin("http://red5.example.com:8080", HOST));
        assertFalse(configurator.checkOrigin("null", HOST));
        // without the request's host only allowed origins are known
        assertFalse(configurator.checkOrigin("http://red5.example.com:5080"));
    }

    @Test
    public void testSameOriginComparesHostAndPort() {
        // a host without a port means the default port of the origin's scheme
        assertTrue(DefaultServerEndpointConfigurator.isSameOrigin("https://example.com", "example.com"));
        assertTrue(DefaultServerEndpointConfigurator.isSameOrigin("http://example.com", "example.com"));
        assertTrue(DefaultServerEndpointConfigurator.isSameOrigin("https://example.com", "example.com:443"));
        assertFalse(DefaultServerEndpointConfigurator.isSameOrigin("https://example.com", "example.com:80"));
        assertFalse(DefaultServerEndpointConfigurator.isSameOrigin("https://example.com", "example.com:5080"));
        assertTrue(DefaultServerEndpointConfigurator.isSameOrigin("http://example.com:5080", "example.com:5080"));
        assertTrue(DefaultServerEndpointConfigurator.isSameOrigin("http://EXAMPLE.com:5080", "example.COM:5080"));
        assertTrue(DefaultServerEndpointConfigurator.isSameOrigin("http://[::1]:5080", "[::1]:5080"));
        // other sites, look-alikes and malformed values never match
        assertFalse(DefaultServerEndpointConfigurator.isSameOrigin("https://evil.example.net", "example.com"));
        assertFalse(DefaultServerEndpointConfigurator.isSameOrigin("https://example.com.evil.net", "example.com"));
        assertFalse(DefaultServerEndpointConfigurator.isSameOrigin("https://sub.example.com", "example.com"));
        assertFalse(DefaultServerEndpointConfigurator.isSameOrigin("null", "example.com"));
        assertFalse(DefaultServerEndpointConfigurator.isSameOrigin("https://example.com", "example.com/path"));
        assertFalse(DefaultServerEndpointConfigurator.isSameOrigin("https://example.com", "user@example.com"));
        assertFalse(DefaultServerEndpointConfigurator.isSameOrigin("https://example.com", ""));
        assertFalse(DefaultServerEndpointConfigurator.isSameOrigin(null, "example.com"));
    }

    @Test
    public void testRequestHostPrefersForwardedHost() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/live");
        assertNull(DefaultServerEndpointConfigurator.requestHost(request));
        request.addHeader("Host", "10.0.0.5:5080");
        assertEquals("10.0.0.5:5080", DefaultServerEndpointConfigurator.requestHost(request));
        // a reverse proxy passes the host the browser connected to
        request.addHeader("X-Forwarded-Host", "red5.example.com, proxy.internal");
        assertEquals("red5.example.com", DefaultServerEndpointConfigurator.requestHost(request));
    }

    @Test
    public void testMissingOrigin() {
        DefaultServerEndpointConfigurator configurator = new DefaultServerEndpointConfigurator();
        // browsers always send an Origin, so a handshake without one comes from a non-browser client
        assertTrue(configurator.checkOrigin(null, HOST));
        configurator.setAllowMissingOrigin(false);
        assertFalse(configurator.checkOrigin(null, HOST));
    }

    @Test
    public void testAllowedOriginsAndWildcard() {
        DefaultServerEndpointConfigurator configurator = new DefaultServerEndpointConfigurator();
        configurator.setAllowedOrigins(new String[] { "https://app.example.com" });
        assertTrue(configurator.checkOrigin("https://app.example.com", HOST));
        // the request's own origin is still accepted
        assertTrue(configurator.checkOrigin("http://red5.example.com:5080", HOST));
        assertFalse(configurator.checkOrigin("https://evil.example.net", HOST));
        configurator.setAllowedOrigins(new String[] { "*" });
        assertTrue(configurator.checkOrigin("https://evil.example.net", HOST));
    }

    @Test
    public void testDisabledAcceptsAll() {
        DefaultServerEndpointConfigurator configurator = new DefaultServerEndpointConfigurator();
        configurator.setCrossOriginPolicy(false);
        assertTrue(configurator.checkOrigin("https://evil.example.net", HOST));
    }

    @Test
    public void testContextParams() {
        DefaultServerEndpointConfigurator configurator = configurator(container(Map.of("allowMissingOrigin", "false")));
        assertTrue(configurator.isCrossOriginPolicy());
        assertFalse(configurator.isAllowMissingOrigin());
        assertFalse(configurator.checkOrigin(null, HOST));
        configurator = configurator(container(Map.of("crossOriginPolicy", "false")));
        assertFalse(configurator.isCrossOriginPolicy());
        assertTrue(configurator.checkOrigin("https://evil.example.net", HOST));
    }

    @Test
    public void testUpgradeRejectsCrossOriginHandshake() throws Exception {
        DefaultWsServerContainer container = container(Map.of());
        ServerEndpointConfig sec = container.findMapping("/wsbinding478").getConfig();
        MockHttpServletResponse rejected = upgrade(container, sec, "https://evil.example.net");
        assertEquals("a cross-site handshake must be refused", 403, rejected.getStatus());
        MockHttpServletResponse accepted = upgrade(container, sec, "http://red5.example.com:5080");
        assertNotEquals("a same-origin handshake must pass the origin check", 403, accepted.getStatus());
    }

    private static MockHttpServletResponse upgrade(DefaultWsServerContainer container, ServerEndpointConfig sec, String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/wsbinding478");
        request.addHeader("Host", HOST);
        request.addHeader("Upgrade", "websocket");
        request.addHeader("Connection", "Upgrade");
        request.addHeader("Sec-WebSocket-Version", "13");
        request.addHeader("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ==");
        request.addHeader("Origin", origin);
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            UpgradeUtil.doUpgrade(container, request, response, sec, Collections.emptyMap());
        } catch (Exception e) {
            // past the origin check the mock request cannot complete the protocol upgrade
        }
        return response;
    }

    private static DefaultWsServerContainer container(Map<String, String> initParams) {
        IScope app = new Scope(new Scope(null, ScopeType.GLOBAL, "origin489", false), ScopeType.APPLICATION, "wsbinding478", false);
        return ApplicationBindingTest.container("Catalina/origin489-" + RUN.incrementAndGet(), app, initParams);
    }

    private static DefaultServerEndpointConfigurator configurator(DefaultWsServerContainer container) {
        return (DefaultServerEndpointConfigurator) container.findMapping("/wsbinding478").getConfig().getConfigurator();
    }

}
