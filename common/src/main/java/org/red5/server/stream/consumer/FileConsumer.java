package org.red5.server.stream.consumer;

import java.io.File;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.red5.codec.VideoCodec;
import org.red5.codec.VideoFrameType;
import org.red5.io.ITag;
import org.red5.io.ITagWriter;
import org.red5.io.flv.impl.FLVWriter;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IClientStream;
import org.red5.server.api.stream.IStreamFilenameGenerator;
import org.red5.server.api.stream.IStreamFilenameGenerator.GenerationType;
import org.red5.server.api.stream.consumer.IFileConsumer;
import org.red5.server.messaging.IMessage;
import org.red5.server.messaging.IMessageComponent;
import org.red5.server.messaging.IPipe;
import org.red5.server.messaging.IPipeConnectionListener;
import org.red5.server.messaging.IPushableConsumer;
import org.red5.server.messaging.OOBControlMessage;
import org.red5.server.messaging.PipeConnectionEvent;
import org.red5.server.net.rtmp.event.IRTMPEvent;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.stream.DefaultStreamFilenameGenerator;
import org.red5.server.stream.IStreamData;
import org.red5.server.stream.message.RTMPMessage;
import org.red5.server.stream.message.ResetMessage;
import org.red5.server.stream.recording.RecordingSession;
import org.red5.server.util.ScopeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

/**
 * Consumer that pushes messages to a writer using priority / comparison.
 *
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class FileConsumer implements Constants, IPushableConsumer, IPipeConnectionListener, DisposableBean, IFileConsumer {

    private static final Logger log = LoggerFactory.getLogger(FileConsumer.class);

    private AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Executor for all instance writer jobs
     */
    private ExecutorService executor = Executors.newFixedThreadPool(1);

    private static QueuedMediaDataComparator comparator = new QueuedMediaDataComparator();

    /**
     * Queue to hold data awaiting writing
     */
    private BlockingQueue<QueuedMediaData> queue;

    /**
     * Scope
     */
    private IScope scope;

    /**
     * Path
     */
    private Path path;

    /**
     * Tag writer
     */
    private ITagWriter writer;

    /**
     * Operation mode
     */
    private String mode = "none";

    /**
     * Start timestamp
     */
    private int startTimestamp = -1;

    /**
     * Video decoder configuration
     */
    private ITag videoConfigurationTag;

    /**
     * Audio decoder configuration
     */
    @SuppressWarnings("unused")
    private ITag audioConfigurationTag;

    /**
     * Keeps track of the write worker; only one is started per consumer.
     */
    private volatile Future<?> writerFuture;

    /**
     * Set by uninit; the write worker drains what is queued and exits, and later messages are ignored.
     */
    private volatile boolean stopping;

    // how long the write worker waits for queued data before checking whether it should stop
    private static final long WRITER_POLL_MS = 100L;

    // how long uninit waits for the write worker to drain the queue and exit
    private static final long WRITER_STOP_TIMEOUT_MS = 5000L;

    private volatile boolean gotKeyFrame = false;

    /**
     * Threshold / size for the queue.
     */
    private int queueThreshold = 240;

    /**
     * Whether or not to wait until a video keyframe arrives before writing video.
     */
    private boolean waitForVideoKeyframe = true;

    /**
     * Whether or not to use a comparator with a priority queue.
     */
    private boolean usePriority = true;

    /**
     * Queue offer timeout in milliseconds.
     */
    private long offerTimeout = 100L;

    /**
     * Size, duration and free space limits of the recording.
     */
    private final RecordingLimits limits = new RecordingLimits();

    /**
     * Reason the recording stopped writing, or null while it is healthy.
     */
    private volatile String failure;

    /**
     * Default ctor
     */
    public FileConsumer() {
    }

    /**
     * Creates file consumer
     *
     * @param scope
     *            Scope of consumer
     * @param file
     *            File
     */
    public FileConsumer(IScope scope, File file) {
        this();
        this.scope = scope;
        this.path = file.toPath();
    }

    /**
     * Creates file consumer
     *
     * @param scope
     *            Scope of consumer
     * @param fileName
     *            The file name without the extension
     * @param mode
     *            The recording mode
     */
    public FileConsumer(IScope scope, String fileName, String mode) {
        this();
        this.scope = scope;
        this.mode = mode;
        setupOutputPath(fileName);
    }

    /**
     * {@inheritDoc}
     *
     * Push message through pipe
     */
    @SuppressWarnings("rawtypes")
    public void pushMessage(IPipe pipe, IMessage message) throws IOException {
        if (failure != null || stopping) {
            // the recording failed, reached a limit or was stopped, nothing more is written
            return;
        }
        if (message instanceof RTMPMessage) {
            final IRTMPEvent msg = ((RTMPMessage) message).getBody();
            // if writes are delayed, queue the data and sort it by time
            if (queue == null) {
                if (usePriority) {
                    if (log.isTraceEnabled()) {
                        log.trace("Creating priority typed packet queue. queueThreshold={}", queueThreshold);
                    }
                    // if we want ordering / comparing built-in
                    queue = new PriorityBlockingQueue<>(queueThreshold <= 0 ? 240 : queueThreshold, comparator);
                } else {
                    if (log.isTraceEnabled()) {
                        log.trace("Creating non-priority typed packet queue");
                    }
                    // process as received
                    queue = new LinkedBlockingQueue<>();
                }
            }
            if (msg instanceof IStreamData) {
                // get the type
                byte dataType = msg.getDataType();
                // get the timestamp
                int timestamp = msg.getTimestamp();
                if (log.isTraceEnabled()) {
                    log.trace("Stream data, body saved, timestamp: {} data type: {} class type: {}", timestamp, dataType, msg.getClass().getName());
                }
                // if the last message was a reset or we just started, use the header timer
                if (startTimestamp == -1) {
                    startTimestamp = timestamp;
                    timestamp = 0;
                } else {
                    timestamp -= startTimestamp;
                }
                // offer to the queue
                try {
                    QueuedMediaData queued = new QueuedMediaData(timestamp, dataType, (IStreamData) msg);
                    if (log.isTraceEnabled()) {
                        log.trace("Inserting packet into queue. timestamp: {} queue size: {}, codecId={}, isConfig={}", timestamp, queue.size(), queued.codecId, queued.config);
                    }
                    if (queue.size() > queueThreshold) {
                        if (queue.size() % 20 == 0) {
                            log.warn("Queue size is greater than threshold. queue size={} threshold={}", queue.size(), queueThreshold);
                        }
                    }
                    if (queue.size() < 2 * queueThreshold) {
                        // Cap queue size to prevent a runaway stream causing OOM.
                        queue.offer(queued, offerTimeout, TimeUnit.MILLISECONDS);
                    }
                } catch (InterruptedException e) {
                    log.warn("Stream data was not accepted by the queue - timestamp: {} data type: {}", timestamp, dataType, e);
                }
            }
            // start the one write worker, which initializes the writer
            if (writerFuture == null) {
                final BlockingQueue<QueuedMediaData> pending = queue;
                writerFuture = executor.submit(new Runnable() {
                    public void run() {
                        Thread.currentThread().setName("ProFileConsumer-" + path.getFileName());
                        try {
                            if (log.isTraceEnabled()) {
                                log.trace("Running FileConsumer thread. queue size: {} initialized: {} writerNotNull={}", pending.size(), initialized, (writer != null));
                            }
                            init();
                            while (writer != null && failure == null) {
                                if (log.isTraceEnabled()) {
                                    log.trace("Processing packet from queue. queue size: {}", pending.size());
                                }

                                try {
                                    // poll rather than take, so the worker exits once uninit is called and the queue is drained
                                    QueuedMediaData queued = pending.poll(WRITER_POLL_MS, TimeUnit.MILLISECONDS);
                                    if (queued == null) {
                                        if (stopping) {
                                            break;
                                        }
                                    } else {
                                        // get data type
                                        byte dataType = queued.getDataType();
                                        // get timestamp
                                        int timestamp = queued.getTimestamp();
                                        ITag tag = queued.getData();
                                        // ensure that our first video frame written is a key frame
                                        if (queued.isVideo()) {
                                            if (log.isTraceEnabled()) {
                                                log.trace("pushMessage video - waitForKeyframe: {} gotKeyframe: {} timestamp: {}", waitForVideoKeyframe, gotKeyFrame, queued.getTimestamp());
                                            }
                                            if (queued.codecId == VideoCodec.AVC.getId()) {
                                                if (queued.isConfig()) {
                                                    videoConfigurationTag = tag;
                                                    gotKeyFrame = true;
                                                }
                                                if (videoConfigurationTag == null && waitForVideoKeyframe) {
                                                    continue;
                                                }
                                            } else {
                                                if (queued.frameType == VideoFrameType.KEYFRAME) {
                                                    gotKeyFrame = true;
                                                }
                                                if (waitForVideoKeyframe && !gotKeyFrame) {
                                                    continue;
                                                }
                                            }
                                        } else if (queued.isAudio()) {
                                            if (queued.isConfig()) {
                                                audioConfigurationTag = tag;
                                            }
                                        }

                                        if (queued.isVideo()) {
                                            if (log.isTraceEnabled()) {
                                                log.trace("Writing packet. frameType={} timestamp={}", queued.frameType, queued.getTimestamp());
                                            }
                                        }

                                        // write
                                        write(dataType, timestamp, tag);
                                        // clean up
                                        queued.dispose();
                                        checkLimits(timestamp);
                                    }
                                } catch (InterruptedException e) {
                                    log.warn("Write worker interrupted for {}", path);
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                                //finally {
                                //    if (log.isTraceEnabled()) {
                                //        log.trace("Clearing queue. queue size: {}", queue.size());
                                //    }
                                //    queue.clear();
                                //}
                            }
                        } catch (IOException e) {
                            log.warn("{}", e.getMessage(), e);
                        }
                    }
                });
            }
        } else if (message instanceof ResetMessage) {
            startTimestamp = -1;
        } else if (log.isDebugEnabled()) {
            log.debug("Ignoring pushed message: {}", message);
        }
    }

    /**
     * {@inheritDoc}
     *
     * Out-of-band control message handler
     */
    public void onOOBControlMessage(IMessageComponent source, IPipe pipe, OOBControlMessage oobCtrlMsg) {
    }

    /**
     * {@inheritDoc}
     *
     * Pipe connection event handler
     */
    @SuppressWarnings("incomplete-switch")
    public void onPipeConnectionEvent(PipeConnectionEvent event) {
        switch (event.getType()) {
            case CONSUMER_CONNECT_PUSH:
                if (event.getConsumer() == this) {
                    Map<String, Object> paramMap = event.getParamMap();
                    if (paramMap != null) {
                        mode = (String) paramMap.get("mode");
                    }
                }
                break;
        }
    }

    /**
     * Initialization
     *
     * @throws IOException
     *             I/O exception
     */
    private void init() throws IOException {
        if (initialized.compareAndSet(false, true)) {
            log.debug("Init: {}", mode);
            // if the path is null, the consumer has been uninitialized
            if (path != null) {
                if (log.isDebugEnabled()) {
                    Path parent = path.getParent();
                    log.debug("Parent abs: {} dir: {}", parent.isAbsolute(), Files.isDirectory(parent));
                }
                if (IClientStream.MODE_APPEND.equals(mode)) {
                    if (Files.notExists(path)) {
                        throw new IOException("File to be appended doesnt exist, verify the record mode");
                    }
                    log.debug("Path: {}\nRead: {} write: {} size: {}", path, Files.isReadable(path), Files.isWritable(path), Files.size(path));
                    writer = new FLVWriter(path, true);
                } else if (IClientStream.MODE_RECORD.equals(mode)) {
                    try {
                        // delete existing file
                        if (Files.deleteIfExists(path)) {
                            log.debug("File deleted");
                        }
                        // ensure parent dirs exist
                        Files.createDirectories(path.getParent());
                        // create the file
                        path = Files.createFile(path);
                    } catch (IOException ioe) {
                        log.error("File creation error: {}", ioe);
                    }
                    if (!Files.isWritable(path)) {
                        throw new IOException("File is not writable");
                    }
                    log.debug("Path: {}\nRead: {} write: {}", path, Files.isReadable(path), Files.isWritable(path));
                    writer = new FLVWriter(path, false);
                } else {
                    try {
                        // delete existing file since we're not recording nor appending
                        if (Files.deleteIfExists(path)) {
                            log.debug("File deleted");
                        }
                    } catch (IOException ioe) {
                        log.error("File creation error: {}", ioe);
                    }
                }
            } else {
                log.warn("Consumer is uninitialized");
            }
            if (writer != null) {
                // an appended file may already be at a limit, and the volume may already be short of space
                checkLimits(0);
            }
            log.debug("Init - complete");
        }
    }

    /**
     * Reset or uninitialize
     */
    public void uninit() {
        // let the write worker drain what is queued and exit; the consumer is created per recording and not reused
        stopping = true;
        Future<?> future = writerFuture;
        if (future != null) {
            try {
                future.get(WRITER_STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                log.warn("Write worker for {} did not finish within {} ms, interrupting it", path, WRITER_STOP_TIMEOUT_MS);
                future.cancel(true);
            } catch (Exception e) {
                log.warn("Exception waiting for write result on uninit", e);
            }
            writerFuture = null;
        }
        // as a prototype bean destroy is never called, so the worker thread is released here
        executor.shutdown();
        if (initialized.get()) {
            log.debug("Uninit");
            if (writer != null) {
                // clear the queue
                queue.clear();
                queue = null;
                // close the writer
                try {
                    writer.close();
                } catch (Exception e) {
                    log.warn("Exception closing writer on uninit", e);
                }
                writer = null;
            }
            // clear path ref
            path = null;
        }
    }

    /**
     * Adjust timestamp and write to the file.
     *
     * @param queued
     *            queued data for write
     */
    private final void write(byte dataType, int timestamp, ITag tag) {
        if (tag != null) {
            // only allow blank tags if they are of audio type
            if (tag.getBodySize() > 0 || dataType == ITag.TYPE_AUDIO) {
                try {
                    if (timestamp >= 0) {
                        if (!writer.writeTag(tag)) {
                            log.warn("Tag was not written");
                        }
                    } else {
                        log.warn("Skipping message with negative timestamp");
                    }
                } catch (ClosedChannelException cce) {
                    // the channel we tried to write to is closed, we should not try again on that writer
                    log.error("The writer is no longer able to write to the file: {} writable: {}", path.getFileName(), path.toFile().canWrite());
                    fail("Recording file is closed");
                } catch (IOException e) {
                    log.warn("Error writing tag", e);
                    // a failed write, such as a full disk, would fail again for every following tag
                    fail("Recording write failed: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Stops writing when the recording file has reached its byte or duration limit, counting any content it is appended to, or the
     * volume is below its free space minimum.
     *
     * @param timestamp timestamp of the tag just written, relative to the start of this recording session
     */
    private void checkLimits(int timestamp) {
        if (writer != null) {
            String reason = limits.check(writer, path, timestamp);
            if (reason != null) {
                fail(reason);
            }
        }
    }

    private void fail(String reason) {
        if (failure == null) {
            failure = reason;
            log.warn("Recording to {} stopped: {}", path, reason);
            if (queue != null) {
                queue.clear();
            }
        }
    }

    /**
     * Returns why this consumer stopped writing, if it did.
     *
     * @return failure reason, or null while recording normally
     */
    public String getFailure() {
        return failure;
    }

    /**
     * Sets the maximum bytes of media data in the recording file, including what it held before when appending; 0 means unlimited.
     *
     * @param maxRecordingBytes maximum bytes
     */
    public void setMaxRecordingBytes(long maxRecordingBytes) {
        limits.setMaxBytes(maxRecordingBytes);
    }

    /**
     * Returns the maximum bytes of media data in the recording file; 0 means unlimited.
     *
     * @return maximum bytes
     */
    public long getMaxRecordingBytes() {
        return limits.getMaxBytes();
    }

    /**
     * Sets the maximum duration of the recording file in milliseconds, measured by stream timestamps and including what it held
     * before when appending; 0 means unlimited.
     *
     * @param maxRecordingDurationMs maximum duration
     */
    public void setMaxRecordingDurationMs(long maxRecordingDurationMs) {
        limits.setMaxDurationMs(maxRecordingDurationMs);
    }

    /**
     * Returns the maximum duration of the recording file in milliseconds; 0 means unlimited.
     *
     * @return maximum duration
     */
    public long getMaxRecordingDurationMs() {
        return limits.getMaxDurationMs();
    }

    /**
     * Sets the minimum usable space to keep free on the recording volume; 0 disables the check.
     *
     * @param minFreeDiskBytes minimum free bytes
     */
    public void setMinFreeDiskBytes(long minFreeDiskBytes) {
        limits.setMinFreeDiskBytes(minFreeDiskBytes);
    }

    /**
     * Returns the minimum usable space to keep free on the recording volume; 0 disables the check.
     *
     * @return minimum free bytes
     */
    public long getMinFreeDiskBytes() {
        return limits.getMinFreeDiskBytes();
    }

    /**
     * Sets the accounting of this recording against the application's recording budget and the principal's quota; the recording
     * stops, like at its own limits, once either is reached.
     *
     * @param session recording session, or null for none
     */
    public void setRecordingSession(RecordingSession session) {
        limits.setSession(session);
    }

    /**
     * Returns the accounting of this recording, if any.
     *
     * @return recording session, or null
     */
    public RecordingSession getRecordingSession() {
        return limits.getSession();
    }

    /**
     * Sets up the output file path for writing.
     *
     * @param name
     *            output filename to use
     */
    public void setupOutputPath(String name) {
        // get stream filename generator
        IStreamFilenameGenerator generator = (IStreamFilenameGenerator) ScopeUtils.getScopeService(scope, IStreamFilenameGenerator.class, DefaultStreamFilenameGenerator.class);
        // generate file path
        String filePath = generator.generateFilename(scope, name, ".flv", GenerationType.RECORD);
        this.path = generator.resolvesToAbsolutePath() ? Paths.get(filePath) : Paths.get(System.getProperty("red5.root"), "webapps", scope.getContextPath(), filePath);
        // if append was requested, ensure the file we want to append exists (append==record)
        File appendee = getFile();
        if (IClientStream.MODE_APPEND.equals(mode) && !appendee.exists()) {
            try {
                if (appendee.createNewFile()) {
                    log.debug("New file created for appending");
                } else {
                    log.debug("Failure to create new file for appending");
                }
            } catch (IOException e) {
                log.warn("Exception creating replacement file for append", e);
            }
        }
    }

    /**
     * Sets the scope for this consumer.
     *
     * @param scope
     *            scope
     */
    public void setScope(IScope scope) {
        this.scope = scope;
    }

    /**
     * Sets the file we're writing to.
     *
     * @param file
     *            file
     */
    public void setFile(File file) {
        path = file.toPath();
    }

    /**
     * Returns the file.
     *
     * @return file
     */
    public File getFile() {
        return path.toFile();
    }

    /**
     * Sets the threshold for the queue. When the threshold is met a worker is spawned to empty the sorted queue to the writer.
     *
     * @param queueThreshold
     *            number of items to queue before spawning worker
     */
    public void setQueueThreshold(int queueThreshold) {
        this.queueThreshold = queueThreshold;
    }

    /**
     * Sets whether or not to use the queue.
     *
     * @param delayWrite
     *            true to use the queue, false if not
     */
    @Deprecated
    public void setDelayWrite(boolean delayWrite) {
    }

    /**
     * Whether or not to wait for the first keyframe before processing video frames.
     *
     * @param waitForVideoKeyframe wait for key frame or not
     */
    public void setWaitForVideoKeyframe(boolean waitForVideoKeyframe) {
        log.debug("setWaitForVideoKeyframe: {}", waitForVideoKeyframe);
        this.waitForVideoKeyframe = waitForVideoKeyframe;
    }

    /**
     * Whether or not to use a PriorityBlockingQueue or LinkedBlockingQueue for data queue.
     *
     * @param usePriority priority queue or blocking queue
     */
    public void setUsePriority(boolean usePriority) {
        this.usePriority = usePriority;
    }

    /**
     * Amount of time in milliseconds to wait for an offer to be accepted.
     *
     * @param offerTimeout how long to wait for offer acceptance
     */
    public void setOfferTimeout(long offerTimeout) {
        this.offerTimeout = offerTimeout;
    }

    /**
     * Sets the recording mode.
     *
     * @param mode
     *            either "record" or "append" depending on the type of action to perform
     */
    public void setMode(String mode) {
        this.mode = mode;
    }

    /** {@inheritDoc} */
    public void setAudioDecoderConfiguration(IRTMPEvent audioConfig) {
        // no-op
    }

    /** {@inheritDoc} */
    public void setVideoDecoderConfiguration(IRTMPEvent videoConfig) {
        // no-op
    }

    /** {@inheritDoc} */
    @Override
    public void destroy() throws Exception {
        if (executor != null) {
            executor.shutdown();
        }
    }

}
