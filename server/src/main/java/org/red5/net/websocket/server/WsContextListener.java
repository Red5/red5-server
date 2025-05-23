package org.red5.net.websocket.server;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

import org.red5.net.websocket.WebSocketPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>WsContextListener class.</p>
 *
 * @author mondain
 */
public class WsContextListener implements ServletContextListener {

    private final Logger log = LoggerFactory.getLogger(WsContextListener.class);

    /** {@inheritDoc} */
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext ctx = sce.getServletContext();
        DefaultWsServerContainer sc = (DefaultWsServerContainer) WebSocketPlugin.getWsServerContainerInstance(ctx);
        log.debug("contextInitialized - path: {} sc: {}", ctx.getContextPath(), sc);
    }

    /** {@inheritDoc} */
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        log.debug("contextDestroyed - sce: {}", sce);
    }

}
