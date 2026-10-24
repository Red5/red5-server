package org.red5.server.net.mediabunny;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.red5.server.net.mediabunny.MediaBunnyStreamRegistry.SubscriberLimitException;

/**
 * Subscriber limits and live counts for one MediaBunny endpoint. Each {@link MediaBunnyServlet} owns its own instance, so the limits
 * configured for one web application neither replace nor share the budget of another, while the stream state itself stays shared in
 * {@link MediaBunnyStreamRegistry}.
 * <p>
 * A slot is taken with {@link #acquire(String, String)} before a subscriber is attached and returned with {@link #release(String, String)}
 * once it ends. Every count is checked and updated atomically, so concurrent subscriptions never exceed a limit.
 */
public class MediaBunnySubscriberLimits {

    /** Default maximum number of subscribers across all streams. */
    public static final int DEFAULT_MAX_SUBSCRIBERS = 1000;

    /** Default maximum number of subscribers to a single stream. */
    public static final int DEFAULT_MAX_SUBSCRIBERS_PER_STREAM = 500;

    /** Default maximum number of subscribers from one remote address, 0 meaning unlimited since many viewers may share a NAT or proxy. */
    public static final int DEFAULT_MAX_SUBSCRIBERS_PER_ADDRESS = 0;

    /** Default maximum bytes queued for one subscriber before it is disconnected as a slow consumer. */
    public static final long DEFAULT_MAX_QUEUED_BYTES = 16L * 1024 * 1024;

    private volatile int maxSubscribers = DEFAULT_MAX_SUBSCRIBERS;

    private volatile int maxSubscribersPerStream = DEFAULT_MAX_SUBSCRIBERS_PER_STREAM;

    private volatile int maxSubscribersPerAddress = DEFAULT_MAX_SUBSCRIBERS_PER_ADDRESS;

    private volatile long maxQueuedBytes = DEFAULT_MAX_QUEUED_BYTES;

    private final AtomicInteger subscriberCount = new AtomicInteger();

    private final Map<String, Integer> streamCounts = new ConcurrentHashMap<>();

    private final Map<String, Integer> addressCounts = new ConcurrentHashMap<>();

    /**
     * Takes a subscriber slot, checking the global, per-stream and per-address limits.
     *
     * @param streamKey stream key, as built by the registry
     * @param address remote address of the subscriber, or null if unknown
     * @throws SubscriberLimitException if any limit would be exceeded, in which case no slot is taken
     */
    void acquire(String streamKey, String address) {
        int max = maxSubscribers;
        if (subscriberCount.incrementAndGet() > max) {
            subscriberCount.decrementAndGet();
            throw new SubscriberLimitException("MediaBunny subscriber limit of " + max + " reached");
        }
        try {
            increment(streamCounts, streamKey, maxSubscribersPerStream, "per-stream", streamKey);
            if (address != null) {
                try {
                    increment(addressCounts, address, maxSubscribersPerAddress, "per-address", address);
                } catch (RuntimeException e) {
                    decrement(streamCounts, streamKey);
                    throw e;
                }
            }
        } catch (RuntimeException e) {
            subscriberCount.decrementAndGet();
            throw e;
        }
    }

    /**
     * Returns a slot taken by {@link #acquire(String, String)}. Must be called exactly once per successful acquire.
     *
     * @param streamKey stream key passed to acquire
     * @param address remote address passed to acquire
     */
    void release(String streamKey, String address) {
        decrement(streamCounts, streamKey);
        if (address != null) {
            decrement(addressCounts, address);
        }
        subscriberCount.decrementAndGet();
    }

    private static void increment(Map<String, Integer> counts, String key, int max, String kind, String what) {
        // the check and the update happen atomically for the key; throwing leaves the mapping unchanged
        counts.compute(key, (k, count) -> {
            int next = (count == null) ? 1 : count + 1;
            if (max > 0 && next > max) {
                throw new SubscriberLimitException("MediaBunny " + kind + " subscriber limit of " + max + " reached for " + what);
            }
            return next;
        });
    }

    private static void decrement(Map<String, Integer> counts, String key) {
        counts.computeIfPresent(key, (k, count) -> count > 1 ? count - 1 : null);
    }

    /**
     * Returns the number of active subscribers across all streams.
     *
     * @return subscriber count
     */
    public int getSubscriberCount() {
        return subscriberCount.get();
    }

    /**
     * Returns the number of active subscribers to a stream.
     *
     * @param streamKey stream key
     * @return subscriber count for the stream
     */
    public int getStreamSubscriberCount(String streamKey) {
        return streamCounts.getOrDefault(streamKey, 0);
    }

    /**
     * Returns the number of active subscribers from a remote address.
     *
     * @param address remote address
     * @return subscriber count for the address
     */
    public int getAddressSubscriberCount(String address) {
        return addressCounts.getOrDefault(address, 0);
    }

    /**
     * Returns the maximum number of subscribers across all streams.
     *
     * @return maximum subscribers
     */
    public int getMaxSubscribers() {
        return maxSubscribers;
    }

    /**
     * Sets the maximum number of subscribers across all streams. Existing subscribers are kept when lowered.
     *
     * @param maxSubscribers maximum subscribers
     */
    public void setMaxSubscribers(int maxSubscribers) {
        this.maxSubscribers = maxSubscribers;
    }

    /**
     * Returns the maximum number of subscribers to a single stream.
     *
     * @return maximum subscribers per stream, 0 meaning unlimited
     */
    public int getMaxSubscribersPerStream() {
        return maxSubscribersPerStream;
    }

    /**
     * Sets the maximum number of subscribers to a single stream.
     *
     * @param maxSubscribersPerStream maximum subscribers per stream, 0 meaning unlimited
     */
    public void setMaxSubscribersPerStream(int maxSubscribersPerStream) {
        this.maxSubscribersPerStream = maxSubscribersPerStream;
    }

    /**
     * Returns the maximum number of subscribers from one remote address.
     *
     * @return maximum subscribers per address, 0 meaning unlimited
     */
    public int getMaxSubscribersPerAddress() {
        return maxSubscribersPerAddress;
    }

    /**
     * Sets the maximum number of subscribers from one remote address. Leave it at 0 (unlimited) when viewers may share an address
     * through NAT or a proxy, unless the container resolves client addresses (for example with Tomcat's RemoteIpValve).
     *
     * @param maxSubscribersPerAddress maximum subscribers per address, 0 meaning unlimited
     */
    public void setMaxSubscribersPerAddress(int maxSubscribersPerAddress) {
        this.maxSubscribersPerAddress = maxSubscribersPerAddress;
    }

    /**
     * Returns the maximum bytes queued for a subscriber before it is disconnected as a slow consumer.
     *
     * @return maximum queued bytes per subscriber
     */
    public long getMaxQueuedBytes() {
        return maxQueuedBytes;
    }

    /**
     * Sets the maximum bytes queued for a subscriber before it is disconnected as a slow consumer. Applies to new subscribers.
     *
     * @param maxQueuedBytes maximum queued bytes per subscriber
     */
    public void setMaxQueuedBytes(long maxQueuedBytes) {
        this.maxQueuedBytes = maxQueuedBytes;
    }

}
