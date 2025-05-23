/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.jmx.mxbeans;

import javax.management.MXBean;

/**
 * <p>RTMPMinaTransportMXBean interface.</p>
 *
 * @author mondain
 */
@MXBean
public interface RTMPMinaTransportMXBean {
    /**
     * <p>setIoThreads.</p>
     *
     * @param ioThreads a int
     */
    public void setIoThreads(int ioThreads);

    /**
     * <p>setTcpNoDelay.</p>
     *
     * @param tcpNoDelay a boolean
     */
    public void setTcpNoDelay(boolean tcpNoDelay);

    /**
     * <p>setUseHeapBuffers.</p>
     *
     * @param useHeapBuffers a boolean
     */
    public void setUseHeapBuffers(boolean useHeapBuffers);

    /**
     * <p>getAddress.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getAddress();

    /**
     * <p>getStatistics.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getStatistics();

    /**
     * <p>start.</p>
     *
     * @throws java.lang.Exception if any.
     */
    public void start() throws Exception;

    /**
     * <p>stop.</p>
     */
    public void stop();
}
