package org.red5.net.websocket.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.websocket.server.ServerEndpointConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.red5.net.websocket.WSConstants;
import org.red5.net.websocket.WebSocketPlugin;
import org.red5.net.websocket.WebSocketScope;
import org.red5.net.websocket.WebSocketScopeManager;
import org.red5.server.Server;
import org.red5.server.adapter.MultiThreadedApplicationAdapter;
import org.red5.server.api.IServer;
import org.red5.server.api.scope.IBasicScope;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.ScopeType;
import org.red5.server.plugin.PluginRegistry;
import org.red5.server.scope.BroadcastScope;
import org.red5.server.scope.Scope;

/**
 * Verifies how a WebSocket handshake resolves the last segment of its path when a stream of that name is already published. Child names are
 * shared by every scope type, so a stream's broadcast scope and a room cannot have the same name under one parent; the handshake must then be
 * served by the scope that holds the stream instead of failing.
 */
public class BroadcastNameHandshakeTest {

    // the servlet context path used by the shared container helper
    private static final String APP = "wsbinding478";

    private static final String MAPPING = "/" + APP;

    // containers are kept in a static map, so each test uses its own host name
    private static final AtomicInteger RUN = new AtomicInteger();

    /**
     * An application scope that has a server, as a deployed one does. Starting and removing a room notifies the server, so rooms cannot
     * be created or removed under a scope tree that has none.
     */
    private static class ServerBackedApp extends Scope {

        private final Server server = new Server() {

            @Override
            public void notifyBasicScopeAdded(IBasicScope scope) {
            }

            @Override
            public void notifyBasicScopeRemoved(IBasicScope scope) {
            }

            @Override
            public void notifyScopeCreated(IScope scope) {
            }

        };

        ServerBackedApp(IScope global) {
            super(global, ScopeType.APPLICATION, APP, false);
        }

        @Override
        public IServer getServer() {
            return server;
        }

        // a deployed global scope has a context, so the context path of an application starts at the application, as in /live/room
        @Override
        public String getContextPath() {
            return MAPPING;
        }

    }

    private long pingInterval;

    private WebSocketPlugin plugin;

    private Scope appScope;

    private DefaultWsServerContainer container;

    @Before
    public void setUp() {
        // no pinger threads for these tests
        pingInterval = WebSocketScopeManager.websocketPingInterval;
        WebSocketScopeManager.websocketPingInterval = -1;
        plugin = new WebSocketPlugin();
        PluginRegistry.register(plugin);
        appScope = new ServerBackedApp(new Scope(null, ScopeType.GLOBAL, "streamhost", false));
        MultiThreadedApplicationAdapter app = new MultiThreadedApplicationAdapter();
        app.setScope(appScope);
        plugin.setApplication(app);
        // as the plugin does for a new application scope
        new WebSocketScope(appScope).register();
        container = ApplicationBindingTest.container("Catalina/streamhost-" + RUN.incrementAndGet(), appScope, Map.of());
        configurator().bindApplicationScope(container.getServletContext());
    }

    @After
    public void tearDown() {
        plugin.removeManager(appScope);
        PluginRegistry.unregister(plugin);
        WebSocketScopeManager.websocketPingInterval = pingInterval;
    }

    @Test
    public void testPublishedStreamNameIsServedByApplicationScope() {
        assertTrue(appScope.addChildScope(new BroadcastScope(appScope, "stream1")));
        WebSocketScope wsScope = handshake(MAPPING + "/stream1");
        assertNotNull("handshake on a published stream name must not be left without a websocket scope", wsScope);
        assertSame(appScope, wsScope.getScope());
        // the application's own websocket scope serves it, no second scope is registered for the stream's name
        assertEquals(MAPPING, wsScope.getPath());
        // no room was created over the stream's name, and the stream is untouched
        assertFalse(appScope.hasChildScope(ScopeType.ROOM, "stream1"));
        assertTrue(appScope.hasChildScope(ScopeType.BROADCAST, "stream1"));
    }

    @Test
    public void testRepeatedHandshakesOnPublishedStreamNameShareScope() {
        assertTrue(appScope.addChildScope(new BroadcastScope(appScope, "stream1")));
        WebSocketScope first = handshake(MAPPING + "/stream1");
        WebSocketScope second = handshake(MAPPING + "/stream1");
        assertNotNull(first);
        assertSame(first, second);
    }

    @Test
    public void testStreamNameInsideRoomIsServedByThatRoom() {
        assertTrue(appScope.createChildScope("room1"));
        IScope room = appScope.getScope("room1");
        assertNotNull(room);
        assertTrue(room.addChildScope(new BroadcastScope(room, "stream2")));
        WebSocketScope wsScope = handshake(MAPPING + "/room1/stream2");
        assertNotNull(wsScope);
        // bound to the room that holds the stream, not to the application and not to a room created over the stream's name
        assertSame(room, wsScope.getScope());
        assertFalse(room.hasChildScope(ScopeType.ROOM, "stream2"));
    }

    @Test
    public void testRoomIsCreatedWhenNoStreamHasThatName() {
        WebSocketScope wsScope = handshake(MAPPING + "/room2");
        assertNotNull(wsScope);
        IScope bound = wsScope.getScope();
        assertEquals(ScopeType.ROOM, bound.getType());
        assertEquals("room2", bound.getName());
        assertSame(appScope, bound.getParent());
    }

    @Test
    public void testNameBecomesRoomOnceStreamIsRemoved() {
        BroadcastScope stream = new BroadcastScope(appScope, "stream3");
        assertTrue(appScope.addChildScope(stream));
        assertSame(appScope, handshake(MAPPING + "/stream3").getScope());
        // once the stream is gone the name is free and a handshake on the same name creates a room as before
        appScope.removeChildScope(stream);
        IScope bound = handshake(MAPPING + "/stream3").getScope();
        assertEquals(ScopeType.ROOM, bound.getType());
        assertEquals("stream3", bound.getName());
    }

    private DefaultServerEndpointConfigurator configurator() {
        return (DefaultServerEndpointConfigurator) container.findMapping(MAPPING).getConfig().getConfigurator();
    }

    private WebSocketScope handshake(String path) {
        ServerEndpointConfig sec = container.findMapping(MAPPING).getConfig();
        WsPerSessionServerEndpointConfig perSession = new WsPerSessionServerEndpointConfig(sec);
        sec.getConfigurator().modifyHandshake(perSession, ApplicationBindingTest.request(path), ApplicationBindingTest.response());
        return (WebSocketScope) perSession.getUserProperties().get(WSConstants.WS_SCOPE);
    }

}
