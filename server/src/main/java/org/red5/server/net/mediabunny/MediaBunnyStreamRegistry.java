package org.red5.server.net.mediabunny;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.red5.server.api.scope.IBroadcastScope;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IClientBroadcastStream;
import org.red5.codec.IStreamCodecInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry for MediaBunny stream subscriptions backed by ClientBroadcastStream listeners. The stream state is shared process-wide, while
 * subscriber limits belong to each endpoint and are passed in with every subscription, see {@link MediaBunnySubscriberLimits}.
 */
public class MediaBunnyStreamRegistry {

    private static final Logger log = LoggerFactory.getLogger(MediaBunnyStreamRegistry.class);

    private static final MediaBunnyStreamRegistry INSTANCE = new MediaBunnyStreamRegistry();

    private final Map<String, StreamState> streams = new ConcurrentHashMap<>();

    private final Map<String, byte[]> pendingInitSegments = new ConcurrentHashMap<>();

    /**
     * Returns the process-wide singleton instance of this registry.
     *
     * @return the shared {@link MediaBunnyStreamRegistry} instance
     */
    public static MediaBunnyStreamRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Subscribes to the named broadcast stream in the given scope, attaching a listener to it if one is not already
     * attached, and returns a subscription whose queue receives the stream's init segment, latest keyframe fragment
     * (if any) and subsequent fragments. The subscriber's slot is taken from, and its queue budget read from, the caller's
     * limits, so each endpoint enforces its own limits while the stream state is shared.
     *
     * @param scope the scope containing the broadcast stream
     * @param streamName the name of the stream to subscribe to
     * @param limits limits of the endpoint the subscriber connected to
     * @param address remote address of the subscriber, or null if unknown
     * @return a new subscription for the stream
     * @throws SubscriberLimitException if one of the limits would be exceeded
     * @throws IllegalStateException if the named stream cannot be found in the scope
     */
    public StreamSubscription subscribe(IScope scope, String streamName, MediaBunnySubscriberLimits limits, String address) {
        log.debug("Subscribing to stream: {}", streamName);
        String key = buildKey(scope, streamName);
        limits.acquire(key, address);
        Subscriber subscriber = new Subscriber(key, limits, address);
        try {
            addSubscriber(scope, streamName, subscriber);
        } catch (RuntimeException e) {
            subscriber.release();
            throw e;
        }
        return new StreamSubscription(subscriber, this);
    }

    private void addSubscriber(IScope scope, String streamName, Subscriber subscriber) {
        String key = subscriber.key;
        log.debug("Subscriber key: {}", key);
        // add the subscriber inside compute, so reusing a state cannot interleave with removeSubscriber dropping it as empty
        StreamState state = streams.compute(key, (k, existing) -> {
            StreamState target = existing;
            IBroadcastScope bs = scope.getBroadcastScope(streamName);
            IClientBroadcastStream currentStream = (bs != null) ? bs.getClientBroadcastStream() : null;
            if (existing == null || currentStream == null || currentStream != existing.stream) {
                if (existing != null) {
                    log.info("Stream changed for {}, detaching old listener", k);
                    // end the old subscribers first, they would receive nothing more and must not get the new stream's init segment
                    existing.retire();
                }
                target = createState(scope, streamName);
                if (target == null) {
                    return null;
                }
            }
            target.subscribers.add(subscriber);
            return target;
        });
        if (state == null) {
            throw new IllegalStateException("Stream not found: " + streamName);
        }
        byte[] pendingInit = pendingInitSegments.remove(key);
        if (pendingInit != null && state.initSegment == null) {
            state.initSegment = pendingInit;
            log.debug("Applied pending init segment for stream {}", key);
        }
        byte[] initSegment = state.initSegment;
        if (initSegment != null) {
            enqueue(subscriber, initSegment);
        }
        byte[] keyframe = state.keyframeFragment;
        if (keyframe != null) {
            enqueue(subscriber, keyframe);
        }
    }

    /**
     * Queues data for a subscriber. A subscriber whose queued bytes would exceed its budget is a slow consumer: dropping fragments
     * would corrupt its fMP4 stream, so its backlog is discarded and it is ended instead.
     */
    private void enqueue(Subscriber subscriber, byte[] data) {
        if (!subscriber.offer(data)) {
            log.warn("MediaBunny subscriber on {} exceeded {} queued bytes, disconnecting slow consumer", subscriber.key, subscriber.maxBytes);
            removeSubscriber(subscriber);
            subscriber.end();
        }
    }

    /**
     * Removes a subscriber from its stream and returns its slot to the limits it was taken from. Safe to call more than once.
     *
     * @param subscriber the subscriber to remove
     */
    void unsubscribe(Subscriber subscriber) {
        log.debug("Unsubscribing from stream: {}", subscriber.key);
        removeSubscriber(subscriber);
        subscriber.release();
    }

    /**
     * Removes a subscriber from the stream's subscriber list, detaching and removing the stream's state once its last subscriber has
     * been removed. This runs under the key's lock in the map, as the reuse in {@link #addSubscriber} does, so a state is never dropped
     * while a new subscriber is being added to it.
     */
    private void removeSubscriber(Subscriber subscriber) {
        streams.computeIfPresent(subscriber.key, (k, state) -> {
            state.subscribers.remove(subscriber);
            if (state.subscribers.isEmpty()) {
                state.detach();
                return null;
            }
            return state;
        });
    }

    /**
     * Returns the number of subscribers attached to a stream's state.
     *
     * @param key the stream key
     * @return attached subscribers, 0 if the stream has no state
     */
    int getAttachedSubscriberCount(String key) {
        StreamState state = streams.get(key);
        return (state != null) ? state.subscribers.size() : 0;
    }

    void onStreamClosed(String key) {
        StreamState state = streams.remove(key);
        if (state != null) {
            log.info("Stream closed for {}, removed state and notifying {} subscribers", key, state.subscribers.size());
            // poison-pill empty array to unblock waiting subscribers
            for (Subscriber subscriber : state.subscribers) {
                subscriber.end();
            }
        }
        pendingInitSegments.remove(key);
    }

    void onInitSegment(String key, byte[] initSegment) {
        StreamState state = streams.get(key);
        if (state == null) {
            pendingInitSegments.put(key, initSegment);
            log.debug("Stored pending init segment for stream {}", key);
            return;
        }
        state.initSegment = initSegment;
        for (Subscriber subscriber : state.subscribers) {
            enqueue(subscriber, initSegment);
        }
    }

    void onKeyframeFragment(String key, byte[] fragment) {
        StreamState state = streams.get(key);
        if (state == null) {
            return;
        }
        state.keyframeFragment = fragment;
        if (log.isDebugEnabled()) {
            log.debug("Dispatching keyframe fragment for {} to {} subscribers ({} bytes)", key, state.subscribers.size(), fragment.length);
        }
        for (Subscriber subscriber : state.subscribers) {
            enqueue(subscriber, fragment);
        }
    }

    void onFragment(String key, byte[] fragment) {
        StreamState state = streams.get(key);
        if (state == null) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("Dispatching fragment for {} to {} subscribers ({} bytes)", key, state.subscribers.size(), fragment.length);
        }
        for (Subscriber subscriber : state.subscribers) {
            enqueue(subscriber, fragment);
        }
    }

    private StreamState createState(IScope scope, String streamName) {
        if (scope == null) {
            log.debug("Scope is null");
            return null;
        }
        IBroadcastScope broadcastScope = scope.getBroadcastScope(streamName);
        if (broadcastScope == null) {
            log.debug("Broadcast scope is null for stream: {}", streamName);
            return null;
        }
        IClientBroadcastStream cbs = broadcastScope.getClientBroadcastStream();
        if (cbs == null) {
            log.debug("Client broadcast stream is null for stream: {}", streamName);
            return null;
        }
        MediaBunnyStreamListener listener = new MediaBunnyStreamListener(buildKey(scope, streamName), this);
        cbs.addStreamListener(listener);
        log.info("Attached MediaBunny listener to stream: {}", streamName);
        IStreamCodecInfo codecInfo = cbs.getCodecInfo();
        if (codecInfo != null) {
            listener.seedFromCodecInfo(codecInfo);
        } else {
            log.debug("No codec info available for stream {}", streamName);
        }
        return new StreamState(cbs, listener);
    }

    private String buildKey(IScope scope, String streamName) {
        return scope.getName() + ":" + streamName;
    }

    static class StreamState {
        private final IClientBroadcastStream stream;

        private final MediaBunnyStreamListener listener;

        private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

        private volatile byte[] initSegment;

        private volatile byte[] keyframeFragment;

        StreamState(IClientBroadcastStream stream, MediaBunnyStreamListener listener) {
            this.stream = stream;
            this.listener = listener;
        }

        void detach() {
            try {
                stream.removeStreamListener(listener);
            } catch (Exception e) {
                log.debug("Failed to remove MediaBunny listener", e);
            }
        }

        /** Detaches the listener and ends every subscriber, used when this state is replaced by one for a new stream. */
        void retire() {
            detach();
            for (Subscriber subscriber : subscribers) {
                subscriber.end();
            }
        }
    }

    /** Thrown when a subscription would exceed the global, per-stream or per-address subscriber limit. */
    public static class SubscriberLimitException extends IllegalStateException {

        private static final long serialVersionUID = 1L;

        SubscriberLimitException(String message) {
            super(message);
        }
    }

    /** A subscriber's fragment queue with a byte budget. An empty array marks the end of the stream. */
    static class Subscriber {

        private static final byte[] END = new byte[0];

        private final String key;

        private final long maxBytes;

        private final MediaBunnySubscriberLimits limits;

        private final String address;

        private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();

        private final AtomicLong queuedBytes = new AtomicLong();

        private final AtomicBoolean ended = new AtomicBoolean();

        private final AtomicBoolean released = new AtomicBoolean();

        Subscriber(String key, MediaBunnySubscriberLimits limits, String address) {
            this.key = key;
            this.limits = limits;
            this.address = address;
            this.maxBytes = limits.getMaxQueuedBytes();
        }

        boolean offer(byte[] data) {
            if (ended.get()) {
                return true;
            }
            if (queuedBytes.addAndGet(data.length) > maxBytes) {
                queuedBytes.addAndGet(-data.length);
                return false;
            }
            return queue.offer(data);
        }

        void end() {
            if (ended.compareAndSet(false, true)) {
                queue.clear();
                queuedBytes.set(0);
                queue.offer(END);
            }
        }

        byte[] poll(long timeout, TimeUnit unit) throws InterruptedException {
            byte[] data = queue.poll(timeout, unit);
            if (data != null && data.length > 0) {
                queuedBytes.addAndGet(-data.length);
            }
            return data;
        }

        long getQueuedBytes() {
            return queuedBytes.get();
        }

        /** Returns this subscriber's slot to the limits it was taken from, once. */
        void release() {
            if (released.compareAndSet(false, true)) {
                limits.release(key, address);
            }
        }
    }

    /** A handle to an active subscription to a MediaBunny stream. */
    public static class StreamSubscription {

        private final Subscriber subscriber;

        private final MediaBunnyStreamRegistry registry;

        StreamSubscription(Subscriber subscriber, MediaBunnyStreamRegistry registry) {
            this.subscriber = subscriber;
            this.registry = registry;
        }

        /**
         * Waits for the next init segment, keyframe or fragment. An empty array means the stream ended or this subscriber was
         * disconnected as a slow consumer; null means nothing arrived within the timeout.
         *
         * @param timeout how long to wait
         * @param unit unit of the timeout
         * @return next chunk, an empty array at end of stream, or null on timeout
         * @throws InterruptedException if interrupted while waiting
         */
        public byte[] poll(long timeout, TimeUnit unit) throws InterruptedException {
            return subscriber.poll(timeout, unit);
        }

        /**
         * Returns the bytes currently queued for this subscription.
         *
         * @return queued bytes
         */
        public long getQueuedBytes() {
            return subscriber.getQueuedBytes();
        }

        /**
         * Unsubscribes from the registry, releasing the stream's listener once no subscribers remain. Safe to call more than once.
         */
        public void close() {
            registry.unsubscribe(subscriber);
        }
    }
}
