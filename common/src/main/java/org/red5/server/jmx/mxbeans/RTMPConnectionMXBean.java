/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.jmx.mxbeans;

import java.util.List;

import javax.management.MXBean;

/**
 * <p>RTMPConnectionMXBean interface.</p>
 *
 * @author mondain
 */
@MXBean
public interface RTMPConnectionMXBean extends AttributeStoreMXBean {

    /**
     * <p>getType.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getType();

    /**
     * <p>getHost.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getHost();

    /**
     * <p>getRemoteAddress.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getRemoteAddress();

    /**
     * <p>getRemoteAddresses.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<String> getRemoteAddresses();

    /**
     * <p>getRemotePort.</p>
     *
     * @return a int
     */
    public int getRemotePort();

    /**
     * <p>getPath.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getPath();

    /**
     * <p>getSessionId.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getSessionId();

    /**
     * <p>isConnected.</p>
     *
     * @return a boolean
     */
    public boolean isConnected();

    /**
     * <p>close.</p>
     */
    public void close();

    /**
     * <p>getReadBytes.</p>
     *
     * @return a long
     */
    public long getReadBytes();

    /**
     * <p>getWrittenBytes.</p>
     *
     * @return a long
     */
    public long getWrittenBytes();

    /**
     * <p>getReadMessages.</p>
     *
     * @return a long
     */
    public long getReadMessages();

    /**
     * <p>getWrittenMessages.</p>
     *
     * @return a long
     */
    public long getWrittenMessages();

    /**
     * <p>getDroppedMessages.</p>
     *
     * @return a long
     */
    public long getDroppedMessages();

    /**
     * <p>getPendingMessages.</p>
     *
     * @return a long
     */
    public long getPendingMessages();

}
