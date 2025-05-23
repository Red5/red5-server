/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.messaging;

import java.io.IOException;

/**
 * A provider that supports passive pulling of messages.
 *
 * @author The Red5 Project
 * @author Steven Gong (steven.gong@gmail.com)
 */
public interface IPullableProvider extends IProvider {
    /** Constant <code>KEY="IPullableProvider.class.getName()"</code> */
    public static final String KEY = IPullableProvider.class.getName();

    /**
     * <p>pullMessage.</p>
     *
     * @param pipe a {@link org.red5.server.messaging.IPipe} object
     * @return a {@link org.red5.server.messaging.IMessage} object
     * @throws java.io.IOException if any.
     */
    IMessage pullMessage(IPipe pipe) throws IOException;

    /**
     * <p>pullMessage.</p>
     *
     * @param pipe a {@link org.red5.server.messaging.IPipe} object
     * @param wait a long
     * @return a {@link org.red5.server.messaging.IMessage} object
     * @throws java.io.IOException if any.
     */
    IMessage pullMessage(IPipe pipe, long wait) throws IOException;
}
