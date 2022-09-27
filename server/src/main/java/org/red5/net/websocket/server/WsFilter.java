package org.red5.net.websocket.server;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.tomcat.websocket.server.Constants;
import org.red5.net.websocket.WSConstants;
import org.red5.net.websocket.WebSocketPlugin;
import org.red5.server.adapter.StatefulScopeWrappingAdapter;
import org.red5.server.api.scope.IScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

/**
 * Handles the initial HTTP connection for WebSocket connections.
 */
public class WsFilter implements Filter {

    private final Logger log = LoggerFactory.getLogger(WsFilter.class);

    private DefaultWsServerContainer sc;

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

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        String contextPath = request.getServletContext().getContextPath();
        log.debug("doFilter: {} endpoints registered: {}", contextPath, sc.areEndpointsRegistered());
        // This filter only needs to handle WebSocket upgrade requests
        if (!sc.areEndpointsRegistered() || !UpgradeUtil.isWebSocketUpgradeRequest(request, response)) {
            chain.doFilter(request, response);
            return;
        }
        // if we need the spring / app context
        ApplicationContext appCtx = (ApplicationContext) request.getServletContext().getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
        if (appCtx != null) {
            // use one level higher than MultithreadedAppAdapter since we only need the scope
            StatefulScopeWrappingAdapter app = (StatefulScopeWrappingAdapter) appCtx.getBean("web.handler");
            // applications scope
            IScope appScope = app.getScope();
            if (appScope != null) {
                log.debug("Application scope: {} ws scope: {}", appScope.getName(), appScope.getAttribute(WSConstants.WS_SCOPE));
            } else {
                log.warn("Application scope is null for {}", appCtx);
            }
        } else {
            log.warn("Application context was not found in the servlet context for {}", contextPath);
        }
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

    @Override
    public void destroy() {
        // NO-OP
    }

}