/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.jmx.mxbeans;

import javax.management.MXBean;

/**
 * JMX interface for accessing Red5 API objects
 *
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
@MXBean
public interface Red5MXBean {

    /**
     * <p>getConnection.</p>
     *
     * @return a {@link org.red5.server.jmx.mxbeans.ConnectionMXBean} object
     */
    public ConnectionMXBean getConnection();

    /**
     * <p>getScope.</p>
     *
     * @return a {@link org.red5.server.jmx.mxbeans.ScopeMXBean} object
     */
    public ScopeMXBean getScope();

    /**
     * <p>getClient.</p>
     *
     * @return a {@link org.red5.server.jmx.mxbeans.ClientMXBean} object
     */
    public ClientMXBean getClient();

    /**
     * <p>getContext.</p>
     *
     * @return a {@link org.red5.server.jmx.mxbeans.ContextMXBean} object
     */
    public ContextMXBean getContext();

}
