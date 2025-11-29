package org.red5.net.websocket.server;

import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tomcat.websocket.BackgroundProcess;
import org.apache.tomcat.websocket.BackgroundProcessManager;

/**
 * Provides timeouts for asynchronous web socket writes. On the server side we only have access to {@link jakarta.servlet.ServletOutputStream}
 * and {@link jakarta.servlet.ServletInputStream} so there is no way to set a timeout for writes to the client.
 *
 * @author mondain
 */
public class WsWriteTimeout implements BackgroundProcess {

    private final Set<WsRemoteEndpointImplServer> endpoints = new ConcurrentSkipListSet<>(new EndpointComparator());

    private final AtomicInteger count = new AtomicInteger(0);

    private int backgroundProcessCount = 0;

    private volatile int processPeriod = 1;

    /** {@inheritDoc} */
    @Override
    public void backgroundProcess() {
        // This method gets called once a second.
        backgroundProcessCount++;
        if (backgroundProcessCount >= processPeriod) {
            backgroundProcessCount = 0;
            long now = System.currentTimeMillis();
            for (WsRemoteEndpointImplServer endpoint : endpoints) {
                if (endpoint.getTimeoutExpiry() < now) {
                    // Background thread, not the thread that triggered the write so no need to use a dispatch
                    endpoint.onTimeout(false);
                } else {
                    // Endpoints are ordered by timeout expiry so if this point is reached there is no need to check the remaining
                    // endpoints
                    break;
                }
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setProcessPeriod(int period) {
        this.processPeriod = period;
    }

    /**
     * {@inheritDoc}
     *
     * The default value is 1 which means asynchronous write timeouts are processed every 1 second.
     */
    @Override
    public int getProcessPeriod() {
        return processPeriod;
    }

    /**
     * <p>register.</p>
     *
     * @param endpoint a {@link org.red5.net.websocket.server.WsRemoteEndpointImplServer} object
     */
    public void register(WsRemoteEndpointImplServer endpoint) {
        boolean result = endpoints.add(endpoint);
        if (result) {
            int newCount = count.incrementAndGet();
            if (newCount == 1) {
                BackgroundProcessManager.getInstance().register(this);
            }
        }
    }

    /**
     * <p>unregister.</p>
     *
     * @param endpoint a {@link org.red5.net.websocket.server.WsRemoteEndpointImplServer} object
     */
    public void unregister(WsRemoteEndpointImplServer endpoint) {
        boolean result = endpoints.remove(endpoint);
        if (result) {
            int newCount = count.decrementAndGet();
            if (newCount == 0) {
                BackgroundProcessManager.getInstance().unregister(this);
            }
        }
    }

    /**
     * Note: this comparator imposes orderings that are inconsistent with equals
     */
    private static class EndpointComparator implements Comparator<WsRemoteEndpointImplServer> {

        @Override
        public int compare(WsRemoteEndpointImplServer o1, WsRemoteEndpointImplServer o2) {
            long t1 = o1.getTimeoutExpiry();
            long t2 = o2.getTimeoutExpiry();
            if (t1 == t2) {
                // fall back to identity hash to keep ordering stable and unique when timeouts match
                return Integer.compare(System.identityHashCode(o1), System.identityHashCode(o2));
            }
            return Long.compare(t1, t2);
        }

    }

}
