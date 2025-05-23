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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>WsFrameServer class.</p>
 *
 * @author mondain
 */
public class WsFrameServer extends WsFrameBase {

    // tomcat requires this log internally
    private final static Log tomcatLog = LogFactory.getLog(WsFrameServer.class);

    private final Logger log = LoggerFactory.getLogger(WsFrameServer.class); // must not be static

    private static final StringManager sm = StringManager.getManager(WsFrameServer.class);

    private final SocketWrapperBase<?> socketWrapper;

    private final UpgradeInfo upgradeInfo;

    private final ClassLoader applicationClassLoader;

    /**
     * <p>Constructor for WsFrameServer.</p>
     *
     * @param socketWrapper a {@link org.apache.tomcat.util.net.SocketWrapperBase} object
     * @param upgradeInfo a {@link org.apache.coyote.http11.upgrade.UpgradeInfo} object
     * @param wsSession a {@link org.apache.tomcat.websocket.WsSession} object
     * @param transformation a {@link org.apache.tomcat.websocket.Transformation} object
     * @param applicationClassLoader a {@link java.lang.ClassLoader} object
     */
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
    }

    /** {@inheritDoc} */
    @Override
    protected void updateStats(long payloadLength) {
        upgradeInfo.addMsgsReceived(1);
        upgradeInfo.addBytesReceived(payloadLength);
    }

    /** {@inheritDoc} */
    @Override
    protected boolean isMasked() {
        // Data is from the client so it should be masked
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected Transformation getTransformation() {
        // Overridden to make it visible to other classes in this package
        return super.getTransformation();
    }

    /** {@inheritDoc} */
    @Override
    protected boolean isOpen() {
        // Overridden to make it visible to other classes in this package
        return super.isOpen();
    }

    /** {@inheritDoc} */
    @Override
    protected Log getLog() {
        return tomcatLog;
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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
