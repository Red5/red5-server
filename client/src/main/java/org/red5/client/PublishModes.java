/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2015 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package org.red5.client;

/**
 * Publish modes according Adobe ActionScript 3 documentation
 *
 * Quoting http://help.adobe.com/en_US/FlashPlatform/reference/actionscript/3/flash/net/NetStream.html#publish%28%29 If you pass "record",
 * the server publishes and records live data, saving the recorded data to a new file with a name matching the value passed to the name
 * parameter. If the file exists, it is overwritten. If you pass "append", the server publishes and records live data, appending the
 * recorded data to a file with a name that matches the value passed to the name parameter. If no file matching the name parameter is found,
 * it is created. If you pass "appendWithGap", additional information about time coordination is passed to help the server determine the
 * correct transition point when dynamic streaming. If you omit this parameter or pass "live", the server publishes live data without
 * recording it. If a file with a name that matches the value passed to the name parameter exists, it is deleted.
 *
 * @author Stoian Ivanov (s.ivanov_at_teracomm.bg)
 */
public final class PublishModes {

    /** Constant <code>LIVE="live"</code> */
    public static final String LIVE = "live";

    /** Constant <code>RECORD="record"</code> */
    public static final String RECORD = "record";

    /** Constant <code>APPEND="append"</code> */
    public static final String APPEND = "append";

    /** Constant <code>APPENDWITHGAP="appendWithGap"</code> */
    public static final String APPENDWITHGAP = "appendWithGap";

}
