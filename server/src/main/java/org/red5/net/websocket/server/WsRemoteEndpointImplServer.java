package org.red5.net.websocket.server;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.coyote.http11.upgrade.UpgradeInfo;
import org.apache.tomcat.util.net.SocketWrapperBase;
import org.apache.tomcat.websocket.Transformation;
import org.apache.tomcat.websocket.WsRemoteEndpointImplBase;

import jakarta.websocket.SendHandler;
import jakarta.websocket.SendResult;

/**
 * This is the server side {@link jakarta.websocket.RemoteEndpoint} implementation - i.e. what the server uses to send data to the client.
 *
 * @author mondain
 */
public class WsRemoteEndpointImplServer extends WsRemoteEndpointImplBase {

    /** Constant <code>SENDRESULT_OK</code> */
    protected static final SendResult SENDRESULT_OK = new SendResult(null, null);

    private final SocketWrapperBase<?> socketWrapper;

    private final UpgradeInfo upgradeInfo;

    private final WsWriteTimeout wsWriteTimeout;

    private volatile SendHandler handler;

    private volatile ByteBuffer[] buffers;

    private volatile long timeoutExpiry = -1;

    private volatile boolean close;

    /**
     * <p>Constructor for WsRemoteEndpointImplServer.</p>
     *
     * @param socketWrapper a {@link org.apache.tomcat.util.net.SocketWrapperBase} object
     * @param upgradeInfo a {@link org.apache.coyote.http11.upgrade.UpgradeInfo} object
     */
    public WsRemoteEndpointImplServer(SocketWrapperBase<?> socketWrapper, UpgradeInfo upgradeInfo) {
        this.socketWrapper = socketWrapper;
        this.upgradeInfo = upgradeInfo;
        // Paul: our impl doesnt use the WsServerContainer.wsWriteTimeout due to its type and what may be registered, we use our impl classes
        this.wsWriteTimeout = new WsWriteTimeout();
    }

    /** {@inheritDoc} */
    @Override
    protected final boolean isMasked() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    protected void doWrite(SendHandler handler, long blockingWriteTimeoutExpiry, ByteBuffer... buffers) {
        if (blockingWriteTimeoutExpiry == -1) {
            this.handler = handler;
            this.buffers = buffers;
            // This is definitely the same thread that triggered the write so a dispatch will be required.
            onWritePossible(true);
        } else {
            // Blocking
            try {
                final long timeout = blockingWriteTimeoutExpiry - System.currentTimeMillis();
                for (ByteBuffer buffer : buffers) {
                    if (timeout <= 0) {
                        SendResult sr = new SendResult(null, new SocketTimeoutException());
                        handler.onResult(sr);
                        return;
                    }
                    socketWrapper.setWriteTimeout(timeout);
                    socketWrapper.write(true, buffer);
                }
                if (timeout <= 0) {
                    SendResult sr = new SendResult(null, new SocketTimeoutException());
                    handler.onResult(sr);
                    return;
                }
                socketWrapper.setWriteTimeout(timeout);
                socketWrapper.flush(true);
                handler.onResult(SENDRESULT_OK);
            } catch (IOException e) {
                SendResult sr = new SendResult(null, e);
                handler.onResult(sr);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void updateStats(long payloadLength) {
        upgradeInfo.addMsgsSent(1);
        upgradeInfo.addBytesSent(payloadLength);
    }

    /**
     * <p>onWritePossible.</p>
     *
     * @param useDispatch a boolean
     */
    public void onWritePossible(boolean useDispatch) {
        ByteBuffer[] buffers = this.buffers;
        if (buffers == null) {
            // Servlet 3.1 will call the write listener once even if nothing was written
            return;
        }
        boolean complete = false;
        try {
            socketWrapper.flush(false);
            // If this is false there will be a call back when it is true
            while (socketWrapper.isReadyForWrite()) {
                complete = true;
                for (ByteBuffer buffer : buffers) {
                    if (buffer.hasRemaining()) {
                        complete = false;
                        socketWrapper.write(false, buffer);
                        break;
                    }
                }
                if (complete) {
                    socketWrapper.flush(false);
                    complete = socketWrapper.isReadyForWrite();
                    if (complete) {
                        wsWriteTimeout.unregister(this);
                        clearHandler(null, useDispatch);
                        if (close) {
                            close();
                        }
                    }
                    break;
                }
            }
        } catch (IOException | IllegalStateException e) {
            wsWriteTimeout.unregister(this);
            clearHandler(e, useDispatch);
            close();
        }
        if (!complete) {
            // Async write is in progress
            long timeout = getSendTimeout();
            if (timeout > 0) {
                // Register with timeout thread
                timeoutExpiry = timeout + System.currentTimeMillis();
                wsWriteTimeout.register(this);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void doClose() {
        if (handler != null) {
            // close() can be triggered by a wide range of scenarios. It is far simpler just to always use a dispatch than it is to try and track
            // whether or not this method was called by the same thread that triggered the write
            clearHandler(new EOFException(), true);
        }
        // no ex thrown here anymore, handled elsewhere in lib
        socketWrapper.close();
        wsWriteTimeout.unregister(this);
    }

    /** {@inheritDoc} */
    @Override
    protected ReentrantLock getLock() {
        // for a lack of better implementation, we use the socket wrapper lock
        return socketWrapper.getLock();
    }

    /**
     * <p>Getter for the field <code>timeoutExpiry</code>.</p>
     *
     * @return a long
     */
    protected long getTimeoutExpiry() {
        return timeoutExpiry;
    }

    /*
     * Currently this is only called from the background thread so we could just call clearHandler() with useDispatch == false but the method parameter was added in case other callers
     * started to use this method to make sure that those callers think through what the correct value of useDispatch is for them.
     */
    /**
     * <p>onTimeout.</p>
     *
     * @param useDispatch a boolean
     */
    protected void onTimeout(boolean useDispatch) {
        if (handler != null) {
            clearHandler(new SocketTimeoutException(), useDispatch);
        }
        close();
    }

    /** {@inheritDoc} */
    @Override
    protected void setTransformation(Transformation transformation) {
        // Overridden purely so it is visible to other classes in this package
        super.setTransformation(transformation);
    }

    /**
     *
     * @param t
     *            The throwable associated with any error that occurred
     * @param useDispatch
     *            Should {@link SendHandler#onResult(SendResult)} be called from a new thread, keeping in mind the requirements of {@link jakarta.websocket.RemoteEndpoint.Async}
     */
    private void clearHandler(Throwable t, boolean useDispatch) {
        // Setting the result marks this (partial) message as complete which means the next one may be sent which
        // could update the value of the handler. Therefore, keep a local copy before signalling the end of the (partial)
        // message.
        SendHandler sh = handler;
        handler = null;
        buffers = null;
        if (sh != null) {
            if (useDispatch) {
                OnResultRunnable r = new OnResultRunnable(sh, t);
                socketWrapper.execute(r);
            } else {
                sh.onResult(new SendResult(null, t));
            }
        }
    }

    private static class OnResultRunnable implements Runnable {

        private final SendHandler sh;

        private final Throwable t;

        private OnResultRunnable(SendHandler sh, Throwable t) {
            this.sh = sh;
            this.t = t;
        }

        @Override
        public void run() {
            sh.onResult(new SendResult(null, t));
        }
    }

}
