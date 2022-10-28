package org.red5.server.api.websocket;

import org.red5.net.websocket.WebSocketConnection;

/**
 * Interface for handlers that are aware of the WebSocketConnection.
 *
 * @author Paul Gregoire
 */
public interface IWebSocketAwareHandler {

    /**
     * Handler method. Called when a WebSocket connects to the application.
     *
     * @param conn
     *            WebSocket connection object
     * @param params
     *            List of parameters after connection URL
     * @return true upon success, false otherwise
     */
    boolean appConnect(WebSocketConnection wsConn, Object[] params);

    /**
     * Handler method. Called when a WebSocket disconnects from the application.
     *
     * @param conn
     *            WebSocket connection object
     * @return true upon success, false otherwise
     */
    boolean appDisconnect(WebSocketConnection conn);

}