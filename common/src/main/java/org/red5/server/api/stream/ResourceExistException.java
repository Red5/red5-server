/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.api.stream;

/**
 * <p>ResourceExistException class.</p>
 *
 * @author mondain
 */
public class ResourceExistException extends Exception {
    private static final long serialVersionUID = 443389396219999143L;

    /**
     * Constructs a new ResourceExistException.
     */
    public ResourceExistException() {
        super();
    }

    /**
     * <p>Constructor for ResourceExistException.</p>
     *
     * @param message a {@link java.lang.String} object
     * @param cause a {@link java.lang.Throwable} object
     */
    public ResourceExistException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * <p>Constructor for ResourceExistException.</p>
     *
     * @param message a {@link java.lang.String} object
     */
    public ResourceExistException(String message) {
        super(message);
    }

    /**
     * <p>Constructor for ResourceExistException.</p>
     *
     * @param cause a {@link java.lang.Throwable} object
     */
    public ResourceExistException(Throwable cause) {
        super(cause);
    }

}
