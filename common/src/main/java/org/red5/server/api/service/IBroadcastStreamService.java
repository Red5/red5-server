/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.api.service;

import java.util.Set;

import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IBroadcastStream;

/**
 * <p>IBroadcastStreamService interface.</p>
 *
 * @author mondain
 */
public interface IBroadcastStreamService {

    /** Constant <code>BROADCAST_STREAM_SERVICE="broadcastStreamService"</code> */
    public final static String BROADCAST_STREAM_SERVICE = "broadcastStreamService";

    /**
     * Does the scope have a broadcast stream registered with a given name
     *
     * @param scope
     *            the scope to check for the stream
     * @param name
     *            name of the broadcast
     * @return true is a stream exists, otherwise false
     */
    boolean hasBroadcastStream(IScope scope, String name);

    /**
     * Get a broadcast stream by name
     *
     * @param scope
     *            the scope to return the stream from
     * @param name
     *            the name of the broadcast
     * @return broadcast stream object
     */
    IBroadcastStream getBroadcastStream(IScope scope, String name);

    /**
     * Get a set containing the names of all the broadcasts
     *
     * @param scope
     *            the scope to search for streams
     * @return set containing all broadcast names
     */
    Set<String> getBroadcastStreamNames(IScope scope);

    /**
     * Returns broadcast streams registered on the scope.
     *
     * @param scope a {@link org.red5.server.api.scope.IScope} object
     * @return set of broadcast streams or empty if none exist
     */
    Set<IBroadcastStream> getBroadcastStreams(IScope scope);

}
