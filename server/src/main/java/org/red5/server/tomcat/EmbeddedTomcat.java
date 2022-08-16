/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2016 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.tomcat;

import java.lang.reflect.InvocationTargetException;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.StandardRoot;

/**
 * Extension of Tomcat's Tomcat class, tailored for Red5.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class EmbeddedTomcat extends Tomcat {

    private long cacheMaxSize = 1024 * 1024;

    /**
     * @see #addWebapp(String, String)
     */
    public Context addWebapp(Host host, String contextPath, String docBase, ContextConfig config) {
        Context ctx = createContext(host, contextPath);
        ctx.setPath(contextPath);
        ctx.setDocBase(docBase);
        ctx.addLifecycleListener(new DefaultWebXmlListener());
        ctx.setConfigFile(getWebappConfigFile(docBase, contextPath));
        ctx.addLifecycleListener(config);
        // prevent it from looking ( if it finds one - it'll have dup error )
        config.setDefaultWebXml(noDefaultWebXmlPath());
        // get the host first, creates a new std host if not already set
        getHost();
        // reset ParentClassLoader
        if (!host.getParentClassLoader().equals(Thread.currentThread().getContextClassLoader())) {
            host.setParentClassLoader(Thread.currentThread().getContextClassLoader());
        }
        StandardRoot standardRoot = new StandardRoot(ctx);
        standardRoot.setCacheMaxSize(cacheMaxSize);
        ctx.setResources(standardRoot);
        // add the context
        host.addChild(ctx);
        return ctx;
    }

    /**
     * Create the configured {@link Context} for the given <code>host</code>. The default constructor of the class that was configured with {@link StandardHost#setContextClass(String)} will be used
     *
     * @param host
     *            host for which the {@link Context} should be created, or <code>null</code> if default host should be used
     * @param url
     *            path of the webapp which should get the {@link Context}
     * @return newly created {@link Context}
     */
    private Context createContext(Host host, String url) {
        String contextClass = StandardContext.class.getName();
        if (host == null) {
            host = this.getHost();
        }
        if (host instanceof StandardHost) {
            contextClass = ((StandardHost) host).getContextClass();
        }
        try {
            return (Context) Class.forName(contextClass).getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException | ClassNotFoundException e) {
            throw new IllegalArgumentException("Can't instantiate context-class " + contextClass + " for host " + host + " and url " + url, e);
        }
    }

    public long getCacheMaxSize() {
        return cacheMaxSize;
    }

    public void setCacheMaxSize(long cacheMaxSize) {
        this.cacheMaxSize = cacheMaxSize;
    }

}
