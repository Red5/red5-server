/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.stream.recording;

/**
 * Thrown when a recording may not start because the application's recording budget or the principal's quota is used up.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class RecordingRefusedException extends IllegalStateException {

    private static final long serialVersionUID = 4908325113452071935L;

    /**
     * Creates the exception.
     *
     * @param reason
     *            why the recording was refused
     */
    public RecordingRefusedException(String reason) {
        super(reason);
    }

}
