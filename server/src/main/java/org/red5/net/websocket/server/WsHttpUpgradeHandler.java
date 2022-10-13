package org.red5.net.websocket.server;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;
import javax.servlet.http.WebConnection;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Extension;
import javax.websocket.server.ServerEndpointConfig;

import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.coyote.http11.upgrade.UpgradeInfo;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.Transformation;
import org.apache.tomcat.websocket.WsIOException;
import org.apache.tomcat.websocket.WsSession;
import org.red5.net.websocket.WSConstants;
import org.red5.net.websocket.WebSocketConnection;
import org.red5.net.websocket.WebSocketScope;
import org.red5.net.websocket.WebSocketScopeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servlet 3.1 HTTP upgrade handler for WebSocket connections.
 */
public class WsHttpUpgradeHandler implements InternalHttpUpgradeHandler {

    private Logger log = LoggerFactory.getLogger(WsHttpUpgradeHandler.class); // must not be static

    private final boolean isTrace = log.isTraceEnabled();

    @SuppressWarnings("unused")
    private final boolean isDebug = log.isDebugEnabled();

    private static final StringManager sm = StringManager.getManager(WsHttpUpgradeHandler.class);

    private final ClassLoader applicationClassLoader;

    private SocketWrapperBase<?> socketWrapper;

    // added at tc 8.5.61
    private UpgradeInfo upgradeInfo = new UpgradeInfo();

    private Endpoint ep;

    private ServerEndpointConfig endpointConfig;

    private DefaultWsServerContainer webSocketContainer;

    private WsHandshakeRequest handshakeRequest;

    private List<Extension> negotiatedExtensions;

    private String subProtocol;

    private Transformation transformation;

    private Map<String, String> pathParameters;

    private boolean secure;

    private WebConnection connection;

    private WsRemoteEndpointImplServer wsRemoteEndpointServer;

    private WsFrameServer wsFrame;

    private WsSession wsSession;

    public WsHttpUpgradeHandler() {
        applicationClassLoader = Thread.currentThread().getContextClassLoader();
    }

    @Override
    public void setSocketWrapper(SocketWrapperBase<?> socketWrapper) {
        this.socketWrapper = socketWrapper;
    }

    public void preInit(Endpoint ep, EndpointConfig endpointConfig, DefaultWsServerContainer wsc, WsHandshakeRequest handshakeRequest, List<Extension> negotiatedExtensionsPhase2, String subProtocol, Transformation transformation, Map<String, String> pathParameters, boolean secure) {
        this.ep = ep;
        this.endpointConfig = (ServerEndpointConfig) endpointConfig;
        this.webSocketContainer = wsc;
        this.handshakeRequest = handshakeRequest;
        this.negotiatedExtensions = negotiatedExtensionsPhase2;
        this.subProtocol = subProtocol;
        this.transformation = transformation;
        this.pathParameters = pathParameters;
        this.secure = secure;
        String httpSessionId = null;
        Object session = handshakeRequest.getHttpSession();
        if (session != null) {
            httpSessionId = ((HttpSession) session).getId();
            log.debug("pre-init with http session id: {}", httpSessionId);
        } else {
            log.debug("pre-init without http session");
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void init(WebConnection connection) {
        if (ep == null) {
            throw new IllegalStateException(sm.getString("wsHttpUpgradeHandler.noPreInit"));
        }
        String httpSessionId = null;
        Object session = handshakeRequest.getHttpSession();
        if (session != null) {
            httpSessionId = ((HttpSession) session).getId();
            log.debug("init with http session id: {}", httpSessionId);
        } else {
            log.debug("init without http session");
        }
        // Need to call onOpen using the web application's class loader
        // Create the frame using the application's class loader so it can pick up application specific config from the ServerContainerImpl
        Thread t = Thread.currentThread();
        ClassLoader cl = t.getContextClassLoader();
        t.setContextClassLoader(applicationClassLoader);
        try {
            // instance a remote endpoint server
            wsRemoteEndpointServer = new WsRemoteEndpointImplServer(socketWrapper, upgradeInfo);
            if (isTrace) {
                log.trace("New connection 1 {}", wsRemoteEndpointServer);
            }
            if (isTrace) {
                log.trace("WS session pre-ctor - wsRemoteEndpointServer: {}, webSocketContainer: {}, handshakeRequest.getRequestURI: {}, handshakeRequest.getParameterMap: {}, handshakeRequest.getQueryString: {}, handshakeRequest.getUserPrincipal: {}, httpSessionId: {}, negotiatedExtensions: {}, subProtocol: {}, pathParameters: {}, secure: {}, endpointConfig: {}", wsRemoteEndpointServer, webSocketContainer,
                        handshakeRequest.getRequestURI(), handshakeRequest.getParameterMap(), handshakeRequest.getQueryString(), handshakeRequest.getUserPrincipal(), httpSessionId, negotiatedExtensions, subProtocol, pathParameters, secure, endpointConfig);
            }
            // deprecated version
            wsSession = new WsSession(ep, wsRemoteEndpointServer, webSocketContainer, handshakeRequest.getRequestURI(), handshakeRequest.getParameterMap(), handshakeRequest.getQueryString(), handshakeRequest.getUserPrincipal(), httpSessionId, negotiatedExtensions, subProtocol, pathParameters, secure, endpointConfig);
            // newest ctor
            //wsSession = new WsSession(wsRemoteEndpointServer, webSocketContainer, handshakeRequest.getRequestURI(), handshakeRequest.getParameterMap(), handshakeRequest.getQueryString(), handshakeRequest.getUserPrincipal(), httpSessionId, negotiatedExtensions, subProtocol, pathParameters, secure, endpointConfig);
            if (isTrace) {
                log.trace("New connection 2 {}", wsSession);
            }
            wsFrame = new WsFrameServer(socketWrapper, upgradeInfo, wsSession, transformation, applicationClassLoader);
            if (isTrace) {
                log.trace("New connection 3 {}", wsFrame);
            }
            // WsFrame adds the necessary final transformations. Copy the completed transformation chain to the remote end point.
            wsRemoteEndpointServer.setTransformation(wsFrame.getTransformation());
            if (isTrace) {
                log.trace("New connection 4");
            }
            // get the ws scope manager from user props
            WebSocketScopeManager manager = (WebSocketScopeManager) endpointConfig.getUserProperties().get(WSConstants.WS_MANAGER);
            if (isTrace) {
                log.trace("New connection 5");
            }
            // get ws scope from user props
            WebSocketScope scope = (WebSocketScope) endpointConfig.getUserProperties().get(WSConstants.WS_SCOPE);
            if (isTrace) {
                log.trace("New connection 6 - Scope: {} WS session: {}", scope, wsSession);
            }
            // create a ws connection instance
            WebSocketConnection conn = new WebSocketConnection(scope, wsSession);
            // in debug check since WebSocketConnection.toString is a tiny bit expensive
            if (isTrace) {
                log.trace("New connection 7: {}", conn);
            }
            // set ip and port
            conn.setAttribute(WSConstants.WS_HEADER_REMOTE_IP, socketWrapper.getRemoteAddr());
            conn.setAttribute(WSConstants.WS_HEADER_REMOTE_PORT, socketWrapper.getRemotePort());
            // add the request headers
            conn.setHeaders(handshakeRequest.getHeaders());
            if (isTrace) {
                log.trace("New connection 8: {}", conn);
            }
            // add the connection to the user props
            endpointConfig.getUserProperties().put(WSConstants.WS_CONNECTION, conn);
            // must be added to the session as well since the session ctor copies from the endpoint and doesnt update
            wsSession.getUserProperties().put(WSConstants.WS_CONNECTION, conn);
            // set connected flag
            conn.setConnected();
            // fire endpoint handler
            ep.onOpen(wsSession, endpointConfig);
            if (isTrace) {
                log.trace("New connection 9: endpoint opened");
            }
            // get the endpoint path to use in registration since we're a server
            String path = ((ServerEndpointConfig) endpointConfig).getPath();
            webSocketContainer.registerSession(path, wsSession);
            if (isTrace) {
                log.trace("New connection 10: session registered");
            }
            // add the connection to the manager
            manager.addConnection(conn);
        } catch (DeploymentException e) {
            throw new IllegalArgumentException(e);
        } finally {
            t.setContextClassLoader(cl);
        }
    }

    @Override
    public SocketState upgradeDispatch(SocketEvent status) {
        switch (status) {
            case OPEN_READ:
                try {
                    return wsFrame.notifyDataAvailable();
                } catch (WsIOException ws) {
                    close(ws.getCloseReason());
                } catch (IOException ioe) {
                    onError(ioe);
                    CloseReason cr = new CloseReason(CloseCodes.CLOSED_ABNORMALLY, ioe.getMessage());
                    close(cr);
                }
                return SocketState.CLOSED;
            case OPEN_WRITE:
                wsRemoteEndpointServer.onWritePossible(false);
                break;
            case STOP:
                CloseReason cr = new CloseReason(CloseCodes.GOING_AWAY, sm.getString("wsHttpUpgradeHandler.serverStop"));
                try {
                    wsSession.close(cr);
                } catch (IOException ioe) {
                    onError(ioe);
                    cr = new CloseReason(CloseCodes.CLOSED_ABNORMALLY, ioe.getMessage());
                    close(cr);
                    return SocketState.CLOSED;
                }
                break;
            case ERROR:
                String msg = sm.getString("wsHttpUpgradeHandler.closeOnError");
                wsSession.doClose(new CloseReason(CloseCodes.GOING_AWAY, msg), new CloseReason(CloseCodes.CLOSED_ABNORMALLY, msg));
                //$FALL-THROUGH$
            case CONNECT_FAIL:
            case DISCONNECT:
            case TIMEOUT:
                return SocketState.CLOSED;
        }
        if (wsFrame.isOpen()) {
            return SocketState.UPGRADED;
        } else {
            return SocketState.CLOSED;
        }
    }

    @Override
    public void pause() {
        // NO-OP
    }

    @Override
    public UpgradeInfo getUpgradeInfo() {
        return upgradeInfo;
    }

    @Override
    public void destroy() {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
                log.error(sm.getString("wsHttpUpgradeHandler.destroyFailed"), e);
            }
        }
    }

    private void onError(Throwable throwable) {
        // Need to call onError using the web application's class loader
        Thread t = Thread.currentThread();
        ClassLoader cl = t.getContextClassLoader();
        t.setContextClassLoader(applicationClassLoader);
        try {
            ep.onError(wsSession, throwable);
        } finally {
            t.setContextClassLoader(cl);
        }
    }

    private void close(CloseReason cr) {
        /*
         * Any call to this method is a result of a problem reading from the client. At this point that state of the connection is unknown. Attempt to send a close frame to the client and
         * then close the socket immediately. There is no point in waiting for a close frame from the client because there is no guarantee that we can recover from whatever messed up state
         * the client put the connection into.
         */
        wsSession.onClose(cr);
    }

    @Override
    public void setSslSupport(SSLSupport sslSupport) {
        // NO-OP. WebSocket has no requirement to access the TLS information associated with the underlying connection.
    }

    /**
     * Check to see if the timeout has expired and process a timeout if that is that case. Note: The name of this method originated with the Servlet 3.0 asynchronous processing but evolved over time to represent a timeout that is triggered independently of the socket read/write timeouts.
     *
     * @param now
     *            - The time (as returned by System.currentTimeMillis() to use as the current time to determine whether the timeout has expired. If negative, the timeout will always be treated as if it has expired.
     */
    @Override
    public void timeoutAsync(long now) {
        log.trace("timeoutAsync: {}", now);
    }

}
