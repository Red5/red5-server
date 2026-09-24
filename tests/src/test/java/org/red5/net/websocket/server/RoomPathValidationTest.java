package org.red5.net.websocket.server;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Verifies the room name grammar and depth limit applied before websocket handshakes create scopes (issue #466).
 */
public class RoomPathValidationTest {

    @Test
    public void testValidPaths() {
        assertTrue(DefaultServerEndpointConfigurator.isValidRoomPath("/live"));
        assertTrue(DefaultServerEndpointConfigurator.isValidRoomPath("/live/room1"));
        assertTrue(DefaultServerEndpointConfigurator.isValidRoomPath("/live/room_1/sub-room.2"));
        assertTrue(DefaultServerEndpointConfigurator.isValidRoomPath("/live/a/b/c/d"));
    }

    @Test
    public void testInvalidNames() {
        assertFalse(DefaultServerEndpointConfigurator.isValidRoomPath("/live/.."));
        assertFalse(DefaultServerEndpointConfigurator.isValidRoomPath("/live/room/."));
        assertFalse(DefaultServerEndpointConfigurator.isValidRoomPath("/live/room%20one"));
        assertFalse(DefaultServerEndpointConfigurator.isValidRoomPath("/live/room one"));
        assertFalse(DefaultServerEndpointConfigurator.isValidRoomPath("/live//room"));
        assertFalse(DefaultServerEndpointConfigurator.isValidRoomPath("/live/" + "x".repeat(65)));
    }

    @Test
    public void testDepthLimit() {
        assertFalse(DefaultServerEndpointConfigurator.isValidRoomPath("/live/a/b/c/d/e"));
    }

}
