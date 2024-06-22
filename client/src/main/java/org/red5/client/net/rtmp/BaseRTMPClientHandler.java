/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2015 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.client.net.rtmp;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.red5.io.utils.ObjectMap;
import org.red5.server.api.IConnection;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.event.IEventDispatcher;
import org.red5.server.api.service.IPendingServiceCall;
import org.red5.server.api.service.IPendingServiceCallback;
import org.red5.server.api.service.IServiceCall;
import org.red5.server.api.service.IServiceInvoker;
import org.red5.server.api.so.IClientSharedObject;
import org.red5.server.api.stream.IClientStream;
import org.red5.server.messaging.IMessage;
import org.red5.server.net.ICommand;
import org.red5.server.net.rtmp.BaseRTMPHandler;
import org.red5.server.net.rtmp.Channel;
import org.red5.server.net.rtmp.DeferredResult;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.codec.RTMP;
import org.red5.server.net.rtmp.event.ChunkSize;
import org.red5.server.net.rtmp.event.ClientBW;
import org.red5.server.net.rtmp.event.Invoke;
import org.red5.server.net.rtmp.event.Notify;
import org.red5.server.net.rtmp.event.Ping;
import org.red5.server.net.rtmp.event.SWFResponse;
import org.red5.server.net.rtmp.event.ServerBW;
import org.red5.server.net.rtmp.message.Header;
import org.red5.server.net.rtmp.status.StatusCodes;
import org.red5.server.service.Call;
import org.red5.server.service.MethodNotFoundException;
import org.red5.server.service.PendingCall;
import org.red5.server.service.ServiceInvoker;
import org.red5.server.so.ClientSharedObject;
import org.red5.server.so.SharedObjectMessage;
import org.red5.server.stream.AbstractClientStream;
import org.red5.server.stream.OutputStream;
import org.red5.server.stream.consumer.ConnectionConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Base class for clients (RTMP and RTMPT)
 */
public abstract class BaseRTMPClientHandler extends BaseRTMPHandler implements IRTMPClient {

    private static final Logger log = LoggerFactory.getLogger(BaseRTMPClientHandler.class);

    /**
     * Connection scheme / protocol
     */
    protected String protocol = "rtmp";

    /**
     * Connection parameters
     */
    protected Map<String, Object> connectionParams;

    /**
     * Connect call arguments
     */
    private Object[] connectArguments;

    /**
     * Connection callback
     */
    private IPendingServiceCallback connectCallback;

    /**
     * Service provider
     */
    private Object serviceProvider;

    /**
     * Service invoker
     */
    private IServiceInvoker serviceInvoker = new ServiceInvoker();

    /**
     * Shared objects map
     */
    private volatile ConcurrentMap<String, ClientSharedObject> sharedObjects = new ConcurrentHashMap<>(1, 0.9f, 1);

    /**
     * Net stream handling
     */
    private volatile CopyOnWriteArraySet<NetStreamPrivateData> streamDataList = new CopyOnWriteArraySet<>();

    /**
     * Task to start on connection close
     */
    private Runnable connectionClosedHandler;

    /**
     * Task to start on connection errors
     */
    private ClientExceptionHandler exceptionHandler;

    /**
     * Stream event dispatcher
     */
    private IEventDispatcher streamEventDispatcher;

    /**
     * Stream event handler
     */
    private INetStreamEventHandler streamEventHandler;

    /**
     * Associated RTMP connection
     */
    protected volatile RTMPConnection conn;

    /**
     * Whether or not the bandwidth done has been invoked
     */
    protected boolean bandwidthCheckDone;

    /**
     * Whether or not the client is subscribed
     */
    protected boolean subscribed;

    /**
     * Whether or not to use swf verification
     */
    private boolean swfVerification;

    private int bytesReadWindow = 2500000;

    private int bytesWrittenWindow = 2500000;

    /**
     * For handling threading / scheduling in the clients.
     */
    protected ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

    protected BaseRTMPClientHandler() {
    }

    /**
     * Start network connection to server
     *
     * @param server
     *            Server
     * @param port
     *            Connection port
     */
    protected abstract void startConnector(String server, int port);

    /**
     * Connect RTMP client to server's application via given port
     *
     * @param server
     *            Server
     * @param port
     *            Connection port
     * @param application
     *            Application at that server
     */
    @Override
    public void connect(String server, int port, String application) {
        //log.debug("connect server: {} port {} application {}", new Object[] { server, port, application });
        connect(server, port, application, null);
    }

    /**
     * Connect RTMP client to server's application via given port with given connection callback
     *
     * @param server
     *            Server
     * @param port
     *            Connection port
     * @param application
     *            Application at that server
     * @param connectCallback
     *            Connection callback
     */
    @Override
    public void connect(String server, int port, String application, IPendingServiceCallback connectCallback) {
        //log.debug("connect server: {} port {} application {} connectCallback {}", new Object[] { server, port, application, connectCallback });
        connect(server, port, makeDefaultConnectionParams(server, port, application), connectCallback);
    }

    /**
     * Creates the default connection parameters collection. Many implementations of this handler will create a tcUrl if not found, it is
     * created with the current server url.
     *
     * @param server
     *            the server location
     * @param port
     *            the port for the protocol
     * @param application
     *            the application name at the given server
     * @return connection parameters map
     */
    @Override
    public Map<String, Object> makeDefaultConnectionParams(String server, int port, String application) {
        Map<String, Object> params = new ObjectMap<>();
        params.put("app", application);
        params.put("objectEncoding", Integer.valueOf(0));
        params.put("fpad", Boolean.FALSE);
        params.put("flashVer", "FMLE/3.0 (compatible; Red5Client)"); // old value WIN 11,2,202,235
        params.put("audioCodecs", Integer.valueOf(0x0FFF)); // old value 3575 = 0x0E0F
        params.put("videoFunction", Integer.valueOf(1));
        params.put("pageUrl", null);
        params.put("path", application);
        params.put("capabilities", Integer.valueOf(15));
        params.put("swfUrl", null);
        params.put("videoCodecs", Integer.valueOf(0x00FF)); // old value 252 = 0x0FC
        params.put("audioFourCcInfoMap", Collections.singletonMap("*", Integer.valueOf(4)));
        //params.put("audioFourCcInfoMap", Collections.singletonMap(AudioCodec.AAC.getFourcc(), Integer.valueOf(4)));
        params.put("videoFourCcInfoMap", Collections.singletonMap("*", Integer.valueOf(4)));
        //params.put("videoFourCcInfoMap", Collections.singletonMap(VideoCodec.AVC.getFourcc(), Integer.valueOf(4)));
        return params;
    }

    /**
     * Connect RTMP client to server via given port and with given connection parameters
     *
     * @param server
     *            Server
     * @param port
     *            Connection port
     * @param connectionParams
     *            Connection parameters
     */
    @Override
    public void connect(String server, int port, Map<String, Object> connectionParams) {
        //log.debug("connect server: {} port {} connectionParams {}", new Object[] { server, port, connectionParams });
        connect(server, port, connectionParams, null);
    }

    /**
     * Connect RTMP client to server's application via given port
     *
     * @param server
     *            Server
     * @param port
     *            Connection port
     * @param connectionParams
     *            Connection parameters
     * @param connectCallback
     *            Connection callback
     */
    @Override
    public void connect(String server, int port, Map<String, Object> connectionParams, IPendingServiceCallback connectCallback) {
        connect(server, port, connectionParams, connectCallback, null);
    }

    /**
     * Connect RTMP client to server's application via given port
     *
     * @param server
     *            Server
     * @param port
     *            Connection port
     * @param connectionParams
     *            Connection parameters
     * @param connectCallback
     *            Connection callback
     * @param connectCallArguments
     *            Arguments for 'connect' call
     */
    @Override
    public void connect(String server, int port, Map<String, Object> connectionParams, IPendingServiceCallback connectCallback, Object[] connectCallArguments) {
        log.debug("connect server: {} port {} connect - params: {} callback: {} args: {}", new Object[] { server, port, connectionParams, connectCallback, Arrays.toString(connectCallArguments) });
        log.info("{}://{}:{}/{}", new Object[] { protocol, server, port, connectionParams.get("app") });
        this.connectionParams = connectionParams;
        this.connectArguments = connectCallArguments;
        if (!connectionParams.containsKey("objectEncoding")) {
            connectionParams.put("objectEncoding", 0);
        }
        this.connectCallback = connectCallback;
        startConnector(server, port);
    }

    /**
     * Register object that provides methods that can be called by the server.
     *
     * @param serviceProvider
     *            Service provider
     */
    @Override
    public void setServiceProvider(Object serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    /**
     * Sets a handler for connection close.
     *
     * @param connectionClosedHandler
     *            close handler
     */
    @Override
    public void setConnectionClosedHandler(Runnable connectionClosedHandler) {
        log.debug("setConnectionClosedHandler: {}", connectionClosedHandler);
        this.connectionClosedHandler = connectionClosedHandler;
    }

    /**
     * Sets a handler for exceptions.
     *
     * @param exceptionHandler
     *            exception handler
     */
    @Override
    public void setExceptionHandler(ClientExceptionHandler exceptionHandler) {
        log.debug("setExceptionHandler: {}", exceptionHandler);
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * Connect to client shared object.
     *
     * @param name
     *            Client shared object name
     * @param persistent
     *            SO persistence flag
     * @return Client shared object instance
     */
    @Override
    public IClientSharedObject getSharedObject(String name, boolean persistent) {
        log.debug("getSharedObject name: {} persistent {}", new Object[] { name, persistent });
        ClientSharedObject result = sharedObjects.get(name);
        if (result != null) {
            if (result.isPersistent() != persistent) {
                throw new RuntimeException("Already connected to a shared object with this name, but with different persistence.");
            }
            return result;
        }
        result = new ClientSharedObject(name, persistent);
        sharedObjects.put(name, result);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    protected void onChunkSize(RTMPConnection conn, Channel channel, Header source, ChunkSize chunkSize) {
        log.debug("onChunkSize");
        // set read and write chunk sizes
        RTMP state = conn.getState();
        state.setReadChunkSize(chunkSize.getSize());
        state.setWriteChunkSize(chunkSize.getSize());
        log.info("ChunkSize configured: {}", chunkSize);
    }

    /** {@inheritDoc} */
    @Override
    protected void onPing(RTMPConnection conn, Channel channel, Header source, Ping ping) {
        log.trace("onPing");
        switch (ping.getEventType()) {
            case Ping.PING_CLIENT:
            case Ping.STREAM_BEGIN:
            case Ping.RECORDED_STREAM:
            case Ping.STREAM_PLAYBUFFER_CLEAR:
                // the server wants to measure the RTT
                Ping pong = new Ping();
                pong.setEventType(Ping.PONG_SERVER);
                pong.setValue2((int) (System.currentTimeMillis() & 0xffffffff));
                conn.ping(pong);
                break;
            case Ping.STREAM_DRY:
                log.debug("Stream indicates there is no data available");
                break;
            case Ping.CLIENT_BUFFER:
                // set the client buffer
                IClientStream stream = null;
                // get the stream id
                Number streamId = ping.getValue2();
                // get requested buffer size in milliseconds
                int buffer = ping.getValue3();
                log.debug("Client sent a buffer size: {} ms for stream id: {}", buffer, streamId);
                // the client wants to set the buffer time
                stream = conn.getStreamById(streamId);
                if (stream != null) {
                    stream.setClientBufferDuration(buffer);
                    log.info("Setting client buffer on stream: {}", buffer);
                }
                // catch-all to make sure buffer size is set
                if (stream == null) {
                    // remember buffer time until stream is created
                    conn.rememberStreamBufferDuration(streamId.intValue(), buffer);
                    log.info("Remembering client buffer on stream: {}", buffer);
                }
                break;
            case Ping.PING_SWF_VERIFY:
                log.debug("SWF verification ping");
                // TODO get the swf verification bytes from the handshake
                SWFResponse swfPong = new SWFResponse(new byte[42]);
                conn.ping(swfPong);
                break;
            case Ping.BUFFER_EMPTY:
                log.debug("Buffer empty ping");

                break;
            case Ping.BUFFER_FULL:
                log.debug("Buffer full ping");

                break;
            default:
                log.debug("Unhandled ping: {}", ping);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void onServerBandwidth(RTMPConnection conn, Channel channel, ServerBW message) {
        log.trace("onServerBandwidth");
        // if the size is not equal to our read size send a client bw control message
        int bandwidth = message.getBandwidth();
        if (bandwidth != bytesReadWindow) {
            ClientBW clientBw = new ClientBW(bandwidth, (byte) 2);
            channel.write(clientBw);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void onClientBandwidth(RTMPConnection conn, Channel channel, ClientBW message) {
        log.trace("onClientBandwidth");
        // if the size is not equal to our write size send a server bw control message
        int bandwidth = message.getBandwidth();
        if (bandwidth != bytesWrittenWindow) {
            ServerBW serverBw = new ServerBW(bandwidth);
            channel.write(serverBw);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void onSharedObject(RTMPConnection conn, Channel channel, Header source, SharedObjectMessage object) {
        log.trace("onSharedObject");
        ClientSharedObject so = sharedObjects.get(object.getName());
        if (so != null) {
            if (so.isPersistent() == object.isPersistent()) {
                log.debug("Received SO request: {}", object);
                so.dispatchEvent(object);
            } else {
                log.error("Ignoring request for wrong-persistent SO: {}", object);
            }
        } else {
            log.error("Ignoring request for non-existend SO: {}", object);
        }
    }

    /**
     * Called when negotiating bandwidth.
     */
    public void onBWCheck() {
        log.debug("onBWCheck");
    }

    /**
     * Called when negotiating bandwidth.
     *
     * @param params
     *            bw parameters
     */
    public void onBWCheck(Object params) {
        log.debug("onBWCheck: {}", params);
    }

    /**
     * Called when bandwidth has been configured.
     *
     * @param params
     *            bw parameters
     */
    public void onBWDone(Object params) {
        log.debug("onBWDone: {}", params);
        bandwidthCheckDone = true;
    }

    /**
     * Called when bandwidth has been configured.
     */
    public void onBWDone() {
        log.debug("onBWDone");
        bandwidthCheckDone = true;
    }

    /**
     * Invoke a method on the server.
     *
     * @param method
     *            Method name
     * @param callback
     *            Callback handler
     */
    @Override
    public void invoke(String method, IPendingServiceCallback callback) {
        log.debug("invoke method: {} params {} callback {}", new Object[] { method, callback });
        // get it from the conn manager
        if (conn != null) {
            conn.invoke(method, callback);
        } else {
            log.info("Connection was null");
            PendingCall result = new PendingCall(method);
            result.setStatus(Call.STATUS_NOT_CONNECTED);
            callback.resultReceived(result);
        }
    }

    /**
     * Invoke a method on the server and pass parameters.
     *
     * @param method
     *            Method
     * @param params
     *            Method call parameters
     * @param callback
     *            Callback object
     */
    @Override
    public void invoke(String method, Object[] params, IPendingServiceCallback callback) {
        log.debug("invoke method: {} params {} callback {}", new Object[] { method, params, callback });
        if (conn != null) {
            conn.invoke(method, params, callback);
        } else {
            log.info("Connection was null");
            PendingCall result = new PendingCall(method, params);
            result.setStatus(Call.STATUS_NOT_CONNECTED);
            callback.resultReceived(result);
        }
    }

    /**
     * Disconnect the first connection in the connection map
     */
    @Override
    public void disconnect() {
        log.debug("disconnect - connection: {}", conn);
        if (conn != null) {
            streamDataList.clear();
            conn.close();
        }
    }

    @Override
    public void createStream(IPendingServiceCallback callback) {
        log.debug("createStream - callback: {}", callback);
        IPendingServiceCallback wrapper = new CreateStreamCallBack(callback);
        invoke("createStream", null, wrapper);
    }

    public void releaseStream(IPendingServiceCallback callback, Object[] params) {
        log.debug("releaseStream - callback: {}", callback);
        IPendingServiceCallback wrapper = new ReleaseStreamCallBack(callback);
        invoke("releaseStream", params, wrapper);
    }

    public void deleteStream(IPendingServiceCallback callback) {
        log.debug("deleteStream - callback: {}", callback);
        IPendingServiceCallback wrapper = new DeleteStreamCallBack(callback);
        invoke("deleteStream", null, wrapper);
    }

    public void subscribe(IPendingServiceCallback callback, Object[] params) {
        log.debug("subscribe - callback: {}", callback);
        IPendingServiceCallback wrapper = new SubscribeStreamCallBack(callback);
        invoke("FCSubscribe", params, wrapper);
    }

    @Override
    public void publish(Number streamId, String name, String mode, INetStreamEventHandler handler) {
        log.debug("publish - stream id: {}, name: {}, mode: {}", streamId, name, mode);
        // setup the netstream handler
        if (handler != null) {
            final int sid = streamId.intValue();
            NetStreamPrivateData streamData = streamDataList.stream().filter(s -> s.getStreamId() == sid).findFirst().orElse(null);
            if (streamData != null) {
                log.debug("Setting handler on stream data - handler: {}", handler);
                streamData.handler = handler;
            } else {
                log.debug("Stream data not found for stream id: {}", streamId);
            }
        }
        // setup publish parameters
        final Object[] params = new Object[2];
        params[0] = name;
        params[1] = mode;
        // call publish
        PendingCall pendingCall = new PendingCall("publish", params);
        conn.invoke(pendingCall, getChannelForStreamId(streamId));
    }

    @Override
    public void unpublish(Number streamId) {
        log.debug("unpublish stream {}", streamId);
        PendingCall pendingCall = new PendingCall("publish", new Object[] { false });
        // this cannot handle a null streamId, so force to 1.0 if null
        if (streamId == null) {
            streamId = 1.0;
        }
        conn.invoke(pendingCall, getChannelForStreamId(streamId));
    }

    @Override
    public void publishStreamData(Number streamId, IMessage message) {
        // get the stream data by index of the list
        final int sid = streamId.intValue();
        NetStreamPrivateData streamData = streamDataList.stream().filter(s -> s.getStreamId() == sid).findFirst().orElse(null);
        if (streamData != null) {
            if (streamData.connConsumer != null) {
                streamData.connConsumer.pushMessage(null, message);
            } else {
                log.warn("Connection consumer was not found for stream id: {}", streamId);
            }
        } else {
            log.warn("Stream data not found for stream id: {} in {}", streamId, streamDataList);
        }
    }

    @Override
    public void play(Number streamId, String name, int start, int length) {
        log.debug("play stream {}, name: {}, start {}, length {}", new Object[] { streamId, name, start, length });
        if (conn != null) {
            // get the channel
            int channel = getChannelForStreamId(streamId);
            // send our requested buffer size
            ping(Ping.CLIENT_BUFFER, streamId, 2000);
            // send our request for a/v
            PendingCall receiveAudioCall = new PendingCall("receiveAudio");
            conn.invoke(receiveAudioCall, channel);
            PendingCall receiveVideoCall = new PendingCall("receiveVideo");
            conn.invoke(receiveVideoCall, channel);
            // call play
            Object[] params = new Object[3];
            params[0] = name;
            params[1] = (start >= 1000 || start <= -1000) ? start : start * 1000;
            params[2] = (length >= 1000 || length <= -1000) ? length : length * 1000;
            PendingCall pendingCall = new PendingCall("play", params);
            conn.invoke(pendingCall, channel);
        } else {
            log.info("Connection was null ?");
        }
    }

    /**
     * Dynamic streaming play method.
     *
     * The following properties are supported on the play options:
     *
     * <pre>
     *         streamName: String. The name of the stream to play or the new stream to switch to.
     *         oldStreamName: String. The name of the initial stream that needs to be switched out. This is not needed and ignored
     *                        when play2 is used for just playing the stream and not switching to a new stream.
     *         start: Number. The start time of the new stream to play, just as supported by the existing play API. and it has the
     *                        same defaults. This is ignored when the method is called for switching (in other words, the transition
     *                        is either NetStreamPlayTransition.SWITCH or NetStreamPlayTransitions.SWAP)
     *         len: Number. The duration of the playback, just as supported by the existing play API and has the same defaults.
     *         transition: String. The transition mode for the playback command. It could be one of the following:
     *                             NetStreamPlayTransitions.RESET
     *                             NetStreamPlayTransitions.APPEND
     *                             NetStreamPlayTransitions.SWITCH
     *                             NetStreamPlayTransitions.SWAP
     * </pre>
     *
     * NetStreamPlayTransitions:
     *
     * <pre>
     *             APPEND : String = "append" - Adds the stream to a playlist and begins playback with the first stream.
     *             APPEND_AND_WAIT : String = "appendAndWait" - Builds a playlist without starting to play it from the first stream.
     *             RESET : String = "reset" - Clears any previous play calls and plays the specified stream immediately.
     *             RESUME : String = "resume" - Requests data from the new connection starting from the point at which the previous connection ended.
     *             STOP : String = "stop" - Stops playing the streams in a playlist.
     *             SWAP : String = "swap" - Replaces a content stream with a different content stream and maintains the rest of the playlist.
     *             SWITCH : String = "switch" - Switches from playing one stream to another stream, typically with streams of the same content.
     * </pre>
     *
     * @see <a href="https://web.archive.org/web/20150911224454/http://www.adobe.com/devnet/adobe-media-server/articles/dynstream_actionscript.html">ActionScript guide to dynamic
     *      streaming</a>
     * @see <a href=
     *      "https://web.archive.org/web/20150908085221/http://help.adobe.com/en_US/FlashPlatform/reference/actionscript/3/flash/net/NetStreamPlayTransitions.html">NetStreamPlayTransitions</a>
     */
    @Override
    public void play2(Number streamId, Map<String, ?> playOptions) {
        log.debug("play2 options: {}", playOptions.toString());
        /*
         * { streamName=streams/new.flv, oldStreamName=streams/old.flv, start=0, len=-1, offset=12.195, transition=switch }
         */
        // get the transition type
        String transition = (String) playOptions.get("transition");
        if (conn != null) {
            if ("NetStreamPlayTransitions.STOP".equals(transition)) {
                PendingCall pendingCall = new PendingCall("play", new Object[] { Boolean.FALSE });
                conn.invoke(pendingCall, getChannelForStreamId(streamId));
            } else if ("NetStreamPlayTransitions.RESET".equals(transition)) {
                // just reset the currently playing stream

            } else {
                Object[] params = new Object[6];
                params[0] = playOptions.get("streamName").toString();
                Object o = playOptions.get("start");
                params[1] = o instanceof Integer ? (Integer) o : Integer.valueOf((String) o);
                o = playOptions.get("len");
                params[2] = o instanceof Integer ? (Integer) o : Integer.valueOf((String) o);
                // new parameters for playback
                params[3] = transition;
                params[4] = playOptions.get("offset");
                params[5] = playOptions.get("oldStreamName");
                // do call
                PendingCall pendingCall = new PendingCall("play2", params);
                conn.invoke(pendingCall, getChannelForStreamId(streamId));
            }
        } else {
            log.info("Connection was null ?");
        }
    }

    /**
     * Sends a ping.
     *
     * @param pingType
     *            the type of ping
     * @param streamId
     *            streams id
     * @param param
     *            ping parameter
     */
    public void ping(short pingType, Number streamId, int param) {
        conn.ping(new Ping(pingType, streamId, param));
    }

    /** {@inheritDoc} */
    @Override
    public void connectionOpened(RTMPConnection conn) {
        log.trace("connectionOpened - conn: {}", conn);
        executor.submit(() -> {
            Thread.currentThread().setName(String.format("ClientOpener@%s", conn.getSessionId()));
            // Send "connect" call to the server
            Channel channel = conn.getChannel(3);
            PendingCall pendingCall = new PendingCall("connect");
            pendingCall.setArguments(connectArguments);
            Invoke invoke = new Invoke(pendingCall);
            invoke.setConnectionParams(connectionParams);
            invoke.setTransactionId(1);
            // register any other callback
            if (connectCallback != null) {
                pendingCall.registerCallback(connectCallback);
            }
            conn.registerPendingCall(invoke.getTransactionId(), pendingCall);
            log.debug("Writing 'connect' invoke: {}, invokeId: {}", invoke, invoke.getTransactionId());
            channel.write(invoke);
        });
    }

    @Override
    public void connectionClosed(RTMPConnection conn) {
        log.debug("connectionClosed");
        super.connectionClosed(conn);
        byte stateCode = conn.getStateCode();
        // submit close handler only if we're not yet disconnected
        if (stateCode != RTMP.STATE_DISCONNECTED) {
            if (connectionClosedHandler != null) {
                executor.submit(connectionClosedHandler);
            }
        }
        // shutdown the executor when we're disconnected
        if (stateCode == RTMP.STATE_DISCONNECTED) {
            log.debug("Shutting down executor");
            executor.shutdown();
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void onCommand(RTMPConnection conn, Channel channel, Header source, ICommand command) {
        log.trace("onCommand: {}, id: {}", command, command.getTransactionId());
        final IServiceCall call = command.getCall();
        final String methodName = call.getServiceMethodName();
        log.debug("Service name: {} args[0]: {}", methodName, (call.getArguments().length != 0 ? call.getArguments()[0] : ""));
        if ("_result".equals(methodName) || "_error".equals(methodName)) {
            final IPendingServiceCall pendingCall = conn.getPendingCall(command.getTransactionId());
            log.debug("Received result for pending call - {}", pendingCall);
            if (pendingCall != null) {
                if ("connect".equals(pendingCall.getServiceMethodName())) {
                    Integer encoding = (Integer) connectionParams.get("objectEncoding");
                    if (encoding != null && encoding.intValue() == 3) {
                        log.debug("Setting encoding to AMF3");
                        conn.getState().setEncoding(IConnection.Encoding.AMF3);
                    }
                }
            }
            handlePendingCallResult(conn, (Invoke) command);
            return;
        }
        // potentially used twice so get the value once
        boolean onStatus = "onStatus".equals(methodName);
        if (onStatus) {
            log.debug("onStatus");
            Number streamId = (Number) (source.getStreamId() != null ? source.getStreamId().doubleValue() : 1.0d);
            if (log.isDebugEnabled()) {
                log.debug("Stream id from header: {}", streamId);
                // XXX create better to serialize ObjectMap to Status object
                ObjectMap<?, ?> objMap = (ObjectMap<?, ?>) call.getArguments()[0];
                // should keep this as an Object to stay compatible with FMS3 etc
                log.debug("Client id from status: {}", objMap.get("clientid"));
            }
            if (streamId != null) {
                // try lookup by stream id, if null return the first one
                final int sid = streamId.intValue();
                NetStreamPrivateData streamData = streamDataList.stream().filter(s -> s.getStreamId() == sid).findFirst().orElse(null);
                if (streamData == null) {
                    log.warn("Stream data was null for id: {} entry count: {}", streamId, streamDataList.size());
                } else if (streamData.handler != null) {
                    log.debug("Got stream data and handler");
                    streamData.handler.onStreamEvent((Notify) command);
                }
            }
        }
        // if this client supports service methods, forward the call
        if (serviceProvider == null) {
            // client doesn't support calling methods on him
            call.setStatus(Call.STATUS_METHOD_NOT_FOUND);
            call.setException(new MethodNotFoundException(methodName));
            log.info("No service provider / method for: {}; to handle calls like onBWCheck, add a service provider", methodName);
        } else {
            serviceInvoker.invoke(call, serviceProvider);
        }
        if (call instanceof IPendingServiceCall) {
            IPendingServiceCall psc = (IPendingServiceCall) call;
            Object result = psc.getResult();
            log.debug("Pending call result is: {}", result);
            if (result instanceof DeferredResult) {
                DeferredResult dr = (DeferredResult) result;
                dr.setTransactionId(command.getTransactionId());
                dr.setServiceCall(psc);
                dr.setChannel(channel);
                conn.registerDeferredResult(dr);
            } else if (!onStatus) {
                if ("onBWCheck".equals(methodName)) {
                    onBWCheck(call.getArguments().length > 0 ? call.getArguments()[0] : null);
                    Invoke reply = new Invoke();
                    reply.setCall(call);
                    reply.setTransactionId(command.getTransactionId());
                    channel.write(reply);
                } else if ("onBWDone".equals(methodName)) {
                    onBWDone(call.getArguments().length > 0 ? call.getArguments()[0] : null);
                } else {
                    Invoke reply = new Invoke();
                    reply.setCall(call);
                    reply.setTransactionId(command.getTransactionId());
                    log.debug("Sending empty call reply: {}", reply);
                    channel.write(reply);
                }
            }
        }
    }

    /**
     * Handle any exceptions that occur.
     *
     * @param throwable
     *            Exception thrown
     */
    public void handleException(Throwable throwable) {
        log.debug("Handle exception: {} with: {}", throwable.getMessage(), exceptionHandler);
        if (exceptionHandler != null) {
            exceptionHandler.handleException(throwable);
        } else {
            log.error("Connection exception", throwable);
            throw new RuntimeException(throwable);
        }
    }

    /**
     * Returns a channel based on the given stream id.
     *
     * @param streamId
     *            stream id
     * @return the channel for this stream id
     */
    protected int getChannelForStreamId(Number streamId) {
        return (streamId.intValue() - 1) * 5 + 4;
    }

    /**
     * Sets the protocol.
     *
     * @param protocol
     *            the data protocol to use.
     * @throws Exception
     *             thrown
     */
    public void setProtocol(String protocol) throws Exception {
        this.protocol = protocol;
    }

    /**
     * Sets a reference to the connection associated with this client handler.
     *
     * @param conn
     *            connection
     */
    public void setConnection(RTMPConnection conn) {
        this.conn = conn;
        this.conn.setHandler(this);
        if (conn.getExecutor() == null) {
            // setup executor
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setDaemon(true);
            executor.setMaxPoolSize(1);
            executor.initialize();
            conn.setExecutor(executor);
        }
    }

    /**
     * Returns the connection associated with this client.
     *
     * @return conn connection
     */
    @Override
    public RTMPConnection getConnection() {
        return conn;
    }

    /**
     * Enables or disables SWF verification.
     *
     * @param enabled
     *            state of SWF verification
     */
    public void setSwfVerification(boolean enabled) {
        swfVerification = enabled;
    }

    /**
     * Returns true if swf verification is enabled
     *
     * @return the swfVerification
     */
    public boolean isSwfVerification() {
        return swfVerification;
    }

    /**
     * Returns true if bandwidth done has been invoked
     *
     * @return the bandwidthCheckDone
     */
    public boolean isBandwidthCheckDone() {
        return bandwidthCheckDone;
    }

    /**
     * Returns true if this client is subscribed
     *
     * @return subscribed state
     */
    public boolean isSubscribed() {
        return subscribed;
    }

    /**
     * @return the connectionParams
     */
    public Map<String, Object> getConnectionParams() {
        return connectionParams;
    }

    /**
     * Setter for stream event dispatcher (useful for saving playing stream to file)
     *
     * @param streamEventDispatcher
     *            event dispatcher
     */
    @Override
    public void setStreamEventDispatcher(IEventDispatcher streamEventDispatcher) {
        this.streamEventDispatcher = streamEventDispatcher;
    }

    /**
     * Setter for the stream event handler.
     *
     * @param streamEventHandler
     *            event handler
     */
    public void setStreamEventHandler(INetStreamEventHandler streamEventHandler) {
        this.streamEventHandler = streamEventHandler;
    }

    private static class NetStream extends AbstractClientStream implements IEventDispatcher {

        private IEventDispatcher dispatcher;

        public NetStream(IEventDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Override
        public void close() {
            log.debug("NetStream close");
        }

        @Override
        public void start() {
            log.debug("NetStream start");
        }

        @Override
        public void stop() {
            log.debug("NetStream stop");
        }

        @Override
        public void dispatchEvent(IEvent event) {
            log.debug("NetStream dispatchEvent: {}", event);
            if (dispatcher != null) {
                dispatcher.dispatchEvent(event);
            }
        }
    }

    private class CreateStreamCallBack implements IPendingServiceCallback {

        private IPendingServiceCallback wrapped;

        public CreateStreamCallBack(IPendingServiceCallback wrapped) {
            log.debug("CreateStreamCallBack {}", wrapped.getClass().getName());
            this.wrapped = wrapped;
        }

        @Override
        public void resultReceived(IPendingServiceCall call) {
            // get the result as base object
            Object callResult = call.getResult();
            if (callResult != null) {
                // we expect a number consisting of the stream id, but we'll check for an object map as well
                int streamId = -1;
                if (callResult instanceof Number) {
                    streamId = ((Number) callResult).intValue();
                } else if (callResult instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) callResult;
                    // XXX(paul) log out the map contents
                    log.warn("CreateStreamCallBack resultReceived - map: {}", map);
                    if (map.containsKey("streamId")) {
                        Object tmpStreamId = map.get("streamId");
                        if (tmpStreamId instanceof Number) {
                            streamId = ((Number) tmpStreamId).intValue();
                        } else {
                            log.warn("CreateStreamCallBack resultReceived - stream id is not a number: {}", tmpStreamId);
                        }
                    }
                }
                log.debug("CreateStreamCallBack resultReceived - stream id: {} call: {} connection: {}", streamId, call, conn);
                if (conn != null && streamId != -1) {
                    log.debug("Setting new net stream");
                    NetStream stream = new NetStream(streamEventDispatcher);
                    stream.setConnection(conn);
                    stream.setStreamId(streamId);
                    conn.addClientStream(stream);
                    NetStreamPrivateData streamData = new NetStreamPrivateData(streamId);
                    streamData.outputStream = conn.createOutputStream(streamId);
                    streamData.connConsumer = new ConnectionConsumer(conn, streamData.outputStream.getVideo(), streamData.outputStream.getAudio(), streamData.outputStream.getData());
                    streamDataList.add(streamData);
                    log.debug("streamDataList: {}", streamDataList);
                }
                wrapped.resultReceived(call);
            } else {
                log.warn("CreateStreamCallBack resultReceived - call result is null");
            }
        }
    }

    private class ReleaseStreamCallBack implements IPendingServiceCallback {

        private IPendingServiceCallback wrapped;

        public ReleaseStreamCallBack(IPendingServiceCallback wrapped) {
            log.debug("ReleaseStreamCallBack {}", wrapped.getClass().getName());
            this.wrapped = wrapped;
        }

        @Override
        public void resultReceived(IPendingServiceCall call) {
            wrapped.resultReceived(call);
        }
    }

    private class DeleteStreamCallBack implements IPendingServiceCallback {

        private IPendingServiceCallback wrapped;

        public DeleteStreamCallBack(IPendingServiceCallback wrapped) {
            log.debug("DeleteStreamCallBack {}", wrapped.getClass().getName());
            this.wrapped = wrapped;
        }

        @Override
        public void resultReceived(IPendingServiceCall call) {
            // get the result as base object
            Object callResult = call.getResult();
            if (callResult != null) {
                // we expect a number consisting of the stream id, but we'll check for an object map as well
                final Number streamId = (Number) (callResult instanceof Number ? callResult : (callResult instanceof Map ? ((Map<?, ?>) callResult).get("streamId") : 1.0));
                log.debug("DeleteStreamCallBack resultReceived - stream id: {} call: {} connection: {}", streamId, call, conn);
                if (conn != null) {
                    log.debug("Deleting net stream");
                    conn.removeClientStream(streamId);
                    // send a delete notify?
                    final int sid = streamId.intValue();
                    NetStreamPrivateData streamData = streamDataList.stream().filter(s -> s.getStreamId() == sid).findFirst().orElse(null);
                    if (streamData != null) {
                        streamDataList.remove(streamData);
                    } else {
                        log.warn("Stream data not found for stream id: {}", streamId);
                    }
                }
                wrapped.resultReceived(call);
            } else {
                log.warn("DeleteStreamCallBack resultReceived - call result is null");
            }
        }
    }

    private class SubscribeStreamCallBack implements IPendingServiceCallback {

        private IPendingServiceCallback wrapped;

        public SubscribeStreamCallBack(IPendingServiceCallback wrapped) {
            log.debug("SubscribeStreamCallBack {}", wrapped.getClass().getName());
            this.wrapped = wrapped;
        }

        @Override
        public void resultReceived(IPendingServiceCall call) {
            log.debug("resultReceived", call);
            if (call.getResult() instanceof ObjectMap<?, ?>) {
                ObjectMap<?, ?> map = (ObjectMap<?, ?>) call.getResult();
                if (map.containsKey("code")) {
                    String code = (String) map.get("code");
                    log.debug("Code: {}", code);
                    if (StatusCodes.NS_PLAY_START.equals(code)) {
                        subscribed = true;
                    }
                }
            }
            wrapped.resultReceived(call);
        }
    }

    private final class NetStreamPrivateData {

        public volatile INetStreamEventHandler handler;

        public volatile OutputStream outputStream;

        public volatile ConnectionConsumer connConsumer;

        private final int streamId;

        NetStreamPrivateData(int streamId) {
            this.streamId = streamId;
            if (streamEventHandler != null) {
                handler = streamEventHandler;
            }
        }

        public int getStreamId() {
            return streamId;
        }

        @Override
        public int hashCode() {
            return streamId;
        }

    }

}
