/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.jmx.mxbeans;

import javax.management.MXBean;

/**
 * Red5 applications loader
 *
 * @author mondain
 */
@MXBean
public interface ContextLoaderMXBean extends ShutdownMXBean {

    /**
     * <p>setContextsConfig.</p>
     *
     * @param contextsConfig a {@link java.lang.String} object
     */
    public void setContextsConfig(String contextsConfig);

    /**
     * <p>init.</p>
     *
     * @throws java.lang.Exception if any.
     */
    public void init() throws Exception;

    /**
     * <p>shutdown.</p>
     */
    public void shutdown();

    /**
     * <p>setParentContext.</p>
     *
     * @param parentContextKey a {@link java.lang.String} object
     * @param appContextId a {@link java.lang.String} object
     */
    public void setParentContext(String parentContextKey, String appContextId);

    /**
     * <p>getContextsConfig.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getContextsConfig();

    /**
     * <p>loadContext.</p>
     *
     * @param name a {@link java.lang.String} object
     * @param config a {@link java.lang.String} object
     */
    public void loadContext(String name, String config);

    /**
     * <p>unloadContext.</p>
     *
     * @param name a {@link java.lang.String} object
     */
    public void unloadContext(String name);

}
