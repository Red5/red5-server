/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.httpflv;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.codec.IStreamCodecInfo;
import org.red5.codec.IVideoStreamCodec;
import org.red5.codec.IAudioStreamCodec;
import org.red5.io.IoConstants;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.api.stream.IStreamListener;
import org.red5.server.api.stream.IStreamPacket;
import org.red5.server.net.rtmp.event.Notify;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Represents an HTTP-FLV connection to a client.
 * This class manages the lifecycle of an HTTP-FLV streaming connection,
 * subscribing to a broadcast stream and delivering FLV data over HTTP.
 *
 * Implements IStreamListener to receive packets from the broadcast stream
 * and converts them to FLV tag format for HTTP delivery.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class HTTPFLVConnection implements IStreamListener {

    private static final Logger log = LoggerFactory.getLogger(HTTPFLVConnection.class);

    /** Unique connection identifier */
    private final String connectionId;

    /** Async servlet context for non-blocking I/O */
    private final AsyncContext asyncContext;

    /** HTTP response for writing FLV data */
    private final HttpServletResponse response;

    /** Red5 scope this connection belongs to */
    private final IScope scope;

    /** The broadcast stream being subscribed to */
    private volatile IBroadcastStream broadcastStream;

    /** Stream name being watched */
    private final String streamName;

    /** Connection state flag */
    private final AtomicBoolean connected = new AtomicBoolean(true);

    /** Flag indicating if FLV header has been sent */
    private final AtomicBoolean headerSent = new AtomicBoolean(false);

    /** Flag indicating if video codec config has been sent */
    private final AtomicBoolean videoConfigSent = new AtomicBoolean(false);

    /** Flag indicating if audio codec config has been sent */
    private final AtomicBoolean audioConfigSent = new AtomicBoolean(false);

    /** Count of packets sent */
    private final AtomicLong packetsSent = new AtomicLong(0);

    /** Count of bytes sent */
    private final AtomicLong bytesSent = new AtomicLong(0);

    /** Last activity timestamp */
    private volatile long lastActivity;

    /** Connection creation time */
    private final long createdTime;

    /** Output stream for writing FLV data */
    private volatile OutputStream outputStream;

    /** Stream configuration for codec config and GOP cache */
    private volatile StreamConfiguration streamConfig;

    /** Flag indicating if metadata has been sent */
    private final AtomicBoolean metadataSent = new AtomicBoolean(false);

    /** Flag indicating if GOP cache has been sent */
    private final AtomicBoolean gopCacheSent = new AtomicBoolean(false);

    /** Flag to wait for keyframe before sending video */
    private final AtomicBoolean waitingForKeyframe = new AtomicBoolean(true);

    /**
     * Creates a new HTTP-FLV connection.
     *
     * @param connectionId Unique identifier for this connection
     * @param asyncContext The async servlet context
     * @param response The HTTP response
     * @param scope The Red5 scope this connection belongs to
     * @param streamName The name of the stream to subscribe to
     */
    public HTTPFLVConnection(String connectionId, AsyncContext asyncContext, HttpServletResponse response, IScope scope, String streamName) {
        this.connectionId = connectionId;
        this.asyncContext = asyncContext;
        this.response = response;
        this.scope = scope;
        this.streamName = streamName;
        this.createdTime = System.currentTimeMillis();
        this.lastActivity = createdTime;
        // Set up HTTP-FLV headers
        response.setContentType("video/x-flv");
        response.setHeader("Cache-Control", "no-cache, no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("Transfer-Encoding", "chunked");
        // CORS headers
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Expose-Headers", "Content-Length");
        log.debug("Created HTTP-FLV connection: {} for stream: {} in scope: {}", connectionId, streamName, scope.getName());
    }

    /**
     * Initializes the connection by sending the FLV header.
     *
     * @return true if initialization was successful
     */
    public boolean initialize() {
        if (!connected.get()) {
            return false;
        }
        try {
            outputStream = response.getOutputStream();
            // Send FLV header
            if (headerSent.compareAndSet(false, true)) {
                byte[] header = FLVStreamWriter.createFLVHeader(true, true);
                outputStream.write(header);
                outputStream.flush();
                bytesSent.addAndGet(header.length);
                lastActivity = System.currentTimeMillis();
                log.debug("Sent FLV header for connection: {}", connectionId);
            }
            return true;
        } catch (IOException e) {
            log.debug("Failed to initialize HTTP-FLV connection {}: {}", connectionId, e.getMessage());
            close();
            return false;
        }
    }

    /**
     * Sends initial stream data including metadata and codec configurations.
     * This should be called after initialize() and before subscribing to the stream.
     *
     * @param config the stream configuration containing cached data
     * @return true if data was sent successfully
     */
    public boolean sendInitialData(StreamConfiguration config) {
        if (!connected.get() || config == null) {
            return false;
        }
        this.streamConfig = config;
        try {
            // Send metadata first if available
            byte[] metadata = config.getMetadataTag();
            if (metadata != null && metadataSent.compareAndSet(false, true)) {
                sendData(metadata);
                log.debug("Sent metadata for connection: {}", connectionId);
            }
            // Send video decoder configuration (SPS/PPS)
            byte[] videoConfig = config.getVideoDecoderConfig();
            if (videoConfig != null && videoConfigSent.compareAndSet(false, true)) {
                sendData(videoConfig);
                log.debug("Sent video decoder config for connection: {}", connectionId);
            }
            // Send audio decoder configuration (AudioSpecificConfig)
            byte[] audioConfig = config.getAudioDecoderConfig();
            if (audioConfig != null && audioConfigSent.compareAndSet(false, true)) {
                sendData(audioConfig);
                log.debug("Sent audio decoder config for connection: {}", connectionId);
            }
            // Send GOP cache if available
            StreamConfiguration.CachedFrame[] gopFrames = config.getGopCache();
            if (gopFrames != null && gopFrames.length > 0 && gopCacheSent.compareAndSet(false, true)) {
                for (StreamConfiguration.CachedFrame frame : gopFrames) {
                    sendData(frame.getData());
                }
                waitingForKeyframe.set(false);
                log.debug("Sent {} GOP cache frames for connection: {}", gopFrames.length, connectionId);
            }
            return true;
        } catch (IOException e) {
            log.debug("Failed to send initial data for connection {}: {}", connectionId, e.getMessage());
            close();
            return false;
        }
    }

    /**
     * Sends codec configuration from the broadcast stream.
     * Called when stream config is not available from service.
     *
     * @param stream the broadcast stream
     */
    private void sendCodecConfigFromStream(IBroadcastStream stream) {
        if (stream == null) {
            return;
        }
        IStreamCodecInfo codecInfo = stream.getCodecInfo();
        if (codecInfo == null) {
            return;
        }
        try {
            // Send video decoder config
            if (!videoConfigSent.get()) {
                IVideoStreamCodec videoCodec = codecInfo.getVideoCodec();
                if (videoCodec != null) {
                    IoBuffer decoderConfig = videoCodec.getDecoderConfiguration();
                    if (decoderConfig != null && decoderConfig.hasRemaining()) {
                        int pos = decoderConfig.position();
                        byte[] configBytes = new byte[decoderConfig.remaining()];
                        decoderConfig.get(configBytes);
                        decoderConfig.position(pos);
                        byte[] flvTag = FLVStreamWriter.createFLVTag(IoConstants.TYPE_VIDEO, 0, configBytes);
                        if (flvTag != null && videoConfigSent.compareAndSet(false, true)) {
                            sendData(flvTag);
                            log.debug("Sent video decoder config from stream for connection: {}", connectionId);
                        }
                    }
                }
            }
            // Send audio decoder config
            if (!audioConfigSent.get()) {
                IAudioStreamCodec audioCodec = codecInfo.getAudioCodec();
                if (audioCodec != null) {
                    IoBuffer decoderConfig = audioCodec.getDecoderConfiguration();
                    if (decoderConfig != null && decoderConfig.hasRemaining()) {
                        int pos = decoderConfig.position();
                        byte[] configBytes = new byte[decoderConfig.remaining()];
                        decoderConfig.get(configBytes);
                        decoderConfig.position(pos);
                        byte[] flvTag = FLVStreamWriter.createFLVTag(IoConstants.TYPE_AUDIO, 0, configBytes);
                        if (flvTag != null && audioConfigSent.compareAndSet(false, true)) {
                            sendData(flvTag);
                            log.debug("Sent audio decoder config from stream for connection: {}", connectionId);
                        }
                    }
                }
            }
            // Send metadata
            if (!metadataSent.get()) {
                Notify metaData = stream.getMetaData();
                if (metaData != null) {
                    IoBuffer data = metaData.getData();
                    if (data != null && data.hasRemaining()) {
                        int pos = data.position();
                        byte[] metaBytes = new byte[data.remaining()];
                        data.get(metaBytes);
                        data.position(pos);
                        byte[] flvTag = FLVStreamWriter.createFLVTag(IoConstants.TYPE_METADATA, 0, metaBytes);
                        if (flvTag != null && metadataSent.compareAndSet(false, true)) {
                            sendData(flvTag);
                            log.debug("Sent metadata from stream for connection: {}", connectionId);
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.debug("Failed to send codec config from stream for connection {}: {}", connectionId, e.getMessage());
        }
    }

    /**
     * Subscribes to the specified broadcast stream.
     *
     * @param stream the broadcast stream to subscribe to
     */
    public void subscribe(IBroadcastStream stream) {
        if (stream == null) {
            log.warn("Cannot subscribe to null stream for connection: {}", connectionId);
            return;
        }
        this.broadcastStream = stream;
        // Try to send codec config from stream if not already sent
        sendCodecConfigFromStream(stream);
        stream.addStreamListener(this);
        log.info("HTTP-FLV connection {} subscribed to stream: {}", connectionId, stream.getPublishedName());
    }

    /**
     * Unsubscribes from the broadcast stream.
     */
    public void unsubscribe() {
        if (broadcastStream != null) {
            broadcastStream.removeStreamListener(this);
            log.debug("HTTP-FLV connection {} unsubscribed from stream", connectionId);
            broadcastStream = null;
        }
    }

    /**
     * Receives a packet from the broadcast stream.
     * Implements IStreamListener interface.
     *
     * @param stream the stream the packet was received from
     * @param packet the stream packet
     */
    @Override
    public void packetReceived(IBroadcastStream stream, IStreamPacket packet) {
        if (!connected.get()) {
            return;
        }
        if (packet == null) {
            return;
        }
        try {
            byte dataType = packet.getDataType();
            IoBuffer data = packet.getData();
            if (data == null || !data.hasRemaining()) {
                return;
            }
            // Check for codec configuration packets
            if (dataType == IoConstants.TYPE_VIDEO) {
                if (!handleVideoPacket(packet)) {
                    return;
                }
            } else if (dataType == IoConstants.TYPE_AUDIO) {
                if (!handleAudioPacket(packet)) {
                    return;
                }
            }
            // Convert packet to FLV tag and send
            byte[] flvTag = FLVStreamWriter.packetToFLVTag(packet);
            if (flvTag != null) {
                sendData(flvTag);
                if (log.isTraceEnabled()) {
                    log.trace("Sent {} packet, timestamp: {}, size: {}", FLVStreamWriter.getDataTypeName(dataType), packet.getTimestamp(), flvTag.length);
                }
            }
        } catch (Exception e) {
            log.debug("Error processing packet for connection {}: {}", connectionId, e.getMessage());
            close();
        }
    }

    /**
     * Handles video packet, checking for codec configuration and keyframes.
     *
     * @param packet the video packet
     * @return true if packet should be sent, false to skip
     */
    private boolean handleVideoPacket(IStreamPacket packet) {
        IoBuffer data = packet.getData();
        if (data.remaining() < 2) {
            return false;
        }
        // Check first byte for codec info and frame type
        int position = data.position();
        int firstByte = data.get() & 0xFF;
        int secondByte = data.get() & 0xFF;
        data.position(position); // Reset position
        // Frame type is in upper 4 bits: 1 = keyframe, 2 = inter frame, etc.
        int frameType = (firstByte >> 4) & 0x0F;
        int codecId = firstByte & 0x0F;
        boolean isKeyframe = (frameType == 1);
        boolean isConfigPacket = false;
        // Check for AVC (H.264) or HEVC (H.265)
        if (codecId == 7 || codecId == 12) { // 7 = AVC, 12 = HEVC
            // Second byte: 0 = sequence header, 1 = NALU, 2 = end of sequence
            if (secondByte == 0) {
                // This is codec configuration (SPS/PPS for AVC, VPS/SPS/PPS for HEVC)
                isConfigPacket = true;
                videoConfigSent.set(true);
                log.debug("Video codec configuration received for connection: {} (codec: {})", connectionId, codecId == 7 ? "AVC" : "HEVC");
                // Update stream config if available
                if (streamConfig != null) {
                    streamConfig.setVideoDecoderConfig(data);
                }
            } else if (!videoConfigSent.get()) {
                // Skip video frames until config is received
                log.trace("Skipping video frame before codec config for connection: {}", connectionId);
                return false;
            }
        }
        // Handle keyframe detection and waiting
        if (!isConfigPacket) {
            if (isKeyframe) {
                waitingForKeyframe.set(false);
                // Update GOP cache if available
                if (streamConfig != null) {
                    streamConfig.addToGopCache(packet, true);
                }
            } else if (waitingForKeyframe.get()) {
                // Skip inter frames until we get a keyframe
                log.trace("Skipping inter frame while waiting for keyframe for connection: {}", connectionId);
                return false;
            } else if (streamConfig != null) {
                // Add inter frame to GOP cache
                streamConfig.addToGopCache(packet, false);
            }
        }
        return true;
    }

    /**
     * Handles audio packet, checking for codec configuration.
     *
     * @param packet the audio packet
     * @return true if packet should be sent, false to skip
     */
    private boolean handleAudioPacket(IStreamPacket packet) {
        IoBuffer data = packet.getData();
        if (data.remaining() < 2) {
            return false;
        }
        // Check first byte for codec info
        int position = data.position();
        int firstByte = data.get() & 0xFF;
        int secondByte = data.get() & 0xFF;
        data.position(position); // Reset position
        // Check if this is AAC codec
        int codecId = (firstByte >> 4) & 0x0F;
        if (codecId == 10) { // AAC
            // Second byte: 0 = AAC sequence header, 1 = AAC raw
            if (secondByte == 0) {
                // This is codec configuration (AudioSpecificConfig)
                audioConfigSent.set(true);
                log.debug("Audio codec configuration received for connection: {}", connectionId);
                // Update stream config if available
                if (streamConfig != null) {
                    streamConfig.setAudioDecoderConfig(data);
                }
            } else if (!audioConfigSent.get()) {
                // Skip audio frames until config is received
                log.trace("Skipping audio frame before codec config for connection: {}", connectionId);
                return false;
            }
        }
        return true;
    }

    /**
     * Sends raw data to the HTTP response.
     *
     * @param data the data to send
     * @throws IOException if writing fails
     */
    private void sendData(byte[] data) throws IOException {
        if (!connected.get() || outputStream == null) {
            return;
        }
        synchronized (outputStream) {
            outputStream.write(data);
            outputStream.flush();
        }
        bytesSent.addAndGet(data.length);
        packetsSent.incrementAndGet();
        lastActivity = System.currentTimeMillis();
    }

    /**
     * Closes the HTTP-FLV connection.
     */
    public void close() {
        if (connected.compareAndSet(true, false)) {
            log.debug("Closing HTTP-FLV connection: {}", connectionId);
            // Unsubscribe from stream
            unsubscribe();
            // Complete async context
            try {
                if (asyncContext != null) {
                    asyncContext.complete();
                }
            } catch (Exception e) {
                log.debug("Error completing async context for connection {}: {}", connectionId, e.getMessage());
            }
            log.info("HTTP-FLV connection closed: {} (packets: {}, bytes: {})", connectionId, packetsSent.get(), bytesSent.get());
        }
    }

    /**
     * Checks if the connection is still active.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * Gets the connection ID.
     *
     * @return connection ID
     */
    public String getConnectionId() {
        return connectionId;
    }

    /**
     * Gets the stream name being watched.
     *
     * @return stream name
     */
    public String getStreamName() {
        return streamName;
    }

    /**
     * Gets the scope this connection belongs to.
     *
     * @return the scope
     */
    public IScope getScope() {
        return scope;
    }

    /**
     * Gets the last activity timestamp.
     *
     * @return last activity time in milliseconds
     */
    public long getLastActivity() {
        return lastActivity;
    }

    /**
     * Gets the connection creation time.
     *
     * @return creation time in milliseconds
     */
    public long getCreatedTime() {
        return createdTime;
    }

    /**
     * Gets the number of packets sent.
     *
     * @return packets sent count
     */
    public long getPacketsSent() {
        return packetsSent.get();
    }

    /**
     * Gets the number of bytes sent.
     *
     * @return bytes sent count
     */
    public long getBytesSent() {
        return bytesSent.get();
    }

    /**
     * Gets the async context.
     *
     * @return async context
     */
    public AsyncContext getAsyncContext() {
        return asyncContext;
    }

    @Override
    public String toString() {
        return "HTTPFLVConnection{" + "connectionId='" + connectionId + '\'' + ", streamName='" + streamName + '\'' + ", scope=" + (scope != null ? scope.getName() : "null") + ", connected=" + connected.get() + ", packetsSent=" + packetsSent.get() + ", bytesSent=" + bytesSent.get() + ", lastActivity=" + lastActivity + '}';
    }

}
