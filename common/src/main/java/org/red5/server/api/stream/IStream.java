/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.api.stream;

import org.red5.codec.IStreamCodecInfo;
import org.red5.server.api.scope.IScope;

/**
 * Base interface for stream objects. A stream object is always associated with a scope.
 *
 * @author mondain
 */
public interface IStream {

    /**
     * Get the name of the stream. The name is unique across the server. This is just an id of the stream and NOT the name that is used at client side to subscribe to the stream. For that name, use {@link org.red5.server.api.stream.IBroadcastStream#getPublishedName()}
     *
     * @return the name of the stream
     */
    public String getName();

    /**
     * Get Codec info for a stream.
     *
     * @return codec info
     */
    IStreamCodecInfo getCodecInfo();

    /**
     * Get the scope this stream is associated with.
     *
     * @return scope object
     */
    public IScope getScope();

    /**
     * Start this stream.
     */
    public void start();

    /**
     * Stop this stream.
     */
    public void stop();

    /**
     * Close this stream.
     */
    public void close();

    /**
     * Returns the timestamp at which the stream was created.
     *
     * @return creation timestamp
     */
    public long getCreationTime();

    /**
     * Returns the timestamp at which the stream was started.
     *
     * @return started timestamp
     */
    long getStartTime();
}
