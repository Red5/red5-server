/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.sse;

import java.io.IOException;

import org.apache.commons.lang3.RandomStringUtils;
import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.IServer;
import org.red5.server.api.scope.IGlobalScope;
import org.red5.server.api.scope.IScope;
import org.red5.server.scope.WebScope;
import org.red5.server.util.ScopeUtils;
import org.slf4j.Logger;
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
 * Servlet that handles Server-Sent Events (SSE) connections.
 * This servlet establishes and manages SSE connections following the W3C
 * Server-Sent Events specification.
 *
 * The servlet supports:
 * - Standard SSE event format with id, event, data, and retry fields
 * - Connection management and cleanup
 * - Integration with Red5 scopes
 * - Async servlet processing for long-lived connections
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class SSEServlet extends HttpServlet implements AsyncListener {

    private static final long serialVersionUID = 1L;

    private static Logger log = Red5LoggerFactory.getLogger(SSEServlet.class);

    private transient WebApplicationContext webAppCtx;

    private transient IServer server;

    private transient WebScope webScope;

    private transient SSEService sseService;

    private transient SSEManager sseManager;

    @Override
    public void init() throws ServletException {
        super.init();
        log.debug("Initializing SSE servlet");
        ServletContext ctx = getServletContext();
        log.debug("Context path: {}", ctx.getContextPath());
        // Get the web application context
        try {
            webAppCtx = WebApplicationContextUtils.getRequiredWebApplicationContext(ctx);
        } catch (IllegalStateException e) {
            log.debug("Required web application context not found, trying fallback");
            webAppCtx = (WebApplicationContext) ctx.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
        }
        if (webAppCtx != null) {
            // Get the Red5 server instance
            server = (IServer) webAppCtx.getBean("red5.server");
            // get the application scope
            webScope = (WebScope) webAppCtx.getBean("web.scope");
            sseService = (SSEService) webScope.getServiceHandler(SSEService.BEAN_NAME);
            if (sseService != null) {
                sseManager = sseService.getSseManager();
                if (sseManager == null) {
                    log.warn("SSEManager not available from SSEService");
                }
            } else {
                log.info("SSEService not available from WebScope");
            }
        } else {
            throw new ServletException("No web application context available");
        }
        log.info("SSE servlet initialized successfully");
    }

    @Override
    public void destroy() {
        log.debug("Destroying SSE servlet");
        super.destroy();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        log.debug("SSE connection request from: {} {}", req.getRemoteAddr(), req.getRequestURI());
        // Validate that this is an SSE request
        String accept = req.getHeader("Accept");
        if (accept == null || !accept.contains("text/event-stream")) {
            log.debug("Request does not accept text/event-stream, rejecting");
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "This endpoint only supports Server-Sent Events");
            return;
        }
        // Handle CORS preflight if needed
        handleCORS(req, resp);
        // Get the scope for this connection
        IScope scope = getScope(req);
        if (scope == null) {
            log.warn("No scope available for SSE connection");
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Scope not available");
            return;
        }
        // Generate unique connection ID
        String connectionId = RandomStringUtils.insecure().nextAlphabetic(11); // random 11 char string
        // Start async processing
        AsyncContext asyncContext = req.startAsync();
        asyncContext.setTimeout(0); // No timeout, managed by SSEManager
        asyncContext.addListener(this);
        // Create SSE connection
        SSEConnection sseConnection = new SSEConnection(connectionId, asyncContext, resp, scope);
        // Add to manager
        sseManager.addConnection(sseConnection);
        // Send initial connection confirmation
        sseConnection.sendEvent("connection", "connected");
        log.info("Established SSE connection: {} for scope: {}", connectionId, scope.getName());
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // Handle CORS preflight
        handleCORS(req, resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * Handles CORS headers for cross-origin requests.
     */
    private void handleCORS(HttpServletRequest req, HttpServletResponse resp) {
        String origin = req.getHeader("Origin");
        if (origin != null) {
            resp.setHeader("Access-Control-Allow-Origin", origin);
        } else {
            resp.setHeader("Access-Control-Allow-Origin", "*");
        }
        resp.setHeader("Access-Control-Allow-Credentials", "true");
        resp.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Accept, Cache-Control, Last-Event-ID");
        resp.setHeader("Access-Control-Max-Age", "3600");
    }

    /**
     * Gets the scope for the current request.
     */
    private IScope getScope(HttpServletRequest req) {
        if (webScope == null) {
            log.warn("Web scope is not available");
            return null;
        }
        IGlobalScope globalScope = server.getGlobal("default");
        if (globalScope == null) {
            log.warn("Global scope is not available");
            return null;
        }
        String path = req.getPathInfo();
        if (path == null || path.equals("/")) {
            // Default to root application
            return ScopeUtils.resolveScope(globalScope, "/live");
        } else {
            // Extract application name from path
            String[] parts = path.split("/");
            if (parts.length > 1) {
                String appName = parts[1];
                IScope appScope = ScopeUtils.resolveScope(globalScope, appName);
                if (appScope != null && ScopeUtils.isApp(appScope)) {
                    return appScope;
                } else {
                    log.warn("Application scope '{}' not found, defaulting to 'live'", appName);
                    return ScopeUtils.resolveScope(globalScope, "/live");
                }
            } else {
                log.warn("Invalid path info '{}', defaulting to 'live'", path);
                return ScopeUtils.resolveScope(globalScope, "/live");
            }
        }
    }

    // AsyncListener implementation

    @Override
    public void onComplete(AsyncEvent event) throws IOException {
        log.debug("Async context completed");
        cleanupConnection(event);
    }

    @Override
    public void onTimeout(AsyncEvent event) throws IOException {
        log.debug("Async context timed out");
        cleanupConnection(event);
    }

    @Override
    public void onError(AsyncEvent event) throws IOException {
        log.debug("Async context error: {}", event.getThrowable() != null ? event.getThrowable().getMessage() : "unknown");
        cleanupConnection(event);
    }

    @Override
    public void onStartAsync(AsyncEvent event) throws IOException {
        log.trace("Async context started");
    }

    /**
     * Cleans up connection resources when async context ends.
     */
    private void cleanupConnection(AsyncEvent event) {
        try {
            AsyncContext asyncContext = event.getAsyncContext();
            if (asyncContext != null) {
                HttpServletRequest req = (HttpServletRequest) asyncContext.getRequest();
                String connectionId = (String) req.getAttribute("sse.connectionId");
                if (connectionId != null && sseManager != null) {
                    SSEConnection connection = sseManager.removeConnection(connectionId);
                    if (connection != null) {
                        connection.close();
                        log.debug("Cleaned up SSE connection: {}", connectionId);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error during connection cleanup: {}", e.getMessage());
        }
    }

    /**
     * Gets the SSE manager for this servlet.
     *
     * @return the SSE manager
     */
    public SSEManager getSseManager() {
        return sseManager;
    }

}