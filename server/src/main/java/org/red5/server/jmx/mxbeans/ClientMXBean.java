/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.jmx.mxbeans;

import java.util.List;
import java.util.Set;

import javax.management.MXBean;

import org.red5.server.api.IConnection;

/**
 * MBean for Client.
 *
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
@MXBean
public interface ClientMXBean {

    /**
     * <p>getId.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getId();

    /**
     * <p>getCreationTime.</p>
     *
     * @return a long
     */
    public long getCreationTime();

    /**
     * <p>getConnections.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<IConnection> getConnections();

    /**
     * <p>iterateScopeNameList.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<String> iterateScopeNameList();

    /**
     * <p>disconnect.</p>
     */
    public void disconnect();

}
