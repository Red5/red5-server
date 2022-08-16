/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2016 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.protocol;

/**
 * Represents current decode state of the protocol.
 */
public class RTMPDecodeState {

    // decoder state enum; use index when byte value required
    private enum State {
        OK, // Decoding finished successfully
        CONTINUE, // Decoding continues
        BUFFER, // Decoder is buffering
        DESTROYED; // Decoding is no longer required
    }

    /**
     * Session id to which this decoding state belongs.
     */
    public final String sessionId;

    /**
     * Classes like the RTMP state object will extend this marker interface.
     */
    private volatile int decoderBufferAmount;

    /**
     * Current decoder state, decoder is stopped by default.
     */
    private volatile State decoderState = State.OK;

    public RTMPDecodeState(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Returns current buffer amount.
     *
     * @return Buffer amount
     */
    public int getDecoderBufferAmount() {
        return decoderBufferAmount;
    }

    /**
     * Specifies buffer decoding amount needed.
     *
     * @param amount Buffer decoding amount
     */
    public void bufferDecoding(int amount) {
        decoderState = State.BUFFER;
        decoderBufferAmount = amount;
    }

    /**
     * Set decoding state as "needed to be continued".
     */
    public void continueDecoding() {
        decoderState = State.CONTINUE;
    }

    /**
     * Checks whether remaining buffer size is greater or equal than buffer amount and so if it makes sense to start decoding.
     *
     * @param remaining Remaining buffer size
     * @return true if there is data to decode, false otherwise
     */
    public boolean canStartDecoding(int remaining) {
        return remaining >= decoderBufferAmount;
    }

    /**
     * Starts decoding. Sets state to "ready" and clears buffer amount.
     */
    public void startDecoding() {
        decoderState = State.OK;
        decoderBufferAmount = 0;
    }

    public void stopDecoding() {
        decoderState = State.DESTROYED;
    }

    /**
     * Checks whether decoding is complete.
     *
     * @return true if decoding has finished, false otherwise
     */
    public boolean hasDecodedObject() {
        return (decoderState == State.OK);
    }

    /**
     * Checks whether decoding process can be continued.
     *
     * @return true if decoding can be continued, false otherwise
     */
    public boolean canContinueDecoding() {
        return (decoderState != State.BUFFER && decoderState != State.DESTROYED);
    }

    /**
     * @return the sessionId
     */
    public String getSessionId() {
        return sessionId;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "RTMPDecodeState [sessionId=" + sessionId + ", decoderState=" + decoderState + ", decoderBufferAmount=" + decoderBufferAmount + "]";
    }

}
