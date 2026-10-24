package org.red5.net.websocket.server;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.tomcat.websocket.server.Constants;
import org.red5.net.websocket.WebSocketPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the initial HTTP connection for WebSocket connections.
 *
 * @author mondain
 */
public class WsFilter implements Filter {

    private final Logger log = LoggerFactory.getLogger(WsFilter.class);

    private DefaultWsServerContainer sc;

    /** {@inheritDoc} */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        ServletContext ctx = filterConfig.getServletContext();
        // attempt to get the server container from the context first
        sc = (DefaultWsServerContainer) ctx.getAttribute(Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE);
        if (sc == null) {
            // look in the ws plugin
            sc = (DefaultWsServerContainer) WebSocketPlugin.getWsServerContainerInstance(ctx);
        }
        log.debug("init completed - sc: {}", sc);
    }

    /** {@inheritDoc} */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        String contextPath = request.getServletContext().getContextPath();
        log.debug("doFilter: {} endpoints registered: {}", contextPath, sc.areEndpointsRegistered());
        // This filter only needs to handle WebSocket upgrade requests
        if (!sc.areEndpointsRegistered() || !UpgradeUtil.isWebSocketUpgradeRequest(request, response)) {
            chain.doFilter(request, response);
            return;
        }
        // the endpoint configurator is bound to this application's scope during the upgrade, see UpgradeUtil.doUpgrade
        // HTTP request with an upgrade header for WebSocket present
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        // Check to see if this WebSocket implementation has a matching mapping
        String requestUri = req.getRequestURI();
        String queryString = req.getQueryString();
        String pathInfo = req.getPathInfo();
        log.debug("Request uri: {} path info: {} query string: {}", requestUri, pathInfo, queryString);
        WsMappingResult mappingResult = sc.findMapping(contextPath);
        log.debug("WsMappingResult: {} for contextPath: {}", mappingResult, contextPath);
        if (mappingResult == null) {
            // No endpoint registered for the requested path. Let the application handle it (it might redirect or forward for example)
            chain.doFilter(request, response);
            return;
        }
        UpgradeUtil.doUpgrade(sc, req, resp, mappingResult.getConfig(), mappingResult.getPathParams());
    }

    /** {@inheritDoc} */
    @Override
    public void destroy() {
        // the filter is destroyed with its context, release the container so a redeployed context does not reuse it
        if (sc != null) {
            WebSocketPlugin.removeWsServerContainer(sc.getServletContext());
            sc = null;
        }
    }

}
