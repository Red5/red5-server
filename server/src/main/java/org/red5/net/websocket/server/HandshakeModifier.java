package org.red5.net.websocket.server;

import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;

/**
 * Allows for modification of a WebSocket handshake.
 *
 * @author Paul Gregoire
 */
public class HandshakeModifier {

    /**
     * Modifies the handshake request and / or response.
     *
     * @param request a {@link jakarta.websocket.server.HandshakeRequest} object
     * @param response a {@link jakarta.websocket.HandshakeResponse} object
     */
    public void modifyHandshake(HandshakeRequest request, HandshakeResponse response) {
    }

}
