package org.red5.server.net.mediabunny;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.red5.server.api.IServer;
import org.red5.server.api.scope.IGlobalScope;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.service.IStreamSecurityService;
import org.red5.server.api.stream.IStreamPlaybackSecurity;
import org.red5.server.net.mediabunny.MediaBunnyStreamRegistry.StreamSubscription;
import org.red5.server.net.mediabunny.MediaBunnyStreamRegistry.SubscriberLimitException;
import org.red5.server.scope.WebScope;
import org.red5.server.util.ScopeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * HTTP endpoint for MediaBunny fMP4 streaming.
 * Usage: /mediabunny?stream={name}
 * <p>
 * Optional servlet init-params, all applying to this servlet only, so each web application keeps its own limits:
 * <ul>
 * <li>maxSubscribers - subscribers across all streams, also the size of the streaming thread pool (default 1000)</li>
 * <li>maxSubscribersPerStream - subscribers to one stream (default 500)</li>
 * <li>maxSubscribersPerAddress - subscribers from one remote address (default 0, unlimited, since viewers may share a NAT or proxy)</li>
 * <li>maxQueuedBytes - bytes queued per subscriber, slow consumers beyond it are disconnected (default 16 MB)</li>
 * <li>idleTimeoutMs - a subscriber that receives nothing for this long is ended (default 30 s)</li>
 * <li>allowedOrigins - comma separated exact origins (scheme://host[:port]) granted credentialed CORS access; any other origin gets
 * {@code Access-Control-Allow-Origin: *} without credentials (default none)</li>
 * </ul>
 * A request beyond a subscriber limit is answered with 503.
 * <p>
 * Stalled readers: each subscriber is served by one pool thread doing blocking writes. A reader that stops reading makes
 * {@code out.write} block once the socket buffers fill; that write is bounded by the connector's socket write timeout (for Tomcat
 * HTTP/1.1 NIO and NIO2 the connector's {@code connectionTimeout}, 20 s in the bundled jee-container.xml; for HTTP/2 the protocol's
 * {@code writeTimeout}), which fails the write so the stream ends and its thread and slot are released. Meanwhile the subscriber's
 * queue is capped by maxQueuedBytes. The servlet API offers no way to abort a blocking write from another thread, and Tomcat measures
 * an {@link AsyncContext} timeout from when async processing started rather than from the last write, so it cannot serve as a write
 * deadline for a long-lived stream. The connector timeout is the bound: do not set it to infinite (-1) where this servlet is deployed.
 */
public class MediaBunnyServlet extends HttpServlet implements AsyncListener {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(MediaBunnyServlet.class);

    private transient WebApplicationContext webAppCtx;

    private transient IServer server;

    private transient WebScope webScope;

    /** Default time a subscriber may go without receiving data before its stream is ended. */
    public static final long DEFAULT_IDLE_TIMEOUT_MS = 30_000L;

    /** This servlet's subscriber limits and counts. */
    private final transient MediaBunnySubscriberLimits limits = new MediaBunnySubscriberLimits();

    /** Thread pool used to run per-connection streaming tasks off the servlet container's request threads, bounded by maxSubscribers. */
    private transient volatile ThreadPoolExecutor executor;

    private volatile long idleTimeoutMs = DEFAULT_IDLE_TIMEOUT_MS;

    /** Exact origins granted credentialed CORS access. */
    private transient volatile Set<String> allowedOrigins = Collections.emptySet();

    private static final int INIT_PREFIX_BYTES = 32;

    @Override
    public void init() throws ServletException {
        super.init();
        String value = getInitParameter("maxSubscribers");
        if (value != null) {
            limits.setMaxSubscribers(Integer.parseInt(value.trim()));
        }
        value = getInitParameter("maxSubscribersPerStream");
        if (value != null) {
            limits.setMaxSubscribersPerStream(Integer.parseInt(value.trim()));
        }
        value = getInitParameter("maxSubscribersPerAddress");
        if (value != null) {
            limits.setMaxSubscribersPerAddress(Integer.parseInt(value.trim()));
        }
        value = getInitParameter("maxQueuedBytes");
        if (value != null) {
            limits.setMaxQueuedBytes(Long.parseLong(value.trim()));
        }
        value = getInitParameter("idleTimeoutMs");
        if (value != null) {
            idleTimeoutMs = Long.parseLong(value.trim());
        }
        value = getInitParameter("allowedOrigins");
        if (value != null) {
            setAllowedOrigins(value);
        }
        executor = new ThreadPoolExecutor(0, poolSize(limits.getMaxSubscribers()), 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), r -> {
            Thread t = new Thread(r, "MediaBunny-subscriber");
            t.setDaemon(true);
            return t;
        });
        lookupWebContext();
    }

    @Override
    public void destroy() {
        if (executor != null) {
            executor.shutdownNow();
        }
        super.destroy();
    }

    @SuppressWarnings("null")
    private void lookupWebContext() {
        ServletContext ctx = getServletContext();
        try {
            webAppCtx = WebApplicationContextUtils.getRequiredWebApplicationContext(ctx);
        } catch (IllegalStateException e) {
            webAppCtx = (WebApplicationContext) ctx.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
        }
        if (webAppCtx != null) {
            server = (IServer) webAppCtx.getBean("red5.server");
            webScope = (WebScope) webAppCtx.getBean("web.scope");
            log.info("MediaBunny servlet initialized");
        } else {
            log.warn("No web application context available");
        }
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handleCORS(req, resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String streamName = req.getParameter("stream");
        log.debug("Mediabunny get request: {}", streamName);
        handleCORS(req, resp);
        if (streamName == null || streamName.isBlank()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing stream parameter");
            return;
        }
        IScope scope = getScope(req);
        if (scope == null) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Scope not available");
            return;
        }
        // normalize the name as RTMP playback does, dropping any query string
        int query = streamName.indexOf('?');
        if (query >= 0) {
            streamName = streamName.substring(0, query);
            if (streamName.isBlank()) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing stream parameter");
                return;
            }
        }
        if (!isPlaybackAllowed(scope, streamName)) {
            log.warn("MediaBunny playback of {} denied by stream playback security", streamName);
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Playback not allowed");
            return;
        }
        StreamSubscription subscription;
        try {
            subscription = MediaBunnyStreamRegistry.getInstance().subscribe(scope, streamName, limits, req.getRemoteAddr());
        } catch (SubscriberLimitException e) {
            log.warn("MediaBunny subscription to {} from {} refused: {}", streamName, req.getRemoteAddr(), e.getMessage());
            resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Too many subscribers");
            return;
        } catch (Exception e) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Stream not found: " + streamName);
            return;
        }
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("video/mp4");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("Connection", "keep-alive");
        AsyncContext asyncContext = req.startAsync();
        // the streaming task ends idle subscribers itself, see idleTimeoutMs
        asyncContext.setTimeout(0);
        asyncContext.addListener(this);
        try {
            executor.execute(() -> streamQueue(asyncContext, subscription));
        } catch (RejectedExecutionException e) {
            // the pool matches maxSubscribers, so this only happens briefly while a finishing stream's thread has released its slot
            // but not yet returned to the pool, or after destroy
            subscription.close();
            resp.reset();
            handleCORS(req, resp);
            resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Too many subscribers");
            asyncContext.complete();
            return;
        }
    }

    /**
     * Writes a subscriber's fragments until the stream ends, the subscriber idles for idleTimeoutMs, or a write fails. A write to a
     * reader that stopped reading blocks until the connector's write timeout fails it, see the class documentation.
     */
    private void streamQueue(AsyncContext asyncContext, StreamSubscription subscription) {
        boolean loggedFirstChunk = false;
        boolean loggedSecondChunk = false;
        int chunkCount = 0;
        try (OutputStream out = asyncContext.getResponse().getOutputStream()) {
            while (true) {
                byte[] chunk = subscription.poll(idleTimeoutMs, TimeUnit.MILLISECONDS);
                if (chunk == null) {
                    log.debug("MediaBunny subscriber idle for {} ms, ending", idleTimeoutMs);
                    break;
                }
                if (chunk.length == 0) {
                    log.debug("MediaBunny received end-of-stream signal");
                    break;
                }
                chunkCount++;
                if (!loggedFirstChunk) {
                    loggedFirstChunk = true;
                    log.info("MediaBunny first chunk ({} bytes) prefix={}", chunk.length, hexPrefix(chunk, INIT_PREFIX_BYTES));
                } else if (!loggedSecondChunk) {
                    loggedSecondChunk = true;
                    log.info("MediaBunny second chunk ({} bytes) prefix={}", chunk.length, hexPrefix(chunk, INIT_PREFIX_BYTES));
                } else if (chunkCount % 50 == 0 && log.isDebugEnabled()) {
                    log.debug("MediaBunny chunk {} ({} bytes)", chunkCount, chunk.length);
                }
                out.write(chunk);
                out.flush();
            }
        } catch (InterruptedException e) {
            // restore the interrupt status and end the stream
            Thread.currentThread().interrupt();
            log.debug("MediaBunny stream interrupted: {}", e.getMessage());
        } catch (Exception e) {
            log.debug("MediaBunny stream ended: {}", e.getMessage());
        } finally {
            subscription.close();
            try {
                asyncContext.complete();
            } catch (IllegalStateException e) {
                log.debug("AsyncContext already completed or in error state", e);
            }
        }
    }

    /**
     * Applies the application's stream playback security handlers, as RTMP playback does. A handler that throws denies playback, since
     * handlers written for RTMP may expect a current connection, which does not exist for this HTTP request.
     *
     * @param scope application scope
     * @param streamName normalized stream name
     * @return true if every registered handler allows playback
     */
    static boolean isPlaybackAllowed(IScope scope, String streamName) {
        IStreamSecurityService security = (IStreamSecurityService) ScopeUtils.getScopeService(scope, IStreamSecurityService.class);
        if (security != null) {
            Set<IStreamPlaybackSecurity> handlers = security.getStreamPlaybackSecurity();
            if (handlers != null) {
                for (IStreamPlaybackSecurity handler : handlers) {
                    try {
                        // live playback defaults: start -2 (live, then recorded), length -1 (until end), flush playlist
                        if (!handler.isPlaybackAllowed(scope, streamName, -2, -1, true)) {
                            return false;
                        }
                    } catch (Exception e) {
                        log.warn("Stream playback security handler {} failed for {}, denying", handler, streamName, e);
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Sets the CORS headers. Only an origin listed in allowedOrigins is echoed back with credentials allowed; any other cross-origin
     * caller may still fetch the stream, but without cookies or HTTP authentication, so a third-party page cannot ride a viewer's
     * session. Same-origin requests, such as the bundled player's, do not depend on these headers.
     */
    private void handleCORS(HttpServletRequest req, HttpServletResponse resp) {
        Set<String> allowed = allowedOrigins;
        String origin = req.getHeader("Origin");
        if (origin != null && allowed.contains(origin)) {
            resp.setHeader("Access-Control-Allow-Origin", origin);
            resp.setHeader("Access-Control-Allow-Credentials", "true");
        } else {
            resp.setHeader("Access-Control-Allow-Origin", "*");
        }
        if (!allowed.isEmpty()) {
            // the answer depends on the request's origin, so shared caches must key on it
            resp.addHeader("Vary", "Origin");
        }
        resp.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Accept, Content-Type");
        resp.setHeader("Access-Control-Max-Age", "3600");
    }

    /**
     * Resolves the application scope for the request from its context path, falling back to the live application.
     *
     * @param req request
     * @return application scope, or null if the server is not available
     */
    IScope getScope(HttpServletRequest req) {
        if (webAppCtx == null) {
            lookupWebContext();
        }
        if (webScope == null || server == null) {
            return null;
        }
        IGlobalScope globalScope = server.getGlobal("default");
        if (globalScope == null) {
            return null;
        }
        String path = req.getContextPath();
        if (path == null || path.equals("/")) {
            return ScopeUtils.resolveScope(globalScope, "/live");
        }
        String[] parts = path.split("/");
        if (parts.length > 1) {
            String appName = parts[1];
            IScope appScope = ScopeUtils.resolveScope(globalScope, appName);
            if (appScope != null && ScopeUtils.isApp(appScope)) {
                return appScope;
            }
        }
        return ScopeUtils.resolveScope(globalScope, "/live");
    }

    /**
     * Sets the maximum number of subscribers across all streams of this servlet, resizing the streaming thread pool to match.
     *
     * @param maxSubscribers maximum subscribers
     */
    public void setMaxSubscribers(int maxSubscribers) {
        limits.setMaxSubscribers(maxSubscribers);
        ThreadPoolExecutor pool = executor;
        if (pool != null) {
            // lowering it lets surplus threads exit once their streams end
            pool.setMaximumPoolSize(poolSize(maxSubscribers));
        }
    }

    /**
     * Sets the maximum number of subscribers to a single stream of this servlet.
     *
     * @param maxSubscribersPerStream maximum subscribers per stream, 0 meaning unlimited
     */
    public void setMaxSubscribersPerStream(int maxSubscribersPerStream) {
        limits.setMaxSubscribersPerStream(maxSubscribersPerStream);
    }

    /**
     * Sets the maximum number of subscribers from one remote address.
     *
     * @param maxSubscribersPerAddress maximum subscribers per address, 0 meaning unlimited
     */
    public void setMaxSubscribersPerAddress(int maxSubscribersPerAddress) {
        limits.setMaxSubscribersPerAddress(maxSubscribersPerAddress);
    }

    /**
     * Sets the maximum bytes queued for a subscriber before it is disconnected as a slow consumer. Applies to new subscribers.
     *
     * @param maxQueuedBytes maximum queued bytes per subscriber
     */
    public void setMaxQueuedBytes(long maxQueuedBytes) {
        limits.setMaxQueuedBytes(maxQueuedBytes);
    }

    /**
     * Sets how long a subscriber may go without receiving data before its stream is ended.
     *
     * @param idleTimeoutMs idle timeout in milliseconds
     */
    public void setIdleTimeoutMs(long idleTimeoutMs) {
        this.idleTimeoutMs = idleTimeoutMs;
    }

    /**
     * Sets the exact origins granted credentialed CORS access.
     *
     * @param origins comma or whitespace separated origins, such as {@code https://player.example.com}
     */
    public void setAllowedOrigins(String origins) {
        Set<String> parsed = new LinkedHashSet<>();
        if (origins != null) {
            for (String origin : origins.split("[,\\s]+")) {
                if (!origin.isEmpty()) {
                    parsed.add(origin);
                }
            }
        }
        allowedOrigins = Collections.unmodifiableSet(parsed);
    }

    /**
     * Returns this servlet's subscriber limits and counts.
     *
     * @return limits
     */
    MediaBunnySubscriberLimits getLimits() {
        return limits;
    }

    /**
     * Returns the streaming thread pool.
     *
     * @return executor, null before init
     */
    ThreadPoolExecutor getExecutor() {
        return executor;
    }

    private static int poolSize(int maxSubscribers) {
        return Math.max(1, maxSubscribers);
    }

    @Override
    public void onComplete(AsyncEvent event) throws IOException {
        // no-op
    }

    @Override
    public void onTimeout(AsyncEvent event) throws IOException {
        // no-op
    }

    @Override
    public void onError(AsyncEvent event) throws IOException {
        // no-op
    }

    @Override
    public void onStartAsync(AsyncEvent event) throws IOException {
        // no-op
    }

    private static String hexPrefix(byte[] data, int maxBytes) {
        if (data == null || data.length == 0) {
            return "<empty>";
        }
        int limit = Math.min(data.length, maxBytes);
        StringBuilder sb = new StringBuilder(limit * 2 + 6);
        for (int i = 0; i < limit; i++) {
            sb.append(String.format("%02x", data[i]));
        }
        if (data.length > limit) {
            sb.append("...");
        }
        return sb.toString();
    }
}
