/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2015 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.client;

/**
 * Utility class for accessing Red5 "client" objects.
 *
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
public final class Red5Client {

    /**
     * Current server version with revision
     */
    public static final String VERSION = "Red5 Client 2.0.17";

    /**
     * Create a new Red5Client object using the connection local to the current thread A bit of magic that lets you access the red5 scope
     * from anywhere
     */
    public Red5Client() {
    }

    /**
     * Returns the current version with revision number
     *
     * @return String version
     */
    public static String getVersion() {
        return VERSION;
    }

}
