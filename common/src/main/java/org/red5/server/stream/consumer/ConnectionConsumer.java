/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2016 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.stream.consumer;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.server.messaging.IMessage;
import org.red5.server.messaging.IMessageComponent;
import org.red5.server.messaging.IPipe;
import org.red5.server.messaging.IPipeConnectionListener;
import org.red5.server.messaging.IPushableConsumer;
import org.red5.server.messaging.OOBControlMessage;
import org.red5.server.messaging.PipeConnectionEvent;
import org.red5.server.net.rtmp.Channel;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.net.rtmp.event.BaseEvent;
import org.red5.server.net.rtmp.event.BytesRead;
import org.red5.server.net.rtmp.event.ChunkSize;
import org.red5.server.net.rtmp.event.FlexStreamSend;
import org.red5.server.net.rtmp.event.IRTMPEvent;
import org.red5.server.net.rtmp.event.Notify;
import org.red5.server.net.rtmp.event.Ping;
import org.red5.server.net.rtmp.event.VideoData;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.net.rtmp.message.Header;
import org.red5.server.stream.message.RTMPMessage;
import org.red5.server.stream.message.ResetMessage;
import org.red5.server.stream.message.StatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RTMP connection consumer.
 */
public class ConnectionConsumer implements IPushableConsumer, IPipeConnectionListener {

    private static final Logger log = LoggerFactory.getLogger(ConnectionConsumer.class);

    private static final boolean isTrace = log.isTraceEnabled();

    @SuppressWarnings("unused")
    private static final boolean isDebug = log.isDebugEnabled();

    /**
     * Connection consumer class name
     */
    public static final String KEY = ConnectionConsumer.class.getName();

    /**
     * Connection object
     */
    private RTMPConnection conn;

    /**
     * Video channel
     */
    private Channel video;

    /**
     * Audio channel
     */
    private Channel audio;

    /**
     * Data channel
     */
    private Channel data;

    /**
     * Chunk size. Packets are sent chunk-by-chunk.
     */
    private int chunkSize = 1024; //TODO: Not sure of the best value here

    /**
     * Whether or not the chunk size has been sent. This seems to be required for h264.
     */
    private AtomicBoolean chunkSizeSent = new AtomicBoolean(false);

    /**
     * Create RTMP connection consumer for given connection and channels.
     *
     * @param conn
     *            RTMP connection
     * @param videoChannel
     *            Video channel
     * @param audioChannel
     *            Audio channel
     * @param dataChannel
     *            Data channel
     */
    public ConnectionConsumer(RTMPConnection conn, Channel videoChannel, Channel audioChannel, Channel dataChannel) {
        log.debug("Channel ids - video: {} audio: {} data: {}", new Object[] { videoChannel, audioChannel, dataChannel });
        this.conn = conn;
        this.video = videoChannel;
        this.audio = audioChannel;
        this.data = dataChannel;
    }

    /**
     * Create connection consumer without an RTMP connection.
     *
     * @param videoChannel
     *            video channel
     * @param audioChannel
     *            audio channel
     * @param dataChannel
     *            data channel
     */
    public ConnectionConsumer(Channel videoChannel, Channel audioChannel, Channel dataChannel) {
        this.video = videoChannel;
        this.audio = audioChannel;
        this.data = dataChannel;
    }

    /** {@inheritDoc} */
    public void pushMessage(IPipe pipe, IMessage message) {
        //log.trace("pushMessage - type: {}", message.getMessageType());
        if (message instanceof ResetMessage) {
            //ignore
        } else if (message instanceof StatusMessage) {
            if (data != null) {
                StatusMessage statusMsg = (StatusMessage) message;
                data.sendStatus(statusMsg.getBody());
            } else {
                log.warn("Channel data is null");
            }
        } else if (message instanceof RTMPMessage) {
            // make sure chunk size has been sent
            sendChunkSize();
            // cast to rtmp message
            RTMPMessage rtmpMsg = (RTMPMessage) message;
            IRTMPEvent msg = rtmpMsg.getBody();
            // get timestamp
            int eventTime = msg.getTimestamp();
            log.debug("Message timestamp: {}", eventTime);
            if (eventTime < 0) {
                //eventTime += Integer.MIN_VALUE;
                //log.debug("Message has negative timestamp, applying {} offset: {}", Integer.MIN_VALUE, eventTime);
                // everyone seems to prefer positive timestamps
                eventTime += (eventTime * -1);
                log.debug("Message has negative timestamp, flipping it to positive: {}", Integer.MIN_VALUE, eventTime);
                msg.setTimestamp(eventTime);
            }
            // get the data type (AMF)
            byte dataType = msg.getDataType();
            if (isTrace) {
                log.trace("Data type: {} source type: {}", dataType, ((BaseEvent) msg).getSourceType());
            }
            // create a new header for the consumer if the message.body doesnt already have one
            final Header header = Optional.ofNullable(msg.getHeader()).orElse(new Header());
            // XXX sets the timerbase, but should we do this if there's already a timerbase?
            header.setTimerBase(eventTime);
            // data buffer
            IoBuffer buf = null;
            switch (dataType) {
                case Constants.TYPE_AGGREGATE:
                    //log.trace("Aggregate data");
                    if (data != null) {
                        data.write(msg);
                    } else {
                        log.warn("Channel data is null, aggregate data was not written");
                    }
                    break;
                case Constants.TYPE_AUDIO_DATA:
                    //log.trace("Audio data");
                    buf = ((AudioData) msg).getData();
                    if (buf != null) {
                        AudioData audioData = new AudioData(buf.asReadOnlyBuffer());
                        audioData.setHeader(header);
                        audioData.setTimestamp(header.getTimer());
                        audioData.setSourceType(((AudioData) msg).getSourceType());
                        audio.write(audioData);
                    } else {
                        log.warn("Audio data was not found");
                    }
                    break;
                case Constants.TYPE_VIDEO_DATA:
                    //log.trace("Video data");
                    buf = ((VideoData) msg).getData();
                    if (buf != null) {
                        VideoData videoData = new VideoData(buf.asReadOnlyBuffer());
                        videoData.setHeader(header);
                        videoData.setTimestamp(header.getTimer());
                        videoData.setSourceType(((VideoData) msg).getSourceType());
                        video.write(videoData);
                    } else {
                        log.warn("Video data was not found");
                    }
                    break;
                case Constants.TYPE_PING:
                    //log.trace("Ping");
                    Ping ping = (Ping) msg;
                    ping.setHeader(header);
                    conn.ping(ping);
                    break;
                case Constants.TYPE_STREAM_METADATA:
                    if (isTrace) {
                        log.trace("Meta data: {}", (Notify) msg);
                    }
                    if (data != null) {
                        Notify notify = (Notify) msg;
                        notify.setHeader(header);
                        notify.setTimestamp(header.getTimer());
                        data.write(notify);
                    } else {
                        log.warn("Channel data is null, metadata was not written");
                    }
                    break;
                case Constants.TYPE_FLEX_STREAM_SEND:
                    //if (isTrace) {
                    //log.trace("Flex stream send: {}", (Notify) msg);
                    //}
                    if (data != null) {
                        FlexStreamSend send = null;
                        if (msg instanceof FlexStreamSend) {
                            send = (FlexStreamSend) msg;
                        } else {
                            send = new FlexStreamSend(((Notify) msg).getData().asReadOnlyBuffer());
                        }
                        send.setHeader(header);
                        send.setTimestamp(header.getTimer());
                        data.write(send);
                    } else {
                        log.warn("Channel data is null, flex stream data was not written");
                    }
                    break;
                case Constants.TYPE_BYTES_READ:
                    //log.trace("Bytes read");
                    BytesRead bytesRead = (BytesRead) msg;
                    bytesRead.setHeader(header);
                    bytesRead.setTimestamp(header.getTimer());
                    conn.getChannel((byte) 2).write(bytesRead);
                    break;
                default:
                    //log.trace("Default: {}", dataType);
                    if (data != null) {
                        data.write(msg);
                    } else {
                        log.warn("Channel data is null, data type: {} was not written", dataType);
                    }
            }
        } else {
            log.debug("Unhandled push message: {}", message);
            if (isTrace) {
                Class<? extends IMessage> clazz = message.getClass();
                log.trace("Class info - name: {} declaring: {} enclosing: {}", new Object[] { clazz.getName(), clazz.getDeclaringClass(), clazz.getEnclosingClass() });
            }
        }
    }

    /** {@inheritDoc} */
    public void onPipeConnectionEvent(PipeConnectionEvent event) {
        if (event.getType().equals(PipeConnectionEvent.EventType.PROVIDER_DISCONNECT)) {
            log.debug("Provider disconnected");
            closeChannels();
        }
    }

    /** {@inheritDoc} */
    public void onOOBControlMessage(IMessageComponent source, IPipe pipe, OOBControlMessage oobCtrlMsg) {
        if ("ConnectionConsumer".equals(oobCtrlMsg.getTarget())) {
            String serviceName = oobCtrlMsg.getServiceName();
            log.trace("Service name: {}", serviceName);
            if ("pendingCount".equals(serviceName)) {
                oobCtrlMsg.setResult(conn.getPendingMessages());
            } else if ("pendingVideoCount".equals(serviceName)) {
                /*
                 * This section relies on the messageSent call-back from Mina to update the pending counter
                 * the logic does not work if RTMPE is used due to the marshalling. For now we will simply return 0
                 * and the caller sending the oob will proceed. The pending video check was implemented to handle
                 * flash player connections on slow links and is most likely irrelevant at this point.
                 *
                IClientStream stream = conn.getStreamByChannelId(video.getId());
                log.trace("pending video count for video id: {} stream: {}", video.getId(), stream);
                if (stream != null) {
                    oobCtrlMsg.setResult(conn.getPendingVideoMessages(stream.getStreamId()));
                } else {
                    oobCtrlMsg.setResult(0L);
                }
                */
                // always return 0 if the connection is encrypted
                oobCtrlMsg.setResult(0L);
            } else if ("writeDelta".equals(serviceName)) {
                //TODO: Revisit the max stream value later
                long maxStream = 120 * 1024;
                // Return the current delta between sent bytes and bytes the client
                // reported to have received, and the interval the client should use
                // for generating BytesRead messages (half of the allowed bandwidth).
                oobCtrlMsg.setResult(new Long[] { conn.getWrittenBytes() - conn.getClientBytesRead(), maxStream / 2 });
            } else if ("chunkSize".equals(serviceName)) {
                int newSize = (Integer) oobCtrlMsg.getServiceParamMap().get("chunkSize");
                if (newSize != chunkSize) {
                    chunkSize = newSize;
                    chunkSizeSent.set(false);
                    sendChunkSize();
                }
            }
        }
    }

    /**
     * Send the chunk size
     */
    private void sendChunkSize() {
        if (chunkSizeSent.compareAndSet(false, true)) {
            log.debug("Sending chunk size: {}", chunkSize);
            ChunkSize chunkSizeMsg = new ChunkSize(chunkSize);
            conn.getChannel((byte) 2).write(chunkSizeMsg);
        }
    }

    /**
     * Close all the channels
     */
    private void closeChannels() {
        if (video != null) {
            conn.closeChannel(video.getId());
        }
        if (audio != null) {
            conn.closeChannel(audio.getId());
        }
        if (data != null) {
            conn.closeChannel(data.getId());
        }
    }

}
