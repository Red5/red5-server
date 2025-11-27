/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.jmx.mxbeans;

import java.util.List;

import javax.management.MXBean;

import org.red5.server.Client;
import org.red5.server.exception.ClientNotFoundException;

/**
 * An MBean interface for the client registry.
 *
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
@MXBean
public interface ClientRegistryMXBean {

    /**
     * <p>hasClient.</p>
     *
     * @param id a {@link java.lang.String} object
     * @return a boolean
     */
    public boolean hasClient(String id);

    /**
     * <p>getClientList.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Client> getClientList();

    /**
     * <p>getClient.</p>
     *
     * @param id a {@link java.lang.String} object
     * @return a {@link org.red5.server.Client} object
     * @throws org.red5.server.exception.ClientNotFoundException if any.
     */
    public Client getClient(String id) throws ClientNotFoundException;

}
