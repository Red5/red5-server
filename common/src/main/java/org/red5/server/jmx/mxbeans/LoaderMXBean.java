/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.jmx.mxbeans;

import javax.management.MXBean;
import jakarta.servlet.ServletException;

/**
 * Simple mbean interface for J2EE container loaders.
 *
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
@MXBean
public interface LoaderMXBean extends ShutdownMXBean {

    /**
     * <p>startWebApplication.</p>
     *
     * @param application a {@link java.lang.String} object
     * @return a boolean
     * @throws jakarta.servlet.ServletException if any.
     */
    public boolean startWebApplication(String application) throws ServletException;

    /**
     * <p>removeContext.</p>
     *
     * @param path a {@link java.lang.String} object
     */
    public void removeContext(String path);

    /**
     * <p>destroy.</p>
     *
     * @throws java.lang.Exception if any.
     */
    public void destroy() throws Exception;

}
