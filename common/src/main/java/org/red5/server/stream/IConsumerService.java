/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.stream;

import org.red5.server.api.stream.IClientStream;
import org.red5.server.messaging.IMessageOutput;

/**
 * Service for consumer objects, used to get pushed messages at consumer endpoint.
 *
 * @author mondain
 */
public interface IConsumerService {
    /** Constant <code>KEY="consumerService"</code> */
    public static final String KEY = "consumerService";

    /**
     * Handles pushed messages
     *
     * @param stream
     *            Client stream object
     * @return Message object
     */
    IMessageOutput getConsumerOutput(IClientStream stream);
}
