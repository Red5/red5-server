package org.red5.server.net.mediabunny;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import org.red5.server.api.scope.IBroadcastScope;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IClientBroadcastStream;
import org.red5.codec.IStreamCodecInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry for MediaBunny stream subscriptions backed by ClientBroadcastStream listeners.
 */
public class MediaBunnyStreamRegistry {

    private static final Logger log = LoggerFactory.getLogger(MediaBunnyStreamRegistry.class);

    private static final MediaBunnyStreamRegistry INSTANCE = new MediaBunnyStreamRegistry();

    private final Map<String, StreamState> streams = new ConcurrentHashMap<>();

    private final Map<String, byte[]> pendingInitSegments = new ConcurrentHashMap<>();

    public static MediaBunnyStreamRegistry getInstance() {
        return INSTANCE;
    }

    public StreamSubscription subscribe(IScope scope, String streamName) {
        log.debug("Subscribing to stream: {}", streamName);
        String key = buildKey(scope, streamName);
        log.debug("Subscriber key: {}", key);
        StreamState state = streams.computeIfAbsent(key, id -> createState(scope, streamName));
        if (state == null) {
            throw new IllegalStateException("Stream not found: " + streamName);
        }
        byte[] pendingInit = pendingInitSegments.remove(key);
        if (pendingInit != null && state.initSegment == null) {
            state.initSegment = pendingInit;
            log.debug("Applied pending init segment for stream {}", key);
        }
        BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
        state.subscribers.add(queue);
        byte[] initSegment = state.initSegment;
        if (initSegment != null) {
            queue.offer(initSegment);
        }
        return new StreamSubscription(key, queue, this);
    }

    public void unsubscribe(String key, BlockingQueue<byte[]> queue) {
        log.debug("Unsubscribing from stream: {}", key);
        StreamState state = streams.get(key);
        if (state == null) {
            return;
        }
        state.subscribers.remove(queue);
        if (state.subscribers.isEmpty()) {
            state.detach();
            streams.remove(key);
        }
    }

    void onInitSegment(String key, byte[] initSegment) {
        StreamState state = streams.get(key);
        if (state == null) {
            pendingInitSegments.put(key, initSegment);
            log.debug("Stored pending init segment for stream {}", key);
            return;
        }
        state.initSegment = initSegment;
        for (BlockingQueue<byte[]> queue : state.subscribers) {
            queue.offer(initSegment);
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
        for (BlockingQueue<byte[]> queue : state.subscribers) {
            queue.offer(fragment);
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

        private final List<BlockingQueue<byte[]>> subscribers = new CopyOnWriteArrayList<>();

        private volatile byte[] initSegment;

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
    }

    public static class StreamSubscription {
        private final String key;

        private final BlockingQueue<byte[]> queue;

        private final MediaBunnyStreamRegistry registry;

        StreamSubscription(String key, BlockingQueue<byte[]> queue, MediaBunnyStreamRegistry registry) {
            this.key = key;
            this.queue = queue;
            this.registry = registry;
        }

        public BlockingQueue<byte[]> getQueue() {
            return queue;
        }

        public void close() {
            registry.unsubscribe(key, queue);
        }
    }
}
