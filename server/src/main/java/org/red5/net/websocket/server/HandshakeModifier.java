package org.red5.net.websocket.server;

import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;

/**
 * Allows for modification of a WebSocket handshake.
 * 
 * @author Paul Gregoire
 */
public class HandshakeModifier {

    /**
     * Modifies the handshake request and / or response.
     * 
     * @param request
     * @param response
     */
    public void modifyHandshake(HandshakeRequest request, HandshakeResponse response) {
    }

}
