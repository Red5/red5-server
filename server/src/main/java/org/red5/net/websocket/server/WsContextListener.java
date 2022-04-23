package org.red5.net.websocket.server;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.red5.net.websocket.WebSocketPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WsContextListener implements ServletContextListener {

    private final Logger log = LoggerFactory.getLogger(WsContextListener.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext ctx = sce.getServletContext();
        DefaultWsServerContainer sc = (DefaultWsServerContainer) WebSocketPlugin.getWsServerContainerInstance(ctx);
        log.debug("contextInitialized - path: {} sc: {}", ctx.getContextPath(), sc);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        log.debug("contextDestroyed - sce: {}", sce);
    }

}