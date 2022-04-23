/*
 * RED5 Open Source Flash Server - http://code.google.com/p/red5/ Copyright 2006-2012 by respective authors (see below). All rights reserved. Licensed under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.client.net.rtmpe;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.red5.client.net.rtmp.ClientExceptionHandler;
import org.red5.client.net.rtmp.ClientState;
import org.red5.client.net.rtmp.IClientListener;
import org.red5.client.net.rtmp.INetStreamEventHandler;
import org.red5.client.net.rtmp.RTMPClient;
import org.red5.io.utils.ObjectMap;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.event.IEventDispatcher;
import org.red5.server.api.service.IPendingServiceCall;
import org.red5.server.api.service.IPendingServiceCallback;
import org.red5.server.messaging.IMessage;
import org.red5.server.messaging.IMessageComponent;
import org.red5.server.messaging.IPipe;
import org.red5.server.messaging.IPipeConnectionListener;
import org.red5.server.messaging.IPushableConsumer;
import org.red5.server.messaging.OOBControlMessage;
import org.red5.server.messaging.PipeConnectionEvent;
import org.red5.server.net.rtmp.event.IRTMPEvent;
import org.red5.server.net.rtmp.event.Notify;
import org.red5.server.net.rtmp.status.StatusCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RTMPE client object based on the original RTMP client.
 * 
 * @author Paul Gregoire
 * @author Gavriloaie Eugen-Andrei
 */
public class RTMPEClient extends RTMPClient implements INetStreamEventHandler, ClientExceptionHandler, IEventDispatcher, IPushableConsumer, IPipeConnectionListener {

    private static final Logger log = LoggerFactory.getLogger(RTMPEClient.class);

    // list to hold any listeners
    private List<IClientListener> clientListeners = new ArrayList<>(1);

    @SuppressWarnings("unused")
    private ClientState state = ClientState.UNINIT;

    private String streamName;

    private String fileName;

    {
        // set our scheme / protocol
        protocol = "rtmpe";
    }

    /** Constructs a new RTMPEClient */
    public RTMPEClient() {
        super();
        log.debug("Creating client for RTMPE connection");
        // set ourself up as a listener / provider / handler etc..
        setServiceProvider(this);
        setStreamEventDispatcher(this);
        setExceptionHandler(this);
        setConnectionClosedHandler(() -> {
            log.warn("Connection closed");
            disconnect();
        });
    }

    /** {@inheritDoc} */
    @Override
    public void disconnect() {
        log.debug("disconnect");
        for (IClientListener listener : clientListeners) {
            listener.stopListening();
        }
        super.disconnect();
    }

    @Override
    public void handleException(Throwable throwable) {
        log.error("{}", new Object[] { throwable.getCause() });
    }

    /**
     * Adds a listener for receiving rtmp events.
     * 
     * @param clientListener
     */
    public void addClientListener(IClientListener clientListener) {
        clientListeners.add(clientListener);
    }

    /**
     * Returns the name of the stream being utilized.
     * 
     * @return the streamName
     */
    public String getStreamName() {
        return streamName;
    }

    /**
     * Sets the name of the stream being utilized.
     * 
     * @param streamName the streamName to set
     */
    public void setStreamName(String streamName) {
        log.debug("setStreamName: {}", streamName);
        this.streamName = streamName;
        String fileExt = ".flv";
        int colonIdx = streamName.indexOf(':');
        if (colonIdx > 0) {
            fileExt = '.' + streamName.substring(0, colonIdx);
            //strip the beginning of the stream name, since its not good for file names
            streamName = streamName.substring(colonIdx);
        }
        if (streamName.endsWith(fileExt)) {
            this.fileName = streamName;
        } else {
            this.fileName = streamName + fileExt;
        }
    }

    /**
     * Returns the filename if vod is being used.
     * 
     * @return the fileName
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Sets the filename for vod.
     * 
     * @param fileName the fileName to set
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /** {@inheritDoc} */
    @Override
    public void onOOBControlMessage(IMessageComponent source, IPipe pipe, OOBControlMessage oobCtrlMsg) {
    }

    /** {@inheritDoc} */
    @Override
    public void onPipeConnectionEvent(PipeConnectionEvent event) {
    }

    /** {@inheritDoc} */
    @Override
    public void pushMessage(IPipe pipe, IMessage message) throws IOException {
    }

    /** {@inheritDoc} */
    @Override
    public void dispatchEvent(IEvent event) {
        log.debug("dispatchEvent - event: {}", event);
        if (event instanceof IRTMPEvent) {
            IRTMPEvent rtmpEvent = (IRTMPEvent) event;
            log.debug("RTMP event - class: {} header: {}", rtmpEvent.getClass().getSimpleName(), rtmpEvent.getHeader());
            for (IClientListener listener : clientListeners) {
                listener.onClientListenerEvent(rtmpEvent);
            }
        } else {
            log.debug("Skipping non rtmp event: {}", event);
        }
    }

    /**
     * Callback method fired when a NetStatusEvent is detected.
     * 
     * {@link http://help.adobe.com/en_US/FlashPlatform/reference/actionscript/3/flash/events/NetStatusEvent.html}
     * @param status
     */
    public void onStatus(Object status) {
        log.debug("onStatus - status: {}", status);
        @SuppressWarnings("rawtypes")
        ObjectMap map = (ObjectMap) status;
        String code = (String) map.get("code");
        log.debug("code: {}", code);
        String description = (String) map.get("description");
        log.debug("description: {}", description);
        String details = (String) map.get("details");
        log.debug("details: {}", details);
        if (StatusCodes.NS_PLAY_START.equals(code)) {
            log.debug("NetStream.Play.Start, start of playback");
            // do playback

        } else if (StatusCodes.NS_PUBLISH_START.equals(code)) {
            // do publishing

        } else if (StatusCodes.NS_PLAY_STOP.equals(code)) {
            log.debug("NetStream.Play.Stop, disconnecting");
            disconnect();
        } else if (StatusCodes.NS_PLAY_RESET.equals(code)) {
            log.debug("NetStream.Play.Reset, pre-start of playback");

        } else if (StatusCodes.NS_BUFFER_EMPTY.equals(code)) {
            log.debug("NetStream.Buffer.Empty");
        }
    }

    /**
     * Callback method fired when a Notify or other stream event is detected.
     * 
     * @param notify
     */
    @Override
    public void onStreamEvent(Notify notify) {
        log.debug("onStreamEvent - notify: {}", notify);
    }

    /**
     * Callback method fired when a MetadataEvent is detected.
     * 
     * {@link http://help.adobe.com/en_US/FlashPlatform/reference/actionscript/3/mx/events/MetadataEvent.html}
     * {@link http://help.adobe.com/en_US/FlashPlatform/reference/actionscript/3/mx/controls/VideoDisplay.html#metadata}
     * @param object metadata from a stream.
     */
    public void onMetaData(Object object) {
        log.debug("onMetaData: {}", object);
    }

    /**
     * Callback handler.
     */
    final class ClientCallback implements IPendingServiceCallback {

        private RTMPEClient client;

        ClientCallback(RTMPEClient client) {
            this.client = client;
        }

        /** {@inheritDoc} */
        @Override
        public void resultReceived(IPendingServiceCall call) {
            log.debug("resultReceived - call: {}", call);
            Object result = call.getResult();
            if (result instanceof ObjectMap) {
                @SuppressWarnings("rawtypes")
                ObjectMap map = (ObjectMap) result;
                String code = (String) map.get("code");
                log.debug("code: {}", code);
                if (StatusCodes.NC_CONNECT_SUCCESS.equals(code)) {
                    log.info("Connected");
                    state = ClientState.STREAM_CREATING;
                    createStream(this);
                }
            } else {
                log.debug("Service call result: {}", call);
                String methodName = call.getServiceMethodName();
                log.debug("Service method: {} result type: {}", methodName, result.getClass().getName());
                if ("connect".equals(methodName)) {
                    client.createStream(this);
                } else if ("createStream".equals(methodName)) {
                    if (result instanceof Number) {
                        int streamId = ((Number) result).intValue();
                        log.debug("CreateStream result stream id: {}", streamId);
                        // http://www.adobe.com/livedocs/flash/9.0/ActionScriptLangRefV3/flash/net/NetStream.html#play()
                        // start: The default value is -2, which looks for a live stream, then a recorded stream, and if it finds neither, opens a live stream. 
                        // If -1, plays only a live stream. If 0 or a positive number, plays a recorded stream, beginning start seconds in.
                        int start = -2;
                        // duration: The default value is -1, which plays a live or recorded stream until it ends. If 0, plays a single frame that is start seconds 
                        // from the beginning of a recorded stream. If a positive number, plays a live or recorded stream for len seconds.
                        int duration = -1;
                        //play(streamId, fileName, start, duration);
                        play(streamId, streamName, start, duration);
                        // update to playing
                        state = ClientState.PLAYING;
                    }
                } else {
                    log.debug("Unhandled method: {}", methodName);
                }
            }
        }
    }

    /**
     * Creates a proxy.
     * 
     * @param client
     * @param host destination host
     * @param port destination port
     * @param app destination application
     * @return proxy
     */
    public static Proxy createProxy(RTMPEClient client, String host, int port, String app) {
        // create a proxy
        Proxy proxy = new Proxy();
        // set proxy destination
        proxy.setHost(host);
        proxy.setPort(port);
        proxy.setApp(app);
        // add the proxy as a listener
        client.addClientListener(proxy);
        // return it
        return proxy;
    }

    /**
     * Creates a writer.
     * 
     * @param client
     * @return writer
     */
    public static Writer createWriter(RTMPEClient client) {
        // test output by writing an flv
        Writer writer = new Writer(Paths.get("target", System.currentTimeMillis() + ".flv"));
        client.addClientListener(writer);
        return writer;
    }

}