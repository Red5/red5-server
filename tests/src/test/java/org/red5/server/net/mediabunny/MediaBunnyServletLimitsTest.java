package org.red5.server.net.mediabunny;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.After;
import org.junit.Test;
import org.red5.server.api.scope.IScope;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletConfig;
import org.springframework.mock.web.MockServletContext;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * Exercises MediaBunnyServlet limits, stalled readers and CORS through the servlet itself (issue #459).
 */
public class MediaBunnyServletLimitsTest {

    private final List<MediaBunnyServlet> servlets = new ArrayList<>();

    private final List<String> streamKeys = new ArrayList<>();

    @After
    public void tearDown() throws Exception {
        for (String key : streamKeys) {
            MediaBunnyStreamRegistry.getInstance().onStreamClosed(key);
        }
        for (MediaBunnyServlet servlet : servlets) {
            await(() -> servlet.getLimits().getSubscriberCount() == 0, 5000);
            servlet.destroy();
        }
    }

    @Test
    public void testConnectionFloodBeyondGlobalCapIsRefused() throws Exception {
        MediaBunnyServlet servlet = servlet("flood", "maxSubscribers", "3");
        String key = key("flood", "stream1");
        int requests = 20;
        ExecutorService clients = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> statuses = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            String address = "10.0.0." + i;
            statuses.add(clients.submit(() -> {
                start.await();
                return get(servlet, "stream1", address).response.getStatus();
            }));
        }
        start.countDown();
        int ok = 0;
        int refused = 0;
        for (Future<Integer> status : statuses) {
            int code = status.get(10, TimeUnit.SECONDS);
            if (code == HttpServletResponse.SC_OK) {
                ok++;
            } else if (code == HttpServletResponse.SC_SERVICE_UNAVAILABLE) {
                refused++;
            }
        }
        clients.shutdown();
        assertEquals(3, ok);
        assertEquals(requests - 3, refused);
        assertEquals(3, servlet.getLimits().getSubscriberCount());
        assertEquals(3, MediaBunnyStreamRegistry.getInstance().getAttachedSubscriberCount(key));
        assertTrue(servlet.getExecutor().getPoolSize() <= 3);
        assertEquals(3, servlet.getExecutor().getMaximumPoolSize());
        // ending the stream releases every slot and thread
        MediaBunnyStreamRegistry.getInstance().onStreamClosed(key);
        assertTrue(await(() -> servlet.getLimits().getSubscriberCount() == 0 && servlet.getExecutor().getActiveCount() == 0, 5000));
        assertEquals(HttpServletResponse.SC_OK, get(servlet, "stream1", "10.0.0.1").response.getStatus());
    }

    @Test
    public void testConnectionFloodFromOneAddressIsRefused() throws Exception {
        MediaBunnyServlet servlet = servlet("peraddr", "maxSubscribersPerAddress", "2");
        int ok = 0;
        for (int i = 0; i < 10; i++) {
            int status = get(servlet, "stream1", "192.0.2.1").response.getStatus();
            if (status == HttpServletResponse.SC_OK) {
                ok++;
            } else {
                assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, status);
            }
        }
        assertEquals(2, ok);
        assertEquals(2, servlet.getLimits().getAddressSubscriberCount("192.0.2.1"));
        assertEquals(2, servlet.getExecutor().getActiveCount());
        // other addresses are not affected
        assertEquals(HttpServletResponse.SC_OK, get(servlet, "stream1", "192.0.2.2").response.getStatus());
    }

    @Test
    public void testPerAddressLimitIsUnlimitedByDefault() throws Exception {
        MediaBunnyServlet servlet = servlet("nat");
        for (int i = 0; i < 10; i++) {
            assertEquals(HttpServletResponse.SC_OK, get(servlet, "stream1", "198.51.100.7").response.getStatus());
        }
        assertEquals(10, servlet.getLimits().getAddressSubscriberCount("198.51.100.7"));
    }

    @Test
    public void testServletsKeepTheirOwnLimits() throws Exception {
        MediaBunnyServlet small = servlet("appone", "maxSubscribers", "1");
        // initialized last, which used to overwrite the shared registry's limits for every servlet
        MediaBunnyServlet large = servlet("apptwo", "maxSubscribers", "4");
        assertEquals(HttpServletResponse.SC_OK, get(small, "stream1", "10.0.0.1").response.getStatus());
        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, get(small, "stream1", "10.0.0.2").response.getStatus());
        for (int i = 0; i < 4; i++) {
            assertEquals(HttpServletResponse.SC_OK, get(large, "stream1", "10.0.1." + i).response.getStatus());
        }
        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, get(large, "stream1", "10.0.1.9").response.getStatus());
        assertEquals(1, small.getLimits().getSubscriberCount());
        assertEquals(4, large.getLimits().getSubscriberCount());
        assertEquals(1, small.getExecutor().getMaximumPoolSize());
        assertEquals(4, large.getExecutor().getMaximumPoolSize());
    }

    @Test
    public void testSetMaxSubscribersResizesThePool() throws Exception {
        MediaBunnyServlet servlet = servlet("resize", "maxSubscribers", "1");
        servlet.setMaxSubscribers(3);
        assertEquals(3, servlet.getExecutor().getMaximumPoolSize());
        for (int i = 0; i < 3; i++) {
            assertEquals(HttpServletResponse.SC_OK, get(servlet, "stream1", "10.0.0." + i).response.getStatus());
        }
        assertEquals(HttpServletResponse.SC_SERVICE_UNAVAILABLE, get(servlet, "stream1", "10.0.0.9").response.getStatus());
        servlet.setMaxSubscribers(2);
        assertEquals(2, servlet.getExecutor().getMaximumPoolSize());
    }

    @Test
    public void testIdleSubscriberIsEnded() throws Exception {
        MediaBunnyServlet servlet = servlet("idle", "idleTimeoutMs", "200");
        Exchange exchange = get(servlet, "stream1", "10.0.0.1");
        assertEquals(HttpServletResponse.SC_OK, exchange.response.getStatus());
        assertTrue(exchange.completed.await(5, TimeUnit.SECONDS));
        assertTrue(await(() -> servlet.getLimits().getSubscriberCount() == 0, 5000));
    }

    @Test
    public void testStalledReaderIsEndedByWriteTimeout() throws Exception {
        MediaBunnyServlet servlet = servlet("stall");
        // a reader that stopped reading: the write blocks, then fails as the connector's socket write timeout does
        StallingOutputStream out = new StallingOutputStream(300);
        Exchange exchange = get(servlet, "stream1", "10.0.0.1", out);
        assertEquals(HttpServletResponse.SC_OK, exchange.response.getStatus());
        MediaBunnyStreamRegistry.getInstance().onFragment(key("stall", "stream1"), new byte[100]);
        assertTrue(out.writeStarted.await(5, TimeUnit.SECONDS));
        assertTrue(exchange.completed.await(5, TimeUnit.SECONDS));
        assertTrue(await(() -> servlet.getLimits().getSubscriberCount() == 0 && servlet.getExecutor().getActiveCount() == 0, 5000));
        assertEquals(0, MediaBunnyStreamRegistry.getInstance().getAttachedSubscriberCount(key("stall", "stream1")));
    }

    @Test
    public void testStalledReaderBacklogIsBoundedWhileWriteBlocks() throws Exception {
        MediaBunnyServlet servlet = servlet("backlog", "maxQueuedBytes", "1000");
        String key = key("backlog", "stream1");
        StallingOutputStream out = new StallingOutputStream(-1);
        Exchange exchange = get(servlet, "stream1", "10.0.0.1", out);
        assertEquals(HttpServletResponse.SC_OK, exchange.response.getStatus());
        MediaBunnyStreamRegistry registry = MediaBunnyStreamRegistry.getInstance();
        registry.onFragment(key, new byte[600]);
        assertTrue(out.writeStarted.await(5, TimeUnit.SECONDS));
        // the writer is blocked, so these pile up until the budget is exceeded and the subscriber is cut off from the stream
        registry.onFragment(key, new byte[600]);
        registry.onFragment(key, new byte[600]);
        assertEquals(0, registry.getAttachedSubscriberCount(key));
        // its slot and thread stay held until the blocked write fails
        assertEquals(1, servlet.getLimits().getSubscriberCount());
        out.fail.countDown();
        assertTrue(exchange.completed.await(5, TimeUnit.SECONDS));
        assertTrue(await(() -> servlet.getLimits().getSubscriberCount() == 0, 5000));
    }

    @Test
    public void testCorsGrantsCredentialsOnlyToAllowedOrigins() throws Exception {
        MediaBunnyServlet servlet = servlet("cors", "allowedOrigins", "https://player.example.com, https://other.example.com");
        MockHttpServletResponse allowed = options(servlet, "https://player.example.com");
        assertEquals("https://player.example.com", allowed.getHeader("Access-Control-Allow-Origin"));
        assertEquals("true", allowed.getHeader("Access-Control-Allow-Credentials"));
        assertEquals("Origin", allowed.getHeader("Vary"));
        MockHttpServletResponse other = options(servlet, "https://other.example.com");
        assertEquals("https://other.example.com", other.getHeader("Access-Control-Allow-Origin"));
        MockHttpServletResponse disallowed = options(servlet, "https://evil.example.com");
        assertEquals("*", disallowed.getHeader("Access-Control-Allow-Origin"));
        assertNull(disallowed.getHeader("Access-Control-Allow-Credentials"));
        assertEquals("Origin", disallowed.getHeader("Vary"));
        // a prefix or suffix of an allowed origin is not allowed
        assertEquals("*", options(servlet, "https://player.example.com.evil.example").getHeader("Access-Control-Allow-Origin"));
        MockHttpServletResponse missing = options(servlet, null);
        assertEquals("*", missing.getHeader("Access-Control-Allow-Origin"));
        assertNull(missing.getHeader("Access-Control-Allow-Credentials"));
    }

    @Test
    public void testCorsWithoutAllowedOriginsNeverGrantsCredentials() throws Exception {
        MediaBunnyServlet servlet = servlet("corsdefault");
        MockHttpServletResponse resp = options(servlet, "https://evil.example.com");
        assertEquals("*", resp.getHeader("Access-Control-Allow-Origin"));
        assertNull(resp.getHeader("Access-Control-Allow-Credentials"));
        assertNull(resp.getHeader("Vary"));
        // the streaming response carries the same headers
        Exchange exchange = get(servlet, "stream1", "10.0.0.1", null, "https://evil.example.com");
        assertEquals(HttpServletResponse.SC_OK, exchange.response.getStatus());
        assertEquals("*", exchange.response.getHeader("Access-Control-Allow-Origin"));
        assertNull(exchange.response.getHeader("Access-Control-Allow-Credentials"));
    }

    private MediaBunnyServlet servlet(String appName, String... initParams) throws Exception {
        IScope scope = MediaBunnyStreamRegistryLimitsTest.scope(appName);
        MediaBunnyServlet servlet = new MediaBunnyServlet() {
            private static final long serialVersionUID = 1L;

            @Override
            IScope getScope(HttpServletRequest req) {
                return scope;
            }
        };
        MockServletConfig config = new MockServletConfig(new MockServletContext(), "mediabunny");
        for (int i = 0; i < initParams.length; i += 2) {
            config.addInitParameter(initParams[i], initParams[i + 1]);
        }
        servlet.init(config);
        servlets.add(servlet);
        streamKeys.add(key(appName, "stream1"));
        return servlet;
    }

    private static String key(String appName, String streamName) {
        return appName + ":" + streamName;
    }

    private static Exchange get(MediaBunnyServlet servlet, String streamName, String address) throws Exception {
        return get(servlet, streamName, address, null, null);
    }

    private static Exchange get(MediaBunnyServlet servlet, String streamName, String address, ServletOutputStream out) throws Exception {
        return get(servlet, streamName, address, out, null);
    }

    private static Exchange get(MediaBunnyServlet servlet, String streamName, String address, ServletOutputStream out, String origin) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        HttpServletResponse target = response;
        if (out != null) {
            target = new HttpServletResponseWrapper(response) {
                @Override
                public ServletOutputStream getOutputStream() {
                    return out;
                }
            };
        }
        Exchange exchange = new Exchange(response, target);
        exchange.request.setMethod("GET");
        exchange.request.setParameter("stream", streamName);
        exchange.request.setRemoteAddr(address);
        if (origin != null) {
            exchange.request.addHeader("Origin", origin);
        }
        servlet.service(exchange.request, target);
        return exchange;
    }

    private static MockHttpServletResponse options(MediaBunnyServlet servlet, String origin) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/live/mb");
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        servlet.service(request, response);
        return response;
    }

    private static boolean await(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                return false;
            }
            Thread.sleep(10);
        }
        return true;
    }

    /** A request and response pair whose async context is given the response and signals its completion. */
    private static class Exchange {

        final MockHttpServletResponse response;

        final CountDownLatch completed = new CountDownLatch(1);

        final MockHttpServletRequest request;

        Exchange(MockHttpServletResponse response, ServletResponse asyncResponse) {
            this.response = response;
            this.request = new MockHttpServletRequest() {
                @Override
                public AsyncContext startAsync() {
                    AsyncContext context = startAsync(this, asyncResponse);
                    context.addListener(new AsyncListener() {
                        @Override
                        public void onComplete(AsyncEvent event) {
                            completed.countDown();
                        }

                        @Override
                        public void onTimeout(AsyncEvent event) {
                        }

                        @Override
                        public void onError(AsyncEvent event) {
                        }

                        @Override
                        public void onStartAsync(AsyncEvent event) {
                        }
                    });
                    return context;
                }
            };
            this.request.setAsyncSupported(true);
        }
    }

    /** An output stream to a reader that stopped reading: every write blocks, then fails like a socket write timeout. */
    private static class StallingOutputStream extends ServletOutputStream {

        final CountDownLatch writeStarted = new CountDownLatch(1);

        final CountDownLatch fail = new CountDownLatch(1);

        private final long stallMs;

        /**
         * @param stallMs how long a write blocks before failing, or -1 to block until {@link #fail} is released
         */
        StallingOutputStream(long stallMs) {
            this.stallMs = stallMs;
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[] { (byte) b }, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            writeStarted.countDown();
            try {
                if (stallMs < 0) {
                    fail.await();
                } else {
                    fail.await(stallMs, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new SocketTimeoutException("write timed out");
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
        }
    }

}
