/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.stream;

import static org.red5.server.net.rtmp.message.Constants.TYPE_AUDIO_DATA;
import static org.red5.server.net.rtmp.message.Constants.TYPE_INVOKE;
import static org.red5.server.net.rtmp.message.Constants.TYPE_NOTIFY;
import static org.red5.server.net.rtmp.message.Constants.TYPE_VIDEO_DATA;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.StandardMBean;

import org.apache.commons.lang3.StringUtils;
import org.apache.mina.core.buffer.IoBuffer;
import org.red5.codec.IAudioStreamCodec;
import org.red5.codec.IStreamCodecInfo;
import org.red5.codec.IVideoStreamCodec;
import org.red5.codec.StreamCodecInfo;
import org.red5.io.amf.Output;
import org.red5.server.api.IConnection;
import org.red5.server.api.Red5;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.event.IEventDispatcher;
import org.red5.server.api.event.IEventListener;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.statistics.IClientBroadcastStreamStatistics;
import org.red5.server.api.statistics.support.StatisticsCounter;
import org.red5.server.api.stream.IClientBroadcastStream;
import org.red5.server.api.stream.IStreamAwareScopeHandler;
import org.red5.server.api.stream.IStreamCapableConnection;
import org.red5.server.api.stream.IStreamListener;
import org.red5.server.api.stream.IStreamPacket;
import org.red5.server.api.stream.StreamState;
import org.red5.server.jmx.mxbeans.ClientBroadcastStreamMXBean;
import org.red5.server.messaging.IConsumer;
import org.red5.server.messaging.IFilter;
import org.red5.server.messaging.IMessage;
import org.red5.server.messaging.IMessageComponent;
import org.red5.server.messaging.IMessageOutput;
import org.red5.server.messaging.IPipe;
import org.red5.server.messaging.IPipeConnectionListener;
import org.red5.server.messaging.IProvider;
import org.red5.server.messaging.IPushableConsumer;
import org.red5.server.messaging.OOBControlMessage;
import org.red5.server.messaging.PipeConnectionEvent;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.net.rtmp.event.IRTMPEvent;
import org.red5.server.net.rtmp.event.Notify;
import org.red5.server.net.rtmp.event.VideoData;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.net.rtmp.message.Header;
import org.red5.server.net.rtmp.status.Status;
import org.red5.server.net.rtmp.status.StatusCodes;
import org.red5.server.stream.message.RTMPMessage;
import org.red5.server.stream.message.StatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jmx.export.annotation.ManagedResource;

/**
 * Represents live stream broadcasted from client. As Flash Media Server, Red5 supports recording mode for live streams, that is,
 * broadcasted stream has broadcast mode. It can be either "live" or "record" and latter causes server-side application to record
 * broadcasted stream.
 *
 * Note that recorded streams are recorded as FLV files.
 *
 * This type of stream uses two different pipes for live streaming and recording.
 *
 * @author The Red5 Project
 * @author Steven Gong
 * @author Paul Gregoire (mondain@gmail.com)
 * @author Vladimir Hmelyoff (vlhm@splitmedialabs.com)
 */
@ManagedResource(objectName = "org.red5.server:type=ClientBroadcastStream", description = "ClientBroadcastStream")
public class ClientBroadcastStream extends AbstractClientStream implements IClientBroadcastStream, IFilter, IPushableConsumer, IPipeConnectionListener, IEventDispatcher, IClientBroadcastStreamStatistics, ClientBroadcastStreamMXBean {

    private static final Logger log = LoggerFactory.getLogger(ClientBroadcastStream.class);

    private static final boolean isDebug = log.isDebugEnabled();

    /**
     * Whether or not to automatically record the associated stream.
     */
    protected boolean automaticRecording;

    /**
     * Total number of bytes received.
     */
    protected volatile long bytesReceived;

    /**
     * Is there need to check video codec?
     */
    protected volatile boolean checkVideoCodec;

    /**
     * Is there need to check audio codec?
     */
    protected volatile boolean checkAudioCodec;

    /**
     * Data is sent by chunks, each of them has size
     */
    protected int chunkSize;

    /**
     * Is this stream still active?
     */
    protected AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Output endpoint that providers use
     */
    protected transient IMessageOutput connMsgOut;

    /**
     * Stores timestamp of first packet
     */
    protected long firstPacketTime = -1;

    /**
     * Pipe for live streaming
     */
    protected transient IPipe livePipe;

    /**
     * Stream published name
     */
    protected String publishedName;

    /**
     * Streaming parameters
     */
    protected Map<String, String> parameters;

    /**
     * Is there need to send start notification?
     */
    protected boolean sendStartNotification = true;

    /**
     * Stores statistics about subscribers.
     */
    private transient StatisticsCounter subscriberStats = new StatisticsCounter();

    /**
     * Listeners to get notified about received packets.
     */
    protected transient Set<IStreamListener> listeners = new CopyOnWriteArraySet<IStreamListener>();

    /**
     * Recording listener
     */
    protected transient WeakReference<IRecordingListener> recordingListener;

    protected volatile long latestTimeStamp = -1;

    /**
     * Whether or not to register with JMX.
     */
    protected boolean registerJMX;

    /**
     * Stream name aliases for the entire server instance.
     */
    protected static CopyOnWriteArraySet<String> localAliases = new CopyOnWriteArraySet<>();

    /**
     * Publish alias for the stream name.
     */
    protected String nameAlias;

    /**
     * Subscribe aliases for this instance.
     */
    protected CopyOnWriteArraySet<String> aliases;

    /**
     * Check and send notification if necessary
     *
     * @param event
     *            Event
     */
    protected void checkSendNotifications(IEvent event) {
        IEventListener source = event.getSource();
        sendStartNotifications(source);
    }

    /**
     * Closes stream, unsubscribes provides, sends stoppage notifications and broadcast close notification.
     */
    public void close() {
        //log.debug("Stream close: {}", publishedName);
        if (closed.compareAndSet(false, true)) {
            if (livePipe != null) {
                livePipe.unsubscribe((IProvider) this);
            }
            // if we have a recording listener, inform that this stream is done
            if (recordingListener != null) {
                sendRecordStopNotify();
                notifyRecordingStop();
                // inform the listener to finish and close
                recordingListener.get().stop();
            }
            sendPublishStopNotify();
            // TODO: can we send the client something to make sure he stops sending data?
            if (connMsgOut != null) {
                connMsgOut.unsubscribe(this);
            }
            notifyBroadcastClose();
            // clear the listener after all the notifications have been sent
            if (recordingListener != null) {
                recordingListener.clear();
            }
            // clear listeners
            if (!listeners.isEmpty()) {
                listeners.clear();
            }
            // deregister with jmx
            unregisterJMX();
            setState(StreamState.CLOSED);
            // clear our aliases and from local registry
            if (aliases != null) {
                localAliases.removeAll(aliases);
                aliases.clear();
            }
            // remove publish alias
            if (nameAlias != null) {
                localAliases.remove(nameAlias);
                nameAlias = null;
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * Dispatches event
     */
    @SuppressWarnings("null")
    public void dispatchEvent(IEvent event) {
        if (event instanceof IRTMPEvent && !closed.get()) {
            switch (event.getType()) {
                case STREAM_CONTROL:
                case STREAM_DATA:
                    // create the event
                    final IRTMPEvent rtmpEvent;
                    try {
                        rtmpEvent = (IRTMPEvent) event;
                    } catch (ClassCastException e) {
                        log.error("Class cast exception in event dispatch", e);
                        return;
                    }
                    int eventTime = rtmpEvent.getTimestamp();
                    // verify and / or set source type
                    if (rtmpEvent.getSourceType() != Constants.SOURCE_TYPE_LIVE) {
                        rtmpEvent.setSourceType(Constants.SOURCE_TYPE_LIVE);
                    }
                    // get the buffer only once per call
                    IoBuffer buf = null;
                    if (rtmpEvent instanceof IStreamData && (buf = ((IStreamData<?>) rtmpEvent).getData()) != null) {
                        bytesReceived += buf.limit();
                    }
                    // get stream codec
                    IStreamCodecInfo codecInfo = getCodecInfo();
                    StreamCodecInfo info = null;
                    if (codecInfo instanceof StreamCodecInfo) {
                        info = (StreamCodecInfo) codecInfo;
                    }
                    //log.trace("Stream codec info: {}", info);
                    switch (rtmpEvent.getDataType()) {
                        case TYPE_AUDIO_DATA: // AudioData
                            //log.trace("Audio: {}", eventTime);
                            IAudioStreamCodec audioStreamCodec = null;
                            if (checkAudioCodec) {
                                // dont try to read codec info from 0 length audio packets
                                if (buf.limit() > 0) {
                                    audioStreamCodec = AudioCodecFactory.getAudioCodec(buf);
                                    if (info != null) {
                                        info.setAudioCodec(audioStreamCodec);
                                    }
                                    checkAudioCodec = false;
                                }
                            } else if (codecInfo != null) {
                                audioStreamCodec = codecInfo.getAudioCodec();
                            }
                            if (audioStreamCodec != null && audioStreamCodec.addData(buf)) {
                                log.debug("Audio codec updated: {}", audioStreamCodec);
                            }
                            if (info != null) {
                                info.setHasAudio(true);
                            }
                            break;
                        case TYPE_VIDEO_DATA: // VideoData
                            //log.trace("Video: {}", eventTime);
                            IVideoStreamCodec videoStreamCodec = null;
                            if (checkVideoCodec) {
                                videoStreamCodec = VideoCodecFactory.getVideoCodec(buf);
                                if (info != null) {
                                    info.setVideoCodec(videoStreamCodec);
                                }
                                checkVideoCodec = false;
                            } else if (codecInfo != null) {
                                videoStreamCodec = codecInfo.getVideoCodec();
                            }
                            if (videoStreamCodec != null && videoStreamCodec.addData(buf, eventTime)) {
                                log.debug("Video codec updated: {}", videoStreamCodec);
                            }
                            if (info != null) {
                                info.setHasVideo(true);
                            }
                            break;
                        case TYPE_NOTIFY:
                            Notify notifyEvent = (Notify) rtmpEvent;
                            String action = notifyEvent.getAction();
                            //if (isDebug) {
                            //log.debug("Notify action: {}", action);
                            //}
                            if ("onMetaData".equals(action)) {
                                // store the metadata
                                try {
                                    //log.debug("Setting metadata");
                                    setMetaData(notifyEvent.duplicate());
                                } catch (Exception e) {
                                    log.warn("Metadata could not be duplicated for this stream", e);
                                }
                            }
                            break;
                        case TYPE_INVOKE:
                            //Invoke invokeEvent = (Invoke) rtmpEvent;
                            //log.debug("Invoke action: {}", invokeEvent.getAction());
                            // event / stream listeners will not be notified of invokes
                            return;
                        default:
                            log.debug("Unknown: {}", rtmpEvent);
                    }
                    // update last event time
                    if (eventTime > latestTimeStamp) {
                        latestTimeStamp = eventTime;
                    }
                    // notify event listeners
                    checkSendNotifications(event);
                    // note this timestamp is set in event/body but not in the associated header route to live
                    if (livePipe != null) {
                        try {
                            // create new RTMP message, initialize it and push through pipe
                            RTMPMessage msg = RTMPMessage.build(rtmpEvent, eventTime);
                            livePipe.pushMessage(msg);
                        } catch (IOException err) {
                            stop();
                        }
                    } else if (isDebug) {
                        log.debug("Live pipe was null, message was not pushed");
                    }
                    // notify listeners about received packet
                    if (rtmpEvent instanceof IStreamPacket) {
                        for (IStreamListener listener : getStreamListeners()) {
                            try {
                                listener.packetReceived(this, (IStreamPacket) rtmpEvent);
                            } catch (Exception e) {
                                log.warn("Error while notifying listener {}", listener, e);
                                if (listener instanceof RecordingListener) {
                                    sendRecordFailedNotify(e.getMessage());
                                }
                            }
                        }
                    }
                    break;
                default:
                    // ignored event
                    if (isDebug) {
                        log.debug("Ignoring event: {}", event.getType());
                    }
            }
        } else {
            if (isDebug) {
                log.debug("Event was of wrong type or stream is closed ({})", closed);
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return a int
     */
    public int getActiveSubscribers() {
        return subscriberStats.getCurrent();
    }

    /**
     * {@inheritDoc}
     *
     * @return a long
     */
    public long getBytesReceived() {
        return bytesReceived;
    }

    /**
     * {@inheritDoc}
     *
     * @return a int
     */
    public int getCurrentTimestamp() {
        return (int) latestTimeStamp;
    }

    /**
     * {@inheritDoc}
     *
     * @return a int
     */
    public int getMaxSubscribers() {
        return subscriberStats.getMax();
    }

    /**
     * Getter for provider
     *
     * @return Provider
     */
    public IProvider getProvider() {
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * Setter for stream published name
     */
    public void setPublishedName(String name) {
        log.debug("setPublishedName: {}", name);
        // a publish name of "false" is a special case, used when stopping a stream
        if (StringUtils.isNotEmpty(name) && !"false".equals(name)) {
            this.publishedName = name;
            registerJMX();
        }
    }

    /**
     * Getter for published name
     *
     * @return Stream published name
     */
    public String getPublishedName() {
        return publishedName;
    }

    /** {@inheritDoc} */
    public void setParameters(Map<String, String> params) {
        this.parameters = params;
    }

    /**
     * {@inheritDoc}
     *
     * @return a {@link java.util.Map} object
     */
    public Map<String, String> getParameters() {
        return parameters;
    }

    /**
     * {@inheritDoc}
     *
     * @return a {@link java.lang.String} object
     */
    public String getSaveFilename() {
        if (recordingListener != null) {
            return recordingListener.get().getFileName();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * @return a {@link org.red5.server.api.statistics.IClientBroadcastStreamStatistics} object
     */
    public IClientBroadcastStreamStatistics getStatistics() {
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * @return a int
     */
    public int getTotalSubscribers() {
        return subscriberStats.getTotal();
    }

    /**
     * <p>isAutomaticRecording.</p>
     *
     * @return the automaticRecording
     */
    public boolean isAutomaticRecording() {
        return automaticRecording;
    }

    /**
     * <p>Setter for the field <code>automaticRecording</code>.</p>
     *
     * @param automaticRecording
     *            the automaticRecording to set
     */
    public void setAutomaticRecording(boolean automaticRecording) {
        this.automaticRecording = automaticRecording;
    }

    /**
     * <p>Setter for the field <code>registerJMX</code>.</p>
     *
     * @param registerJMX
     *            the registerJMX to set
     */
    public void setRegisterJMX(boolean registerJMX) {
        this.registerJMX = registerJMX;
    }

    /**
     * Notifies handler on stream broadcast close
     */
    protected void notifyBroadcastClose() {
        final IStreamAwareScopeHandler handler = getStreamAwareHandler();
        if (handler != null) {
            try {
                handler.streamBroadcastClose(this);
            } catch (Throwable t) {
                log.error("Error in notifyBroadcastClose", t);
            }
        }
    }

    /**
     * Notifies handler on stream recording stop
     */
    protected void notifyRecordingStop() {
        IStreamAwareScopeHandler handler = getStreamAwareHandler();
        if (handler != null) {
            try {
                handler.streamRecordStop(this);
            } catch (Throwable t) {
                log.error("Error in notifyRecordingStop", t);
            }
        }
    }

    /**
     * Notifies handler on stream broadcast start
     */
    protected void notifyBroadcastStart() {
        IStreamAwareScopeHandler handler = getStreamAwareHandler();
        if (handler != null) {
            try {
                handler.streamBroadcastStart(this);
            } catch (Throwable t) {
                log.error("Error in notifyBroadcastStart", t);
            }
        }
        // send metadata for creation and start dates
        IoBuffer buf = IoBuffer.allocate(256);
        buf.setAutoExpand(true);
        Output out = new Output(buf);
        out.writeString("onMetaData");
        Map<Object, Object> params = new HashMap<>();
        Calendar cal = GregorianCalendar.getInstance();
        cal.setTimeInMillis(creationTime);
        params.put("creationdate", ZonedDateTime.ofInstant(cal.toInstant(), ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT));
        cal.setTimeInMillis(startTime);
        params.put("startdate", ZonedDateTime.ofInstant(cal.toInstant(), ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT));
        if (isDebug) {
            log.debug("Params: {}", params);
        }
        out.writeMap(params);
        buf.flip();
        Notify notify = new Notify(buf);
        notify.setAction("onMetaData");
        notify.setHeader(new Header());
        notify.getHeader().setDataType(Notify.TYPE_STREAM_METADATA);
        notify.getHeader().setStreamId(0);
        notify.setTimestamp(0);
        dispatchEvent(notify);
    }

    /**
     * Send OOB control message with chunk size
     */
    protected void notifyChunkSize() {
        if (chunkSize > 0 && livePipe != null) {
            OOBControlMessage setChunkSize = new OOBControlMessage();
            setChunkSize.setTarget("ConnectionConsumer");
            setChunkSize.setServiceName("chunkSize");
            if (setChunkSize.getServiceParamMap() == null) {
                setChunkSize.setServiceParamMap(new HashMap<String, Object>());
            }
            setChunkSize.getServiceParamMap().put("chunkSize", chunkSize);
            livePipe.sendOOBControlMessage(getProvider(), setChunkSize);
        }
    }

    /**
     * {@inheritDoc}
     *
     * Out-of-band control message handler
     */
    public void onOOBControlMessage(IMessageComponent source, IPipe pipe, OOBControlMessage oobCtrlMsg) {
        String target = oobCtrlMsg.getTarget();
        if ("ClientBroadcastStream".equals(target)) {
            String serviceName = oobCtrlMsg.getServiceName();
            if ("chunkSize".equals(serviceName)) {
                chunkSize = (Integer) oobCtrlMsg.getServiceParamMap().get("chunkSize");
                notifyChunkSize();
            } else {
                log.debug("Unhandled OOB control message for service: {}", serviceName);
            }
        } else {
            log.debug("Unhandled OOB control message to target: {}", target);
        }
    }

    /**
     * {@inheritDoc}
     *
     * Pipe connection event handler
     */
    @SuppressWarnings("unused")
    public void onPipeConnectionEvent(PipeConnectionEvent event) {
        switch (event.getType()) {
            case PROVIDER_CONNECT_PUSH:
                //log.debug("Provider connect");
                if (event.getProvider() == this && event.getSource() != connMsgOut && (event.getParamMap() == null || !event.getParamMap().containsKey("record"))) {
                    livePipe = (IPipe) event.getSource();
                    //log.debug("Provider: {}", livePipe.getClass().getName());
                    for (IConsumer consumer : livePipe.getConsumers()) {
                        subscriberStats.increment();
                    }
                }
                break;
            case PROVIDER_DISCONNECT:
                //log.debug("Provider disconnect");
                //if (isDebug && livePipe != null) {
                //log.debug("Provider: {}", livePipe.getClass().getName());
                //}
                if (livePipe == event.getSource()) {
                    livePipe = null;
                }
                break;
            case CONSUMER_CONNECT_PUSH:
                //log.debug("Consumer connect");
                IPipe pipe = (IPipe) event.getSource();
                //if (isDebug && pipe != null) {
                //log.debug("Consumer: {}", pipe.getClass().getName());
                //}
                if (livePipe == pipe) {
                    notifyChunkSize();
                }
                subscriberStats.increment();
                break;
            case CONSUMER_DISCONNECT:
                //log.debug("Consumer disconnect: {}", event.getSource().getClass().getName());
                subscriberStats.decrement();
                break;
            default:
        }
    }

    /**
     * {@inheritDoc}
     *
     * Currently not implemented
     */
    public void pushMessage(IPipe pipe, IMessage message) {
    }

    /**
     * {@inheritDoc}
     *
     * Save broadcasted stream.
     */
    public void saveAs(String name, boolean isAppend) throws IOException {
        //log.debug("SaveAs - name: {} append: {}", name, isAppend);
        // get connection to check if client is still streaming
        IStreamCapableConnection conn = getConnection();
        if (conn == null) {
            throw new IOException("Stream is no longer connected");
        }
        // one recording listener at a time via this entry point
        if (recordingListener == null) {
            // XXX Paul: Revisit this section to allow for implementation of custom IRecordingListener
            //IRecordingListener listener = (IRecordingListener) ScopeUtils.getScopeService(conn.getScope(), IRecordingListener.class, RecordingListener.class, false);
            // create a recording listener
            IRecordingListener listener = new RecordingListener();
            //log.debug("Created: {}", listener);
            // initialize the listener
            if (listener.init(conn, name, isAppend)) {
                // get decoder info if it exists for the stream
                IStreamCodecInfo codecInfo = getCodecInfo();
                //log.debug("Codec info: {}", codecInfo);
                if (codecInfo instanceof StreamCodecInfo) {
                    StreamCodecInfo info = (StreamCodecInfo) codecInfo;
                    IVideoStreamCodec videoCodec = info.getVideoCodec();
                    //log.debug("Video codec: {}", videoCodec);
                    if (videoCodec != null) {
                        //check for decoder configuration to send
                        IoBuffer config = videoCodec.getDecoderConfiguration();
                        if (config != null) {
                            //log.debug("Decoder configuration is available for {}", videoCodec.getName());
                            VideoData videoConf = new VideoData(config.asReadOnlyBuffer());
                            try {
                                //log.debug("Setting decoder configuration for recording");
                                listener.getFileConsumer().setVideoDecoderConfiguration(videoConf);
                            } finally {
                                videoConf.release();
                            }
                        }
                    } else {
                        log.debug("Could not initialize stream output, videoCodec is null");
                    }
                    IAudioStreamCodec audioCodec = info.getAudioCodec();
                    //log.debug("Audio codec: {}", audioCodec);
                    if (audioCodec != null) {
                        //check for decoder configuration to send
                        IoBuffer config = audioCodec.getDecoderConfiguration();
                        if (config != null) {
                            //log.debug("Decoder configuration is available for {}", audioCodec.getName());
                            AudioData audioConf = new AudioData(config.asReadOnlyBuffer());
                            try {
                                //log.debug("Setting decoder configuration for recording");
                                listener.getFileConsumer().setAudioDecoderConfiguration(audioConf);
                            } finally {
                                audioConf.release();
                            }
                        }
                    } else {
                        log.debug("No decoder configuration available, audioCodec is null");
                    }
                }
                // set as primary listener
                recordingListener = new WeakReference<IRecordingListener>(listener);
                // add as a listener
                addStreamListener(listener);
                // start the listener thread
                listener.start();
            } else {
                log.warn("Recording listener failed to initialize for stream: {}", name);
            }
        } else {
            log.debug("Recording listener already exists for stream: {} auto record enabled: {}", name, automaticRecording);
        }
    }

    /**
     * Sends publish start notifications
     */
    protected void sendPublishStartNotify() {
        Status publishStatus = new Status(StatusCodes.NS_PUBLISH_START);
        publishStatus.setClientid(getStreamId());
        publishStatus.setDetails(getPublishedName());

        StatusMessage startMsg = new StatusMessage();
        startMsg.setBody(publishStatus);
        pushMessage(startMsg);
        setState(StreamState.PUBLISHING);
    }

    /**
     * Sends publish stop notifications
     */
    protected void sendPublishStopNotify() {
        Status stopStatus = new Status(StatusCodes.NS_UNPUBLISHED_SUCCESS);
        stopStatus.setClientid(getStreamId());
        stopStatus.setDetails(getPublishedName());

        StatusMessage stopMsg = new StatusMessage();
        stopMsg.setBody(stopStatus);
        pushMessage(stopMsg);
        setState(StreamState.STOPPED);
    }

    /**
     * Sends record failed notifications
     *
     * @param reason a {@link java.lang.String} object
     */
    protected void sendRecordFailedNotify(String reason) {
        Status failedStatus = new Status(StatusCodes.NS_RECORD_FAILED);
        failedStatus.setLevel(Status.ERROR);
        failedStatus.setClientid(getStreamId());
        failedStatus.setDetails(getPublishedName());
        failedStatus.setDesciption(reason);

        StatusMessage failedMsg = new StatusMessage();
        failedMsg.setBody(failedStatus);
        pushMessage(failedMsg);
    }

    /**
     * Sends record start notifications
     */
    protected void sendRecordStartNotify() {
        Status recordStatus = new Status(StatusCodes.NS_RECORD_START);
        recordStatus.setClientid(getStreamId());
        recordStatus.setDetails(getPublishedName());

        StatusMessage startMsg = new StatusMessage();
        startMsg.setBody(recordStatus);
        pushMessage(startMsg);
    }

    /**
     * Sends record stop notifications
     */
    protected void sendRecordStopNotify() {
        Status stopStatus = new Status(StatusCodes.NS_RECORD_STOP);
        stopStatus.setClientid(getStreamId());
        stopStatus.setDetails(getPublishedName());

        StatusMessage stopMsg = new StatusMessage();
        stopMsg.setBody(stopStatus);
        pushMessage(stopMsg);
    }

    /**
     * Pushes a message out to a consumer.
     *
     * @param msg
     *            StatusMessage
     */
    protected void pushMessage(StatusMessage msg) {
        if (connMsgOut != null) {
            try {
                connMsgOut.pushMessage(msg);
            } catch (IOException err) {
                log.error("Error while pushing message: {}", msg, err);
            }
        } else {
            log.warn("Consumer message output is null");
        }
    }

    /**
     * <p>sendStartNotifications.</p>
     *
     * @param source a {@link org.red5.server.api.event.IEventListener} object
     */
    protected void sendStartNotifications(IEventListener source) {
        if (sendStartNotification) {
            // notify handler that stream starts recording/publishing
            sendStartNotification = false;
            if (source instanceof IConnection) {
                IScope scope = ((IConnection) source).getScope();
                if (scope.hasHandler()) {
                    final Object handler = scope.getHandler();
                    if (handler instanceof IStreamAwareScopeHandler) {
                        if (recordingListener != null && recordingListener.get().isRecording()) {
                            // callback for record start
                            ((IStreamAwareScopeHandler) handler).streamRecordStart(this);
                        } else {
                            // delete any previously recorded versions of this now "live" stream per
                            // http://livedocs.adobe.com/flashmediaserver/3.0/hpdocs/help.html?content=00000186.html
                            //                            try {
                            //                                File file = getRecordFile(scope, publishedName);
                            //                                if (file != null && file.exists()) {
                            //                                    if (!file.delete()) {
                            //                                        log.debug("File was not deleted: {}", file.getAbsoluteFile());
                            //                                    }
                            //                                }
                            //                            } catch (Exception e) {
                            //                                log.warn("Exception removing previously recorded file", e);
                            //                            }
                            // callback for publish start
                            ((IStreamAwareScopeHandler) handler).streamPublishStart(this);
                        }
                    }
                }
            }
            // send start notifications
            sendPublishStartNotify();
            if (recordingListener != null && recordingListener.get().isRecording()) {
                sendRecordStartNotify();
            }
            notifyBroadcastStart();
        }
    }

    /**
     * Starts stream, creates pipes, connects
     */
    public void start() {
        //log.info("Stream start: {}", publishedName);
        checkVideoCodec = true;
        checkAudioCodec = true;
        firstPacketTime = -1;
        latestTimeStamp = -1;
        bytesReceived = 0;
        IConsumerService consumerManager = (IConsumerService) getScope().getContext().getBean(IConsumerService.KEY);
        connMsgOut = consumerManager.getConsumerOutput(this);
        if (connMsgOut != null && connMsgOut.subscribe(this, null)) {
            // technically this would be a 'start' time
            startTime = System.currentTimeMillis();
        } else {
            log.warn("Subscribe failed");
        }
        setState(StreamState.STARTED);
    }

    /**
     * {@inheritDoc}
     */
    public void startPublishing() {
        // We send the start messages before the first packet is received.
        // This is required so FME actually starts publishing.
        sendStartNotifications(Red5.getConnectionLocal());
        // force recording if set
        if (automaticRecording) {
            //log.debug("Starting automatic recording of {}", publishedName);
            try {
                saveAs(publishedName, false);
            } catch (Exception e) {
                log.warn("Start of automatic recording failed", e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void stop() {
        //log.info("Stream stop: {}", publishedName);
        setState(StreamState.STOPPED);
        stopRecording();
        close();
    }

    /**
     * Stops any currently active recording.
     */
    public void stopRecording() {
        IRecordingListener listener = null;
        if (recordingListener != null && (listener = recordingListener.get()).isRecording()) {
            sendRecordStopNotify();
            notifyRecordingStop();
            // remove the listener
            removeStreamListener(listener);
            // stop the recording listener
            listener.stop();
            // clear and null-out the thread local
            recordingListener.clear();
            recordingListener = null;
        }
    }

    /**
     * <p>isRecording.</p>
     *
     * @return a boolean
     */
    public boolean isRecording() {
        return recordingListener != null && recordingListener.get().isRecording();
    }

    /** {@inheritDoc} */
    public void addStreamListener(IStreamListener listener) {
        listeners.add(listener);
    }

    /**
     * {@inheritDoc}
     *
     * @return a {@link java.util.Collection} object
     */
    public Collection<IStreamListener> getStreamListeners() {
        return listeners;
    }

    /** {@inheritDoc} */
    public void removeStreamListener(IStreamListener listener) {
        listeners.remove(listener);
    }

    /**
     * Get the file we'd be recording to based on scope and given name.
     *
     * @param scope
     *            scope
     * @param name
     *            record name
     * @return file
     */
    protected File getRecordFile(IScope scope, String name) {
        return RecordingListener.getRecordFile(scope, name);
    }

    /**
     * <p>registerJMX.</p>
     */
    protected void registerJMX() {
        if (registerJMX && StringUtils.isNotEmpty(publishedName) && !"false".equals(publishedName)) {
            // register with jmx
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            try {
                // replace any colons with pipes as they are invalid characters for jmx object names
                ObjectName oName = new ObjectName(String.format("org.red5.server:type=ClientBroadcastStream,scope=%s,publishedName=%s", getScope().getName(), publishedName.replaceAll(":", "|")));
                mbs.registerMBean(new StandardMBean(this, ClientBroadcastStreamMXBean.class, true), oName);
            } catch (InstanceAlreadyExistsException e) {
                log.debug("Instance already registered", e);
            } catch (Exception e) {
                log.warn("Error on jmx registration", e);
            }
        }
    }

    /**
     * <p>unregisterJMX.</p>
     */
    protected void unregisterJMX() {
        if (registerJMX) {
            if (StringUtils.isNotEmpty(publishedName) && !"false".equals(publishedName)) {
                MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
                try {
                    // replace any colons with pipes as they are invalid characters for jmx object names
                    ObjectName oName = new ObjectName(String.format("org.red5.server:type=ClientBroadcastStream,scope=%s,publishedName=%s", getScope().getName(), publishedName.replaceAll(":", "|")));
                    mbs.unregisterMBean(oName);
                } catch (Exception e) {
                    log.warn("Exception unregistering", e);
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean addAlias(String alias) {
        log.debug("Adding alias: {}", alias);
        if (aliases == null) {
            aliases = new CopyOnWriteArraySet<>();
        }
        // check local registry first then attempt the add
        if (!localAliases.contains(alias) && aliases.add(alias)) {
            return true;
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasAlias() {
        if (aliases != null && !aliases.isEmpty()) {
            return true;
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public String getAlias() {
        String alias = null;
        if (hasAlias()) {
            int bound = aliases.size();
            if (bound > 1) {
                int index = ThreadLocalRandom.current().nextInt(bound);
                alias = aliases.stream().skip(index).findFirst().get();
            } else {
                alias = aliases.stream().findFirst().get();
            }
            log.debug("Returning alias: {}", alias);
        }
        return alias;
    }

    /** {@inheritDoc} */
    @Override
    public boolean containsAlias(String alias) {
        if (aliases != null && !aliases.isEmpty()) {
            return aliases.contains(alias);
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> getAliases() {
        if (aliases != null) {
            return aliases;
        }
        return Collections.emptySet();
    }

    /** {@inheritDoc} */
    @Override
    public void setNameAlias(String nameAlias) {
        // remove any existing registration
        if (this.nameAlias != null && nameAlias != null) {
            if (localAliases.remove(this.nameAlias)) {
                log.warn("Publish name: {} has hijacked previously registered alias", nameAlias);
            }
        }
        // this will overwrite any existing value
        this.nameAlias = nameAlias;
    }

    /** {@inheritDoc} */
    @Override
    public String getNameAlias() {
        return nameAlias;
    }

    /** {@inheritDoc} */
    @Override
    public boolean aliasRegistered(String alias) {
        return localAliases.contains(alias);
    }

}
