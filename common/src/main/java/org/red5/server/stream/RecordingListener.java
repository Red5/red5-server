/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.stream;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.io.IKeyFrameMetaCache;
import org.red5.server.api.IConnection;
import org.red5.server.api.IContext;
import org.red5.server.api.scheduling.IScheduledJob;
import org.red5.server.api.scheduling.ISchedulingService;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.api.stream.IStreamFilenameGenerator;
import org.red5.server.api.stream.IStreamFilenameGenerator.GenerationType;
import org.red5.server.api.stream.IStreamPacket;
import org.red5.server.net.rtmp.event.Aggregate;
import org.red5.server.net.rtmp.event.AudioData;
import org.red5.server.net.rtmp.event.CachedEvent;
import org.red5.server.net.rtmp.event.IRTMPEvent;
import org.red5.server.net.rtmp.event.Notify;
import org.red5.server.net.rtmp.event.VideoData;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.stream.consumer.FileConsumer;
import org.red5.server.stream.message.RTMPMessage;
import org.red5.server.stream.recording.RecordingBudget;
import org.red5.server.stream.recording.RecordingRefusedException;
import org.red5.server.stream.recording.RecordingSession;
import org.red5.server.util.ScopeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;

/**
 * Stream listener for recording stream events to a file.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class RecordingListener implements IRecordingListener {

    private static final Logger log = LoggerFactory.getLogger(RecordingListener.class);

    /**
     * Scheduler
     */
    private ISchedulingService scheduler;

    /**
     * Event queue worker job name
     */
    private String eventQueueJobName;

    /**
     * Whether we are recording or not
     */
    private AtomicBoolean recording = new AtomicBoolean(false);

    /**
     * Whether we are appending or not
     */
    private boolean appending;

    /**
     * FileConsumer used to output recording to disk
     */
    private FileConsumer recordingConsumer;

    /**
     * The filename we are recording to.
     */
    private String fileName;

    /**
     * Queue to hold incoming stream event packets.
     */
    private final BlockingQueue<CachedEvent> queue = new LinkedBlockingQueue<>(8192);

    /**
    * The instance holding the queued processQueue() calls
    **/
    private EventQueueJob eqj;

    /**
     * Budget accounting the recording, or null.
     */
    private RecordingBudget budget;

    /**
     * Accounting of the recording, or null.
     */
    private RecordingSession session;

    // name used to find the record directory of an application through its filename generator
    private static final String RECORD_DIRECTORY_PROBE = "recording";

    /**
     * Get the file we'd be recording to based on scope and given name.
     *
     * @param scope
     *            scope
     * @param name
     *            name
     * @return file
     */
    @SuppressWarnings("null")
    public static File getRecordFile(IScope scope, String name) {
        // get stream filename generator
        IStreamFilenameGenerator generator = (IStreamFilenameGenerator) ScopeUtils.getScopeService(scope, IStreamFilenameGenerator.class, DefaultStreamFilenameGenerator.class);
        // generate filename
        String fileName;
        try {
            fileName = generator.generateFilename(scope, name, ".flv", GenerationType.RECORD);
        } catch (IllegalArgumentException e) {
            log.warn("Refusing to record stream with invalid name: {}", e.getMessage());
            return null;
        }
        File file = null;
        if (generator.resolvesToAbsolutePath()) {
            file = new File(fileName);
        } else {
            Resource resource = scope.getContext().getResource(fileName);
            if (resource.exists()) {
                try {
                    file = resource.getFile();
                    log.debug("File exists: {} writable: {}", file.exists(), file.canWrite());
                } catch (IOException ioe) {
                    log.error("File error: {}", ioe);
                }
            } else {
                String appScopeName = ScopeUtils.findApplication(scope).getName();
                file = new File(String.format("%s/webapps/%s/%s", System.getProperty("red5.root"), appScopeName, fileName));
            }
        }
        return file;
    }

    /**
     * Get the record directory of the application of a scope: the directory its recordings are written to, which holds the directories
     * of its sub-scopes.
     *
     * @param scope
     *            scope
     * @return directory, or null if the filename generator does not provide one
     */
    public static File getRecordDirectory(IScope scope) {
        IScope app = ScopeUtils.findApplication(scope);
        File probe = getRecordFile(app != null ? app : scope, RECORD_DIRECTORY_PROBE);
        return probe != null ? probe.getAbsoluteFile().getParentFile() : null;
    }

    /**
     * Creates the recording listener for a stream in a scope: the application's prototype bean named
     * {@value IRecordingListener#BEAN_NAME} if it declares one, or a new {@link RecordingListener}.
     *
     * @param scope
     *            scope of the stream
     * @return a new recording listener
     */
    public static IRecordingListener createRecordingListener(IScope scope) {
        IContext context = scope != null ? scope.getContext() : null;
        ApplicationContext appCtx = context != null ? context.getApplicationContext() : null;
        if (appCtx != null && appCtx.containsBean(IRecordingListener.BEAN_NAME)) {
            if (!appCtx.isPrototype(IRecordingListener.BEAN_NAME)) {
                log.warn("The {} bean is not a prototype, so it would be shared by every recording; using the default listener", IRecordingListener.BEAN_NAME);
            } else {
                Object bean = appCtx.getBean(IRecordingListener.BEAN_NAME);
                if (bean instanceof IRecordingListener) {
                    log.debug("Using the application's recording listener: {}", bean.getClass().getName());
                    return (IRecordingListener) bean;
                }
                log.warn("The {} bean is not an IRecordingListener: {}; using the default listener", IRecordingListener.BEAN_NAME, bean.getClass().getName());
            }
        }
        return new RecordingListener();
    }

    /** {@inheritDoc} */
    public boolean init(IConnection conn, String name, boolean isAppend) {
        // get connections scope
        return init(conn.getScope(), conn, name, isAppend);
    }

    /** {@inheritDoc} */
    public boolean init(IScope scope, String name, boolean isAppend) {
        return init(scope, null, name, isAppend);
    }

    /**
     * Initialize the listener, accounting the recording with the application's recording budget, if it has one.
     *
     * @param scope
     *            Stream source scope
     * @param conn
     *            Stream source connection, or null
     * @param name
     *            Stream name
     * @param isAppend
     *            Append mode
     * @return true if initialization completes and false otherwise
     * @throws RecordingRefusedException
     *             if the application's recording budget or the principal's quota does not allow the recording
     */
    protected boolean init(IScope scope, IConnection conn, String name, boolean isAppend) {
        // get the file for our filename
        File file = getRecordFile(scope, name);
        if (file == null) {
            log.warn("Record file is null");
            return false;
        }
        // account the recording before any existing file is replaced, so a refused recording leaves it in place
        startSession(scope, conn, name, file, isAppend && file.exists());
        boolean initialized = false;
        try {
            initialized = init(scope, file, isAppend);
        } finally {
            if (!initialized) {
                stopSession("Recording could not be initialized");
            }
        }
        return initialized;
    }

    /**
     * Prepares the recording file and the file consumer.
     */
    private boolean init(IScope scope, File file, boolean isAppend) {
        // If append mode is on...
        if (!isAppend) {
            if (file.exists()) {
                // when "live" or "record" is used, any previously recorded stream with the same stream URI is deleted.
                if (!file.delete()) {
                    log.warn("Existing file: {} could not be deleted", file.getName());
                    return false;
                }
            }
        } else {
            if (file.exists()) {
                appending = true;
            } else {
                // if a recorded stream at the same URI does not already exist, "append" creates the stream as though "record" was passed.
                isAppend = false;
            }
        }
        // if the file doesn't exist yet, create it
        if (!file.exists()) {
            // Make sure the destination directory exists
            String path = file.getAbsolutePath();
            int slashPos = path.lastIndexOf(File.separator);
            if (slashPos != -1) {
                path = path.substring(0, slashPos);
            }
            File tmp = new File(path);
            if (!tmp.isDirectory()) {
                tmp.mkdirs();
            }
            try {
                file.createNewFile();
            } catch (IOException e) {
                log.warn("New recording file could not be created for: {}", file.getName(), e);
                return false;
            }
        }
        if (log.isDebugEnabled()) {
            try {
                log.debug("Recording file: {}", file.getCanonicalPath());
            } catch (IOException e) {
                log.warn("Exception getting file path", e);
            }
        }
        //remove existing meta info
        if (scope.getContext().hasBean("keyframe.cache")) {
            IKeyFrameMetaCache keyFrameCache = (IKeyFrameMetaCache) scope.getContext().getBean("keyframe.cache");
            keyFrameCache.removeKeyFrameMeta(file);
        }
        // get instance via spring
        if (scope.getContext().hasBean("fileConsumer")) {
            log.debug("Context contains a file consumer");
            recordingConsumer = (FileConsumer) scope.getContext().getBean("fileConsumer");
            recordingConsumer.setScope(scope);
            recordingConsumer.setFile(file);
        } else {
            log.debug("Context does not contain a file consumer, using direct instance");
            // get a new instance
            recordingConsumer = new FileConsumer(scope, file);
        }
        // set the mode on the consumer
        if (isAppend) {
            recordingConsumer.setMode("append");
        } else {
            recordingConsumer.setMode("record");
        }
        // the consumer reports what it writes to the budget and stops at the budget or quota
        recordingConsumer.setRecordingSession(session);
        // set the filename
        setFileName(file.getName());
        // get the scheduler
        scheduler = (ISchedulingService) scope.getParent().getContext().getBean(ISchedulingService.BEAN_NAME);
        // set recording true
        recording.set(true);
        // since init finished, return recording flag
        return recording.get();
    }

    /**
     * Accounts the recording with the application's recording budget, if the context has one.
     */
    private void startSession(IScope scope, IConnection conn, String name, File file, boolean isAppend) {
        IContext context = scope.getContext();
        if (context != null && context.hasBean(RecordingBudget.BEAN_NAME)) {
            RecordingBudget recordingBudget = (RecordingBudget) context.getBean(RecordingBudget.BEAN_NAME);
            File directory = getRecordDirectory(scope);
            if (directory == null) {
                directory = file.getAbsoluteFile().getParentFile();
            }
            IScope app = ScopeUtils.findApplication(scope);
            String application = app != null ? app.getName() : scope.getName();
            // throws when the budget or quota is used up
            session = recordingBudget.start(directory.toPath(), application, conn, name, file.toPath(), isAppend);
            budget = recordingBudget;
        }
    }

    /**
     * Ends the accounting of the recording, once its file is closed.
     */
    private void stopSession(String failure) {
        if (session != null) {
            if (failure != null && session.getFailure() == null) {
                session.setFailure(failure);
            }
            budget.stop(session);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void start() {
        // start the worker
        eqj = new EventQueueJob();
        eventQueueJobName = scheduler.addScheduledJob(3000, eqj);
    }

    /**
     * {@inheritDoc}
     */
    public void stop() {
        // set the record flag to false
        if (recording.compareAndSet(true, false)) {
            // remove the scheduled job
            scheduler.removeScheduledJob(eventQueueJobName);
            if (queue.isEmpty()) {
                log.debug("Event queue was empty on stop");
            } else {
                if (!eqj.processing.get()) {
                    log.debug("Event queue was not empty on stop and it's not processing, processing..");
                    do {
                        processQueue();
                    } while (!queue.isEmpty());
                } else {
                    log.debug("Event queue was not empty on stop but it's in processing, waiting..");
                    do {
                    } while (!queue.isEmpty());
                }
                log.debug("Processing done, event queue empty, moving on");
            }
            recordingConsumer.uninit();
            // the file is closed, so the budget measures its final size
            stopSession(recordingConsumer.getFailure());
        } else {
            log.debug("Recording listener was already stopped");
        }
    }

    /** {@inheritDoc} */
    public void packetReceived(IBroadcastStream stream, IStreamPacket packet) {
        if (recording.get()) {
            String failure = recordingConsumer.getFailure();
            if (failure != null) {
                // the broadcast stream reports this as a failed recording and stops it
                throw new IllegalStateException(failure);
            }
            // store everything we would need to perform a write of the stream data
            CachedEvent event = new CachedEvent();
            event.setData(packet.getData().duplicate());
            event.setDataType(packet.getDataType());
            event.setReceivedTime(System.currentTimeMillis());
            event.setTimestamp(packet.getTimestamp());
            // queue the event
            if (!queue.add(event)) {
                log.debug("Event packet not added to recording queue");
            }
        } else {
            log.info("A packet was received by recording listener, but it's not recording anymore. {}", stream.getPublishedName());
        }
    }

    /**
     * Process the queued items.
     */
    private void processQueue() {
        CachedEvent cachedEvent;
        try {
            IRTMPEvent event = null;
            RTMPMessage message = null;
            // get first event in the queue
            cachedEvent = queue.poll();
            if (cachedEvent != null) {
                // get the data type
                final byte dataType = cachedEvent.getDataType();
                // get the data
                IoBuffer buffer = cachedEvent.getData();
                // get the current size of the buffer / data
                int bufferLimit = buffer.limit();
                if (bufferLimit > 0) {
                    // create new RTMP message and push to the consumer
                    switch (dataType) {
                        case Constants.TYPE_AGGREGATE:
                            event = new Aggregate(buffer);
                            event.setTimestamp(cachedEvent.getTimestamp());
                            message = RTMPMessage.build(event);
                            break;
                        case Constants.TYPE_AUDIO_DATA:
                            event = new AudioData(buffer);
                            event.setTimestamp(cachedEvent.getTimestamp());
                            message = RTMPMessage.build(event);
                            break;
                        case Constants.TYPE_VIDEO_DATA:
                            event = new VideoData(buffer);
                            event.setTimestamp(cachedEvent.getTimestamp());
                            message = RTMPMessage.build(event);
                            break;
                        default:
                            event = new Notify(buffer);
                            event.setTimestamp(cachedEvent.getTimestamp());
                            message = RTMPMessage.build(event);
                            break;
                    }
                    // push it down to the recorder
                    recordingConsumer.pushMessage(null, message);
                } else if (bufferLimit == 0 && dataType == Constants.TYPE_AUDIO_DATA) {
                    log.debug("Stream data size was 0, sending empty audio message");
                    // allow for 0 byte audio packets
                    event = new AudioData(IoBuffer.allocate(0));
                    event.setTimestamp(cachedEvent.getTimestamp());
                    message = RTMPMessage.build(event);
                    // push it down to the recorder
                    recordingConsumer.pushMessage(null, message);
                } else {
                    log.debug("Stream data size was 0, recording pipe will not be notified");
                }
            }
        } catch (Exception e) {
            log.warn("Exception while pushing to consumer", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return a boolean
     */
    public boolean isRecording() {
        return recording.get();
    }

    /**
     * {@inheritDoc}
     *
     * @return a boolean
     */
    public boolean isAppending() {
        return appending;
    }

    /**
     * {@inheritDoc}
     *
     * @return a {@link org.red5.server.stream.consumer.FileConsumer} object
     */
    public FileConsumer getFileConsumer() {
        return recordingConsumer;
    }

    /** {@inheritDoc} */
    public void setFileConsumer(FileConsumer recordingConsumer) {
        this.recordingConsumer = recordingConsumer;
    }

    /**
     * {@inheritDoc}
     *
     * @return a {@link java.lang.String} object
     */
    public String getFileName() {
        return fileName;
    }

    /** {@inheritDoc} */
    public void setFileName(String fileName) {
        log.debug("File name: {}", fileName);
        this.fileName = fileName;
    }

    private class EventQueueJob implements IScheduledJob {

        public AtomicBoolean processing = new AtomicBoolean(false);

        public void execute(ISchedulingService service) {
            if (processing.compareAndSet(false, true)) {
                if (log.isTraceEnabled()) {
                    log.trace("Event queue size: {}", queue.size());
                }
                try {
                    if (!queue.isEmpty()) {
                        while (!queue.isEmpty()) {
                            if (log.isTraceEnabled()) {
                                log.trace("Taking one more item from queue, size: {}", queue.size());
                            }
                            processQueue();
                        }
                    } else {
                        log.trace("Nothing to record");
                    }
                } catch (Exception e) {
                    log.error("Error processing queue", e);
                } finally {
                    processing.set(false);
                }
            }
        }

    }

}
