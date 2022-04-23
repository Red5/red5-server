/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2016 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.stream;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.codec.IAudioStreamCodec;
import org.red5.codec.IStreamCodecInfo;
import org.red5.codec.IVideoStreamCodec;
import org.red5.codec.IVideoStreamCodec.FrameData;
import org.red5.codec.StreamCodecInfo;
import org.red5.io.amf.Output;
import org.red5.io.utils.ObjectMap;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.scheduling.IScheduledJob;
import org.red5.server.api.scheduling.ISchedulingService;
import org.red5.server.api.scope.IBroadcastScope;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.api.stream.IPlayItem;
import org.red5.server.api.stream.IPlaylistSubscriberStream;
import org.red5.server.api.stream.ISubscriberStream;
import org.red5.server.api.stream.OperationNotSupportedException;
import org.red5.server.api.stream.StreamState;
import org.red5.server.api.stream.support.DynamicPlayItem;
import org.red5.server.messaging.AbstractMessage;
import org.red5.server.messaging.IConsumer;
import org.red5.server.messaging.IFilter;
import org.red5.server.messaging.IMessage;
import org.red5.server.messaging.IMessageComponent;
import org.red5.server.messaging.IMessageInput;
import org.red5.server.messaging.IMessageOutput;
import org.red5.server.messaging.IPassive;
import org.red5.server.messaging.IPipe;
import org.red5.server.messaging.IPipeConnectionListener;
import org.red5.server.messaging.IProvider;
import org.red5.server.messaging.IPushableConsumer;
import org.red5.server.messaging.InMemoryPushPushPipe;
import org.red5.server.messaging.OOBControlMessage;
import org.red5.server.messaging.PipeConnectionEvent;
import org.red5.server.net.rtmp.event.Aggregate;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.net.rtmp.event.IRTMPEvent;
import org.red5.server.net.rtmp.event.Notify;
import org.red5.server.net.rtmp.event.Ping;
import org.red5.server.net.rtmp.event.VideoData;
import org.red5.server.net.rtmp.event.VideoData.FrameType;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.net.rtmp.message.Header;
import org.red5.server.net.rtmp.status.Status;
import org.red5.server.net.rtmp.status.StatusCodes;
import org.red5.server.stream.message.RTMPMessage;
import org.red5.server.stream.message.ResetMessage;
import org.red5.server.stream.message.StatusMessage;
import org.slf4j.Logger;

/**
 * A play engine for playing a IPlayItem.
 * 
 * @author The Red5 Project
 * @author Steven Gong
 * @author Paul Gregoire (mondain@gmail.com)
 * @author Dan Rossi
 * @author Tiago Daniel Jacobs (tiago@imdt.com.br)
 * @author Vladimir Hmelyoff (vlhm@splitmedialabs.com)
 */
public final class PlayEngine implements IFilter, IPushableConsumer, IPipeConnectionListener {

    private static final Logger log = Red5LoggerFactory.getLogger(PlayEngine.class);

    private final AtomicReference<IMessageInput> msgInReference = new AtomicReference<>();

    private final AtomicReference<IMessageOutput> msgOutReference = new AtomicReference<>();

    private final ISubscriberStream subscriberStream;

    private ISchedulingService schedulingService;

    private IConsumerService consumerService;

    private IProviderService providerService;

    private Number streamId;

    /**
     * Receive video?
     */
    private boolean receiveVideo = true;

    /**
     * Receive audio?
     */
    private boolean receiveAudio = true;

    private boolean pullMode;

    private String waitLiveJob;

    /**
     * timestamp of first sent packet
     */
    private AtomicInteger streamStartTS = new AtomicInteger(-1);

    private AtomicReference<IPlayItem> currentItem = new AtomicReference<>();

    private RTMPMessage pendingMessage;

    /**
     * Interval in ms to check for buffer underruns in VOD streams.
     */
    private int bufferCheckInterval = 0;

    /**
     * Number of pending messages at which a NetStream.Play.InsufficientBW message is generated for VOD streams.
     */
    private int underrunTrigger = 10;

    /**
     * Threshold for number of pending video frames
     */
    private int maxPendingVideoFrames = 10;

    /**
     * If we have more than 1 pending video frames, but less than maxPendingVideoFrames, continue sending until there are this many
     * sequential frames with more than 1 pending
     */
    private int maxSequentialPendingVideoFrames = 10;

    /**
     * the number of sequential video frames with > 0 pending frames
     */
    private int numSequentialPendingVideoFrames = 0;

    /**
     * State machine for video frame dropping in live streams
     */
    private IFrameDropper videoFrameDropper = new VideoFrameDropper();

    private int timestampOffset = 0;

    /**
     * Timestamp of the last message sent to the client.
     */
    private int lastMessageTs = -1;

    /**
     * Number of bytes sent.
     */
    private AtomicLong bytesSent = new AtomicLong(0);

    /**
     * Start time of stream playback. It's not a time when the stream is being played but the time when the stream should be played if it's
     * played from the very beginning. Eg. A stream is played at timestamp 5s on 1:00:05. The playbackStart is 1:00:00.
     */
    private volatile long playbackStart;

    /**
     * Flag denoting whether or not the push and pull job is scheduled. The job makes sure messages are sent to the client.
     */
    private volatile String pullAndPush;

    /**
     * Flag denoting whether or not the job that closes stream after buffer runs out is scheduled.
     */
    private volatile String deferredStop;

    /**
     * Monitor guarding completion of a given push/pull run. Used to wait for job cancellation to finish.
     */
    private final AtomicBoolean pushPullRunning = new AtomicBoolean(false);

    /**
     * Offset in milliseconds where the stream started.
     */
    private int streamOffset;

    /**
     * Timestamp when buffer should be checked for underruns next.
     */
    private long nextCheckBufferUnderrun;

    /**
     * Send blank audio packet next?
     */
    private boolean sendBlankAudio;

    /**
     * Decision: 0 for Live, 1 for File, 2 for Wait, 3 for N/A
     */
    private int playDecision = 3;

    /**
     * Index of the buffered interframe to send instead of current frame
     */
    private int bufferedInterframeIdx = -1;

    /**
     * List of pending operations
     */
    private ConcurrentLinkedQueue<Runnable> pendingOperations = new ConcurrentLinkedQueue<>();

    // Keep count of dropped packets so we can log every so often.
    private long droppedPacketsCount;

    private long droppedPacketsCountLastLogTimestamp = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());

    private long droppedPacketsCountLogInterval = 60 * 1000L;

    private boolean configsDone;

    /**
     * Constructs a new PlayEngine.
     */
    private PlayEngine(Builder builder) {
        subscriberStream = builder.subscriberStream;
        schedulingService = builder.schedulingService;
        consumerService = builder.consumerService;
        providerService = builder.providerService;
        // get the stream id
        streamId = subscriberStream.getStreamId();
    }

    /**
     * Builder pattern
     */
    public final static class Builder {
        //Required for play engine
        private ISubscriberStream subscriberStream;

        //Required for play engine
        private ISchedulingService schedulingService;

        //Required for play engine
        private IConsumerService consumerService;

        //Required for play engine
        private IProviderService providerService;

        public Builder(ISubscriberStream subscriberStream, ISchedulingService schedulingService, IConsumerService consumerService, IProviderService providerService) {
            this.subscriberStream = subscriberStream;
            this.schedulingService = schedulingService;
            this.consumerService = consumerService;
            this.providerService = providerService;
        }

        public PlayEngine build() {
            return new PlayEngine(this);
        }

    }

    public void setBufferCheckInterval(int bufferCheckInterval) {
        this.bufferCheckInterval = bufferCheckInterval;
    }

    public void setUnderrunTrigger(int underrunTrigger) {
        this.underrunTrigger = underrunTrigger;
    }

    void setMessageOut(IMessageOutput msgOut) {
        this.msgOutReference.set(msgOut);
    }

    /**
     * Start stream
     */
    public void start() {
        if (log.isDebugEnabled()) {
            log.debug("start - subscriber stream state: {}", (subscriberStream != null ? subscriberStream.getState() : null));
        }
        switch (subscriberStream.getState()) {
            case UNINIT:
                // allow start if uninitialized and change state to stopped
                subscriberStream.setState(StreamState.STOPPED);
                IMessageOutput out = consumerService.getConsumerOutput(subscriberStream);
                if (msgOutReference.compareAndSet(null, out)) {
                    out.subscribe(this, null);
                } else if (log.isDebugEnabled()) {
                    log.debug("Message output was already set for stream: {}", subscriberStream);
                }
                break;
            default:
                throw new IllegalStateException(String.format("Cannot start in current state: %s", subscriberStream.getState()));
        }
    }

    /**
     * Play stream
     * 
     * @param item
     *            Playlist item
     * @throws StreamNotFoundException
     *             Stream not found
     * @throws IllegalStateException
     *             Stream is in stopped state
     * @throws IOException
     *             Stream had io exception
     */
    public void play(IPlayItem item) throws StreamNotFoundException, IllegalStateException, IOException {
        play(item, true);
    }

    /**
     * Play stream
     * 
     * See: https://www.adobe.com/devnet/adobe-media-server/articles/dynstream_actionscript.html
     * 
     * @param item
     *            Playlist item
     * @param withReset
     *            Send reset status before playing.
     * @throws StreamNotFoundException
     *             Stream not found
     * @throws IllegalStateException
     *             Stream is in stopped state
     * @throws IOException
     *             Stream had IO exception
     */
    public void play(IPlayItem item, boolean withReset) throws StreamNotFoundException, IllegalStateException, IOException {
        IMessageInput in = null;
        // cannot play if state is not stopped
        switch (subscriberStream.getState()) {
            case STOPPED:
                in = msgInReference.get();
                if (in != null) {
                    in.unsubscribe(this);
                    msgInReference.set(null);
                }
                break;
            default:
                throw new IllegalStateException("Cannot play from non-stopped state");
        }
        // Play type determination
        // https://help.adobe.com/en_US/FlashPlatform/reference/actionscript/3/flash/net/NetStream.html#play()
        // The start time, in seconds. Allowed values are -2, -1, 0, or a positive number. 
        // The default value is -2, which looks for a live stream, then a recorded stream, 
        // and if it finds neither, opens a live stream. 
        // If -1, plays only a live stream. 
        // If 0 or a positive number, plays a recorded stream, beginning start seconds in.
        //
        // -2: live then recorded, -1: live, >=0: recorded
        int type = (int) (item.getStart() / 1000);
        log.debug("Type {}", type);
        // see if it's a published stream
        IScope thisScope = subscriberStream.getScope();
        final String itemName = item.getName();
        //check for input and type
        IProviderService.INPUT_TYPE sourceType = providerService.lookupProviderInput(thisScope, itemName, type);
        boolean sendNotifications = true;
        // decision: 0 for Live, 1 for File, 2 for Wait, 3 for N/A
        switch (type) {
            case -2:
                if (sourceType == IProviderService.INPUT_TYPE.LIVE) {
                    playDecision = 0;
                } else if (sourceType == IProviderService.INPUT_TYPE.VOD) {
                    playDecision = 1;
                } else if (sourceType == IProviderService.INPUT_TYPE.LIVE_WAIT) {
                    playDecision = 2;
                }
                break;
            case -1:
                if (sourceType == IProviderService.INPUT_TYPE.LIVE) {
                    playDecision = 0;
                } else if (sourceType == IProviderService.INPUT_TYPE.LIVE_WAIT) {
                    playDecision = 2;
                }
                break;
            default:
                if (sourceType == IProviderService.INPUT_TYPE.VOD) {
                    playDecision = 1;
                }
                break;
        }
        IMessage msg = null;
        currentItem.set(item);
        long itemLength = item.getLength();
        if (log.isDebugEnabled()) {
            log.debug("Play decision is {} (0=Live, 1=File, 2=Wait, 3=N/A) item length: {}", playDecision, itemLength);
        }
        switch (playDecision) {
            case 0:
                // get source input without create
                in = providerService.getLiveProviderInput(thisScope, itemName, false);
                if (msgInReference.compareAndSet(null, in)) {
                    // drop all frames up to the next keyframe
                    videoFrameDropper.reset(IFrameDropper.SEND_KEYFRAMES_CHECK);
                    if (in instanceof IBroadcastScope) {
                        IBroadcastStream stream = (IBroadcastStream) ((IBroadcastScope) in).getClientBroadcastStream();
                        if (stream != null && stream.getCodecInfo() != null) {
                            IVideoStreamCodec videoCodec = stream.getCodecInfo().getVideoCodec();
                            if (videoCodec != null) {
                                if (withReset) {
                                    sendReset();
                                    sendResetStatus(item);
                                    sendStartStatus(item);
                                }
                                sendNotifications = false;
                                if (videoCodec.getNumInterframes() > 0 || videoCodec.getKeyframe() != null) {
                                    bufferedInterframeIdx = 0;
                                    videoFrameDropper.reset(IFrameDropper.SEND_ALL);
                                }
                            }
                        }
                    }
                    // subscribe to stream (ClientBroadcastStream.onPipeConnectionEvent)
                    in.subscribe(this, null);
                    // execute the processes to get Live playback setup
                    playLive();
                } else {
                    sendStreamNotFoundStatus(item);
                    throw new StreamNotFoundException(itemName);
                }
                break;
            case 2:
                // get source input with create
                in = providerService.getLiveProviderInput(thisScope, itemName, true);
                if (msgInReference.compareAndSet(null, in)) {
                    if (type == -1 && itemLength >= 0) {
                        if (log.isDebugEnabled()) {
                            log.debug("Creating wait job for {}", itemLength);
                        }
                        // Wait given timeout for stream to be published
                        waitLiveJob = schedulingService.addScheduledOnceJob(itemLength, new IScheduledJob() {
                            public void execute(ISchedulingService service) {
                                connectToProvider(itemName);
                                waitLiveJob = null;
                                subscriberStream.onChange(StreamState.END);
                            }
                        });
                    } else if (type == -2) {
                        if (log.isDebugEnabled()) {
                            log.debug("Creating wait job");
                        }
                        // Wait x seconds for the stream to be published
                        waitLiveJob = schedulingService.addScheduledOnceJob(15000, new IScheduledJob() {
                            public void execute(ISchedulingService service) {
                                connectToProvider(itemName);
                                waitLiveJob = null;
                            }
                        });
                    } else {
                        connectToProvider(itemName);
                    }
                } else if (log.isDebugEnabled()) {
                    log.debug("Message input already set for {}", itemName);
                }
                break;
            case 1:
                in = providerService.getVODProviderInput(thisScope, itemName);
                if (msgInReference.compareAndSet(null, in)) {
                    if (in.subscribe(this, null)) {
                        // execute the processes to get VOD playback setup
                        msg = playVOD(withReset, itemLength);
                    } else {
                        log.warn("Input source subscribe failed");
                        throw new IOException(String.format("Subscribe to %s failed", itemName));
                    }
                } else {
                    sendStreamNotFoundStatus(item);
                    throw new StreamNotFoundException(itemName);
                }
                break;
            default:
                sendStreamNotFoundStatus(item);
                throw new StreamNotFoundException(itemName);
        }
        //continue with common play processes (live and vod)
        if (sendNotifications) {
            if (withReset) {
                sendReset();
                sendResetStatus(item);
            }
            sendStartStatus(item);
            if (!withReset) {
                sendSwitchStatus();
            }
            // if its dynamic playback send the complete status
            if (item instanceof DynamicPlayItem) {
                sendTransitionStatus();
            }
        }
        if (msg != null) {
            sendMessage((RTMPMessage) msg);
        }
        subscriberStream.onChange(StreamState.PLAYING, item, !pullMode);
        if (withReset) {
            log.debug("Resetting times");
            long currentTime = System.currentTimeMillis();
            playbackStart = currentTime - streamOffset;
            nextCheckBufferUnderrun = currentTime + bufferCheckInterval;
            if (item.getLength() != 0) {
                ensurePullAndPushRunning();
            }
        }
    }

    /**
     * Performs the processes needed for live streams. The following items are sent if they exist:
     * <ul>
     * <li>Metadata</li>
     * <li>Decoder configurations (ie. AVC codec)</li>
     * <li>Most recent keyframe</li>
     * </ul>
     * 
     * @throws IOException
     */
    private final void playLive() throws IOException {
        // change state
        subscriberStream.setState(StreamState.PLAYING);
        IMessageInput in = msgInReference.get();
        IMessageOutput out = msgOutReference.get();
        if (in != null && out != null) {
            // get the stream so that we can grab any metadata and decoder configs
            IBroadcastStream stream = (IBroadcastStream) ((IBroadcastScope) in).getClientBroadcastStream();
            // prevent an NPE when a play list is created and then immediately flushed
            int ts = 0;
            if (stream != null) {
                Notify metaData = stream.getMetaData();
                //check for metadata to send
                if (metaData != null) {
                    ts = metaData.getTimestamp();
                    log.debug("Metadata is available");
                    RTMPMessage metaMsg = RTMPMessage.build(metaData, metaData.getTimestamp());
                    sendMessage(metaMsg);
                } else {
                    log.debug("No metadata available");
                }
                IStreamCodecInfo codecInfo = stream.getCodecInfo();
                log.debug("Codec info: {}", codecInfo);
                if (codecInfo instanceof StreamCodecInfo) {
                    StreamCodecInfo info = (StreamCodecInfo) codecInfo;
                    // handle video codec with configuration
                    IVideoStreamCodec videoCodec = info.getVideoCodec();
                    log.debug("Video codec: {}", videoCodec);
                    if (videoCodec != null) {
                        // check for decoder configuration to send
                        IoBuffer config = videoCodec.getDecoderConfiguration();
                        if (config != null) {
                            log.debug("Decoder configuration is available for {}", videoCodec.getName());
                            VideoData conf = new VideoData(config, true);
                            log.debug("Pushing video decoder configuration");
                            sendMessage(RTMPMessage.build(conf, ts));
                        }
                        // check for keyframes to send
                        FrameData[] keyFrames = videoCodec.getKeyframes();
                        for (FrameData keyframe : keyFrames) {
                            log.debug("Keyframe is available");
                            VideoData video = new VideoData(keyframe.getFrame(), true);
                            log.debug("Pushing keyframe");
                            sendMessage(RTMPMessage.build(video, ts));
                        }
                    } else {
                        log.debug("No video decoder configuration available");
                    }
                    // handle audio codec with configuration
                    IAudioStreamCodec audioCodec = info.getAudioCodec();
                    log.debug("Audio codec: {}", audioCodec);
                    if (audioCodec != null) {
                        // check for decoder configuration to send
                        IoBuffer config = audioCodec.getDecoderConfiguration();
                        if (config != null) {
                            log.debug("Decoder configuration is available for {}", audioCodec.getName());
                            AudioData conf = new AudioData(config.asReadOnlyBuffer());
                            log.debug("Pushing audio decoder configuration");
                            sendMessage(RTMPMessage.build(conf, ts));
                        }
                    } else {
                        log.debug("No audio decoder configuration available");
                    }
                }
            }
        } else {
            throw new IOException(String.format("A message pipe is null - in: %b out: %b", (msgInReference == null), (msgOutReference == null)));
        }
        configsDone = true;
    }

    /**
     * Performs the processes needed for VOD / pre-recorded streams.
     * 
     * @param withReset
     *            whether or not to perform reset on the stream
     * @param itemLength
     *            length of the item to be played
     * @return message for the consumer
     * @throws IOException
     */
    private final IMessage playVOD(boolean withReset, long itemLength) throws IOException {
        IMessage msg = null;
        // change state
        subscriberStream.setState(StreamState.PLAYING);
        if (withReset) {
            releasePendingMessage();
        }
        sendVODInitCM(currentItem.get());
        // Don't use pullAndPush to detect IOExceptions prior to sending NetStream.Play.Start
        int start = (int) currentItem.get().getStart();
        if (start > 0) {
            streamOffset = sendVODSeekCM(start);
            // We seeked to the nearest keyframe so use real timestamp now
            if (streamOffset == -1) {
                streamOffset = start;
            }
        }
        IMessageInput in = msgInReference.get();
        msg = in.pullMessage();
        if (msg instanceof RTMPMessage) {
            // Only send first video frame
            IRTMPEvent body = ((RTMPMessage) msg).getBody();
            if (itemLength == 0) {
                while (body != null && !(body instanceof VideoData)) {
                    msg = in.pullMessage();
                    if (msg != null && msg instanceof RTMPMessage) {
                        body = ((RTMPMessage) msg).getBody();
                    } else {
                        break;
                    }
                }
            }
            if (body != null) {
                // Adjust timestamp when playing lists
                body.setTimestamp(body.getTimestamp() + timestampOffset);
            }
        }
        return msg;
    }

    /**
     * Connects to the data provider.
     * 
     * @param itemName
     *            name of the item to play
     */
    private final void connectToProvider(String itemName) {
        log.debug("Attempting connection to {}", itemName);
        IMessageInput in = msgInReference.get();
        if (in == null) {
            in = providerService.getLiveProviderInput(subscriberStream.getScope(), itemName, true);
            msgInReference.set(in);
        }
        if (in != null) {
            log.debug("Provider: {}", msgInReference.get());
            if (in.subscribe(this, null)) {
                log.debug("Subscribed to {} provider", itemName);
                // execute the processes to get Live playback setup
                try {
                    playLive();
                } catch (IOException e) {
                    log.warn("Could not play live stream: {}", itemName, e);
                }
            } else {
                log.warn("Subscribe to {} provider failed", itemName);
            }
        } else {
            log.warn("Provider was not found for {}", itemName);
            StreamService.sendNetStreamStatus(subscriberStream.getConnection(), StatusCodes.NS_PLAY_STREAMNOTFOUND, "Stream was not found", itemName, Status.ERROR, streamId);
        }
    }

    /**
     * Pause at position
     * 
     * @param position
     *            Position in file
     * @throws IllegalStateException
     *             If stream is stopped
     */
    public void pause(int position) throws IllegalStateException {
        // allow pause if playing or stopped
        switch (subscriberStream.getState()) {
            case PLAYING:
            case STOPPED:
                subscriberStream.setState(StreamState.PAUSED);
                clearWaitJobs();
                sendPauseStatus(currentItem.get());
                sendClearPing();
                subscriberStream.onChange(StreamState.PAUSED, currentItem.get(), position);
                break;
            default:
                throw new IllegalStateException("Cannot pause in current state");
        }
    }

    /**
     * Resume playback
     * 
     * @param position
     *            Resumes playback
     * @throws IllegalStateException
     *             If stream is stopped
     */
    public void resume(int position) throws IllegalStateException {
        // allow resume from pause
        switch (subscriberStream.getState()) {
            case PAUSED:
                subscriberStream.setState(StreamState.PLAYING);
                sendReset();
                sendResumeStatus(currentItem.get());
                if (pullMode) {
                    sendVODSeekCM(position);
                    subscriberStream.onChange(StreamState.RESUMED, currentItem.get(), position);
                    playbackStart = System.currentTimeMillis() - position;
                    long length = currentItem.get().getLength();
                    if (length >= 0 && (position - streamOffset) >= length) {
                        // Resume after end of stream
                        stop();
                    } else {
                        ensurePullAndPushRunning();
                    }
                } else {
                    subscriberStream.onChange(StreamState.RESUMED, currentItem.get(), position);
                    videoFrameDropper.reset(VideoFrameDropper.SEND_KEYFRAMES_CHECK);
                }
                break;
            default:
                throw new IllegalStateException("Cannot resume from non-paused state");
        }
    }

    /**
     * Seek to a given position
     * 
     * @param position
     *            Position
     * @throws IllegalStateException
     *             If stream is in stopped state
     * @throws OperationNotSupportedException
     *             If this object doesn't support the operation.
     */
    public void seek(int position) throws IllegalStateException, OperationNotSupportedException {
        // add this pending seek operation to the list
        pendingOperations.add(new SeekRunnable(position));
        cancelDeferredStop();
        ensurePullAndPushRunning();
    }

    /**
     * Stop playback
     * 
     * @throws IllegalStateException
     *             If stream is in stopped state
     */
    public void stop() throws IllegalStateException {
        if (log.isDebugEnabled()) {
            log.debug("stop - subscriber stream state: {}", (subscriberStream != null ? subscriberStream.getState() : null));
        }
        // allow stop if playing or paused
        switch (subscriberStream.getState()) {
            case PLAYING:
            case PAUSED:
                subscriberStream.setState(StreamState.STOPPED);
                IMessageInput in = msgInReference.get();
                if (in != null && !pullMode) {
                    in.unsubscribe(this);
                    msgInReference.set(null);
                }
                subscriberStream.onChange(StreamState.STOPPED, currentItem.get());
                clearWaitJobs();
                cancelDeferredStop();
                if (subscriberStream instanceof IPlaylistSubscriberStream) {
                    IPlaylistSubscriberStream pss = (IPlaylistSubscriberStream) subscriberStream;
                    if (!pss.hasMoreItems()) {
                        releasePendingMessage();
                        sendCompleteStatus();
                        bytesSent.set(0);
                        sendStopStatus(currentItem.get());
                        sendClearPing();
                    } else {
                        if (lastMessageTs > 0) {
                            // remember last timestamp so we can generate correct headers in playlists.
                            timestampOffset = lastMessageTs;
                        }
                        pss.nextItem();
                    }
                }
                break;
            case CLOSED:
                clearWaitJobs();
                cancelDeferredStop();
                break;
            case STOPPED:
                log.trace("Already in stopped state");
                break;
            default:
                throw new IllegalStateException(String.format("Cannot stop in current state: %s", subscriberStream.getState()));
        }
    }

    /**
     * Close stream
     */
    public void close() {
        if (log.isDebugEnabled()) {
            log.debug("close");
        }
        if (!subscriberStream.getState().equals(StreamState.CLOSED)) {
            IMessageInput in = msgInReference.get();
            if (in != null) {
                in.unsubscribe(this);
                msgInReference.set(null);
            }
            subscriberStream.setState(StreamState.CLOSED);
            clearWaitJobs();
            releasePendingMessage();
            lastMessageTs = 0;
            // XXX is clear ping required?
            //sendClearPing();
            InMemoryPushPushPipe out = (InMemoryPushPushPipe) msgOutReference.get();
            if (out != null) {
                List<IConsumer> consumers = out.getConsumers();
                // assume a list of 1 in most cases
                if (log.isDebugEnabled()) {
                    log.debug("Message out consumers: {}", consumers.size());
                }
                if (!consumers.isEmpty()) {
                    for (IConsumer consumer : consumers) {
                        out.unsubscribe(consumer);
                    }
                }
                msgOutReference.set(null);
            }
        } else {
            log.debug("Stream is already in closed state");
        }
    }

    /**
     * Check if it's okay to send the client more data. This takes the configured bandwidth as well as the requested client buffer into
     * account.
     * 
     * @param message
     * @return true if it is ok to send more, false otherwise
     */
    private boolean okayToSendMessage(IRTMPEvent message) {
        if (message instanceof IStreamData) {
            final long now = System.currentTimeMillis();
            // check client buffer size
            if (isClientBufferFull(now)) {
                return false;
            }
            // get pending message count
            long pending = pendingMessages();
            if (bufferCheckInterval > 0 && now >= nextCheckBufferUnderrun) {
                if (pending > underrunTrigger) {
                    // client is playing behind speed, notify him
                    sendInsufficientBandwidthStatus(currentItem.get());
                }
                nextCheckBufferUnderrun = now + bufferCheckInterval;
            }
            // check for under run
            if (pending > underrunTrigger) {
                // too many messages already queued on the connection
                return false;
            }
            return true;
        } else {
            String itemName = "Undefined";
            // if current item exists get the name to help debug this issue
            if (currentItem.get() != null) {
                itemName = currentItem.get().getName();
            }
            Object[] errorItems = new Object[] { message.getClass(), message.getDataType(), itemName };
            throw new RuntimeException(String.format("Expected IStreamData but got %s (type %s) for %s", errorItems));
        }
    }

    /**
     * Estimate client buffer fill.
     * 
     * @param now
     *            The current timestamp being used.
     * @return True if it appears that the client buffer is full, otherwise false.
     */
    private boolean isClientBufferFull(final long now) {
        // check client buffer length when we've already sent some messages
        if (lastMessageTs > 0) {
            // duration the stream is playing / playback duration
            final long delta = now - playbackStart;
            // buffer size as requested by the client
            final long buffer = subscriberStream.getClientBufferDuration();
            // expected amount of data present in client buffer
            final long buffered = lastMessageTs - delta;
            log.trace("isClientBufferFull: timestamp {} delta {} buffered {} buffer duration {}", new Object[] { lastMessageTs, delta, buffered, buffer });
            // fix for SN-122, this sends double the size of the client buffer
            if (buffer > 0 && buffered > (buffer * 2)) {
                // client is likely to have enough data in the buffer
                return true;
            }
        }
        return false;
    }

    private boolean isClientBufferEmpty() {
        // check client buffer length when we've already sent some messages
        if (lastMessageTs >= 0) {
            // duration the stream is playing / playback duration
            final long delta = System.currentTimeMillis() - playbackStart;
            // expected amount of data present in client buffer
            final long buffered = lastMessageTs - delta;
            log.trace("isClientBufferEmpty: timestamp {} delta {} buffered {}", new Object[] { lastMessageTs, delta, buffered });
            if (buffered < 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Make sure the pull and push processing is running.
     */
    private void ensurePullAndPushRunning() {
        log.trace("State should be PLAYING to running this task: {}", subscriberStream.getState());
        if (pullMode && pullAndPush == null && subscriberStream.getState() == StreamState.PLAYING) {
            // client buffer is at least 100ms
            pullAndPush = subscriberStream.scheduleWithFixedDelay(new PullAndPushRunnable(), 10);
        }
    }

    /**
     * Clear all scheduled waiting jobs
     */
    private void clearWaitJobs() {
        log.debug("Clear wait jobs");
        if (pullAndPush != null) {
            subscriberStream.cancelJob(pullAndPush);
            releasePendingMessage();
            pullAndPush = null;
        }
        if (waitLiveJob != null) {
            schedulingService.removeScheduledJob(waitLiveJob);
            waitLiveJob = null;
        }
    }

    /**
     * Sends a status message.
     * 
     * @param status
     */
    private void doPushMessage(Status status) {
        StatusMessage message = new StatusMessage();
        message.setBody(status);
        doPushMessage(message);
    }

    /**
     * Send message to output stream and handle exceptions.
     * 
     * @param message
     *            The message to send.
     */
    private void doPushMessage(AbstractMessage message) {
        if (log.isTraceEnabled()) {
            String msgType = message.getMessageType();
            log.trace("doPushMessage: {}", msgType);
        }
        IMessageOutput out = msgOutReference.get();
        if (out != null) {
            try {
                out.pushMessage(message);
                if (message instanceof RTMPMessage) {
                    IRTMPEvent body = ((RTMPMessage) message).getBody();
                    // update the last message sent's timestamp
                    lastMessageTs = body.getTimestamp();
                    IoBuffer streamData = null;
                    if (body instanceof IStreamData && (streamData = ((IStreamData<?>) body).getData()) != null) {
                        bytesSent.addAndGet(streamData.limit());
                    }
                }
            } catch (IOException err) {
                log.warn("Error while pushing message", err);
            }
        } else {
            log.warn("Push message failed due to null output pipe");
        }
    }

    /**
     * Send an RTMP message
     * 
     * @param messageIn
     *            incoming RTMP message
     */
    private void sendMessage(RTMPMessage messageIn) {
        IRTMPEvent eventIn = messageIn.getBody();
        IRTMPEvent event;
        switch (eventIn.getDataType()) {
            case Constants.TYPE_AGGREGATE:
                event = new Aggregate(((Aggregate) eventIn).getData());
                break;
            case Constants.TYPE_AUDIO_DATA:
                event = new AudioData(((AudioData) eventIn).getData());
                break;
            case Constants.TYPE_VIDEO_DATA:
                event = new VideoData(((VideoData) eventIn).getData());
                break;
            default:
                event = new Notify(((Notify) eventIn).getData());
                break;
        }
        // get the incoming event time
        int eventTime = eventIn.getTimestamp();
        // get the incoming event source type and set on the outgoing event
        event.setSourceType(eventIn.getSourceType());
        // instance the outgoing message
        RTMPMessage messageOut = RTMPMessage.build(event, eventTime);
        if (log.isTraceEnabled()) {
            log.trace("Source type - in: {} out: {}", eventIn.getSourceType(), messageOut.getBody().getSourceType());
            long delta = System.currentTimeMillis() - playbackStart;
            log.trace("sendMessage: streamStartTS {}, length {}, streamOffset {}, timestamp {} last timestamp {} delta {} buffered {}", new Object[] { streamStartTS.get(), currentItem.get().getLength(), streamOffset, eventTime, lastMessageTs, delta, lastMessageTs - delta });
        }
        if (playDecision == 1) { // 1 == vod/file
            if (eventTime > 0 && streamStartTS.compareAndSet(-1, eventTime)) {
                log.debug("sendMessage: set streamStartTS");
                messageOut.getBody().setTimestamp(0);
            }
            long length = currentItem.get().getLength();
            if (length >= 0) {
                int duration = eventTime - streamStartTS.get();
                if (log.isTraceEnabled()) {
                    log.trace("sendMessage duration={} length={}", duration, length);
                }
                if (duration - streamOffset >= length) {
                    // sent enough data to client
                    stop();
                    return;
                }
            }
        } else {
            // don't reset streamStartTS to 0 for live streams
            if (eventTime > 0 && streamStartTS.compareAndSet(-1, eventTime)) {
                log.debug("sendMessage: set streamStartTS");
            }
            // relative timestamp adjustment for live streams
            int startTs = streamStartTS.get();
            if (startTs > 0) {
                // subtract the offset time of when the stream started playing for the client
                eventTime -= startTs;
                messageOut.getBody().setTimestamp(eventTime);
                if (log.isTraceEnabled()) {
                    log.trace("sendMessage (updated): streamStartTS={}, length={}, streamOffset={}, timestamp={}", new Object[] { startTs, currentItem.get().getLength(), streamOffset, eventTime });
                }
            }
        }
        doPushMessage(messageOut);
    }

    /**
     * Send clear ping. Lets client know that stream has no more data to send.
     */
    private void sendClearPing() {
        Ping eof = new Ping();
        eof.setEventType(Ping.STREAM_PLAYBUFFER_CLEAR);
        eof.setValue2(streamId);
        // eos 
        RTMPMessage eofMsg = RTMPMessage.build(eof);
        doPushMessage(eofMsg);
    }

    /**
     * Send reset message
     */
    private void sendReset() {
        if (pullMode) {
            Ping recorded = new Ping();
            recorded.setEventType(Ping.RECORDED_STREAM);
            recorded.setValue2(streamId);
            // recorded 
            RTMPMessage recordedMsg = RTMPMessage.build(recorded);
            doPushMessage(recordedMsg);
        }
        Ping begin = new Ping();
        begin.setEventType(Ping.STREAM_BEGIN);
        begin.setValue2(streamId);
        // begin 
        RTMPMessage beginMsg = RTMPMessage.build(begin);
        doPushMessage(beginMsg);
        // reset
        ResetMessage reset = new ResetMessage();
        doPushMessage(reset);
    }

    /**
     * Send reset status for item
     * 
     * @param item
     *            Playlist item
     */
    private void sendResetStatus(IPlayItem item) {
        Status reset = new Status(StatusCodes.NS_PLAY_RESET);
        reset.setClientid(streamId);
        reset.setDetails(item.getName());
        reset.setDesciption(String.format("Playing and resetting %s.", item.getName()));

        doPushMessage(reset);
    }

    /**
     * Send playback start status notification
     * 
     * @param item
     *            Playlist item
     */
    private void sendStartStatus(IPlayItem item) {
        Status start = new Status(StatusCodes.NS_PLAY_START);
        start.setClientid(streamId);
        start.setDetails(item.getName());
        start.setDesciption(String.format("Started playing %s.", item.getName()));

        doPushMessage(start);
    }

    /**
     * Send playback stoppage status notification
     * 
     * @param item
     *            Playlist item
     */
    private void sendStopStatus(IPlayItem item) {
        Status stop = new Status(StatusCodes.NS_PLAY_STOP);
        stop.setClientid(streamId);
        stop.setDesciption(String.format("Stopped playing %s.", item.getName()));
        stop.setDetails(item.getName());

        doPushMessage(stop);
    }

    /**
     * Sends an onPlayStatus message.
     * 
     * http://help.adobe.com/en_US/FlashPlatform/reference/actionscript/3/flash/events/NetDataEvent.html
     * 
     * @param code
     * @param duration
     * @param bytes
     */
    private void sendOnPlayStatus(String code, int duration, long bytes) {
        if (log.isDebugEnabled()) {
            log.debug("Sending onPlayStatus - code: {} duration: {} bytes: {}", code, duration, bytes);
        }
        // create the buffer
        IoBuffer buf = IoBuffer.allocate(102);
        buf.setAutoExpand(true);
        Output out = new Output(buf);
        out.writeString("onPlayStatus");
        ObjectMap<Object, Object> args = new ObjectMap<>();
        args.put("code", code);
        args.put("level", Status.STATUS);
        args.put("duration", duration);
        args.put("bytes", bytes);
        String name = currentItem.get().getName();
        if (StatusCodes.NS_PLAY_TRANSITION_COMPLETE.equals(code)) {
            args.put("clientId", streamId);
            args.put("details", name);
            args.put("description", String.format("Transitioned to %s", name));
            args.put("isFastPlay", false);
        }
        out.writeObject(args);
        buf.flip();
        Notify event = new Notify(buf, "onPlayStatus");
        if (lastMessageTs > 0) {
            event.setTimestamp(lastMessageTs);
        } else {
            event.setTimestamp(0);
        }
        RTMPMessage msg = RTMPMessage.build(event);
        doPushMessage(msg);
    }

    /**
     * Send playlist switch status notification
     */
    private void sendSwitchStatus() {
        // TODO: find correct duration to send
        sendOnPlayStatus(StatusCodes.NS_PLAY_SWITCH, 1, bytesSent.get());
    }

    /**
     * Send transition status notification
     */
    private void sendTransitionStatus() {
        sendOnPlayStatus(StatusCodes.NS_PLAY_TRANSITION_COMPLETE, 0, bytesSent.get());
    }

    /**
     * Send playlist complete status notification
     *
     */
    private void sendCompleteStatus() {
        // may be the correct duration
        int duration = (lastMessageTs > 0) ? Math.max(0, lastMessageTs - streamStartTS.get()) : 0;
        if (log.isDebugEnabled()) {
            log.debug("sendCompleteStatus - duration: {} bytes sent: {}", duration, bytesSent.get());
        }
        sendOnPlayStatus(StatusCodes.NS_PLAY_COMPLETE, duration, bytesSent.get());
    }

    /**
     * Send seek status notification
     * 
     * @param item
     *            Playlist item
     * @param position
     *            Seek position
     */
    private void sendSeekStatus(IPlayItem item, int position) {
        Status seek = new Status(StatusCodes.NS_SEEK_NOTIFY);
        seek.setClientid(streamId);
        seek.setDetails(item.getName());
        seek.setDesciption(String.format("Seeking %d (stream ID: %d).", position, streamId));

        doPushMessage(seek);
    }

    /**
     * Send pause status notification
     * 
     * @param item
     *            Playlist item
     */
    private void sendPauseStatus(IPlayItem item) {
        Status pause = new Status(StatusCodes.NS_PAUSE_NOTIFY);
        pause.setClientid(streamId);
        pause.setDetails(item.getName());

        doPushMessage(pause);
    }

    /**
     * Send resume status notification
     * 
     * @param item
     *            Playlist item
     */
    private void sendResumeStatus(IPlayItem item) {
        Status resume = new Status(StatusCodes.NS_UNPAUSE_NOTIFY);
        resume.setClientid(streamId);
        resume.setDetails(item.getName());

        doPushMessage(resume);
    }

    /**
     * Send published status notification
     * 
     * @param item
     *            Playlist item
     */
    private void sendPublishedStatus(IPlayItem item) {
        Status published = new Status(StatusCodes.NS_PLAY_PUBLISHNOTIFY);
        published.setClientid(streamId);
        published.setDetails(item.getName());

        doPushMessage(published);
    }

    /**
     * Send unpublished status notification
     * 
     * @param item
     *            Playlist item
     */
    private void sendUnpublishedStatus(IPlayItem item) {
        Status unpublished = new Status(StatusCodes.NS_PLAY_UNPUBLISHNOTIFY);
        unpublished.setClientid(streamId);
        unpublished.setDetails(item.getName());

        doPushMessage(unpublished);
    }

    /**
     * Stream not found status notification
     * 
     * @param item
     *            Playlist item
     */
    private void sendStreamNotFoundStatus(IPlayItem item) {
        Status notFound = new Status(StatusCodes.NS_PLAY_STREAMNOTFOUND);
        notFound.setClientid(streamId);
        notFound.setLevel(Status.ERROR);
        notFound.setDetails(item.getName());

        doPushMessage(notFound);
    }

    /**
     * Insufficient bandwidth notification
     * 
     * @param item
     *            Playlist item
     */
    private void sendInsufficientBandwidthStatus(IPlayItem item) {
        Status insufficientBW = new Status(StatusCodes.NS_PLAY_INSUFFICIENT_BW);
        insufficientBW.setClientid(streamId);
        insufficientBW.setLevel(Status.WARNING);
        insufficientBW.setDetails(item.getName());
        insufficientBW.setDesciption("Data is playing behind the normal speed.");

        doPushMessage(insufficientBW);
    }

    /**
     * Send VOD init control message
     * 
     * @param item
     *            Playlist item
     */
    private void sendVODInitCM(IPlayItem item) {
        OOBControlMessage oobCtrlMsg = new OOBControlMessage();
        oobCtrlMsg.setTarget(IPassive.KEY);
        oobCtrlMsg.setServiceName("init");
        Map<String, Object> paramMap = new HashMap<String, Object>(1);
        paramMap.put("startTS", (int) item.getStart());
        oobCtrlMsg.setServiceParamMap(paramMap);
        msgInReference.get().sendOOBControlMessage(this, oobCtrlMsg);
    }

    /**
     * Send VOD seek control message
     * 
     * @param msgIn
     *            Message input
     * @param position
     *            Playlist item
     * @return Out-of-band control message call result or -1 on failure
     */
    private int sendVODSeekCM(int position) {
        OOBControlMessage oobCtrlMsg = new OOBControlMessage();
        oobCtrlMsg.setTarget(ISeekableProvider.KEY);
        oobCtrlMsg.setServiceName("seek");
        Map<String, Object> paramMap = new HashMap<String, Object>(1);
        paramMap.put("position", position);
        oobCtrlMsg.setServiceParamMap(paramMap);
        msgInReference.get().sendOOBControlMessage(this, oobCtrlMsg);
        if (oobCtrlMsg.getResult() instanceof Integer) {
            return (Integer) oobCtrlMsg.getResult();
        } else {
            return -1;
        }
    }

    /**
     * Send VOD check video control message
     * 
     * @return result of oob control message
     */
    private boolean sendCheckVideoCM() {
        OOBControlMessage oobCtrlMsg = new OOBControlMessage();
        oobCtrlMsg.setTarget(IStreamTypeAwareProvider.KEY);
        oobCtrlMsg.setServiceName("hasVideo");
        msgInReference.get().sendOOBControlMessage(this, oobCtrlMsg);
        if (oobCtrlMsg.getResult() instanceof Boolean) {
            return (Boolean) oobCtrlMsg.getResult();
        } else {
            return false;
        }
    }

    /** {@inheritDoc} */
    public void onOOBControlMessage(IMessageComponent source, IPipe pipe, OOBControlMessage oobCtrlMsg) {
        if ("ConnectionConsumer".equals(oobCtrlMsg.getTarget())) {
            if (source instanceof IProvider) {
                IMessageOutput out = msgOutReference.get();
                if (out != null) {
                    out.sendOOBControlMessage((IProvider) source, oobCtrlMsg);
                } else {
                    // this may occur when a client attempts to play and then disconnects
                    log.warn("Output is not available, message cannot be sent");
                    close();
                }
            }
        }
    }

    /** {@inheritDoc} */
    public void onPipeConnectionEvent(PipeConnectionEvent event) {
        switch (event.getType()) {
            case PROVIDER_CONNECT_PUSH:
                if (event.getProvider() != this) {
                    if (waitLiveJob != null) {
                        schedulingService.removeScheduledJob(waitLiveJob);
                        waitLiveJob = null;
                    }
                    sendPublishedStatus(currentItem.get());
                }
                break;
            case PROVIDER_DISCONNECT:
                if (pullMode) {
                    sendStopStatus(currentItem.get());
                } else {
                    sendUnpublishedStatus(currentItem.get());
                }
                break;
            case CONSUMER_CONNECT_PULL:
                if (event.getConsumer() == this) {
                    pullMode = true;
                }
                break;
            case CONSUMER_CONNECT_PUSH:
                if (event.getConsumer() == this) {
                    pullMode = false;
                }
                break;
            default:
                if (log.isDebugEnabled()) {
                    log.debug("Unhandled pipe event: {}", event);
                }
        }
    }

    private boolean shouldLogPacketDrop() {
        long now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        if (now - droppedPacketsCountLastLogTimestamp > droppedPacketsCountLogInterval) {
            droppedPacketsCountLastLogTimestamp = now;
            return true;
        }

        return false;
    }

    /** {@inheritDoc} */
    public void pushMessage(IPipe pipe, IMessage message) throws IOException {
        if (!pullMode) {
            if (!configsDone) {
                log.debug("dump early");
                return;
            }
        }
        String sessionId = subscriberStream.getConnection().getSessionId();
        if (message instanceof RTMPMessage) {
            IMessageInput msgIn = msgInReference.get();
            RTMPMessage rtmpMessage = (RTMPMessage) message;
            IRTMPEvent body = rtmpMessage.getBody();
            if (body instanceof IStreamData) {
                // the subscriber paused 
                if (subscriberStream.getState() == StreamState.PAUSED) {
                    if (log.isInfoEnabled() && shouldLogPacketDrop()) {
                        log.info("Dropping packet because we are paused. sessionId={} stream={} count={}", sessionId, subscriberStream.getBroadcastStreamPublishName(), droppedPacketsCount);
                    }
                    videoFrameDropper.dropPacket(rtmpMessage);
                    return;
                }
                if (body instanceof VideoData && body.getSourceType() == Constants.SOURCE_TYPE_LIVE) {
                    // We only want to drop packets from a live stream. VOD streams we let it buffer.
                    // We don't want a user watching a movie to see a choppy video due to low bandwidth.
                    if (msgIn instanceof IBroadcastScope) {
                        IBroadcastStream stream = (IBroadcastStream) ((IBroadcastScope) msgIn).getClientBroadcastStream();
                        if (stream != null && stream.getCodecInfo() != null) {
                            IVideoStreamCodec videoCodec = stream.getCodecInfo().getVideoCodec();
                            // dont try to drop frames if video codec is null
                            if (videoCodec != null && videoCodec.canDropFrames()) {
                                if (!receiveVideo) {
                                    videoFrameDropper.dropPacket(rtmpMessage);
                                    droppedPacketsCount++;
                                    if (log.isInfoEnabled() && shouldLogPacketDrop()) {
                                        // client disabled video or the app doesn't have enough bandwidth allowed for this stream
                                        log.info("Drop packet. Failed to acquire token or no video. sessionId={} stream={} count={}", sessionId, subscriberStream.getBroadcastStreamPublishName(), droppedPacketsCount);
                                    }
                                    return;
                                }
                                // Implement some sort of back-pressure on video streams. When the client is on a congested
                                // connection, Red5 cannot send packets fast enough. Mina puts these packets into an
                                // unbounded queue. If we generate video packets fast enough, the queue would get large
                                // which may trigger an OutOfMemory exception. To mitigate this, we check the size of
                                // pending video messages and drop video packets until the queue is below the threshold.
                                // only check for frame dropping if the codec supports it
                                long pendingVideos = pendingVideoMessages();
                                if (log.isTraceEnabled()) {
                                    log.trace("Pending messages sessionId={} pending={} threshold={} sequential={} stream={}, count={}", new Object[] { sessionId, pendingVideos, maxPendingVideoFrames, numSequentialPendingVideoFrames, subscriberStream.getBroadcastStreamPublishName(), droppedPacketsCount });
                                }
                                if (!videoFrameDropper.canSendPacket(rtmpMessage, pendingVideos)) {
                                    // drop frame as it depends on other frames that were dropped before
                                    droppedPacketsCount++;
                                    if (log.isInfoEnabled() && shouldLogPacketDrop()) {
                                        log.info("Frame dropper says to drop packet. sessionId={} stream={} count={}", sessionId, subscriberStream.getBroadcastStreamPublishName(), droppedPacketsCount);
                                    }
                                    return;
                                }
                                // increment the number of times we had pending video frames sequentially
                                if (pendingVideos > 1) {
                                    numSequentialPendingVideoFrames++;
                                } else {
                                    // reset number of sequential pending frames if 1 or 0 are pending
                                    numSequentialPendingVideoFrames = 0;
                                }
                                if (pendingVideos > maxPendingVideoFrames || numSequentialPendingVideoFrames > maxSequentialPendingVideoFrames) {
                                    droppedPacketsCount++;
                                    if (log.isInfoEnabled() && shouldLogPacketDrop()) {
                                        log.info("Drop packet. Pending above threshold. sessionId={} pending={} threshold={} sequential={} stream={} count={}", new Object[] { sessionId, pendingVideos, maxPendingVideoFrames, numSequentialPendingVideoFrames, subscriberStream.getBroadcastStreamPublishName(), droppedPacketsCount });
                                    }
                                    // drop because the client has insufficient bandwidth
                                    long now = System.currentTimeMillis();
                                    if (bufferCheckInterval > 0 && now >= nextCheckBufferUnderrun) {
                                        // notify client about frame dropping (keyframe)
                                        sendInsufficientBandwidthStatus(currentItem.get());
                                        nextCheckBufferUnderrun = now + bufferCheckInterval;
                                    }
                                    videoFrameDropper.dropPacket(rtmpMessage);
                                    return;
                                }
                                // we are ok to send, check if we should send buffered frame
                                if (bufferedInterframeIdx > -1) {
                                    IVideoStreamCodec.FrameData fd = videoCodec.getInterframe(bufferedInterframeIdx++);
                                    if (fd != null) {
                                        VideoData interframe = new VideoData(fd.getFrame());
                                        interframe.setTimestamp(body.getTimestamp());
                                        rtmpMessage = RTMPMessage.build(interframe);
                                    } else {
                                        // it means that new keyframe was received and we should send current frames instead of buffered
                                        bufferedInterframeIdx = -1;
                                    }
                                }
                            }
                        }
                    }
                } else if (body instanceof AudioData) {
                    if (!receiveAudio && sendBlankAudio) {
                        // send blank audio packet to reset player
                        sendBlankAudio = false;
                        body = new AudioData();
                        if (lastMessageTs > 0) {
                            body.setTimestamp(lastMessageTs);
                        } else {
                            body.setTimestamp(0);
                        }
                        rtmpMessage = RTMPMessage.build(body);
                    } else if (!receiveAudio) {
                        return;
                    }
                }
                sendMessage(rtmpMessage);
            } else {
                throw new RuntimeException(String.format("Expected IStreamData but got %s (type %s)", body.getClass(), body.getDataType()));
            }
        } else if (message instanceof ResetMessage) {
            sendReset();
        } else {
            msgOutReference.get().pushMessage(message);
        }
    }

    /**
     * Get number of pending video messages
     * 
     * @return Number of pending video messages
     */
    private long pendingVideoMessages() {
        IMessageOutput out = msgOutReference.get();
        if (out != null) {
            OOBControlMessage pendingRequest = new OOBControlMessage();
            pendingRequest.setTarget("ConnectionConsumer");
            pendingRequest.setServiceName("pendingVideoCount");
            out.sendOOBControlMessage(this, pendingRequest);
            if (pendingRequest.getResult() != null) {
                return (Long) pendingRequest.getResult();
            }
        }
        return 0;
    }

    /**
     * Get number of pending messages to be sent
     * 
     * @return Number of pending messages
     */
    private long pendingMessages() {
        return subscriberStream.getConnection().getPendingMessages();
    }

    public boolean isPullMode() {
        return pullMode;
    }

    public boolean isPaused() {
        return subscriberStream.isPaused();
    }

    /**
     * Returns the timestamp of the last message sent.
     * 
     * @return last message timestamp
     */
    public int getLastMessageTimestamp() {
        return lastMessageTs;
    }

    public long getPlaybackStart() {
        return playbackStart;
    }

    public void sendBlankAudio(boolean sendBlankAudio) {
        this.sendBlankAudio = sendBlankAudio;
    }

    /**
     * Returns true if the engine currently receives audio.
     * 
     * @return receive audio
     */
    public boolean receiveAudio() {
        return receiveAudio;
    }

    /**
     * Returns true if the engine currently receives audio and sets the new value.
     * 
     * @param receive
     *            new value
     * @return old value
     */
    public boolean receiveAudio(boolean receive) {
        boolean oldValue = receiveAudio;
        //set new value
        if (receiveAudio != receive) {
            receiveAudio = receive;
        }
        return oldValue;
    }

    /**
     * Returns true if the engine currently receives video.
     * 
     * @return receive video
     */
    public boolean receiveVideo() {
        return receiveVideo;
    }

    /**
     * Returns true if the engine currently receives video and sets the new value.
     * 
     * @param receive
     *            new value
     * @return old value
     */
    public boolean receiveVideo(boolean receive) {
        boolean oldValue = receiveVideo;
        //set new value
        if (receiveVideo != receive) {
            receiveVideo = receive;
        }
        return oldValue;
    }

    /**
     * Releases pending message body, nullifies pending message object
     */
    private void releasePendingMessage() {
        if (pendingMessage != null) {
            IRTMPEvent body = pendingMessage.getBody();
            if (body instanceof IStreamData && ((IStreamData<?>) body).getData() != null) {
                ((IStreamData<?>) body).getData().free();
            }
            pendingMessage = null;
        }
    }

    /**
     * Check if sending the given message was enabled by the client.
     * 
     * @param message
     *            the message to check
     * @return true if the message should be sent, false otherwise (and the message is discarded)
     */
    protected boolean checkSendMessageEnabled(RTMPMessage message) {
        IRTMPEvent body = message.getBody();
        if (!receiveAudio && body instanceof AudioData) {
            // The user doesn't want to get audio packets
            ((IStreamData<?>) body).getData().free();
            if (sendBlankAudio) {
                // Send reset audio packet
                sendBlankAudio = false;
                body = new AudioData();
                // We need a zero timestamp
                if (lastMessageTs >= 0) {
                    body.setTimestamp(lastMessageTs - timestampOffset);
                } else {
                    body.setTimestamp(-timestampOffset);
                }
                message = RTMPMessage.build(body);
            } else {
                return false;
            }
        } else if (!receiveVideo && body instanceof VideoData) {
            // The user doesn't want to get video packets
            ((IStreamData<?>) body).getData().free();
            return false;
        }
        return true;
    }

    /**
     * Schedule a stop to be run from a separate thread to allow the background thread to stop cleanly.
     */
    private void runDeferredStop() {
        // Stop current jobs from running.
        clearWaitJobs();
        // Schedule deferred stop executor.
        log.trace("Ran deferred stop");
        if (deferredStop == null) {
            // set deferred stop if we get a job name returned 
            deferredStop = subscriberStream.scheduleWithFixedDelay(new DeferredStopRunnable(), 100);
        }
    }

    private void cancelDeferredStop() {
        log.debug("Cancel deferred stop");
        if (deferredStop != null) {
            subscriberStream.cancelJob(deferredStop);
            deferredStop = null;
        }
    }

    /**
     * Runnable worker to handle seek operations.
     */
    private final class SeekRunnable implements Runnable {

        private final int position;

        SeekRunnable(int position) {
            this.position = position;
        }

        @SuppressWarnings("incomplete-switch")
        public void run() {
            log.trace("Seek: {}", position);
            boolean startPullPushThread = false;
            switch (subscriberStream.getState()) {
                case PLAYING:
                    startPullPushThread = true;
                case PAUSED:
                case STOPPED:
                    //allow seek if playing, paused, or stopped
                    if (!pullMode) {
                        // throw new OperationNotSupportedException();
                        throw new RuntimeException();
                    }
                    releasePendingMessage();
                    clearWaitJobs();
                    break;
                default:
                    throw new IllegalStateException("Cannot seek in current state");
            }
            sendClearPing();
            sendReset();
            sendSeekStatus(currentItem.get(), position);
            sendStartStatus(currentItem.get());
            int seekPos = sendVODSeekCM(position);
            // we seeked to the nearest keyframe so use real timestamp now
            if (seekPos == -1) {
                seekPos = position;
            }
            //what should our start be?
            log.trace("Current playback start: {}", playbackStart);
            playbackStart = System.currentTimeMillis() - seekPos;
            log.trace("Playback start: {} seek pos: {}", playbackStart, seekPos);
            subscriberStream.onChange(StreamState.SEEK, currentItem.get(), seekPos);
            // start off with not having sent any message
            boolean messageSent = false;
            // read our client state
            switch (subscriberStream.getState()) {
                case PAUSED:
                case STOPPED:
                    // we send a single snapshot on pause
                    if (sendCheckVideoCM()) {
                        IMessage msg = null;
                        IMessageInput in = msgInReference.get();
                        do {
                            try {
                                msg = in.pullMessage();
                            } catch (Throwable err) {
                                log.warn("Error while pulling message", err);
                                break;
                            }
                            if (msg instanceof RTMPMessage) {
                                RTMPMessage rtmpMessage = (RTMPMessage) msg;
                                IRTMPEvent body = rtmpMessage.getBody();
                                if (body instanceof VideoData && ((VideoData) body).getFrameType() == FrameType.KEYFRAME) {
                                    //body.setTimestamp(seekPos);
                                    doPushMessage(rtmpMessage);
                                    rtmpMessage.getBody().release();
                                    messageSent = true;
                                    lastMessageTs = body.getTimestamp();
                                    break;
                                }
                            }
                        } while (msg != null);
                    }
            }
            // seeked past end of stream
            long length = currentItem.get().getLength();
            if (length >= 0 && (position - streamOffset) >= length) {
                stop();
            }
            // if no message has been sent by this point send an audio packet
            if (!messageSent) {
                // Send blank audio packet to notify client about new position
                log.debug("Sending blank audio packet");
                AudioData audio = new AudioData();
                audio.setTimestamp(seekPos);
                audio.setHeader(new Header());
                audio.getHeader().setTimer(seekPos);
                RTMPMessage audioMessage = RTMPMessage.build(audio);
                lastMessageTs = seekPos;
                doPushMessage(audioMessage);
                audioMessage.getBody().release();
            }
            if (!messageSent && subscriberStream.getState() == StreamState.PLAYING) {
                boolean isRTMPTPlayback = subscriberStream.getConnection().getProtocol().equals("rtmpt");
                // send all frames from last keyframe up to requested position and fill client buffer
                if (sendCheckVideoCM()) {
                    final long clientBuffer = subscriberStream.getClientBufferDuration();
                    IMessage msg = null;
                    IMessageInput in = msgInReference.get();
                    int msgSent = 0;
                    do {
                        try {
                            msg = in.pullMessage();
                            if (msg instanceof RTMPMessage) {
                                RTMPMessage rtmpMessage = (RTMPMessage) msg;
                                IRTMPEvent body = rtmpMessage.getBody();
                                if (body.getTimestamp() >= position + (clientBuffer * 2)) {
                                    // client buffer should be full by now, continue regular pull/push
                                    releasePendingMessage();
                                    if (checkSendMessageEnabled(rtmpMessage)) {
                                        pendingMessage = rtmpMessage;
                                    }
                                    break;
                                }
                                if (!checkSendMessageEnabled(rtmpMessage)) {
                                    continue;
                                }
                                msgSent++;
                                sendMessage(rtmpMessage);
                            }
                        } catch (Throwable err) {
                            log.warn("Error while pulling message", err);
                            break;
                        }
                    } while (!isRTMPTPlayback && (msg != null));
                    log.trace("msgSent: {}", msgSent);
                    playbackStart = System.currentTimeMillis() - lastMessageTs;
                }
            }
            // start pull-push
            if (startPullPushThread) {
                ensurePullAndPushRunning();
            }
        }
    }

    /**
     * Periodically triggered by executor to send messages to the client.
     */
    private final class PullAndPushRunnable implements IScheduledJob {

        /**
         * Trigger sending of messages.
         */
        public void execute(ISchedulingService svc) {
            // ensure the job is not already running
            if (pushPullRunning.compareAndSet(false, true)) {
                try {
                    // handle any pending operations
                    Runnable worker = null;
                    while (!pendingOperations.isEmpty()) {
                        log.debug("Pending operations: {}", pendingOperations.size());
                        // remove the first operation and execute it 
                        worker = pendingOperations.remove();
                        log.debug("Worker: {}", worker);
                        // if the operation is seek, ensure it is the last request in the set
                        while (worker instanceof SeekRunnable) {
                            Runnable tmp = pendingOperations.peek();
                            if (tmp != null && tmp instanceof SeekRunnable) {
                                worker = pendingOperations.remove();
                            } else {
                                break;
                            }
                        }
                        if (worker != null) {
                            log.debug("Executing pending operation");
                            worker.run();
                        }
                    }
                    // receive then send if message is data (not audio or video)
                    if (subscriberStream.getState() == StreamState.PLAYING && pullMode) {
                        if (pendingMessage != null) {
                            IRTMPEvent body = pendingMessage.getBody();
                            if (okayToSendMessage(body)) {
                                sendMessage(pendingMessage);
                                releasePendingMessage();
                            } else {
                                return;
                            }
                        } else {
                            IMessage msg = null;
                            IMessageInput in = msgInReference.get();
                            do {
                                msg = in.pullMessage();
                                if (msg != null) {
                                    if (msg instanceof RTMPMessage) {
                                        RTMPMessage rtmpMessage = (RTMPMessage) msg;
                                        if (checkSendMessageEnabled(rtmpMessage)) {
                                            // Adjust timestamp when playing lists
                                            IRTMPEvent body = rtmpMessage.getBody();
                                            body.setTimestamp(body.getTimestamp() + timestampOffset);
                                            if (okayToSendMessage(body)) {
                                                log.trace("ts: {}", rtmpMessage.getBody().getTimestamp());
                                                sendMessage(rtmpMessage);
                                                IoBuffer data = ((IStreamData<?>) body).getData();
                                                if (data != null) {
                                                    data.free();
                                                }
                                            } else {
                                                pendingMessage = rtmpMessage;
                                            }
                                            ensurePullAndPushRunning();
                                            break;
                                        }
                                    }
                                } else {
                                    // No more packets to send
                                    log.debug("Ran out of packets");
                                    runDeferredStop();
                                }
                            } while (msg != null);
                        }
                    }
                } catch (IOException err) {
                    // we couldn't get more data, stop stream.
                    log.warn("Error while getting message", err);
                    runDeferredStop();
                } finally {
                    // reset running flag
                    pushPullRunning.compareAndSet(true, false);
                }
            } else {
                log.debug("Push / pull already running");
            }
        }
    }

    private class DeferredStopRunnable implements IScheduledJob {

        public void execute(ISchedulingService service) {
            if (isClientBufferEmpty()) {
                log.trace("Buffer is empty, stop will proceed");
                stop();
            }
        }

    }

    /**
     * @param maxPendingVideoFrames
     *            the maxPendingVideoFrames to set
     */
    public void setMaxPendingVideoFrames(int maxPendingVideoFrames) {
        this.maxPendingVideoFrames = maxPendingVideoFrames;
    }

    /**
     * @param maxSequentialPendingVideoFrames
     *            the maxSequentialPendingVideoFrames to set
     */
    public void setMaxSequentialPendingVideoFrames(int maxSequentialPendingVideoFrames) {
        this.maxSequentialPendingVideoFrames = maxSequentialPendingVideoFrames;
    }

}
