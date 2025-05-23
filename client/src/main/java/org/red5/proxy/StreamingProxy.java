/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2015 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.proxy;

import java.io.IOException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import org.red5.client.net.rtmp.ClientExceptionHandler;
import org.red5.client.net.rtmp.INetStreamEventHandler;
import org.red5.client.net.rtmp.RTMPClient;
import org.red5.client.net.rtmpe.RTMPEClient;
import org.red5.client.net.rtmps.RTMPSClient;
import org.red5.io.utils.ObjectMap;
import org.red5.server.api.service.IPendingServiceCall;
import org.red5.server.api.service.IPendingServiceCallback;
import org.red5.server.messaging.IMessage;
import org.red5.server.messaging.IMessageComponent;
import org.red5.server.messaging.IPipe;
import org.red5.server.messaging.IPipeConnectionListener;
import org.red5.server.messaging.IPushableConsumer;
import org.red5.server.messaging.OOBControlMessage;
import org.red5.server.messaging.PipeConnectionEvent;
import org.red5.server.net.rtmp.event.Notify;
import org.red5.server.net.rtmp.status.StatusCodes;
import org.red5.server.stream.message.RTMPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A proxy to publish stream from server to server.
 *
 * TODO: Use timer to monitor the connect/stream creation.
 *
 * @author Steven Gong (steven.gong@gmail.com)
 * @author Andy Shaules (bowljoman@hotmail.com)
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class StreamingProxy implements IPushableConsumer, IPipeConnectionListener, INetStreamEventHandler, IPendingServiceCallback {

    private static Logger log = LoggerFactory.getLogger(StreamingProxy.class);

    private ConcurrentLinkedQueue<IMessage> frameBuffer = new ConcurrentLinkedQueue<>();

    private String host;

    private int port;

    private String app;

    private RTMPClient rtmpClient;

    private StreamState state = StreamState.UNINITIALIZED;

    private String publishName;

    private Number streamId;

    private String publishMode;

    private final Semaphore lock = new Semaphore(1, true);

    // task timer
    private static Timer timer;

    /**
     * <p>init.</p>
     */
    public void init() {
        init(ClientType.RTMP);
    }

    /**
     * <p>init.</p>
     *
     * @param clientType a {@link org.red5.proxy.ClientType} object
     */
    public void init(ClientType clientType) {
        switch (clientType) {
            case RTMPE:
                rtmpClient = new RTMPEClient();
                break;
            case RTMPS:
                rtmpClient = new RTMPSClient();
                break;
            case RTMP:
            default:
                rtmpClient = new RTMPClient();
                break;
        }
        log.debug("Initialized: {}", rtmpClient);
        setState(StreamState.STOPPED);
        // create a timer
        timer = new Timer();
    }

    /**
     * <p>start.</p>
     *
     * @param publishName a {@link java.lang.String} object
     * @param publishMode a {@link java.lang.String} object
     * @param params an array of {@link java.lang.Object} objects
     */
    public void start(String publishName, String publishMode, Object[] params) {
        setState(StreamState.CONNECTING);
        this.publishName = publishName;
        this.publishMode = publishMode;
        // construct the default params
        Map<String, Object> defParams = rtmpClient.makeDefaultConnectionParams(host, port, app);
        defParams.put("swfUrl", "app:/Red5-StreamProxy.swf");
        //defParams.put("pageUrl", String.format("http://%s:%d/%s", host, port, app));
        defParams.put("pageUrl", "");
        rtmpClient.setSwfVerification(true);
        // set this as the netstream handler
        rtmpClient.setStreamEventHandler(this);
        // connect the client
        rtmpClient.connect(host, port, defParams, this, params);
    }

    /**
     * <p>stop.</p>
     */
    public void stop() {
        timer.cancel();
        if (state != StreamState.STOPPED) {
            rtmpClient.disconnect();
        }
        setState(StreamState.STOPPED);
        frameBuffer.clear();
    }

    private void createStream() {
        setState(StreamState.STREAM_CREATING);
        rtmpClient.createStream(this);
    }

    /** {@inheritDoc} */
    @Override
    public void onPipeConnectionEvent(PipeConnectionEvent event) {
        log.debug("onPipeConnectionEvent: {}", event);
    }

    /** {@inheritDoc} */
    @Override
    public void pushMessage(IPipe pipe, IMessage message) throws IOException {
        if (isPublished() && message instanceof RTMPMessage) {
            RTMPMessage rtmpMsg = (RTMPMessage) message;
            rtmpClient.publishStreamData(streamId, rtmpMsg);
        } else {
            log.trace("Adding message to buffer. Current size: {}", frameBuffer.size());
            frameBuffer.add(message);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onOOBControlMessage(IMessageComponent source, IPipe pipe, OOBControlMessage oobCtrlMsg) {
        log.debug("onOOBControlMessage: {}", oobCtrlMsg);
    }

    /**
     * Called when bandwidth has been configured.
     */
    public void onBWDone() {
        log.debug("onBWDone");
        rtmpClient.onBWDone(null);
    }

    /**
     * <p>Setter for the field <code>host</code>.</p>
     *
     * @param host a {@link java.lang.String} object
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * <p>Setter for the field <code>port</code>.</p>
     *
     * @param port a int
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * <p>Setter for the field <code>app</code>.</p>
     *
     * @param app a {@link java.lang.String} object
     */
    public void setApp(String app) {
        this.app = app;
    }

    /** {@inheritDoc} */
    @Override
    public void onStreamEvent(Notify notify) {
        log.debug("onStreamEvent: {}", notify);
        ObjectMap<?, ?> map = (ObjectMap<?, ?>) notify.getCall().getArguments()[0];
        String code = (String) map.get("code");
        log.debug("<:{}", code);
        if (StatusCodes.NS_PUBLISH_START.equals(code)) {
            setState(StreamState.PUBLISHED);
            IMessage message = null;
            while ((message = frameBuffer.poll()) != null) {
                rtmpClient.publishStreamData(streamId, message);
            }
        } else if (StatusCodes.NS_UNPUBLISHED_SUCCESS.equals(code)) {
            setState(StreamState.UNPUBLISHED);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void resultReceived(IPendingServiceCall call) {
        String method = call.getServiceMethodName();
        log.debug("resultReceived: {}", method);
        if ("connect".equals(method)) {
            //rtmpClient.releaseStream(this, new Object[] { publishName });
            timer.schedule(new BandwidthStatusTask(), 2000L);
        } else if ("releaseStream".equals(method)) {
            //rtmpClient.invoke("FCPublish", new Object[] { publishName }, this);
        } else if ("createStream".equals(method)) {
            setState(StreamState.PUBLISHING);
            Object result = call.getResult();
            if (result instanceof Number) {
                streamId = (Number) result;
                log.debug("Publishing: {}", state);
                rtmpClient.publish(streamId, publishName, publishMode, this);
            } else {
                rtmpClient.disconnect();
                setState(StreamState.STOPPED);
            }
        } else if ("FCPublish".equals(method)) {

        }
    }

    /**
     * <p>Setter for the field <code>state</code>.</p>
     *
     * @param state a {@link org.red5.proxy.StreamState} object
     */
    protected void setState(StreamState state) {
        try {
            lock.acquire();
            this.state = state;
        } catch (InterruptedException e) {
            log.warn("Exception setting state", e);
        } finally {
            lock.release();
        }
    }

    /**
     * <p>Getter for the field <code>state</code>.</p>
     *
     * @return a {@link org.red5.proxy.StreamState} object
     */
    protected StreamState getState() {
        return state;
    }

    /**
     * <p>setConnectionClosedHandler.</p>
     *
     * @param connectionClosedHandler a {@link java.lang.Runnable} object
     */
    public void setConnectionClosedHandler(Runnable connectionClosedHandler) {
        log.debug("setConnectionClosedHandler: {}", connectionClosedHandler);
        if (rtmpClient != null) {
            rtmpClient.setConnectionClosedHandler(connectionClosedHandler);
        } else {
            log.warn("Internal client is null, ensure that init() is called before adding handlers");
        }
    }

    /**
     * <p>setExceptionHandler.</p>
     *
     * @param exceptionHandler a {@link org.red5.client.net.rtmp.ClientExceptionHandler} object
     */
    public void setExceptionHandler(ClientExceptionHandler exceptionHandler) {
        log.debug("setExceptionHandler: {}", exceptionHandler);
        if (rtmpClient != null) {
            rtmpClient.setExceptionHandler(exceptionHandler);
        } else {
            log.warn("Internal client is null, ensure that init() is called before adding handlers");
        }
    }

    /**
     * <p>isPublished.</p>
     *
     * @return a boolean
     */
    public boolean isPublished() {
        return getState().equals(StreamState.PUBLISHED);
    }

    /**
     * <p>isRunning.</p>
     *
     * @return a boolean
     */
    public boolean isRunning() {
        return !getState().equals(StreamState.STOPPED);
    }

    /**
     * Continues to check for onBWDone
     */
    private final class BandwidthStatusTask extends TimerTask {

        @Override
        public void run() {
            // check for onBWDone
            log.debug("Bandwidth check done: {}", rtmpClient.isBandwidthCheckDone());
            // cancel this task
            this.cancel();
            // initiate the stream creation
            createStream();
        }

    }

}
