/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.api.service;

import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.IScopeService;
import org.red5.server.api.stream.ISubscriberStream;

/**
 * <p>ISubscriberStreamService interface.</p>
 *
 * @author mondain
 */
public interface ISubscriberStreamService extends IScopeService {

    /** Constant <code>BEAN_NAME="subscriberStreamService"</code> */
    public static String BEAN_NAME = "subscriberStreamService";

    /**
     * Returns a stream that can subscribe a broadcast stream with the given name using "IBroadcastStream.subscribe".
     *
     * @param scope
     *            the scope to return the stream from
     * @param name
     *            the name of the stream
     * @return the stream object
     */
    public ISubscriberStream getSubscriberStream(IScope scope, String name);

}
