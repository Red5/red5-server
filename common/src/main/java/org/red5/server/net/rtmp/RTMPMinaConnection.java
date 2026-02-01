/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.rtmp;

import java.beans.ConstructorProperties;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.StandardMBean;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.future.CloseFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.session.IoSession;
import org.red5.server.api.IConnection;
import org.red5.server.api.scope.IScope;
import org.red5.server.jmx.mxbeans.RTMPMinaConnectionMXBean;
import org.red5.server.net.rtmp.codec.RTMP;
import org.red5.server.net.rtmp.event.ClientBW;
import org.red5.server.net.rtmp.event.ServerBW;
import org.red5.server.net.rtmp.message.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jmx.export.annotation.ManagedResource;

/**
 * Represents an RTMP connection using Mina.
 *
 * @see "http://mina.apache.org/report/trunk/apidocs/org/apache/mina/core/session/IoSession.html"
 * @author Paul Gregoire
 */
@ManagedResource
public class RTMPMinaConnection extends RTMPConnection implements RTMPMinaConnectionMXBean {

    /** Constant <code>log</code> */
    protected static Logger log = LoggerFactory.getLogger(RTMPMinaConnection.class);

    /**
     * Closing flag
     */
    private final AtomicBoolean closing = new AtomicBoolean(false);

    /**
     * MINA I/O session, connection between two end points
     */
    private transient IoSession ioSession;

    /**
     * MBean object name used for de/registration purposes.
     */
    private ObjectName oName;

    protected int defaultServerBandwidth = 10000000;

    protected int defaultClientBandwidth = 10000000;

    protected boolean bandwidthDetection = true;

    /**
     * Constructs a new RTMPMinaConnection.
     */
    @ConstructorProperties(value = { "persistent" })
    public RTMPMinaConnection() {
        super(IConnection.Type.PERSISTENT.name().toLowerCase());
    }

    /** {@inheritDoc} */
    @Override
    public boolean connect(IScope newScope, Object[] params) {
        log.debug("Connect scope: {}", newScope);
        boolean success = super.connect(newScope, params);
        if (success) {
            // get control channel if available
            Optional<Channel> opt = Optional.ofNullable(getChannel(2));
            if (opt.isPresent()) {
                Channel channel = opt.get();
                // tell the flash player how fast we want data and how fast we shall send it
                channel.write(new ServerBW(defaultServerBandwidth));
                // second param is the limit type (0=hard,1=soft,2=dynamic)
                channel.write(new ClientBW(defaultClientBandwidth, (byte) limitType));
            }
            // if the client is null for some reason, skip the jmx registration
            if (client != null) {
                // perform bandwidth detection
                if (bandwidthDetection && !client.isBandwidthChecked()) {
                    client.checkBandwidth();
                }
                registerJMX();
            } else {
                log.warn("Client was null");
            }
        } else {
            log.debug("Connect failed");
        }
        return success;
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        if (closing.compareAndSet(false, true)) {
            super.closeInternal();
            log.debug("IO Session closing: {}", (ioSession != null ? ioSession.isClosing() : null));
            if (ioSession != null && !ioSession.isClosing()) {
                // set a ref to ourself so that the handler can be notified when close future is done
                final RTMPMinaConnection self = this;
                // close now, no flushing, no waiting
                final CloseFuture future = ioSession.closeNow();
                log.debug("Connection close future: {}", future);
                IoFutureListener<CloseFuture> listener = new IoFutureListener<CloseFuture>() {
                    public void operationComplete(CloseFuture future) {
                        if (future.isClosed()) {
                            log.info("Connection is closed: {}", getSessionId());
                            if (log.isTraceEnabled()) {
                                log.trace("Session id - local: {} session: {}", getSessionId(), (String) ioSession.getAttribute(RTMPConnection.RTMP_SESSION_ID));
                            }
                            handler.connectionClosed(self);
                        } else {
                            log.debug("Connection is not yet closed");
                        }
                        future.removeListener(this);
                    }
                };
                future.addListener(listener);
            }
            log.debug("Connection state: {}", getState());
            if (getStateCode() != RTMP.STATE_DISCONNECTED) {
                handler.connectionClosed(this);
            }
            // de-register with JMX
            unregisterJMX();
        } else if (log.isDebugEnabled()) {
            log.debug("Close has already been called");
        }
    }

    /**
     * {@inheritDoc}
     *
     * Return MINA I/O session.
     */
    @Override
    public IoSession getIoSession() {
        return ioSession;
    }

    /**
     * <p>Getter for the field <code>defaultServerBandwidth</code>.</p>
     *
     * @return the defaultServerBandwidth
     */
    public int getDefaultServerBandwidth() {
        return defaultServerBandwidth;
    }

    /**
     * <p>Setter for the field <code>defaultServerBandwidth</code>.</p>
     *
     * @param defaultServerBandwidth
     *            the defaultServerBandwidth to set
     */
    public void setDefaultServerBandwidth(int defaultServerBandwidth) {
        this.defaultServerBandwidth = defaultServerBandwidth;
    }

    /**
     * <p>Getter for the field <code>defaultClientBandwidth</code>.</p>
     *
     * @return the defaultClientBandwidth
     */
    public int getDefaultClientBandwidth() {
        return defaultClientBandwidth;
    }

    /**
     * <p>Setter for the field <code>defaultClientBandwidth</code>.</p>
     *
     * @param defaultClientBandwidth
     *            the defaultClientBandwidth to set
     */
    public void setDefaultClientBandwidth(int defaultClientBandwidth) {
        this.defaultClientBandwidth = defaultClientBandwidth;
    }

    /**
     * <p>getLimitType.</p>
     *
     * @return the limitType
     */
    public int getLimitType() {
        return limitType;
    }

    /**
     * <p>setLimitType.</p>
     *
     * @param limitType
     *            the limitType to set
     */
    public void setLimitType(int limitType) {
        this.limitType = limitType;
    }

    /**
     * <p>isBandwidthDetection.</p>
     *
     * @return the bandwidthDetection
     */
    public boolean isBandwidthDetection() {
        return bandwidthDetection;
    }

    /**
     * <p>Setter for the field <code>bandwidthDetection</code>.</p>
     *
     * @param bandwidthDetection
     *            the bandwidthDetection to set
     */
    public void setBandwidthDetection(boolean bandwidthDetection) {
        this.bandwidthDetection = bandwidthDetection;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isReaderIdle() {
        if (ioSession != null) {
            return ioSession.isReaderIdle();
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isWriterIdle() {
        if (ioSession != null) {
            return ioSession.isWriterIdle();
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public long getPendingMessages() {
        if (ioSession != null) {
            return ioSession.getScheduledWriteMessages();
        }
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public long getReadBytes() {
        if (ioSession != null) {
            return ioSession.getReadBytes();
        }
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public long getWrittenBytes() {
        if (ioSession != null) {
            return ioSession.getWrittenBytes();
        }
        return 0;
    }

    /** {@inheritDoc} */
    public void invokeMethod(String method) {
        invoke(method);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isConnected() {
        if (log.isTraceEnabled()) {
            log.trace("Connected: {}", (ioSession != null && ioSession.isConnected()));
        }
        // XXX Paul: not sure isClosing is actually working as we expect here
        return super.isConnected() && (ioSession != null && ioSession.isConnected());
    }

    /** {@inheritDoc} */
    @Override
    public boolean isIdle() {
        // expect an io session if there isnt one, we're idle
        boolean idle = super.isIdle() && (ioSession != null ? ioSession.isBothIdle() : true);
        if (!idle) {
            // get io time
            long ioTime = System.currentTimeMillis() - ioSession.getLastIoTime();
            if (log.isDebugEnabled()) {
                log.debug("Session last io time: {} ms", ioTime);
                log.debug("Connection idle - read: {} write: {}", ioSession.isReaderIdle(), ioSession.isWriterIdle());
                if (log.isTraceEnabled()) {
                    log.trace("Session - write queue: {} session count: {}", ioSession.getWriteRequestQueue().size(), ioSession.getService().getManagedSessionCount());
                }
            }
            // if exceeds max inactivity kill and clean up
            if (ioTime >= maxInactivity) {
                log.warn("Connection {} has exceeded the max inactivity threshold of {} ms", getSessionId(), maxInactivity);
                log.debug("Prepared to clear write queue, if session is connected: {}; closing? {}", ioSession.isConnected(), ioSession.isClosing());
                if (ioSession.isConnected()) {
                    // clear the write queue
                    ioSession.getWriteRequestQueue().clear(ioSession);
                }
                idle = true;
            }
        }
        if (idle) {
            log.debug("Connection is idle");
            // fire inactive event
            onInactive();
        }
        return idle;
    }

    /** {@inheritDoc} */
    @Override
    protected void onInactive() {
        close();
    }

    /**
     * Setter for MINA I/O session (connection).
     *
     * @param protocolSession
     *            Protocol session
     */
    public void setIoSession(IoSession protocolSession) {
        SocketAddress remote = protocolSession.getRemoteAddress();
        if (remote instanceof InetSocketAddress) {
            remoteAddress = ((InetSocketAddress) remote).getAddress().getHostAddress();
            remotePort = ((InetSocketAddress) remote).getPort();
        } else {
            remoteAddress = remote.toString();
            remotePort = -1;
        }
        remoteAddresses = new ArrayList<String>(1);
        remoteAddresses.add(remoteAddress);
        remoteAddresses = Collections.unmodifiableList(remoteAddresses);
        this.ioSession = protocolSession;
        if (log.isTraceEnabled()) {
            log.trace("setIoSession conn: {}", this);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void write(Packet out) {
        if (ioSession != null) {
            final Semaphore lock = getLock();
            //if (log.isTraceEnabled()) {
            //    log.trace("Write lock wait count: {} closed: {}", lock.getQueueLength(), isClosed());
            //}
            while (state.getState() < RTMP.STATE_ERROR) {
                boolean acquired = false;
                try {
                    acquired = lock.tryAcquire(10, TimeUnit.MILLISECONDS);
                    if (acquired) { // attempt write if not closing
                        if (!ioSession.isClosing()) {
                            if (log.isTraceEnabled()) {
                                log.trace("Writing message");
                            }
                            writingMessage(out);
                            ioSession.write(out);
                        }
                        break;
                    }
                } catch (InterruptedException e) {
                    log.warn("Interrupted while waiting for write lock. State: {}", RTMP.states[state.getState()], e);
                    if (log.isInfoEnabled()) {
                        // further debugging to assist with possible connection problems
                        log.info("Session id: {} in queue size: {} pending msgs: {} last ping/pong: {}", getSessionId(), currentQueueSize(), getPendingMessages(), getLastPingSentAndLastPongReceivedInterval());
                        log.info("Available permits - decoder: {} encoder: {}", decoderLock.availablePermits(), encoderLock.availablePermits());
                    }
                    String exMsg = e.getMessage(); // if the exception cause is null break out of here to prevent looping until closed
                    if (exMsg == null || exMsg.indexOf("null") >= 0) {
                        log.debug("Exception writing to connection: {}", this);
                        break;
                    }
                } finally {
                    if (acquired) {
                        lock.release();
                    }
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void writeRaw(IoBuffer out) {
        if (ioSession != null) {
            final Semaphore lock = getLock();
            while (state.getState() < RTMP.STATE_ERROR) {
                boolean acquired = false;
                try {
                    acquired = lock.tryAcquire(10, TimeUnit.MILLISECONDS);
                    if (acquired) {
                        if (log.isTraceEnabled()) {
                            log.trace("Writing raw message");
                        }
                        ioSession.write(out);
                        break;
                    }
                } catch (InterruptedException e) {
                    log.warn("Interrupted while waiting for write lock (writeRaw). State: {}", RTMP.states[state.getState()], e);
                    String exMsg = e.getMessage();
                    // if the exception cause is null break out of here to prevent looping until closed
                    if (exMsg == null || exMsg.indexOf("null") >= 0) {
                        log.debug("Exception writing to connection: {}", this);
                        break;
                    }
                } finally {
                    if (acquired) {
                        lock.release();
                    }
                }
            }
        }
    }

    /**
     * <p>registerJMX.</p>
     */
    protected void registerJMX() {
        // register with jmx
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            String cName = this.getClass().getName();
            if (cName.indexOf('.') != -1) {
                cName = cName.substring(cName.lastIndexOf('.')).replaceFirst("[\\.]", "");
            }
            String hostStr = host;
            int port = 1935;
            if (host != null && host.indexOf(":") > -1) {
                String[] arr = host.split(":");
                hostStr = arr[0];
                port = Integer.parseInt(arr[1]);
            }
            // Create a new mbean for this instance
            oName = new ObjectName(String.format("org.red5.server:type=%s,connectionType=%s,host=%s,port=%d,clientId=%s", cName, type, hostStr, port, client.getId()));
            if (!mbs.isRegistered(oName)) {
                mbs.registerMBean(new StandardMBean(this, RTMPMinaConnectionMXBean.class, true), oName);
            } else {
                log.debug("Connection is already registered in JMX");
            }
        } catch (Exception e) {
            log.warn("Error on jmx registration", e);
        }
    }

    /**
     * <p>unregisterJMX.</p>
     */
    protected void unregisterJMX() {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        if (oName != null && mbs.isRegistered(oName)) {
            try {
                mbs.unregisterMBean(oName);
            } catch (Exception e) {
                log.warn("Exception unregistering: {}", oName, e);
            }
            oName = null;
        }
    }

    /**
     * <p>dumpInfo.</p>
     */
    public void dumpInfo() {
        log.trace("{} session: {} state: {} keep-alive running: {}", new Object[] { getClass().getSimpleName(), getSessionId(), RTMP.states[getStateCode()], running });
        log.trace("Decoder lock - permits: {} queue length: {}", decoderLock.availablePermits(), decoderLock.getQueueLength());
        log.trace("Encoder lock - permits: {} queue length: {}", encoderLock.availablePermits(), encoderLock.getQueueLength());
        log.trace("Client streams: {} used: {}", getStreams().size(), getUsedStreamCount());
        if (!getAttributes().isEmpty()) {
            log.trace("Attributes: {}", getAttributes());
        }
        getBasicScopes().forEachRemaining(scope -> {
            log.trace("Scope: {}", scope);
        });
    }

}
