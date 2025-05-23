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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.red5.server.jmx.mxbeans.LoaderMXBean;
import org.red5.server.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import jakarta.servlet.ServletException;

/**
 * This service provides the means to auto-deploy a war.
 *
 * Note: This class has deprecated use of Spring and has been refactored to be instantiated and controlled via the
 * TomcatLoader, it is no longer meant to be used in the jee-container.xml as a bean.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public final class WarDeployer implements ApplicationContextAware {

    private Logger log = LoggerFactory.getLogger(WarDeployer.class);

    //that wars are currently being installed
    private static AtomicBoolean deploying = new AtomicBoolean(false);

    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private ScheduledFuture<?> future;

    /**
     * How often to check for new war files
     */
    private int checkInterval = 600000; //ten minutes

    /**
     * Deployment directory
     */
    private final File webappsDirectory;

    {
        log.info("War deployer service created");
    }

    @Deprecated(since = "2.0.9", forRemoval = true)
    /**
     * <p>Constructor for WarDeployer.</p>
     */
    public WarDeployer() {
        log.warn("Use via constructor or as a Spring bean is deprecated");
        webappsDirectory = new File("webapps");
    }

    /**
     * <p>Constructor for WarDeployer.</p>
     *
     * @param webappsDirectory a {@link java.io.File} object
     */
    public WarDeployer(File webappsDirectory) {
        this(webappsDirectory, false);
    }

    /**
     * <p>Constructor for WarDeployer.</p>
     *
     * @param webappsDirectory a {@link java.io.File} object
     * @param expandWars a boolean
     */
    public WarDeployer(File webappsDirectory, boolean expandWars) {
        log.info("Starting WarDeployer - webapps directory: {}", webappsDirectory.getAbsolutePath());
        // set the webapp folder
        this.webappsDirectory = webappsDirectory;
        // expand wars if so requested
        if (expandWars) {
            log.debug("Deploying wars, not starting applications");
            deploy(false);
        }
        // create the job and schedule it
        future = (ScheduledFuture<?>) scheduler.scheduleAtFixedRate(() -> {
            log.debug("Starting scheduled deployment of wars");
            deploy(true);
        }, 60000L, checkInterval, TimeUnit.MILLISECONDS);
    }

    private void deploy(boolean startApplication) {
        log.info("Deploy wars {} app start", (startApplication ? "with" : "without"));
        if (deploying.compareAndSet(false, true)) {
            // short name
            String application = null;
            // file name
            String applicationWarName = null;
            // get a list of wars
            File[] files = webappsDirectory.listFiles(new DirectoryFilter());
            for (File f : files) {
                // get the war name
                applicationWarName = f.getName();
                int dashIndex = applicationWarName.indexOf('-');
                if (dashIndex != -1) {
                    // strip everything except the applications name
                    application = applicationWarName.substring(0, dashIndex);
                } else {
                    // grab every char up to the last '.'
                    application = applicationWarName.substring(0, applicationWarName.lastIndexOf('.'));
                }
                log.debug("Application name: {}", application);
                // setup context
                String contextPath = '/' + application;
                String contextDir = webappsDirectory.getAbsolutePath() + contextPath;
                log.debug("Web context: {} context directory: {}", contextPath, contextDir);
                // verify this is a unique app
                File appDir = new File(webappsDirectory, application);
                if (appDir.exists()) {
                    if (appDir.isDirectory()) {
                        log.debug("Application directory exists");
                    } else {
                        log.warn("Application destination is not a directory");
                    }
                    log.info("Application {} already installed, please un-install before attempting another install", application);
                } else {
                    log.debug("Unwaring and starting...");
                    // un-archive it to app dir
                    FileUtil.unzip(webappsDirectory.getAbsolutePath() + '/' + applicationWarName, contextDir);
                    // load and start the context
                    if (startApplication) {
                        // get the webapp loader from jmx
                        LoaderMXBean loader = getLoader();
                        if (loader != null) {
                            try {
                                loader.startWebApplication(application);
                            } catch (ServletException e) {
                                log.error("Unexpected error while staring web application", e);
                            }
                        }
                    }
                    // remove the war file
                    File warFile = new File(webappsDirectory, applicationWarName);
                    if (warFile.delete()) {
                        log.debug("{} was deleted", warFile.getName());
                    } else {
                        log.debug("{} was not deleted", warFile.getName());
                        warFile.deleteOnExit();
                    }
                    warFile = null;
                }
                appDir = null;
            }
            // reset sentinel
            deploying.set(false);
        }
    }

    /**
     * <p>stop.</p>
     *
     * @throws java.lang.Exception if any.
     */
    public void stop() throws Exception {
        if (future != null) {
            future.cancel(true);
        }
        scheduler.shutdownNow();
    }

    /**
     * <p>Setter for the field <code>checkInterval</code>.</p>
     *
     * @param checkInterval a int
     */
    public void setCheckInterval(int checkInterval) {
        this.checkInterval = checkInterval;
    }

    /**
     * <p>Getter for the field <code>checkInterval</code>.</p>
     *
     * @return a int
     */
    public int getCheckInterval() {
        return checkInterval;
    }

    /**
     * Returns the LoaderMBean.
     *
     * @return LoadeerMBean
     */
    public LoaderMXBean getLoader() {
        LoaderMXBean loader = null;
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName oName;
        try {
            // TODO support all loaders
            oName = new ObjectName("org.red5.server:type=TomcatLoader");
            if (mbs.isRegistered(oName)) {
                loader = JMX.newMXBeanProxy(mbs, oName, LoaderMXBean.class, true);
                log.debug("Loader was found");
            } else {
                log.warn("Loader not found");
            }
        } catch (Exception e) {
            log.error("Exception getting loader", e);
        }
        return loader;
    }

    /**
     * Filters directory content
     */
    protected class DirectoryFilter implements FilenameFilter {
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
            log.trace("Filtering: {} name: {}", dir.getName(), name);
            // filter out all but war files
            boolean result = f.getName().endsWith("war");
            // nullify
            f = null;
            return result;
        }
    }

    /** {@inheritDoc} */
    @Deprecated(since = "2.0.9", forRemoval = true)
    @SuppressWarnings("null")
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        log.warn("This method is deprecated and should not be used; instances are created and controlled internally via TomcatLoader");
    }

}
