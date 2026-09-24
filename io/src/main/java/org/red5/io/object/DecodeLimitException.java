/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.object;

/**
 * Thrown when AMF input exceeds a decoding limit, such as the maximum nesting depth or a declared length that the remaining input cannot
 * satisfy.
 *
 * @author The Red5 Project
 */
public class DecodeLimitException extends RuntimeException {

    private static final long serialVersionUID = 4193067258201456734L;

    /**
     * Creates a new exception.
     *
     * @param message
     *            detail message
     */
    public DecodeLimitException(String message) {
        super(message);
    }

}
