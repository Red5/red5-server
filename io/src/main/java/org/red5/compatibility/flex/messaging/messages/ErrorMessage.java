/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.compatibility.flex.messaging.messages;

/**
 * Compatibility flex error message to be returned to the client.
 *
 * @author The Red5 Project
 * @author Joachim Bauch (jojo@struktur.de)
 */
public class ErrorMessage extends AsyncMessage {

    private static final long serialVersionUID = -9069412644250075809L;

    /** Fault code identifying the error that occurred. */
    public String faultCode;

    /** Additional detail describing the fault. */
    public String faultDetail;

    /** Human-readable description of the fault. */
    public String faultString;

    /** Nested cause of this error, if one is available. */
    public Object rootCause;

    /** Additional application-specific data associated with the error. */
    public Object extendedData;

}
