package org.red5.server.net.mediabunny;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.red5.server.api.IServer;
import org.red5.server.api.scope.IGlobalScope;
import org.red5.server.api.scope.IScope;
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
 */
public class MediaBunnyServlet extends HttpServlet implements AsyncListener {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(MediaBunnyServlet.class);

    private transient WebApplicationContext webAppCtx;

    private transient IServer server;

    private transient WebScope webScope;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private static final int INIT_PREFIX_BYTES = 32;

    @SuppressWarnings("null")
    @Override
    public void init() throws ServletException {
        super.init();
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
    public void destroy() {
        executor.shutdownNow();
        super.destroy();
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
        MediaBunnyStreamRegistry.StreamSubscription subscription;
        try {
            subscription = MediaBunnyStreamRegistry.getInstance().subscribe(scope, streamName);
        } catch (Exception e) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Stream not found: " + streamName);
            return;
        }
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("video/mp4");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("Connection", "keep-alive");

        AsyncContext asyncContext = req.startAsync();
        asyncContext.setTimeout(0);
        asyncContext.addListener(this);

        executor.execute(() -> streamQueue(asyncContext, subscription));
    }

    private void streamQueue(AsyncContext asyncContext, MediaBunnyStreamRegistry.StreamSubscription subscription) {
        boolean loggedFirstChunk = false;
        boolean loggedSecondChunk = false;
        int chunkCount = 0;
        try (OutputStream out = asyncContext.getResponse().getOutputStream()) {
            BlockingQueue<byte[]> queue = subscription.getQueue();
            while (true) {
                byte[] chunk = queue.take();
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

    private void handleCORS(HttpServletRequest req, HttpServletResponse resp) {
        String origin = req.getHeader("Origin");
        if (origin != null) {
            resp.setHeader("Access-Control-Allow-Origin", origin);
        } else {
            resp.setHeader("Access-Control-Allow-Origin", "*");
        }
        resp.setHeader("Access-Control-Allow-Credentials", "true");
        resp.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Accept, Content-Type");
        resp.setHeader("Access-Control-Max-Age", "3600");
        // Ensure web application context is available
        if (webAppCtx == null) {
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
    }

    private IScope getScope(HttpServletRequest req) {
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
