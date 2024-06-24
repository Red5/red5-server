/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2015 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.client.net.rtmp;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.mina.core.future.CloseFuture;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.transport.socket.SocketConnector;
import org.apache.mina.transport.socket.nio.NioSocketConnector;

/**
 * RTMP client implementation supporting "rtmp" and "rtmpe" protocols.
 *
 * @author The Red5 Project
 * @author Christian Eckerle (ce@publishing-etc.de)
 * @author Joachim Bauch (jojo@struktur.de)
 * @author Paul Gregoire (mondain@gmail.com)
 * @author Steven Gong (steven.gong@gmail.com)
 * @author Anton Lebedevich (mabrek@gmail.com)
 * @author Tiago Daniel Jacobs (tiago@imdt.com.br)
 * @author Jon Valliere
 */
public class RTMPClient extends BaseRTMPClientHandler {

    protected static final int CONNECTOR_WORKER_TIMEOUT = 7000; // milliseconds

    // I/O handler
    protected final RTMPMinaIoHandler ioHandler;

    // Socket connector, disposed on disconnect
    protected SocketConnector socketConnector;

    // ConnectFuture
    protected ConnectFuture future;

    // Connected IoSession
    protected IoSession session;

    /** Constructs a new RTMPClient. */
    public RTMPClient() {
        ioHandler = new RTMPMinaIoHandler();
        ioHandler.setHandler(this);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> makeDefaultConnectionParams(String server, int port, String application) {
        Map<String, Object> params = super.makeDefaultConnectionParams(server, port, application);
        if (!params.containsKey("tcUrl")) {
            params.put("tcUrl", String.format("%s://%s:%s/%s", protocol, server, port, application));
        }
        return params;
    }

    /** {@inheritDoc} */
    @Override
    protected void startConnector(String server, int port) {
        log.debug("startConnector - server: {} port: {}", server, port);
        socketConnector = new NioSocketConnector();
        socketConnector.setHandler(ioHandler);
        future = socketConnector.connect(new InetSocketAddress(server, port));
        future.addListener(new IoFutureListener<ConnectFuture>() {
            @Override
            public void operationComplete(ConnectFuture future) {
                try {
                    // will throw RuntimeException after connection error
                    session = future.getSession();
                } catch (Throwable e) {
                    log.warn("Exception in startConnector", e);
                    // disconnect this
                    disconnect();
                    // if there isn't an ClientExceptionHandler set, a RuntimeException may be thrown in handleException
                    handleException(e);
                }
            }
        });
        // Now wait for the connect to be completed
        future.awaitUninterruptibly(CONNECTOR_WORKER_TIMEOUT);
        log.debug("startConnector {} done", Thread.currentThread().getName());
    }

    /** {@inheritDoc} */
    @Override
    public void disconnect() {
        if (future != null) {
            try {
                // session will be null if connect failed
                if (session != null) {
                    // close, now
                    CloseFuture closeFuture = session.closeNow();
                    // now wait for the close to be completed
                    if (closeFuture.await(1000, TimeUnit.MILLISECONDS)) {
                        if (!future.isCanceled()) {
                            if (future.cancel()) {
                                log.debug("Connect future cancelled after close future");
                            }
                        }
                    }
                } else if (future.cancel()) {
                    log.debug("Connect future cancelled");
                }
            } catch (Exception e) {
                log.warn("Exception during disconnect", e);
            } finally {
                // we can now dispose the connector
                socketConnector.dispose(false);
            }
        }
        super.disconnect();
    }

    /**
     * Sets the RTMP protocol, the default is "rtmp". If "rtmps" or "rtmpt" are required, the appropriate client type should be selected.
     *
     * @param protocol
     *            the protocol to set
     * @throws Exception thrown
     */
    @Override
    public void setProtocol(String protocol) throws Exception {
        this.protocol = protocol;
        if ("rtmps".equals(protocol) || "rtmpt".equals(protocol) || "rtmpte".equals(protocol) || "rtmfp".equals(protocol)) {
            throw new Exception("Unsupported protocol specified, please use the correct client for the intended protocol.");
        }
    }
}
