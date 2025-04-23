package org.red5.net.websocket.server;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.coyote.http11.upgrade.InternalHttpUpgradeHandler;
import org.apache.coyote.http11.upgrade.UpgradeInfo;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.Constants;
import org.apache.tomcat.websocket.Transformation;
import org.apache.tomcat.websocket.WsIOException;
import org.apache.tomcat.websocket.WsSession;
import org.red5.net.websocket.WSConstants;
import org.red5.net.websocket.WebSocketConnection;
import org.red5.net.websocket.WebSocketScope;
import org.red5.net.websocket.WebSocketScopeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.WebConnection;
import jakarta.websocket.CloseReason;
import jakarta.websocket.CloseReason.CloseCodes;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Extension;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * Servlet 3.1 HTTP upgrade handler for WebSocket connections.
 */
public class WsHttpUpgradeHandler implements InternalHttpUpgradeHandler {

    private Logger log = LoggerFactory.getLogger(WsHttpUpgradeHandler.class); // must not be static

    private final boolean isTrace = log.isTraceEnabled(), isDebug = log.isDebugEnabled();

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

    private long lastTimeoutCheck = System.currentTimeMillis();

    private long lastReadBytes, lastWrittenBytes;

    private transient WebSocketScopeManager manager;

    private transient WebSocketScope scope;

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
        // user props
        Map<String, Object> userProps = endpointConfig.getUserProperties();
        // get the ws scope manager from user props
        manager = (WebSocketScopeManager) userProps.get(WSConstants.WS_MANAGER);
        // get ws scope from user props
        scope = (WebSocketScope) userProps.get(WSConstants.WS_SCOPE);
    }

    @Override
    public void init(WebConnection connection) {
        if (ep != null) {
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
                    log.trace("WS session pre-ctor - wsRemoteEndpointServer: {}, webSocketContainer: {}, handshakeRequest.getRequestURI: {}, handshakeRequest.getParameterMap: {}, handshakeRequest.getQueryString: {}, handshakeRequest.getUserPrincipal: {}, httpSessionId: {}, negotiatedExtensions: {}, subProtocol: {}, pathParameters: {}, secure: {}, endpointConfig: {}", wsRemoteEndpointServer, webSocketContainer,
                            handshakeRequest.getRequestURI(), handshakeRequest.getParameterMap(), handshakeRequest.getQueryString(), handshakeRequest.getUserPrincipal(), httpSessionId, negotiatedExtensions, subProtocol, pathParameters, secure, endpointConfig);
                }
                // ClientEndpointHolder clientEndpoint = new EndpointHolder(ep);
                wsSession = new WsSession(wsRemoteEndpointServer, webSocketContainer, handshakeRequest.getRequestURI(), handshakeRequest.getParameterMap(), handshakeRequest.getQueryString(), handshakeRequest.getUserPrincipal(), httpSessionId, negotiatedExtensions, subProtocol, pathParameters, secure, endpointConfig);
                wsFrame = new WsFrameServer(socketWrapper, upgradeInfo, wsSession, transformation, applicationClassLoader);
                // WsFrame adds the necessary final transformations. Copy the completed transformation chain to the remote end point.
                wsRemoteEndpointServer.setTransformation(wsFrame.getTransformation());
                // create a ws connection instance
                WebSocketConnection conn = new WebSocketConnection(scope, wsSession);
                // set ip and port
                conn.setAttribute(WSConstants.WS_HEADER_REMOTE_IP, socketWrapper.getRemoteAddr());
                conn.setAttribute(WSConstants.WS_HEADER_REMOTE_PORT, socketWrapper.getRemotePort());
                // add the request headers
                conn.setHeaders(handshakeRequest.getHeaders());
                // add the connection to the user props
                endpointConfig.getUserProperties().put(WSConstants.WS_CONNECTION, conn);
                // must be added to the session as well since the session ctor copies from the endpoint and doesnt update
                wsSession.getUserProperties().put(WSConstants.WS_CONNECTION, conn);
                // set connected flag
                conn.setConnected();
                // fire endpoint handler
                ep.onOpen(wsSession, endpointConfig);
                // get the endpoint path to use in registration since we're a server
                String path = ((ServerEndpointConfig) endpointConfig).getPath();
                webSocketContainer.registerSession(path, wsSession);
                // add the connection to the manager
                manager.addConnection(conn);
            } catch (DeploymentException e) {
                throw new IllegalArgumentException(e);
            } finally {
                t.setContextClassLoader(cl);
            }
        } else {
            throw new IllegalStateException(sm.getString("wsHttpUpgradeHandler.noPreInit"));
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
            } finally {
                connection = null;
            }
        }
    }

    private void onError(Throwable throwable) {
        if (isDebug) {
            log.debug("onError for ws id: {}", wsSession.getId(), throwable);
        }
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
        if (isDebug) {
            log.debug("close for ws id: {} reason: {}", wsSession.getId(), cr);
        }
        /*
         * Any call to this method is a result of a problem reading from the client. At this point that state of the
         * connection is unknown. Attempt to send a close frame to the client and then close the socket immediately.
         * There is no point in waiting for a close frame from the client because there is no guarantee that we can
         * recover from whatever messed up state the client put the connection into.
         */
        wsSession.onClose(cr);
        // null these so that we don't try to use them again
        wsSession = null;
        connection = null;
        upgradeInfo = null;
        // null the socket wrapper so that we don't try to use it again
        if (socketWrapper != null) {
            socketWrapper.close();
            socketWrapper = null;
        }
    }

    @Override
    public void setSslSupport(SSLSupport sslSupport) {
        // NO-OP. WebSocket has no requirement to access the TLS information associated with the underlying connection.
    }

    /**
     * Check to see if the timeout has expired and process a timeout if that is the case. Note: The name of this method
     * originated with the Servlet 3.0 asynchronous processing but evolved over time to represent a timeout that is
     * triggered independently of the socket read/write timeouts.
     *
     * @param now
     *            - The time (as returned by System.currentTimeMillis() to use as the current time to determine whether
     * the timeout has expired. If negative, the timeout will always be treated as if it has expired.
     */
    @Override
    public void timeoutAsync(long now) {
        log.trace("timeoutAsync: {} on session: {}", now, wsSession);
        if (wsSession != null) {
            try {
                final String wsSessionId = wsSession.getId();
                // do lookup by session id, skips need for session user props
                WebSocketConnection conn = scope.getConnectionBySessionId(wsSessionId);
                // if we don't get it from the scope, try the session lookup
                if (conn == null && wsSession.isOpen()) {
                    // session methods may not be called if its not open
                    conn = (WebSocketConnection) wsSession.getUserProperties().get(WSConstants.WS_CONNECTION);
                }
                // last check, if we don't have a connection, log a warning
                if (conn == null) {
                    log.warn("Connection for id: {} was not found in the scope or session: {}", wsSession.getId(), scope.getPath());
                    return;
                }
                // negative now means always treat as expired
                if (now > 0) {
                    long checkDelta = now - lastTimeoutCheck;
                    long readBytes = conn.getReadBytes(), writtenBytes = conn.getWrittenBytes();
                    log.info("timeoutAsync: {}ms on session id: {} read: {} written: {}", checkDelta, wsSessionId, readBytes, writtenBytes);
                    Map<String, Object> props = wsSession.getUserProperties();
                    log.debug("Session properties: {}", props);
                    long maxIdleTimeout = wsSession.getMaxIdleTimeout();
                    long readTimeout = (long) props.get(Constants.READ_IDLE_TIMEOUT_MS);
                    long writeTimeout = (long) props.get(Constants.WRITE_IDLE_TIMEOUT_MS);
                    log.debug("Session timeouts - max: {} read: {} write: {}", maxIdleTimeout, readTimeout, writeTimeout);
                    if (maxIdleTimeout > 0) {
                        if (checkDelta > maxIdleTimeout && (readBytes == lastReadBytes || writtenBytes == lastWrittenBytes)) {
                            log.info("Max idle timeout: {}ms on session id: {}", checkDelta, wsSessionId);
                            conn.close(CloseCodes.GOING_AWAY, "Max idle timeout");
                        }
                    } else {
                        if (readTimeout > 0) {
                            if (readBytes == lastReadBytes) {
                                if (checkDelta > readTimeout) {
                                    log.info("Read timeout: {}ms on session id: {}", checkDelta, wsSessionId);
                                    conn.close(CloseCodes.GOING_AWAY, "Read timeout");
                                }
                            }
                        }
                        if (writeTimeout > 0) {
                            if (writtenBytes == lastWrittenBytes) {
                                if (checkDelta > writeTimeout) {
                                    log.info("Write timeout: {}ms on session id: {}", checkDelta, wsSessionId);
                                    conn.close(CloseCodes.GOING_AWAY, "Write timeout");
                                }
                            }
                        }
                    }
                    lastReadBytes = readBytes;
                    lastWrittenBytes = writtenBytes;
                    lastTimeoutCheck = now;
                } else {
                    log.warn("timeoutAsync: negative time on session id: {}", wsSessionId);
                    conn.close(CloseCodes.GOING_AWAY, "Timeout expired");
                }
            } catch (Throwable t) {
                log.warn(sm.getString("wsHttpUpgradeHandler.timeoutAsyncFailed"), t);
            }
        }
    }

}
