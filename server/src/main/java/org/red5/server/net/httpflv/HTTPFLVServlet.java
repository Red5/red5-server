/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.httpflv;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.RandomStringUtils;
import org.red5.server.api.IServer;
import org.red5.server.api.scope.IBroadcastScope;
import org.red5.server.api.scope.IGlobalScope;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IBroadcastStream;
import org.red5.server.api.stream.IClientBroadcastStream;
import org.red5.server.scope.WebScope;
import org.red5.server.stream.IProviderService;
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
 * Servlet that handles HTTP-FLV streaming requests.
 * This servlet enables playback of live RTMP streams via HTTP using the FLV container format,
 * compatible with flv.js and similar HTML5 FLV players.
 *
 * URL format: http://server:port/app/stream.flv
 * Example: http://localhost:5080/live/mystream.flv
 *
 * The servlet supports:
 * - Live stream subscription via HTTP GET
 * - Chunked transfer encoding for continuous streaming
 * - CORS headers for cross-origin playback
 * - Async servlet processing for long-lived connections
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class HTTPFLVServlet extends HttpServlet implements AsyncListener {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(HTTPFLVServlet.class);

    /** Active connections map */
    private final Map<String, HTTPFLVConnection> connections = new ConcurrentHashMap<>();

    /** Spring web application context */
    private transient WebApplicationContext webAppCtx;

    /** Red5 server instance */
    private transient IServer server;

    /** Web scope for this application */
    private transient WebScope webScope;

    /** HTTP-FLV service for stream configuration management */
    private transient HTTPFLVService httpFlvService;

    /** HTTP-FLV manager for connection lifecycle */
    private transient HTTPFLVManager httpFlvManager;

    @Override
    public void init() throws ServletException {
        super.init();
        log.debug("Initializing HTTP-FLV servlet");
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
            // Get the application scope
            webScope = (WebScope) webAppCtx.getBean("web.scope");
            // Try to get the HTTP-FLV service (optional)
            try {
                httpFlvService = (HTTPFLVService) webAppCtx.getBean(HTTPFLVService.BEAN_NAME);
                log.debug("HTTP-FLV service found and initialized");
            } catch (Exception e) {
                log.debug("HTTP-FLV service not configured, using default behavior");
            }
            // Try to get the HTTP-FLV manager (optional)
            try {
                httpFlvManager = (HTTPFLVManager) webAppCtx.getBean(HTTPFLVManager.BEAN_NAME);
                log.debug("HTTP-FLV manager found and initialized");
            } catch (Exception e) {
                log.debug("HTTP-FLV manager not configured, using default behavior");
            }
        } else {
            throw new ServletException("No web application context available");
        }
        log.info("HTTP-FLV servlet initialized successfully");
    }

    @Override
    public void destroy() {
        log.debug("Destroying HTTP-FLV servlet");
        // Close all active connections
        if (httpFlvService != null) {
            httpFlvService.closeAllConnections();
        }
        connections.values().forEach(HTTPFLVConnection::close);
        connections.clear();
        super.destroy();
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // Handle CORS preflight
        handleCORS(req, resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        log.debug("HTTP-FLV request from: {} {}", req.getRemoteAddr(), req.getRequestURI());
        // Handle CORS
        handleCORS(req, resp);
        // Parse stream name from request URI
        String streamName = parseStreamName(req);
        if (streamName == null || streamName.isEmpty()) {
            log.debug("No stream name specified in request");
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Stream name is required");
            return;
        }
        log.debug("Requested stream: {}", streamName);
        // Get the scope for this connection
        IScope scope = getScope(req);
        if (scope == null) {
            log.warn("No scope available for HTTP-FLV connection");
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Scope not available");
            return;
        }
        // Find the broadcast stream
        IBroadcastStream broadcastStream = getBroadcastStream(scope, streamName);
        if (broadcastStream == null) {
            log.debug("Stream not found: {} in scope: {}", streamName, scope.getName());
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Stream not found: " + streamName);
            return;
        }
        // Check if service is enabled and if connection limit is reached
        if (httpFlvService != null && !httpFlvService.isEnabled()) {
            log.debug("HTTP-FLV service is disabled");
            resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "HTTP-FLV service is disabled");
            return;
        }
        // Generate unique connection ID
        String connectionId = RandomStringUtils.insecure().nextAlphanumeric(12);
        // Start async processing
        AsyncContext asyncContext = req.startAsync();
        asyncContext.setTimeout(0); // No timeout, connection managed by us
        asyncContext.addListener(this);
        // Store connection ID in request for cleanup
        req.setAttribute("httpflv.connectionId", connectionId);
        // Create HTTP-FLV connection
        HTTPFLVConnection connection = new HTTPFLVConnection(connectionId, asyncContext, resp, scope, streamName);
        // Initialize connection (sends FLV header)
        if (!connection.initialize()) {
            log.warn("Failed to initialize HTTP-FLV connection: {}", connectionId);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to initialize stream");
            return;
        }
        // Register with service if available
        if (httpFlvService != null) {
            if (!httpFlvService.registerConnection(connection)) {
                log.warn("Failed to register connection with service: {}", connectionId);
                connection.close();
                resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Connection limit reached");
                return;
            }
            // Send initial stream configuration (metadata, codec config, GOP cache)
            StreamConfiguration streamConfig = httpFlvService.getStreamConfiguration(streamName);
            streamConfig.updateFromStream(broadcastStream);
            connection.sendInitialData(streamConfig);
        }
        // Register with manager for lifecycle management
        if (httpFlvManager != null) {
            httpFlvManager.registerConnection(connection);
        }
        // Store connection in local map as fallback
        connections.put(connectionId, connection);
        // Subscribe to broadcast stream
        connection.subscribe(broadcastStream);
        log.info("Established HTTP-FLV connection: {} for stream: {} in scope: {}", connectionId, streamName, scope.getName());
    }

    /**
     * Parses the stream name from the request URI.
     * Expected format: /app/stream.flv or /app/path/stream.flv
     *
     * @param req the HTTP request
     * @return the stream name without .flv extension, or null if not found
     */
    private String parseStreamName(HttpServletRequest req) {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.isEmpty() || pathInfo.equals("/")) {
            // Try to get from request URI
            String requestURI = req.getRequestURI();
            String contextPath = req.getContextPath();
            String servletPath = req.getServletPath();
            // Remove context path and servlet path to get the stream part
            String streamPart = requestURI;
            if (contextPath != null && !contextPath.isEmpty()) {
                streamPart = streamPart.substring(contextPath.length());
            }
            if (servletPath != null && !servletPath.isEmpty()) {
                streamPart = streamPart.substring(servletPath.length());
            }
            pathInfo = streamPart;
        }
        if (pathInfo == null || pathInfo.isEmpty() || pathInfo.equals("/")) {
            return null;
        }
        // Remove leading slash
        if (pathInfo.startsWith("/")) {
            pathInfo = pathInfo.substring(1);
        }
        // Remove .flv extension if present
        if (pathInfo.toLowerCase().endsWith(".flv")) {
            pathInfo = pathInfo.substring(0, pathInfo.length() - 4);
        }
        return pathInfo;
    }

    /**
     * Gets the broadcast stream for the given scope and stream name.
     *
     * @param scope the scope to search in
     * @param streamName the stream name
     * @return the broadcast stream, or null if not found
     */
    private IBroadcastStream getBroadcastStream(IScope scope, String streamName) {
        // First try to get the broadcast scope directly
        IBroadcastScope broadcastScope = scope.getBroadcastScope(streamName);
        if (broadcastScope != null) {
            IClientBroadcastStream clientStream = broadcastScope.getClientBroadcastStream();
            if (clientStream != null) {
                log.debug("Found broadcast stream: {} via broadcast scope", streamName);
                return clientStream;
            }
        }
        // Try via provider service to check if stream exists
        IProviderService providerService = (IProviderService) scope.getContext().getBean(IProviderService.BEAN_NAME);
        if (providerService != null) {
            IProviderService.INPUT_TYPE inputType = providerService.lookupProviderInput(scope, streamName, 0);
            if (inputType == IProviderService.INPUT_TYPE.LIVE) {
                // Re-check broadcast scope after confirming stream is live
                broadcastScope = scope.getBroadcastScope(streamName);
                if (broadcastScope != null) {
                    IClientBroadcastStream clientStream = broadcastScope.getClientBroadcastStream();
                    if (clientStream != null) {
                        log.debug("Found broadcast stream: {} via provider service lookup", streamName);
                        return clientStream;
                    }
                }
            } else if (inputType == IProviderService.INPUT_TYPE.LIVE_WAIT) {
                log.debug("Stream {} not yet publishing, input type: {}", streamName, inputType);
            } else {
                log.debug("Stream {} not found, input type: {}", streamName, inputType);
            }
        }
        return null;
    }

    /**
     * Handles CORS headers for cross-origin requests.
     *
     * @param req the HTTP request
     * @param resp the HTTP response
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
        resp.setHeader("Access-Control-Allow-Headers", "Origin, Accept, Content-Type, Range");
        resp.setHeader("Access-Control-Expose-Headers", "Content-Length, Content-Range");
        resp.setHeader("Access-Control-Max-Age", "3600");
    }

    /**
     * Gets the scope for the current request.
     *
     * @param req the HTTP request
     * @return the scope, or null if not available
     */
    private IScope getScope(HttpServletRequest req) {
        if (webScope != null) {
            return webScope;
        }
        if (server == null) {
            log.warn("Server is not available");
            return null;
        }
        IGlobalScope globalScope = server.getGlobal("default");
        if (globalScope == null) {
            log.warn("Global scope is not available");
            return null;
        }
        String contextPath = req.getContextPath();
        log.debug("Request context path: {}", contextPath);
        if (contextPath == null || contextPath.equals("/") || contextPath.isEmpty()) {
            // Default to live application
            return ScopeUtils.resolveScope(globalScope, "/live");
        }
        // Extract application name from context path
        String appName = contextPath;
        if (appName.startsWith("/")) {
            appName = appName.substring(1);
        }
        IScope appScope = ScopeUtils.resolveScope(globalScope, "/" + appName);
        if (appScope != null) {
            return appScope;
        }
        log.warn("Application scope '{}' not found, defaulting to 'live'", appName);
        return ScopeUtils.resolveScope(globalScope, "/live");
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
     *
     * @param event the async event
     */
    private void cleanupConnection(AsyncEvent event) {
        try {
            AsyncContext asyncContext = event.getAsyncContext();
            if (asyncContext != null) {
                HttpServletRequest req = (HttpServletRequest) asyncContext.getRequest();
                String connectionId = (String) req.getAttribute("httpflv.connectionId");
                if (connectionId != null) {
                    // Unregister from service if available
                    if (httpFlvService != null) {
                        httpFlvService.unregisterConnection(connectionId);
                    }
                    // Unregister from manager if available
                    if (httpFlvManager != null) {
                        httpFlvManager.unregisterConnection(connectionId);
                    }
                    HTTPFLVConnection connection = connections.remove(connectionId);
                    if (connection != null) {
                        connection.close();
                        log.debug("Cleaned up HTTP-FLV connection: {}", connectionId);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error during connection cleanup: {}", e.getMessage());
        }
    }

    /**
     * Gets the count of active connections.
     *
     * @return active connection count
     */
    public int getConnectionCount() {
        return connections.size();
    }

    /**
     * Gets a connection by ID.
     *
     * @param connectionId the connection ID
     * @return the connection, or null if not found
     */
    public HTTPFLVConnection getConnection(String connectionId) {
        return connections.get(connectionId);
    }

    /**
     * Removes a connection by ID.
     *
     * @param connectionId the connection ID
     * @return the removed connection, or null if not found
     */
    public HTTPFLVConnection removeConnection(String connectionId) {
        return connections.remove(connectionId);
    }

}
