package org.red5.net.websocket.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Verifies exact WebSocket Origin matching (issue #470).
 */
public class OriginValidationTest {

    @Test
    public void testSameOrigin() {
        DefaultServerEndpointConfigurator configurator = configurator("https://app.example.com");
        assertTrue(configurator.checkOrigin("https://app.example.com"));
        // default port and case are normalized
        assertTrue(configurator.checkOrigin("https://APP.example.com:443"));
    }

    @Test
    public void testDifferentOrigin() {
        DefaultServerEndpointConfigurator configurator = configurator("https://app.example.com");
        assertFalse(configurator.checkOrigin("https://evil.example.net"));
        assertFalse(configurator.checkOrigin("http://app.example.com"));
        assertFalse(configurator.checkOrigin("https://app.example.com:8443"));
        assertFalse(configurator.checkOrigin("null"));
        assertFalse(configurator.checkOrigin(null));
    }

    @Test
    public void testSuffixConfusion() {
        DefaultServerEndpointConfigurator configurator = configurator("https://example.com");
        assertFalse(configurator.checkOrigin("https://evilexample.com"));
        assertFalse(configurator.checkOrigin("https://sub.example.com"));
        assertFalse(configurator.checkOrigin("https://example.com.evil.net"));
        // a partial origin once matched through the reversed endsWith check
        assertFalse(configurator.checkOrigin("e.com"));
        assertFalse(configurator.checkOrigin("example.com"));
    }

    @Test
    public void testHostOnlyEntry() {
        DefaultServerEndpointConfigurator configurator = configurator("example.com");
        assertTrue(configurator.checkOrigin("https://example.com"));
        assertTrue(configurator.checkOrigin("http://example.com:5080"));
        assertFalse(configurator.checkOrigin("https://sub.example.com"));
        configurator = configurator("localhost:5080");
        assertTrue(configurator.checkOrigin("http://localhost:5080"));
        assertFalse(configurator.checkOrigin("http://localhost:8080"));
    }

    @Test
    public void testWildcard() {
        DefaultServerEndpointConfigurator configurator = configurator("*");
        assertTrue(configurator.checkOrigin("https://anything.example"));
    }

    @Test
    public void testDisabledPolicyAllowsAll() {
        DefaultServerEndpointConfigurator configurator = new DefaultServerEndpointConfigurator();
        configurator.setCrossOriginPolicy(false);
        assertTrue(configurator.checkOrigin("https://anything.example"));
    }

    @Test
    public void testMultipleApplicationsAreIndependent() {
        DefaultServerEndpointConfigurator a = configurator("https://a.example.com");
        DefaultServerEndpointConfigurator b = configurator("https://b.example.com");
        assertTrue(a.checkOrigin("https://a.example.com"));
        assertFalse(a.checkOrigin("https://b.example.com"));
        assertTrue(b.checkOrigin("https://b.example.com"));
        assertFalse(b.checkOrigin("https://a.example.com"));
    }

    private static DefaultServerEndpointConfigurator configurator(String... allowed) {
        DefaultServerEndpointConfigurator configurator = new DefaultServerEndpointConfigurator();
        configurator.setCrossOriginPolicy(true);
        configurator.setAllowedOrigins(allowed);
        return configurator;
    }

}
