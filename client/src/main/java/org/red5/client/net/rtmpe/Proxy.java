/*
 * RED5 Open Source Flash Server - http://code.google.com/p/red5/ Copyright 2006-2012 by respective authors (see below). All rights reserved. Licensed under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.client.net.rtmpe;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.red5.client.net.rtmp.ClientState;
import org.red5.client.net.rtmp.IClientListener;
import org.red5.client.net.rtmp.INetStreamEventHandler;
import org.red5.client.net.rtmp.RTMPClient;
import org.red5.io.utils.ObjectMap;
import org.red5.server.api.Red5;
import org.red5.server.api.service.IPendingServiceCall;
import org.red5.server.api.service.IPendingServiceCallback;
import org.red5.server.messaging.IPipeConnectionListener;
import org.red5.server.messaging.PipeConnectionEvent;
import org.red5.server.net.rtmp.event.Aggregate;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.net.rtmp.event.IRTMPEvent;
import org.red5.server.net.rtmp.event.Notify;
import org.red5.server.net.rtmp.event.VideoData;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.net.rtmp.message.Header;
import org.red5.server.net.rtmp.status.StatusCodes;
import org.red5.server.stream.message.RTMPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A proxy for publishing an RTMPE stream to an RTMP stream.
 *
 * @author Andy Shaules (bowljoman@hotmail.com)
 * @author Paul Gregoire
 */
public class Proxy implements IClientListener {

    private static Logger log = LoggerFactory.getLogger(Proxy.class);

    private ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);

    private volatile Queue<IRTMPEvent> queue = new ConcurrentLinkedQueue<IRTMPEvent>();

    private volatile ClientState state = ClientState.UNINIT;

    private ScheduledFuture<?> future;

    private String host = "localhost";

    private int port = 1935;

    private String app;

    private String publishName;

    private String publishMode;

    private int streamId;

    // whether or not to break-up the aggregate events
    private boolean deaggregate = true;

    // whether or not to use the FMLE / CDN style publish command
    private boolean useFCPublish = false;

    /**
     * Starts the process of proxying data.
     *
     * @param publishName
     * @param publishMode
     */
    public void start(String publishName, String publishMode) {
        this.publishName = publishName;
        this.publishMode = publishMode;
        future = scheduledExecutor.scheduleAtFixedRate(new ProxyWorker(), 1000, 100, TimeUnit.MILLISECONDS);
    }

    /**
     * Stops proxying.
     */
    public void stop() {
        // delay until worker runs again
        long delay = future.getDelay(TimeUnit.MILLISECONDS);
        log.debug("Before publish - delay: {} ms", delay);
        // sleep for delay
        if (delay > 0) {
            try {
                Thread.sleep(delay + 1L);
            } catch (InterruptedException e) {
            }
        }
        // set to stopped state
        state = ClientState.STOPPED;
        // get latest delay
        delay = future.getDelay(TimeUnit.MILLISECONDS);
        log.debug("After publish - delay: {} ms", delay);
        // sleep for delay
        try {
            Thread.sleep(delay + 1L);
        } catch (InterruptedException e) {
        }
        // cancel the worker
        future.cancel(false);
    }

    /**
     * Sets the host to proxy to.
     *
     * @param host
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * Sets the port to proxy to.
     *
     * @param port
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Sets the applicaiton to proxy to.
     *
     * @param app
     */
    public void setApp(String app) {
        this.app = app;
    }

    /** {@inheritDoc} */
    public void onClientListenerEvent(IRTMPEvent rtmpEvent) {
        log.debug("onClientListenerEvent: {}", rtmpEvent);
        // disregard events that are too small
        if (rtmpEvent.getHeader().getSize() > 0) {
            // make a copy and put it in the buffer
            try {
                if (rtmpEvent instanceof Aggregate) {
                    queue.add(((Aggregate) rtmpEvent).duplicate());
                } else if (rtmpEvent instanceof AudioData) {
                    queue.add(((AudioData) rtmpEvent).duplicate());
                } else if (rtmpEvent instanceof VideoData) {
                    queue.add(((VideoData) rtmpEvent).duplicate());
                } else {
                    log.debug("Unprocessed type: {}", rtmpEvent);
                }
            } catch (Exception e) {
                log.warn("Exception during run", e);
            }
        } else {
            log.debug("Disregarding small event");
        }
    }

    /** {@inheritDoc} */
    public void stopListening() {
        log.debug("stopListening, client is finished providing data");
        stop();
    }

    private final class ProxyWorker implements Runnable, IPendingServiceCallback, INetStreamEventHandler, IPipeConnectionListener {

        RTMPClient client;

        @Override
        public void run() {
            log.trace("ProxyWorker - run");
            try {
                if (state == ClientState.PUBLISHING) {
                    log.trace("Publishing message");
                    IRTMPEvent event = null;
                    RTMPMessage message = null;
                    while (!queue.isEmpty()) {
                        event = queue.poll();
                        if (event != null) {
                            // get the header
                            Header header = event.getHeader();
                            log.debug("Header: {}", header);
                            byte dataType = header.getDataType();
                            switch (dataType) {
                                case Constants.TYPE_AGGREGATE:
                                    if (deaggregate) {
                                        // this block will break-up the aggregate into its individual parts
                                        Aggregate aggregate = (Aggregate) event;
                                        int aggTimestamp = event.getTimestamp();
                                        log.debug("Timestamp (aggregate): {}", aggTimestamp);
                                        LinkedList<IRTMPEvent> parts = aggregate.getParts();
                                        for (IRTMPEvent part : parts) {
                                            // get the timestamp
                                            int partTimestamp = part.getTimestamp();
                                            log.debug("Timestamp (part): {}", partTimestamp);
                                            // create an rtmp message
                                            message = RTMPMessage.build(part);
                                            // send it
                                            client.publishStreamData(streamId, message);
                                        }
                                        break;
                                    }
                                case Constants.TYPE_AUDIO_DATA:
                                case Constants.TYPE_VIDEO_DATA:
                                    //case Constants.TYPE_STREAM_METADATA:
                                    // get the timestamp
                                    int timestamp = event.getTimestamp();
                                    log.debug("Timestamp (a/v): {}", timestamp);
                                    // create an rtmp message
                                    message = RTMPMessage.build(event);
                                    // send it
                                    client.publishStreamData(streamId, message);
                                    break;
                                default:
                                    log.debug("Data type not processed: {}", dataType);
                            }
                        } else {
                            break;
                        }
                    }
                } else if (state == ClientState.UNINIT) {
                    client = new RTMPClient();
                    state = ClientState.CONNECTING;
                    Map<String, Object> defParams = client.makeDefaultConnectionParams(host, port, app);
                    client.connect(host, port, defParams, this, new Object[0]);
                } else if (state == ClientState.STOPPED) {
                    client.unpublish(streamId);
                    client.disconnect();
                } else {
                    log.debug("Queue was empty or we are not in the publish state, current state: {}", state);
                }
            } catch (Throwable t) {
                log.warn("Exception during run", t);
            }
            log.trace("ProxyWorker - end");
        }

        public void resultReceived(IPendingServiceCall call) {
            log.debug("resultReceived: {}", call);
            String methodName = call.getServiceMethodName();
            log.debug("Method: {}", methodName);
            if ("connect".equals(methodName)) {
                state = ClientState.CONNECTING;
                client.createStream(this);
            } else if ("createStream".equals(methodName)) {
                state = ClientState.STREAM_CREATING;
                Object result = call.getResult();
                if (result instanceof Integer) {
                    streamId = (Integer) result;
                    log.debug("Publish - stream id: {} state: {}", streamId, state);
                    client.publish(streamId, publishName, publishMode, this);
                } else {
                    stop();
                }
            } else if ("publish".equals(methodName) || "FCPublish".equals(methodName)) {
                state = ClientState.PUBLISHING;
            }
        }

        public void onStreamEvent(Notify notify) {
            log.debug("onStreamEvent: {}", notify);
            ObjectMap<?, ?> map = (ObjectMap<?, ?>) notify.getCall().getArguments()[0];
            String code = (String) map.get("code");
            log.debug("Code: {}", code);
            if (StatusCodes.NS_PUBLISH_START.equals(code)) {
                // cdn's appear to need this call to work properly
                if (useFCPublish) {
                    client.invoke("FCPublish", new Object[] { publishName }, this);
                } else {
                    // if we are not talking to a cdn just set to publishing state here
                    // instead of waiting for a result from fms/red5
                    state = ClientState.PUBLISHING;
                }
            } else if (StatusCodes.NC_CONNECT_SUCCESS.equals(code)) {
                state = ClientState.CONNECTED;
                // set the connection local
                Red5.setConnectionLocal(client.getConnection());
            } else if (StatusCodes.NS_PLAY_STOP.equals(code)) {
                log.debug("NetStream.Play.Stop, disconnecting");
                if (state != ClientState.STOPPED) {
                    stop();
                }
            }
        }

        public void onPipeConnectionEvent(PipeConnectionEvent event) {
            // nothing to do
        }

    }

}
