/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.jmx.mxbeans;

import javax.management.MXBean;

/**
 * An MBean interface for the web scope object.
 *
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
@MXBean
public interface WebScopeMXBean extends ScopeMXBean {

    /**
     * <p>setContextPath.</p>
     *
     * @param contextPath a {@link java.lang.String} object
     */
    public void setContextPath(String contextPath);

    /**
     * <p>setVirtualHosts.</p>
     *
     * @param virtualHosts a {@link java.lang.String} object
     */
    public void setVirtualHosts(String virtualHosts);

    /**
     * <p>register.</p>
     */
    public void register();

    /**
     * <p>unregister.</p>
     */
    public void unregister();

    /**
     * <p>isShuttingDown.</p>
     *
     * @return a boolean
     */
    public boolean isShuttingDown();

}
