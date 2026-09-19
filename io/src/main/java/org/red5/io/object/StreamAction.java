/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.object;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents all the actions which may be permitted on a stream. Some actions are called by client implementations other than a Flash Player itself; ex "getStreamLength".
 * If an action is not specified here, the "CUSTOM" enum will be returned.
 *
 * @author Paul Gregoire
 */
public enum StreamAction {

    /** Requests a new client connection to the application. */
    CONNECT("connect"),
    /** Requests that the connection be closed. */
    DISCONNECT("disconnect"),
    /** Requests creation of a new stream on the connection. */
    CREATE_STREAM("createStream"),
    /** Requests deletion of an existing stream. */
    DELETE_STREAM("deleteStream"),
    /** Requests that a stream be closed. */
    CLOSE_STREAM("closeStream"),
    /** Requests initialization of a stream prior to use. */
    INIT_STREAM("initStream"),
    /** Requests that a previously reserved stream be released. */
    RELEASE_STREAM("releaseStream"),
    /** Requests that the client begin publishing a stream. */
    PUBLISH("publish"),
    /** Requests that stream playback be paused or resumed. */
    PAUSE("pause"),
    /** Requests a raw (non-buffered) pause/resume of stream playback. */
    PAUSE_RAW("pauseRaw"),
    /** Requests a seek to a specific position within a stream. */
    SEEK("seek"),
    /** Requests playback of a stream. */
    PLAY("play"),
    /** Requests playback of a stream using the extended play2 semantics (transitions, multiple items). */
    PLAY2("play2"),
    /** Requests that stream playback be stopped. */
    STOP("stop"),
    /** Requests enabling or disabling receipt of video data on the stream. */
    RECEIVE_VIDEO("receiveVideo"),
    /** Requests enabling or disabling receipt of audio data on the stream. */
    RECEIVE_AUDIO("receiveAudio"),
    /** Requests the length of a recorded stream. */
    GET_STREAM_LENGTH("getStreamLength"),
    /** Represents an action not covered by the other predefined constants. */
    CUSTOM("");

    // presize to fit all enums in
    /** Constant <code>map</code> */
    private final static Map<String, StreamAction> map = new HashMap<>(StreamAction.values().length);

    // the stream action this enum is for
    private final String actionString;

    StreamAction(String actionString) {
        this.actionString = actionString;
    }

    /**
     * <p>Getter for the field <code>actionString</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getActionString() {
        return actionString;
    }

    /**
     * <p>getEnum.</p>
     *
     * @param actionString a {@link java.lang.String} object
     * @return a {@link org.red5.io.object.StreamAction} object
     */
    public static StreamAction getEnum(String actionString) {
        // fill the map if its empty
        if (map.isEmpty()) {
            // do this only once
            for (StreamAction action : values()) {
                map.put(action.getActionString(), action);
            }
        }
        // look up the action from the predefined set
        StreamAction match = map.get(actionString);
        if (match != null) {
            return match;
        }
        // return an action representing a custom type
        return CUSTOM;
    }

    /**
     * <p>equals.</p>
     *
     * @param action a {@link org.red5.io.object.StreamAction} object
     * @return a boolean
     */
    public boolean equals(StreamAction action) {
        return action.getActionString().equals(actionString);
    }

    /**
     * <p>equals.</p>
     *
     * @param actionString a {@link java.lang.String} object
     * @return a boolean
     */
    public boolean equals(String actionString) {
        return getActionString().equals(actionString);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return actionString;
    }

}
