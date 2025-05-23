/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net;

import org.apache.mina.core.buffer.IoBuffer;

/**
 * Base interface for Handshake classes.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public interface IHandshake {

    /**
     * <p>doHandshake.</p>
     *
     * @param input a {@link org.apache.mina.core.buffer.IoBuffer} object
     * @return a {@link org.apache.mina.core.buffer.IoBuffer} object
     */
    public IoBuffer doHandshake(IoBuffer input);

    /**
     * <p>validate.</p>
     *
     * @param handshake an array of {@link byte} objects
     * @return a boolean
     */
    public boolean validate(byte[] handshake);

    /**
     * <p>useEncryption.</p>
     *
     * @return a boolean
     */
    public boolean useEncryption();

}
