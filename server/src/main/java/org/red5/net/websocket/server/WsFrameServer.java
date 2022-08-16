package org.red5.net.websocket.server;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.coyote.http11.upgrade.UpgradeInfo;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SocketEvent;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.websocket.Transformation;
import org.apache.tomcat.websocket.WsFrameBase;
import org.apache.tomcat.websocket.WsIOException;
import org.apache.tomcat.websocket.WsSession;
import org.red5.net.websocket.WSConstants;
import org.red5.net.websocket.WebSocketConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WsFrameServer extends WsFrameBase {

    // tomcat requires this log internally
    private final static Log tomcatLog = LogFactory.getLog(WsFrameServer.class);

    private final Logger log = LoggerFactory.getLogger(WsFrameServer.class); // must not be static

    private static final StringManager sm = StringManager.getManager(WsFrameServer.class);

    private final SocketWrapperBase<?> socketWrapper;

    private final UpgradeInfo upgradeInfo;

    private final ClassLoader applicationClassLoader;

    public WsFrameServer(SocketWrapperBase<?> socketWrapper, UpgradeInfo upgradeInfo, WsSession wsSession, Transformation transformation, ClassLoader applicationClassLoader) {
        super(wsSession, transformation);
        this.socketWrapper = socketWrapper;
        this.upgradeInfo = upgradeInfo;
        this.applicationClassLoader = applicationClassLoader;
    }

    /**
     * Called when there is data in the ServletInputStream to process.
     *
     * @throws IOException
     *             if an I/O error occurs while processing the available data
     */
    private void onDataAvailable() throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("wsFrameServer.onDataAvailable - session {}", wsSession.getId());
        }
        /*
        // set connection local to the message handler so WSMessage will contain the connection
        DefaultWebSocketEndpoint ep = (DefaultWebSocketEndpoint) wsSession.getLocal();
        if (ep.getConnectionLocal() == null) {
            if (log.isDebugEnabled()) {
                log.debug("Endpoint had no connection local for session: {}", wsSession.getId());
            }
            WebSocketConnection conn = (WebSocketConnection) wsSession.getUserProperties().get(WSConstants.WS_CONNECTION);
            ep.setConnectionLocal(conn);
        }
        */
        // handle input
        if (isOpen() && inputBuffer.hasRemaining() && !isSuspended()) {
            // There might be a data that was left in the buffer when the read has been suspended.
            // Consume this data before reading from the socket.
            processInputBuffer();
        }
        // handle anything else
        while (isOpen() && !isSuspended()) {
            // Fill up the input buffer with as much data as we can
            inputBuffer.mark();
            inputBuffer.position(inputBuffer.limit()).limit(inputBuffer.capacity());
            int read = socketWrapper.read(false, inputBuffer);
            inputBuffer.limit(inputBuffer.position()).reset();
            if (read < 0) {
                throw new EOFException();
            } else if (read == 0) {
                return;
            }
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("wsFrameServer.bytesRead", Integer.toString(read)));
            }
            processInputBuffer();
        }
        /*
        // clear thread local
        ((DefaultWebSocketEndpoint) wsSession.getLocal()).setConnectionLocal(null);
        */
    }

    @Override
    protected void updateStats(long payloadLength) {
        upgradeInfo.addMsgsReceived(1);
        upgradeInfo.addBytesReceived(payloadLength);
    }

    @Override
    protected boolean isMasked() {
        // Data is from the client so it should be masked
        return true;
    }

    @Override
    protected Transformation getTransformation() {
        // Overridden to make it visible to other classes in this package
        return super.getTransformation();
    }

    @Override
    protected boolean isOpen() {
        // Overridden to make it visible to other classes in this package
        return super.isOpen();
    }

    @Override
    protected Log getLog() {
        return tomcatLog;
    }

    @Override
    protected void sendMessageText(boolean last) throws WsIOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(applicationClassLoader);
            // super!
            super.sendMessageText(last);
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }

    @Override
    protected void sendMessageBinary(ByteBuffer msg, boolean last) throws WsIOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(applicationClassLoader);
            // super!
            super.sendMessageBinary(msg, last);
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }

    @Override
    protected void resumeProcessing() {
        socketWrapper.processSocket(SocketEvent.OPEN_READ, true);
    }

    SocketState notifyDataAvailable() throws IOException {
        while (isOpen()) {
            switch (getReadState()) {
                case WAITING:
                    if (!changeReadState(ReadState.WAITING, ReadState.PROCESSING)) {
                        continue;
                    }
                    try {
                        return doOnDataAvailable();
                    } catch (IOException e) {
                        changeReadState(ReadState.CLOSING);
                        throw e;
                    }
                case SUSPENDING_WAIT:
                    if (!changeReadState(ReadState.SUSPENDING_WAIT, ReadState.SUSPENDED)) {
                        continue;
                    }
                    return SocketState.SUSPENDED;
                default:
                    throw new IllegalStateException(sm.getString("wsFrameServer.illegalReadState", getReadState()));
            }
        }

        return SocketState.CLOSED;
    }

    private SocketState doOnDataAvailable() throws IOException {
        onDataAvailable();
        while (isOpen()) {
            switch (getReadState()) {
                case PROCESSING:
                    if (!changeReadState(ReadState.PROCESSING, ReadState.WAITING)) {
                        continue;
                    }
                    return SocketState.UPGRADED;
                case SUSPENDING_PROCESS:
                    if (!changeReadState(ReadState.SUSPENDING_PROCESS, ReadState.SUSPENDED)) {
                        continue;
                    }
                    return SocketState.SUSPENDED;
                default:
                    throw new IllegalStateException(sm.getString("wsFrameServer.illegalReadState", getReadState()));
            }
        }
        return SocketState.CLOSED;
    }
}
