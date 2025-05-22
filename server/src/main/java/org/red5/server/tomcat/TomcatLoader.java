/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.tomcat;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.management.ManagementFactory;
import java.net.BindException;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Loader;
import org.apache.catalina.Realm;
import org.apache.catalina.Valve;
import org.apache.catalina.authenticator.jaspic.AuthConfigFactoryImpl;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.ContainerBase;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.loader.WebappLoader;
import org.apache.catalina.realm.JAASRealm;
import org.apache.catalina.realm.NullRealm;
import org.apache.catalina.realm.RealmBase;
import org.red5.net.websocket.WebSocketPlugin;
import org.red5.server.ContextLoader;
import org.red5.server.LoaderBase;
import org.red5.server.Server;
import org.red5.server.api.IApplicationContext;
import org.red5.server.api.Red5;
import org.red5.server.jmx.mxbeans.ContextLoaderMXBean;
import org.red5.server.jmx.mxbeans.LoaderMXBean;
import org.red5.server.plugin.PluginRegistry;
import org.red5.server.security.IRed5Realm;
import org.red5.server.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.XmlWebApplicationContext;

import jakarta.security.auth.message.config.AuthConfigFactory;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;

/**
 * Red5 loader for Tomcat.
 *
 * http://tomcat.apache.org/tomcat-8.5-doc/api/index.html
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
@ManagedResource(objectName = "org.red5.server:type=TomcatLoader", description = "TomcatLoader")
public class TomcatLoader extends LoaderBase implements InitializingBean, DisposableBean, LoaderMXBean {

    static {
        // set jaspic AuthConfigFactory to prevent NPEs like this:
        // java.lang.NullPointerException
        //     at org.apache.catalina.authenticator.AuthenticatorBase.getJaspicProvider(AuthenticatorBase.java:1140)
        //     at org.apache.catalina.authenticator.AuthenticatorBase.invoke(AuthenticatorBase.java:431)
        //     at org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:140)
        Security.setProperty(AuthConfigFactory.DEFAULT_FACTORY_SECURITY_PROPERTY, AuthConfigFactoryImpl.class.getName());
    }

    /**
     * Filters directory content
     */
    protected final static class DirectoryFilter implements FilenameFilter {
        /**
         * Check whether file matches filter rules
         *
         * @param dir
         *            Directory
         * @param name
         *            File name
         * @return true If file does match filter rules, false otherwise
         */
        public boolean accept(File dir, String name) {
            File f = new File(dir, name);
            if (log.isTraceEnabled()) {
                log.trace("Filtering: {} name: {} dir: {}", dir.getName(), name, f.getAbsolutePath());
            }
            // filter out all non-directories that are hidden and/or not readable
            boolean result = f.isDirectory() && f.canRead() && !f.isHidden();
            // nullify
            f = null;
            return result;
        }
    }

    private static final String CONFIG_LOCATION_PARAM = "contextConfigLocation";

    private static final String LOCATOR_FACTORY_KEY_PARAM = "locatorFactorySelector";

    private static final String CONTEXT_CLASS_PARAM = "contextClass";

    // Initialize Logging
    private static Logger log = LoggerFactory.getLogger(TomcatLoader.class);

    public static final String defaultSpringConfigLocation = "/WEB-INF/red5-*.xml";

    public static final String defaultParentContextKey = "default.context";

    static {
        log.debug("Initializing Tomcat");
    }

    /**
     * Common name for the Service and Engine components.
     */
    public String serviceEngineName = "red5Engine";

    /**
     * Base container host.
     */
    protected Host host;

    /**
     * Embedded Tomcat service (like Catalina).
     */
    protected static EmbeddedTomcat embedded;

    /**
     * Tomcat engine.
     */
    protected static Engine engine;

    /**
     * Tomcat realm.
     */
    protected Realm realm;

    /**
     * Hosts
     */
    protected List<Host> hosts;

    /**
     * Connectors
     */
    protected List<TomcatConnector> connectors;

    /**
     * Valves
     */
    protected List<Valve> valves = new ArrayList<>();

    /**
     * WebSocket feature
     */
    protected boolean websocketEnabled = true;

    /**
     * HTTPS/WSS feature
     */
    protected boolean secureEnabled;

    /**
     * Flag to indicate if we should await plugin loading
     */
    protected boolean awaitPlugins;

    /**
     * War deployer
     */
    private WarDeployer deployer;

    {
        // allow setting to true if we're running in Red5 Pro
        if (!awaitPlugins) {
            try {
                awaitPlugins = Class.forName("com.red5pro.plugin.Red5ProPlugin") != null;
                log.debug("Red5ProPlugin found, awaiting plugins");
            } catch (ClassNotFoundException e) {
                log.debug("Red5ProPlugin not found, not awaiting plugins");
            }
        }
    }

    // TODO(paul) decouple this from Spring init bean use start method
    @Override
    public void afterPropertiesSet() throws Exception {
        // if we are not awaiting plugins, start immediately
        if (awaitPlugins) {
            log.info("Awaiting plugin loading");
            Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
                Thread.currentThread().setName("TomcatLoader-delayed-start");
                try {
                    // wait for plugins to load but only up to 60 seconds
                    long startTime = System.currentTimeMillis();
                    while (System.currentTimeMillis() - startTime < 60000L) {
                        // check if plugins are ready
                        if (Red5.isPluginsReady()) {
                            log.info("Plugins are ready");
                            break;
                        }
                        log.trace("Waiting for plugins to load");
                        Thread.sleep(2000L);
                    }
                    start();
                } catch (ServletException e) {
                    log.error("Error starting Tomcat", e);
                } catch (InterruptedException e) {
                    log.error("Error waiting for plugins", e);
                }
            });
        } else {
            try {
                start();
            } catch (ServletException e) {
                log.error("Error starting Tomcat", e);
            }
        }
    }

    /**
     * Add context for path and docbase to current host.
     *
     * @param contextPath
     *            Path
     * @param docBase
     *            Document base
     * @return Catalina context (that is, web application)
     * @throws ServletException
     */
    public Context addContext(String path, String docBase) throws ServletException {
        return addContext(path, docBase, host);
    }

    /**
     * Add context for path and docbase to a host.
     *
     * @param contextPath
     *            Path
     * @param docBase
     *            Document base
     * @param host
     *            Host to add context to
     * @return Catalina context (that is, web application)
     * @throws ServletException
     */
    public Context addContext(String contextPath, String docBase, Host host) throws ServletException {
        log.debug("Add context - path: {} docbase: {}", contextPath, docBase);
        // instance a context
        org.apache.catalina.Context ctx = embedded.addWebapp(host, contextPath, docBase);
        // if websockets are enabled, filter out the tomcat WS context initializer
        if (websocketEnabled) {
            ctx.setContainerSciFilter("WsSci");
        }
        // grab the current classloader
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        // this is the expected / proper way to add the class loader
        ctx.setParentClassLoader(classLoader);
        // get the associated loader for the context
        Object ldr = ctx.getLoader();
        log.trace("Context loader (null if the context has not been started): {}", ldr);
        if (ldr == null) {
            // create a webapp loader and set it on the context
            ctx.setLoader(new WebappLoader());
        }
        log.trace("Context loader (check): {} Context classloader: {}", ctx.getLoader(), ctx.getLoader().getClassLoader());
        LoaderBase.setRed5ApplicationContext(getHostId() + contextPath, new TomcatApplicationContext(ctx));
        return ctx;
    }

    /**
     * Remove context from the current host.
     *
     * @param path
     *            Path
     */
    @Override
    public void removeContext(String path) {
        Container[] children = host.findChildren();
        for (Container c : children) {
            if (c instanceof StandardContext && c.getName().equals(path)) {
                try {
                    ((StandardContext) c).stop();
                    host.removeChild(c);
                    break;
                } catch (Exception e) {
                    log.error("Could not remove context: {}", c.getName(), e);
                }
            }
        }
        IApplicationContext ctx = LoaderBase.removeRed5ApplicationContext(path);
        if (ctx != null) {
            ctx.stop();
        } else {
            log.warn("Context could not be stopped, it was null for path: {}", path);
        }
    }

    /**
     * Initialization.
     */
    public void start() throws ServletException {
        log.info("Loading Tomcat");
        // if websockets are enabled, ensure the websocket plugin is loaded
        if (websocketEnabled) {
            checkWebsocketPlugin();
        }
        // get a reference to the current threads classloader
        final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        // root location for servlet container
        String serverRoot = System.getProperty("red5.root");
        log.info("Server root: {}", serverRoot);
        String confRoot = System.getProperty("red5.config_root");
        log.info("Config root: {}", confRoot);
        // check naming flag
        Boolean useNaming = Boolean.valueOf(System.getProperty("catalina.useNaming"));
        // create one embedded (server) and use it everywhere
        if (embedded == null) {
            embedded = new EmbeddedTomcat();
        }
        File serverRootF = new File(serverRoot);
        embedded.getServer().setCatalinaBase(serverRootF);
        embedded.getServer().setCatalinaHome(serverRootF);
        embedded.setHost(host);
        // controls if the loggers will be silenced or not
        embedded.setSilent(false);
        // get the engine
        engine = embedded.getEngine();
        // give the engine a name
        engine.setName(serviceEngineName);
        // set the default host for our engine
        engine.setDefaultHost(host.getName());
        // set the webapp folder if not already specified
        if (webappFolder == null) {
            // Use default webapps directory
            webappFolder = FileUtil.formatPath(serverRoot, "/webapps");
        }
        System.setProperty("red5.webapp.root", webappFolder);
        log.info("Application root: {}", webappFolder);
        // Root applications directory
        File appDirBase = new File(webappFolder);
        // create/start the war deployer, but don't start any expanded apps, yet
        deployer = new WarDeployer(appDirBase, true);
        // Subdirs of root apps dir
        File[] dirs = appDirBase.listFiles(new DirectoryFilter());
        // Search for additional context files
        for (File dir : dirs) {
            String dirName = '/' + dir.getName();
            // check to see if the directory is already mapped
            if (null == host.findChild(dirName)) {
                String webappContextDir = FileUtil.formatPath(appDirBase.getAbsolutePath(), dirName);
                log.debug("Webapp context directory (full path): {}", webappContextDir);
                Context ctx = null;
                if ("/root".equalsIgnoreCase(dirName)) {
                    log.trace("Adding ROOT context");
                    ctx = addContext("", webappContextDir);
                } else {
                    log.trace("Adding context from directory scan: {}", dirName);
                    ctx = addContext(dirName, webappContextDir);
                }
                log.trace("Context: {}", ctx);
                webappContextDir = null;
            }
        }
        appDirBase = null;
        dirs = null;
        // Dump context list
        if (log.isDebugEnabled()) {
            for (Container cont : host.findChildren()) {
                log.debug("Context child name: {}", cont.getName());
            }
        }
        // set a realm on the "server" if specified
        if (realm != null) {
            embedded.getEngine().setRealm(realm);
        } else {
            realm = new NullRealm();
            embedded.getEngine().setRealm(realm);
        }
        // use Tomcat jndi or not
        if (Boolean.TRUE.equals(useNaming)) {
            embedded.enableNaming();
        }
        // add the valves to the host
        for (Valve valve : valves) {
            log.debug("Adding host valve: {}", valve);
            ((StandardHost) host).addValve(valve);
        }
        // add any additional hosts
        if (hosts != null && !hosts.isEmpty()) {
            // grab current contexts from base host
            Container[] currentContexts = host.findChildren();
            log.info("Adding {} additional hosts", hosts.size());
            for (Host h : hosts) {
                log.debug("Host - name: {} appBase: {} info: {}", new Object[] { h.getName(), h.getAppBase(), h });
                //add the contexts to each host
                for (Container cont : currentContexts) {
                    Context c = (Context) cont;
                    addContext(c.getPath(), c.getDocBase(), h);
                }
                //add the host to the engine
                engine.addChild(h);
            }
        }
        try {
            // loop through connectors and apply methods / props
            boolean added = false;
            for (TomcatConnector tomcatConnector : connectors) {
                // get the connector
                Connector connector = tomcatConnector.getConnector();
                // check for secure connector and skip it if secure is not enabled
                if (!secureEnabled && connector.getSecure()) {
                    log.debug("Skipping secure connector");
                    continue;
                }
                // add new Connector to set of Connectors for embedded server, associated with Engine
                if (!added) {
                    embedded.setConnector(connector);
                    added = true;
                } else {
                    embedded.getService().addConnector(connector);
                }
                log.trace("Connector oName: {}", connector.getObjectName());
            }
        } catch (Exception ex) {
            log.warn("An exception occurred during network configuration", ex);
        }
        // create an executor for "ordered" start-up of the webapps
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            log.info("Starting Tomcat servlet engine");
            embedded.start();
            // create references for later lookup
            LoaderBase.setApplicationLoader(new TomcatApplicationLoader(embedded, host, applicationContext));
            for (final Container cont : host.findChildren()) {
                if (cont instanceof StandardContext) {
                    if (log.isDebugEnabled()) {
                        ContainerBase cb = (ContainerBase) cont;
                        log.debug("Oname - domain: {}", cb.getDomain());
                    }
                    final StandardContext ctx = (StandardContext) cont;
                    final ServletContext servletContext = ctx.getServletContext();
                    // set the hosts id
                    servletContext.setAttribute("red5.host.id", getHostId());
                    final String prefix = servletContext.getRealPath("/");
                    log.info("Context initialized: {} path: {}", servletContext.getContextPath(), prefix);
                    try {
                        ctx.resourcesStart();
                        log.debug("Context - privileged: {}, start time: {}, reloadable: {}", new Object[] { ctx.getPrivileged(), ctx.getStartTime(), ctx.getReloadable() });
                        Loader cldr = ctx.getLoader();
                        log.debug("Loader delegate: {} type: {}", cldr.getDelegate(), cldr.getClass().getName());
                        if (log.isTraceEnabled()) {
                            if (cldr instanceof WebappLoader) {
                                log.trace("WebappLoader class path: {}", ((WebappLoader) cldr).getClasspath());
                            }
                        }
                        final ClassLoader webClassLoader = cldr.getClassLoader();
                        log.debug("Webapp classloader: {}", webClassLoader);
                        // get the (spring) config file path
                        final String contextConfigLocation = servletContext.getInitParameter(CONFIG_LOCATION_PARAM) == null ? defaultSpringConfigLocation : servletContext.getInitParameter(CONFIG_LOCATION_PARAM);
                        log.debug("Spring context config location: {}", contextConfigLocation);
                        // get the (spring) parent context key
                        final String parentContextKey = servletContext.getInitParameter(LOCATOR_FACTORY_KEY_PARAM) == null ? defaultParentContextKey : servletContext.getInitParameter(LOCATOR_FACTORY_KEY_PARAM);
                        log.debug("Spring parent context key: {}", parentContextKey);
                        // set current threads classloader to the webapp classloader
                        Thread.currentThread().setContextClassLoader(webClassLoader);
                        // create a thread to speed-up application loading
                        Future<?> appStartTask = executor.submit(new Runnable() {
                            public void run() {
                                //set thread context classloader to web classloader
                                Thread.currentThread().setContextClassLoader(webClassLoader);
                                Thread.currentThread().setName("Loader:" + servletContext.getContextPath());
                                //get the web app's parent context
                                ApplicationContext parentContext = null;
                                if (applicationContext.containsBean(parentContextKey)) {
                                    parentContext = (ApplicationContext) applicationContext.getBean(parentContextKey);
                                } else {
                                    log.warn("Parent context was not found: {}", parentContextKey);
                                }
                                // create a spring web application context
                                final String contextClass = servletContext.getInitParameter(CONTEXT_CLASS_PARAM) == null ? XmlWebApplicationContext.class.getName() : servletContext.getInitParameter(CONTEXT_CLASS_PARAM);
                                // web app context (spring)
                                ConfigurableWebApplicationContext appctx = null;
                                try {
                                    Class<?> clazz = Class.forName(contextClass, true, webClassLoader);
                                    appctx = (ConfigurableWebApplicationContext) clazz.getDeclaredConstructor().newInstance();
                                    // set the root webapp ctx attr on the each servlet context so spring can find it later
                                    servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, appctx);
                                    appctx.setConfigLocations(new String[] { contextConfigLocation });
                                    appctx.setServletContext(servletContext);
                                    // set parent context or use current app context
                                    if (parentContext != null) {
                                        appctx.setParent(parentContext);
                                    } else {
                                        appctx.setParent(applicationContext);
                                    }
                                    // refresh the factory
                                    log.trace("Classloader prior to refresh: {}", appctx.getClassLoader());
                                    appctx.refresh();
                                    if (log.isDebugEnabled()) {
                                        log.debug("Red5 app is active: {} running: {}", appctx.isActive(), appctx.isRunning());
                                    }
                                    // set a realm for the webapp if one is specified
                                    if (appctx.containsBean("realm")) {
                                        log.debug("Realm specified in context configuration");
                                        Realm contextRealm = (Realm) appctx.getBean("realm");
                                        if (contextRealm != null) {
                                            log.debug("Realm class: {}", contextRealm.getClass().getName());
                                            contextRealm.setContainer(cont);
                                            ctx.setRealm(contextRealm);
                                            // when a realm implements our red5 realm, add the app and servlet contexts
                                            if (contextRealm instanceof IRed5Realm) {
                                                ((IRed5Realm) contextRealm).setApplicationContext(appctx);
                                                ((IRed5Realm) contextRealm).setServletContext(servletContext);
                                            }
                                            // set the system property to allow the config to be located
                                            if (contextRealm instanceof JAASRealm) {
                                                log.debug("Realm is JAAS type");
                                                // this may interfere with other concurrently loaded jaas realms
                                                System.setProperty("java.security.auth.login.config", prefix + "WEB-INF/jaas.config");
                                            }
                                            log.debug("Realm info: {} path: {}", contextRealm, ((RealmBase) contextRealm).getRealmPath());
                                        }
                                    }
                                    appctx.start();
                                } catch (Throwable e) {
                                    throw new RuntimeException("Failed to load webapplication context class", e);
                                }
                            }
                        });
                        // see if everything completed
                        log.debug("Context: {} done: {}", servletContext.getContextPath(), appStartTask.isDone());
                    } catch (Throwable t) {
                        log.error("Error setting up context: {} due to: {}", servletContext.getContextPath(), t.getMessage());
                        t.printStackTrace();
                    } finally {
                        //reset the classloader
                        Thread.currentThread().setContextClassLoader(originalClassLoader);
                    }
                }
            }
            // if everything is ok at this point then call the rtmpt and rtmps beans so they will init
            if (applicationContext.containsBean("rtmpt.server")) {
                log.debug("Initializing RTMPT");
                applicationContext.getBean("rtmpt.server");
                log.debug("Finished initializing RTMPT");
            } else {
                log.debug("Dedicated RTMPT server configuration was not specified");
            }
            if (applicationContext.containsBean("rtmps.server")) {
                log.debug("Initializing RTMPS");
                applicationContext.getBean("rtmps.server");
                log.debug("Finished initializing RTMPS");
            } else {
                log.debug("Dedicated RTMPS server configuration was not specified");
            }
        } catch (Exception e) {
            if (e instanceof BindException || e.getMessage().indexOf("BindException") != -1) {
                log.error("Error loading tomcat, unable to bind connector. You may not have permission to use the selected port", e);
            } else {
                log.error("Error loading tomcat", e);
            }
        } finally {
            // finish-up with the executor
            executor.shutdown();
            // do our jmx stuff
            registerJMX();
        }
        log.debug("Tomcat load completed");
    }

    private void checkWebsocketPlugin() {
        // if websockets are enabled, ensure the websocket plugin is loaded
        if (PluginRegistry.getPlugin(WebSocketPlugin.NAME) == null) {
            // get common context
            ApplicationContext common = (ApplicationContext) applicationContext.getBean("red5.common");
            Server server = (Server) common.getBean("red5.server");
            // instance the plugin
            WebSocketPlugin plugin = new WebSocketPlugin();
            plugin.setApplicationContext(applicationContext);
            plugin.setServer(server);
            // register it
            PluginRegistry.register(plugin);
            // start it
            try {
                plugin.doStart();
            } catch (Exception e) {
                log.warn("WebSocket plugin start, failed", e);
            }
        }
    }

    /**
     * Starts a web application and its red5 (spring) component. This is basically a stripped down version of start().
     *
     * @return true on success
     * @throws ServletException
     */
    @SuppressWarnings("null")
    public boolean startWebApplication(String applicationName) throws ServletException {
        log.info("Starting Tomcat - Web application");
        boolean result = false;
        //get a reference to the current threads classloader
        final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        log.debug("Webapp root: {}", webappFolder);
        if (webappFolder == null) {
            // Use default webapps directory
            webappFolder = System.getProperty("red5.root") + "/webapps";
        }
        System.setProperty("red5.webapp.root", webappFolder);
        log.info("Application root: {}", webappFolder);
        // application directory
        String contextName = '/' + applicationName;
        Container ctx = null;
        // Root applications directory
        File appDirBase = new File(webappFolder);
        // check if the context already exists for the host
        if ((ctx = host.findChild(contextName)) == null) {
            log.debug("Context did not exist in host");
            String webappContextDir = FileUtil.formatPath(appDirBase.getAbsolutePath(), applicationName);
            log.debug("Webapp context directory (full path): {}", webappContextDir);
            // set the newly created context as the current container
            ctx = addContext(contextName, webappContextDir);
        } else {
            log.debug("Context already exists in host");
        }
        final ServletContext servletContext = ((Context) ctx).getServletContext();
        log.debug("Context initialized: {}", servletContext.getContextPath());
        String prefix = servletContext.getRealPath("/");
        log.debug("Path: {}", prefix);
        try {
            Loader cldr = ((Context) ctx).getLoader();
            log.debug("Loader delegate: {} type: {}", cldr.getDelegate(), cldr.getClass().getName());
            if (cldr instanceof WebappLoader) {
                log.debug("WebappLoader class path: {}", ((WebappLoader) cldr).getClasspath());
            }
            final ClassLoader webClassLoader = cldr.getClassLoader();
            log.debug("Webapp classloader: {}", webClassLoader);
            // get the (spring) config file path
            final String contextConfigLocation = servletContext.getInitParameter("contextConfigLocation") == null ? defaultSpringConfigLocation : servletContext.getInitParameter("contextConfigLocation");
            log.debug("Spring context config location: {}", contextConfigLocation);
            // get the (spring) parent context key
            final String parentContextKey = servletContext.getInitParameter("parentContextKey") == null ? defaultParentContextKey : servletContext.getInitParameter("parentContextKey");
            log.debug("Spring parent context key: {}", parentContextKey);
            //set current threads classloader to the webapp classloader
            Thread.currentThread().setContextClassLoader(webClassLoader);
            //create a thread to speed-up application loading
            Thread thread = new Thread("Launcher:" + servletContext.getContextPath()) {
                public void run() {
                    //set current threads classloader to the webapp classloader
                    Thread.currentThread().setContextClassLoader(webClassLoader);
                    // create a spring web application context
                    XmlWebApplicationContext appctx = new XmlWebApplicationContext();
                    appctx.setClassLoader(webClassLoader);
                    appctx.setConfigLocations(new String[] { contextConfigLocation });
                    // check for red5 context bean
                    ApplicationContext parentAppCtx = null;
                    if (applicationContext.containsBean(defaultParentContextKey)) {
                        parentAppCtx = (ApplicationContext) applicationContext.getBean(defaultParentContextKey);
                        appctx.setParent(parentAppCtx);
                    } else {
                        log.warn("{} bean was not found in context: {}", defaultParentContextKey, applicationContext.getDisplayName());
                        // lookup context loader and attempt to get what we need from it
                        if (applicationContext.containsBean("context.loader")) {
                            ContextLoader contextLoader = (ContextLoader) applicationContext.getBean("context.loader");
                            parentAppCtx = contextLoader.getContext(defaultParentContextKey);
                            appctx.setParent(parentAppCtx);
                        } else {
                            log.debug("Context loader was not found, trying JMX");
                            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
                            // get the ContextLoader from jmx
                            ContextLoaderMXBean proxy = null;
                            ObjectName oName = null;
                            try {
                                oName = new ObjectName("org.red5.server:name=contextLoader,type=ContextLoader");
                                if (mbs.isRegistered(oName)) {
                                    proxy = JMX.newMXBeanProxy(mbs, oName, ContextLoaderMXBean.class, true);
                                    log.debug("Context loader was found");
                                    proxy.setParentContext(defaultParentContextKey, appctx.getId());
                                } else {
                                    log.warn("Context loader was not found");
                                }
                            } catch (Exception e) {
                                log.warn("Exception looking up ContextLoader", e);
                            }
                        }
                    }
                    if (log.isDebugEnabled()) {
                        if (appctx.getParent() != null) {
                            log.debug("Parent application context: {}", appctx.getParent().getDisplayName());
                        }
                    }
                    // add the servlet context
                    appctx.setServletContext(servletContext);
                    // set the root webapp ctx attr on the each servlet context so spring can find it later
                    servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, appctx);
                    appctx.refresh();
                }
            };
            thread.setDaemon(true);
            thread.start();
            result = true;
        } catch (Throwable t) {
            log.error("Error setting up context: {} due to: {}", servletContext.getContextPath(), t.getMessage());
            t.printStackTrace();
        } finally {
            //reset the classloader
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
        return result;
    }

    /**
     * Set base host.
     *
     * @param baseHost
     *            Base host
     */
    public void setBaseHost(Host baseHost) {
        log.debug("setBaseHost: {}", baseHost);
        this.host = baseHost;
    }

    /**
     * Get base host.
     *
     * @return Base host
     */
    public Host getBaseHost() {
        return host;
    }

    /**
     * Return Tomcat engine.
     *
     * @return Tomcat engine
     */
    public Engine getEngine() {
        return engine;
    }

    /**
     * Set connectors.
     *
     * @param connectors
     */
    public void setConnectors(List<TomcatConnector> connectors) {
        log.debug("setConnectors: {}", connectors.size());
        this.connectors = connectors;
    }

    /**
     * Set additional contexts.
     *
     * @param contexts
     *            Map of contexts
     * @throws ServletException
     */
    public void setContexts(Map<String, String> contexts) throws ServletException {
        log.debug("setContexts: {}", contexts.size());
        for (Map.Entry<String, String> entry : contexts.entrySet()) {
            host.addChild(embedded.addWebapp(entry.getKey(), webappFolder + entry.getValue()));
        }
    }

    /**
     * Setter for embedded object.
     *
     * @param embedded
     *            Embedded object
     */
    public void setEmbedded(EmbeddedTomcat embedded) {
        log.info("Setting embedded: {}", embedded.getClass().getName());
        TomcatLoader.embedded = embedded;
    }

    /**
     * Getter for embedded object.
     *
     * @return Embedded object
     */
    public EmbeddedTomcat getEmbedded() {
        return embedded;
    }

    /**
     * Get the host.
     *
     * @return host
     */
    public Host getHost() {
        return host;
    }

    /**
     * Set the host.
     *
     * @param host
     *            Host
     */
    public void setHost(Host host) {
        log.debug("setHost");
        this.host = host;
    }

    /**
     * Set additional hosts.
     *
     * @param hosts
     *            List of hosts added to engine
     */
    public void setHosts(List<Host> hosts) {
        log.debug("setHosts: {}", hosts.size());
        this.hosts = hosts;
    }

    /**
     * Setter for realm.
     *
     * @param realm
     *            Realm
     */
    public void setRealm(Realm realm) {
        log.info("Setting realm: {}", realm.getClass().getName());
        this.realm = realm;
    }

    /**
     * Getter for realm.
     *
     * @return Realm
     */
    public Realm getRealm() {
        return realm;
    }

    /**
     * Set additional valves.
     *
     * @param valves
     *            List of valves
     */
    public void setValves(List<Valve> valves) {
        log.debug("setValves: {}", valves.size());
        this.valves.addAll(valves);
    }

    /**
     * Returns enabled state of websocket support.
     *
     * @return true if enabled and false otherwise
     */
    public boolean isWebsocketEnabled() {
        return websocketEnabled;
    }

    /**
     * Set websocket feature enabled / disabled.
     *
     * @param websocketEnabled
     */
    public void setWebsocketEnabled(boolean websocketEnabled) {
        this.websocketEnabled = websocketEnabled;
    }

    /**
     * Returns enabled state of secure support.
     *
     * @return true if enabled and false otherwise
     */
    public boolean isSecureEnabled() {
        return secureEnabled;
    }

    /**
     * Set secure feature enabled / disabled.
     *
     * @param secureEnabled
     */
    public void setSecureEnabled(boolean secureEnabled) {
        this.secureEnabled = secureEnabled;
    }

    /**
     * Returns await plugin loading state.
     *
     * @return true if awaiting plugin loading and false otherwise
     */
    public void setAwaitPlugins(boolean awaitPlugins) {
        this.awaitPlugins = awaitPlugins;
    }

    /**
     * Returns a semi-unique id for this host based on its host values
     *
     * @return host id
     */
    protected String getHostId() {
        String hostId = host.getName();
        log.debug("Host id: {}", hostId);
        return hostId;
    }

    protected void registerJMX() {
        // register with jmx
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            ObjectName oName = new ObjectName("org.red5.server:type=TomcatLoader");
            // check for existing registration before registering
            if (!mbs.isRegistered(oName)) {
                mbs.registerMBean(this, oName);
            } else {
                log.debug("ContextLoader is already registered in JMX");
            }
        } catch (Exception e) {
            log.warn("Error on jmx registration", e);
        }
    }

    protected void unregisterJMX() {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            ObjectName oName = new ObjectName("org.red5.server:type=TomcatLoader");
            mbs.unregisterMBean(oName);
        } catch (Exception e) {
            log.warn("Exception unregistering", e);
        }
    }

    /**
     * Shut server down.
     */
    @Override
    public void destroy() throws Exception {
        log.info("Shutting down Tomcat context");
        if (deployer != null) {
            deployer.stop();
            deployer = null;
        }
        // run through the applications and ensure that spring is told to commence shutdown / disposal
        AbstractApplicationContext absCtx = (AbstractApplicationContext) LoaderBase.getApplicationContext();
        if (absCtx != null) {
            log.debug("Using loader base application context for shutdown");
            // get all the app (web) contexts and shut them down first
            Map<String, IApplicationContext> contexts = LoaderBase.getRed5ApplicationContexts();
            if (contexts.isEmpty()) {
                log.info("No contexts were found to shutdown");
            }
            for (Map.Entry<String, IApplicationContext> entry : contexts.entrySet()) {
                // stop the context
                log.debug("Calling stop on context: {}", entry.getKey());
                entry.getValue().stop();
            }
            if (absCtx.isActive()) {
                log.debug("Closing application context");
                absCtx.close();
            }
        } else {
            log.error("Error getting Spring bean factory for shutdown");
        }
        // no need to stop the websocket plugin if it is not registered
        if (PluginRegistry.isRegistered(WebSocketPlugin.NAME)) {
            // stop websocket
            try {
                WebSocketPlugin plugin = (WebSocketPlugin) PluginRegistry.getPlugin(WebSocketPlugin.NAME);
                if (plugin != null) {
                    plugin.doStop();
                }
            } catch (Exception e) {
                log.warn("WebSocket plugin stop, failed", e);
            }
        }
        try {
            // stop tomcat
            embedded.stop();
        } catch (Exception e) {
            log.warn("Tomcat could not be stopped", e);
            throw new RuntimeException("Tomcat could not be stopped");
        }
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "TomcatLoader [serviceEngineName=" + serviceEngineName + "]";
    }

}
