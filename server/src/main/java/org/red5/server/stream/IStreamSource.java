/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.stream;

import org.red5.server.api.event.IEvent;

/**
 * Source for streams
 *
 * @author mondain
 */
public interface IStreamSource {
    /**
     * Is there something more to stream?
     *
     * @return <pre>
     * true
     * </pre>
     *
     *         if there's streamable data,
     *
     *         <pre>
     * false
     * </pre>
     *
     *         otherwise
     */
    public abstract boolean hasMore();

    /**
     * Double ended queue of event objects
     *
     * @return Event from queue
     */
    public abstract IEvent dequeue();

    /**
     * Close stream source
     */
    public abstract void close();

}
