/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2016 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.stream;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.IConnection;
import org.red5.server.api.IContext;
import org.red5.server.api.Red5;
import org.red5.server.api.scheduling.IScheduledJob;
import org.red5.server.api.scheduling.ISchedulingService;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.statistics.IPlaylistSubscriberStreamStatistics;
import org.red5.server.api.stream.IPlayItem;
import org.red5.server.api.stream.IPlaylistController;
import org.red5.server.api.stream.IPlaylistSubscriberStream;
import org.red5.server.api.stream.IStreamAwareScopeHandler;
import org.red5.server.api.stream.OperationNotSupportedException;
import org.red5.server.api.stream.StreamState;
import org.slf4j.Logger;

/**
 * Stream of playlist subscriber
 */
public class PlaylistSubscriberStream extends AbstractClientStream implements IPlaylistSubscriberStream, IPlaylistSubscriberStreamStatistics {

    private static final Logger log = Red5LoggerFactory.getLogger(PlaylistSubscriberStream.class);

    /**
     * Playlist controller
     */
    private IPlaylistController controller;

    /**
     * Default playlist controller
     */
    private IPlaylistController defaultController = new SimplePlaylistController();

    /**
     * Playlist items
     */
    private final CopyOnWriteArrayList<PlayItemEntry> items = new CopyOnWriteArrayList<>();

    /**
     * Current item index
     */
    private int currentItemIndex = -1;

    /**
     * Plays items back
     */
    protected PlayEngine engine;

    /**
     * Rewind mode state
     */
    protected boolean rewind;

    /**
     * Random mode state
     */
    protected boolean random;

    /**
     * Repeat mode state
     */
    protected boolean repeat;

    /**
     * Service used to provide notifications, keep client buffer filled, clean up, etc...
     */
    protected ISchedulingService schedulingService;

    /**
     * Scheduled job names
     */
    protected CopyOnWriteArraySet<String> jobs = new CopyOnWriteArraySet<String>();

    /**
     * Interval in ms to check for buffer underruns in VOD streams.
     */
    protected int bufferCheckInterval = 0;

    /**
     * Number of pending messages at which a
     * 
     * <pre>
     * NetStream.Play.InsufficientBW
     * </pre>
     * 
     * message is generated for VOD streams.
     */
    protected int underrunTrigger = 10;

    /**
     * Timestamp this stream was created.
     */
    protected long creationTime = System.currentTimeMillis();

    /**
     * Number of bytes sent.
     */
    protected long bytesSent = 0;

    /**
     * see PlayEngine.maxPendingVideoFrames
     */
    private int maxPendingVideoFrames = 10;

    /**
     * see PlayEngine.maxSequentialPendingVideoFrames
     */
    private int maxSequentialPendingVideoFrames = 10;

    /** Constructs a new PlaylistSubscriberStream. */
    public PlaylistSubscriberStream() {
    }

    /**
     * Creates a play engine based on current services (scheduling service, consumer service, and provider service). This method is useful
     * during unit testing.
     */
    PlayEngine createEngine(ISchedulingService schedulingService, IConsumerService consumerService, IProviderService providerService) {
        engine = new PlayEngine.Builder(this, schedulingService, consumerService, providerService).build();
        // set the max pending video frames to the play engine
        engine.setMaxPendingVideoFrames(maxPendingVideoFrames);
        // set the max sequential pending video frames to the play engine
        engine.setMaxSequentialPendingVideoFrames(maxSequentialPendingVideoFrames);
        return engine;
    }

    /**
     * Set interval to check for buffer underruns. Set to 0 to disable.
     * 
     * @param bufferCheckInterval
     *            interval in ms
     */
    public void setBufferCheckInterval(int bufferCheckInterval) {
        this.bufferCheckInterval = bufferCheckInterval;
    }

    /**
     * Set maximum number of pending messages at which a
     * 
     * <pre>
     * NetStream.Play.InsufficientBW
     * </pre>
     * 
     * message will be generated for VOD streams
     * 
     * @param underrunTrigger
     *            the maximum number of pending messages
     */
    public void setUnderrunTrigger(int underrunTrigger) {
        this.underrunTrigger = underrunTrigger;
    }

    /** {@inheritDoc} */
    public void start() {
        //ensure the play engine exists
        if (engine == null) {
            IScope scope = getScope();
            if (scope != null) {
                IContext ctx = scope.getContext();
                if (ctx.hasBean(ISchedulingService.BEAN_NAME)) {
                    schedulingService = (ISchedulingService) ctx.getBean(ISchedulingService.BEAN_NAME);
                } else {
                    //try the parent
                    schedulingService = (ISchedulingService) scope.getParent().getContext().getBean(ISchedulingService.BEAN_NAME);
                }
                IConsumerService consumerService = null;
                if (ctx.hasBean(IConsumerService.KEY)) {
                    consumerService = (IConsumerService) ctx.getBean(IConsumerService.KEY);
                } else {
                    //try the parent
                    consumerService = (IConsumerService) scope.getParent().getContext().getBean(IConsumerService.KEY);
                }
                IProviderService providerService = null;
                if (ctx.hasBean(IProviderService.BEAN_NAME)) {
                    providerService = (IProviderService) ctx.getBean(IProviderService.BEAN_NAME);
                } else {
                    //try the parent
                    providerService = (IProviderService) scope.getParent().getContext().getBean(IProviderService.BEAN_NAME);
                }
                engine = new PlayEngine.Builder(this, schedulingService, consumerService, providerService).build();
            } else {
                throw new IllegalStateException("Scope was null on start playing");
            }
        }
        //set buffer check interval
        engine.setBufferCheckInterval(bufferCheckInterval);
        //set underrun trigger
        engine.setUnderrunTrigger(underrunTrigger);
        // set the max pending video frames to the play engine
        engine.setMaxPendingVideoFrames(maxPendingVideoFrames);
        // set the max sequential pending video frames to the play engine
        engine.setMaxSequentialPendingVideoFrames(maxSequentialPendingVideoFrames);
        // Start playback engine
        engine.start();
        // Notify subscribers on start
        onChange(StreamState.STARTED);
    }

    /** {@inheritDoc} */
    public void play() throws IOException {
        // Return if playlist is empty
        if (!items.isEmpty()) {
            // Move to next if current item is set to -1
            if (currentItemIndex == -1) {
                moveToNext();
            }
            // If there's some more items on list then play current item
            do {
                IPlayItem item = null;
                try {
                    // Get playlist item
                    PlayItemEntry entry = items.get(currentItemIndex);
                    if (entry != null) {
                        item = entry.item;
                        engine.play(item);
                        entry.played = true;
                        break;
                    }
                } catch (StreamNotFoundException e) {
                    // go for next item
                    moveToNext();
                    if (currentItemIndex == -1) {
                        // we reached the end
                        break;
                    }
                } catch (IllegalStateException e) {
                    // an stream is already playing
                    break;
                }
            } while (!items.isEmpty());
        }
    }

    /** {@inheritDoc} */
    public void pause(int position) {
        try {
            engine.pause(position);
        } catch (IllegalStateException e) {
            log.debug("pause caught an IllegalStateException");
        }
    }

    /** {@inheritDoc} */
    public void resume(int position) {
        try {
            engine.resume(position);
        } catch (IllegalStateException e) {
            log.debug("resume caught an IllegalStateException");
        }
    }

    /** {@inheritDoc} */
    public void stop() {
        if (log.isDebugEnabled()) {
            log.debug("stop");
        }
        try {
            engine.stop();
        } catch (IllegalStateException e) {
            if (log.isTraceEnabled()) {
                log.warn("stop caught an IllegalStateException", e);
            } else if (log.isDebugEnabled()) {
                log.debug("stop caught an IllegalStateException");
            }
        }
    }

    /** {@inheritDoc} */
    public void seek(int position) throws OperationNotSupportedException {
        try {
            engine.seek(position);
        } catch (IllegalStateException e) {
            log.debug("seek caught an IllegalStateException");
        }
    }

    /** {@inheritDoc} */
    public void close() {
        if (log.isDebugEnabled()) {
            log.debug("close");
        }
        if (engine != null) {
            // before or on close we may need to allow the queued messages a chance to clear
            engine.close();
            onChange(StreamState.CLOSED);
            items.clear();
            // clear jobs
            if (schedulingService != null && !jobs.isEmpty()) {
                jobs.forEach(jobName -> schedulingService.removeScheduledJob(jobName));
                jobs.clear();
            }
        }
    }

    /** {@inheritDoc} */
    public boolean isPaused() {
        return state.get() == StreamState.PAUSED;
    }

    /** {@inheritDoc} */
    public void addItem(IPlayItem item) {
        items.add(new PlayItemEntry(item));
    }

    /** {@inheritDoc} */
    public void addItem(IPlayItem item, int index) {
        items.add(index, new PlayItemEntry(item));
    }

    /** {@inheritDoc} */
    public void removeItem(int index) {
        if (index < 0 || index >= items.size()) {
            return;
        }
        int originSize = items.size();
        items.remove(index);
        if (currentItemIndex == index) {
            // set the next item.
            if (index == originSize - 1) {
                currentItemIndex = index - 1;
            }
        }
    }

    /** {@inheritDoc} */
    public void removeAllItems() {
        // we try to stop the engine first
        stop();
        items.clear();
    }

    /** {@inheritDoc} */
    public void previousItem() {
        stop();
        moveToPrevious();
        if (currentItemIndex == -1) {
            return;
        }
        IPlayItem item = null;
        do {
            try {
                PlayItemEntry entry = items.get(currentItemIndex);
                if (entry != null) {
                    item = entry.item;
                    engine.play(item);
                    entry.played = true;
                    break;
                }
            } catch (IOException err) {
                log.warn("Error while starting to play item, moving to previous", err);
                // go for next item
                moveToPrevious();
                if (currentItemIndex == -1) {
                    // we reaches the end.
                    break;
                }
            } catch (StreamNotFoundException e) {
                // go for next item
                moveToPrevious();
                if (currentItemIndex == -1) {
                    // we reaches the end.
                    break;
                }
            } catch (IllegalStateException e) {
                // an stream is already playing
                break;
            }
        } while (!items.isEmpty());
    }

    /** {@inheritDoc} */
    public boolean hasMoreItems() {
        int nextItem = currentItemIndex + 1;
        if (nextItem >= items.size() && !repeat) {
            return false;
        } else {
            return true;
        }
    }

    /** {@inheritDoc} */
    public void nextItem() {
        moveToNext();
        if (currentItemIndex == -1) {
            return;
        }
        IPlayItem item = null;
        do {
            try {
                PlayItemEntry entry = items.get(currentItemIndex);
                if (entry != null) {
                    item = entry.item;
                    engine.play(item, false);
                    entry.played = true;
                    break;
                }
            } catch (IOException err) {
                log.warn("Error while starting to play item, moving to next", err);
                // go for next item
                moveToNext();
                if (currentItemIndex == -1) {
                    // we reaches the end.
                    break;
                }
            } catch (StreamNotFoundException e) {
                // go for next item
                moveToNext();
                if (currentItemIndex == -1) {
                    // we reaches the end.
                    break;
                }
            } catch (IllegalStateException e) {
                // an stream is already playing
                break;
            }
        } while (!items.isEmpty());
    }

    /** {@inheritDoc} */
    public void setItem(int index) {
        if (index < 0 || index >= items.size()) {
            return;
        }
        stop();
        currentItemIndex = index;
        try {
            PlayItemEntry entry = items.get(currentItemIndex);
            if (entry != null) {
                IPlayItem item = entry.item;
                engine.play(item);
                entry.played = true;
            }
        } catch (IOException e) {
            log.warn("setItem caught a IOException", e);
        } catch (StreamNotFoundException e) {
            // let the engine retain the STOPPED stateand wait for control from outside
            log.debug("setItem caught a StreamNotFoundException");
        } catch (IllegalStateException e) {
            log.warn("Illegal state exception on playlist item setup", e);
        }
    }

    /** {@inheritDoc} */
    public boolean isRandom() {
        return random;
    }

    /** {@inheritDoc} */
    public void setRandom(boolean random) {
        this.random = random;
    }

    /** {@inheritDoc} */
    public boolean isRewind() {
        return rewind;
    }

    /** {@inheritDoc} */
    public void setRewind(boolean rewind) {
        this.rewind = rewind;
    }

    /** {@inheritDoc} */
    public boolean isRepeat() {
        return repeat;
    }

    /** {@inheritDoc} */
    public void setRepeat(boolean repeat) {
        this.repeat = repeat;
    }

    /**
     * Seek to current position to restart playback with audio and/or video.
     */
    private void seekToCurrentPlayback() {
        if (engine.isPullMode()) {
            try {
                // TODO: figure out if this is the correct position to seek to
                final long delta = System.currentTimeMillis() - engine.getPlaybackStart();
                engine.seek((int) delta);
            } catch (OperationNotSupportedException err) {
                // Ignore error, should not happen for pullMode engines
            }
        }
    }

    /** {@inheritDoc} */
    public void receiveVideo(boolean receive) {
        if (engine != null) {
            boolean receiveVideo = engine.receiveVideo(receive);
            if (!receiveVideo && receive) {
                // video has been re-enabled
                seekToCurrentPlayback();
            }
        } else {
            log.debug("PlayEngine was null, receiveVideo cannot be modified");
        }
    }

    /** {@inheritDoc} */
    public void receiveAudio(boolean receive) {
        if (engine != null) {
            // check if engine currently receives audio, returns previous value
            boolean receiveAudio = engine.receiveAudio(receive);
            if (receiveAudio && !receive) {
                // send a blank audio packet to reset the player
                engine.sendBlankAudio(true);
            } else if (!receiveAudio && receive) {
                // do a seek	
                seekToCurrentPlayback();
            }
        } else {
            log.debug("PlayEngine was null, receiveAudio cannot be modified");
        }
    }

    /** {@inheritDoc} */
    public void setPlaylistController(IPlaylistController controller) {
        this.controller = controller;
    }

    /** {@inheritDoc} */
    public int getItemSize() {
        return items.size();
    }

    /** {@inheritDoc} */
    public int getCurrentItemIndex() {
        return currentItemIndex;
    }

    /**
     * {@inheritDoc}
     */
    public IPlayItem getCurrentItem() {
        return getItem(getCurrentItemIndex());
    }

    /** {@inheritDoc} */
    public IPlayItem getItem(int index) {
        return items.get(index).item;
    }

    /** {@inheritDoc} */
    public boolean replace(IPlayItem oldItem, IPlayItem newItem) {
        boolean[] result = { false };
        int[] index = { 0 };
        items.forEach(itemEntry -> {
            // locate a match
            if (oldItem.equals(itemEntry.item)) {
                // remove the entry
                items.remove(itemEntry);
                // do the insertion at index
                items.add(index[0], new PlayItemEntry(newItem));
                // update the flag
                result[0] = true;
                // exit early
                return;
            }
            index[0]++;
        });
        log.debug("Replacement results: {} at {}", result[0], index[0]);
        return result[0];
    }

    /**
     * Move the current item to the next in list.
     */
    private void moveToNext() {
        if (controller != null) {
            currentItemIndex = controller.nextItem(this, currentItemIndex);
        } else {
            currentItemIndex = defaultController.nextItem(this, currentItemIndex);
        }
    }

    /**
     * Move the current item to the previous in list.
     */
    private void moveToPrevious() {
        if (controller != null) {
            currentItemIndex = controller.previousItem(this, currentItemIndex);
        } else {
            currentItemIndex = defaultController.previousItem(this, currentItemIndex);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void onChange(final StreamState state, final Object... changed) {
        final IConnection conn = Red5.getConnectionLocal();
        IStreamAwareScopeHandler handler = getStreamAwareHandler();
        Notifier notifier = null;
        switch (state) {
            case SEEK:
                //notifies subscribers on seek
                if (handler != null) {
                    notifier = new Notifier(this, handler, conn) {
                        public void execute(ISchedulingService service) {
                            //make sure those notified have the correct connection
                            Red5.setConnectionLocal(conn);
                            //get item being played
                            IPlayItem item = (IPlayItem) changed[0];
                            //seek position
                            int position = (Integer) changed[1];
                            try {
                                handler.streamPlayItemSeek(stream, item, position);
                            } catch (Throwable t) {
                                log.warn("error notify streamPlayItemSeek", t);
                            } finally {
                                // clear thread local reference
                                Red5.setConnectionLocal(null);
                            }
                        }
                    };
                }
                break;
            case PAUSED:
                // set the paused state
                setState(StreamState.PAUSED);
                // notifies subscribers on pause
                if (handler != null) {
                    notifier = new Notifier(this, handler, conn) {
                        public void execute(ISchedulingService service) {
                            //make sure those notified have the correct connection
                            Red5.setConnectionLocal(conn);
                            //get item being played
                            IPlayItem item = (IPlayItem) changed[0];
                            //playback position
                            int position = (Integer) changed[1];
                            try {
                                handler.streamPlayItemPause(stream, item, position);
                            } catch (Throwable t) {
                                log.warn("error notify streamPlayItemPause", t);
                            } finally {
                                // clear thread local reference
                                Red5.setConnectionLocal(null);
                            }
                        }
                    };
                }
                break;
            case RESUMED:
                // resume playing
                setState(StreamState.PLAYING);
                // notifies subscribers on resume
                if (handler != null) {
                    notifier = new Notifier(this, handler, conn) {
                        public void execute(ISchedulingService service) {
                            // make sure those notified have the correct connection
                            Red5.setConnectionLocal(conn);
                            // get item being played
                            IPlayItem item = (IPlayItem) changed[0];
                            // playback position
                            int position = (Integer) changed[1];
                            try {
                                handler.streamPlayItemResume(stream, item, position);
                            } catch (Throwable t) {
                                log.warn("error notify streamPlayItemResume", t);
                            } finally {
                                // clear thread local reference
                                Red5.setConnectionLocal(null);
                            }
                        }
                    };
                }
                break;
            case PLAYING:
                // notifies subscribers on play
                if (handler != null) {
                    notifier = new Notifier(this, handler, conn) {
                        public void execute(ISchedulingService service) {
                            // make sure those notified have the correct connection
                            Red5.setConnectionLocal(conn);
                            // get item being played
                            IPlayItem item = (IPlayItem) changed[0];
                            // is it a live broadcast
                            boolean isLive = (Boolean) changed[1];
                            try {
                                handler.streamPlayItemPlay(stream, item, isLive);
                            } catch (Throwable t) {
                                log.warn("error notify streamPlayItemPlay", t);
                            } finally {
                                // clear thread local reference
                                Red5.setConnectionLocal(null);
                            }
                        }
                    };
                }
                break;
            case CLOSED:
                // notifies subscribers on close
                if (handler != null) {
                    notifier = new Notifier(this, handler, conn) {
                        public void execute(ISchedulingService service) {
                            // make sure those notified have the correct connection
                            Red5.setConnectionLocal(conn);
                            try {
                                handler.streamSubscriberClose(stream);
                            } catch (Throwable t) {
                                log.warn("error notify streamSubscriberClose", t);
                            } finally {
                                // clear thread local reference
                                Red5.setConnectionLocal(null);
                            }
                        }
                    };
                }
                break;
            case STARTED:
                // notifies subscribers on start
                if (handler != null) {
                    notifier = new Notifier(this, handler, conn) {
                        public void execute(ISchedulingService service) {
                            // make sure those notified have the correct connection
                            Red5.setConnectionLocal(conn);
                            try {
                                handler.streamSubscriberStart(stream);
                            } catch (Throwable t) {
                                log.warn("error notify streamSubscriberStart", t);
                            } finally {
                                // clear thread local reference
                                Red5.setConnectionLocal(null);
                            }
                        }
                    };
                }
                break;
            case STOPPED:
                // set the stopped state
                setState(StreamState.STOPPED);
                //notifies subscribers on stop
                if (handler != null) {
                    notifier = new Notifier(this, handler, conn) {
                        public void execute(ISchedulingService service) {
                            //make sure those notified have the correct connection
                            Red5.setConnectionLocal(conn);
                            //get the item that was stopped
                            IPlayItem item = (IPlayItem) changed[0];
                            try {
                                handler.streamPlayItemStop(stream, item);
                            } catch (Throwable t) {
                                log.warn("error notify streamPlaylistItemStop", t);
                            } finally {
                                // clear thread local reference
                                Red5.setConnectionLocal(null);
                            }
                        }
                    };
                }
                break;
            case END:
                // notified by the play engine when the current item reaches the end
                nextItem();
                break;
            default:
                //there is no "default" handling
                log.warn("Unhandled change: {}", state);
        }
        if (notifier != null) {
            scheduleOnceJob(notifier);
        }
    }

    /** {@inheritDoc} */
    public IPlaylistSubscriberStreamStatistics getStatistics() {
        return this;
    }

    /** {@inheritDoc} */
    public long getCreationTime() {
        return creationTime;
    }

    /** {@inheritDoc} */
    public int getCurrentTimestamp() {
        int lastMessageTs = engine.getLastMessageTimestamp();
        if (lastMessageTs >= 0) {
            return 0;
        }
        return lastMessageTs;
    }

    /** {@inheritDoc} */
    public long getBytesSent() {
        return bytesSent;
    }

    /** {@inheritDoc} */
    public double getEstimatedBufferFill() {
        // check to see if any messages have been sent
        int lastMessageTs = engine.getLastMessageTimestamp();
        if (lastMessageTs < 0) {
            // nothing has been sent yet
            return 0.0;
        }
        // buffer size as requested by the client
        final long buffer = getClientBufferDuration();
        if (buffer == 0) {
            return 100.0;
        }
        // duration the stream is playing
        final long delta = System.currentTimeMillis() - engine.getPlaybackStart();
        // expected amount of data present in client buffer
        final long buffered = lastMessageTs - delta;
        return (buffered * 100.0) / buffer;
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

    /** {@inheritDoc} */
    public String scheduleOnceJob(IScheduledJob job) {
        String jobName = schedulingService.addScheduledOnceJob(10, job);
        return jobName;
    }

    /** {@inheritDoc} */
    public String scheduleWithFixedDelay(IScheduledJob job, int interval) {
        String jobName = schedulingService.addScheduledJob(interval, job);
        jobs.add(jobName);
        return jobName;
    }

    /** {@inheritDoc} */
    public void cancelJob(String jobName) {
        schedulingService.removeScheduledJob(jobName);
    }

    /**
     * Handles notifications in a separate thread.
     */
    public class Notifier implements IScheduledJob {

        final IPlaylistSubscriberStream stream;

        final IStreamAwareScopeHandler handler;

        final IConnection conn;

        public Notifier(IPlaylistSubscriberStream stream, IStreamAwareScopeHandler handler, IConnection conn) {
            log.trace("Notifier - stream: {} handler: {}", stream, handler);
            this.conn = conn;
            this.stream = stream;
            this.handler = handler;
        }

        public void execute(ISchedulingService service) {
        }

    }

    class PlayItemEntry implements Comparable<PlayItemEntry> {

        final long addTime;

        final IPlayItem item;

        boolean played;

        PlayItemEntry(IPlayItem item) {
            addTime = System.nanoTime();
            this.item = item;
        }

        @Override
        public int compareTo(PlayItemEntry other) {
            if (this.addTime > other.addTime) {
                return 1;
            } else if (this.addTime < other.addTime) {
                return -1;
            }
            return 0;
        }

    }
}
