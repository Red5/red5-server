/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2022 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.codec;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.io.IoConstants;
import org.red5.io.utils.LEB128;
import org.red5.util.ByteNibbler;

/**
 * Red5 video codec for the AV1 video format. Portions of this AV1 code are based on the work of the Pion project.
 *
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class AV1Video extends AbstractVideo {

    private static final byte Z_MASK = (byte) 0b10000000;

    private static final int Z_BITSHIFT = 7;

    private static final byte Y_MASK = (byte) 0b01000000;

    private static final int Y_BITSHIFT = 6;

    private static final byte W_MASK = (byte) 0b00110000;

    private static final int W_BITSHIFT = 4;

    private static final byte N_MASK = (byte) 0b00001000;

    private static final int N_BITSHIFT = 3;

    private static final byte OBU_FRAME_TYPE_MASK = (byte) 0b01111000;

    private static final int OBU_FRAME_TYPE_BITSHIFT = 3;

    private static final byte OBU_FRAME_TYPE_SEQUENCE_HEADER = 1;

    private static final int AV1_PAYLOADER_HEADER_SIZE = 1;

    private static final int LEB128_SIZE = 1;

    // not sure if this is needed or not
    private FrameData decoderConfiguration;

    {
        codec = VideoCodec.AV1;
    }

    /** {@inheritDoc} */
    @Override
    public boolean canDropFrames() {
        return true;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("incomplete-switch")
    @Override
    public boolean addData(IoBuffer data, int timestamp) {
        log.trace("{} addData timestamp: {} remaining: {} pos: {}", codec.name(), timestamp, data.remaining(), data.position());
        boolean result = false;
        // go back to the beginning, this only works in non-multitrack scenarios
        if (data.position() > 0) {
            data.rewind();
        }
        // no data, no operation
        if (data.hasRemaining()) {
            // mark the position before we get the flags
            data.mark();
            // get the first byte for v1 codec type or enhanced codec bit
            byte flg = data.get();
            // determine if we've got an enhanced codec
            enhanced = ByteNibbler.isBitSet(flg, 7);
            // for frame type we need get 3 bits
            int ft = ((flg & 0b01110000) >> 4);
            frameType = VideoFrameType.valueOf(ft);
            if (enhanced) {
                // get the packet type
                packetType = VideoPacketType.valueOf(flg & IoConstants.MASK_VIDEO_CODEC);
                // get the fourcc
                int fourcc = data.getInt();
                // reset back to the beginning after we got the fourcc
                data.reset();
                if (isDebug) {
                    log.debug("{} - frame type: {} packet type: {}", VideoCodec.valueOfByFourCc(fourcc), frameType, packetType);
                }
                switch (packetType) {
                    case SequenceStart:
                        if (frameType == VideoFrameType.KEYFRAME) {
                            if (isDebug) {
                                log.debug("Decoder configuration");
                            }
                            // Store AV1 DecoderConfigurationRecord data, if one exists
                            if (decoderConfiguration == null) {
                                decoderConfiguration = new FrameData(data);
                            } else {
                                decoderConfiguration.setData(data);
                            }
                            // new sequence, clear keyframe and interframe collections
                            softReset();
                        }
                        break;
                    case CodedFramesX: // pass coded data without comp time offset
                        switch (frameType) {
                            case KEYFRAME: // keyframe
                                if (isDebug) {
                                    log.debug("Keyframe - keyframeTimestamp: {}", keyframeTimestamp);
                                }
                                // get the time stamp and compare with the current value
                                if (timestamp != keyframeTimestamp) {
                                    //log.trace("New keyframe");
                                    // new keyframe
                                    keyframeTimestamp = timestamp;
                                    // if its a new keyframe, clear keyframe and interframe collections
                                    softReset();
                                }
                                // store keyframe
                                keyframes.add(new FrameData(data));
                                break;
                            case INTERFRAME:
                                if (bufferInterframes) {
                                    if (isDebug) {
                                        log.debug("Interframe - timestamp: {}", timestamp);
                                    }
                                    if (interframes == null) {
                                        interframes = new CopyOnWriteArrayList<>();
                                    }
                                    try {
                                        int lastInterframe = numInterframes.getAndIncrement();
                                        //log.trace("Buffering interframe #{}", lastInterframe);
                                        if (lastInterframe < interframes.size()) {
                                            interframes.get(lastInterframe).setData(data);
                                        } else {
                                            interframes.add(new FrameData(data));
                                        }
                                    } catch (Throwable e) {
                                        log.warn("Failed to buffer interframe", e);
                                    }
                                    //log.trace("Interframes: {}", interframes.size());
                                }
                                break;
                        }
                        break;
                    case CodedFrames: // pass coded data
                        int compTimeOffset = (data.get() << 16 | data.get() << 8 | data.get());
                        switch (frameType) {
                            case KEYFRAME: // keyframe
                                if (isDebug) {
                                    log.debug("Keyframe - keyframeTimestamp: {} compTimeOffset: {}", keyframeTimestamp, compTimeOffset);
                                }
                                keyframes.add(new FrameData(data, compTimeOffset));
                                break;
                        }
                        break;
                }
            } else {
                // no non-enhanced codec suspport yet
            }
            //log.trace("Keyframes: {}", keyframes.size());
            // we handled the data
            result = true;
        }
        // reset the position
        data.rewind();
        return result;
    }

    public static class AV1Payloader {

        private byte[] sequenceHeader;

        public List<byte[]> payload(int mtu, byte[] payload) {
            List<byte[]> payloads = new ArrayList<>();
            int payloadDataIndex = 0;
            int payloadDataRemaining = payload.length;

            if (mtu <= 0 || payloadDataRemaining <= 0) {
                return payloads;
            }

            byte frameType = (byte) ((payload[0] & OBU_FRAME_TYPE_MASK) >> OBU_FRAME_TYPE_BITSHIFT);
            if (frameType == OBU_FRAME_TYPE_SEQUENCE_HEADER) {
                sequenceHeader = payload;
                return payloads;
            }

            while (payloadDataRemaining > 0) {
                byte obuCount = 1;
                int metadataSize = AV1_PAYLOADER_HEADER_SIZE;
                if (sequenceHeader != null && sequenceHeader.length != 0) {
                    obuCount++;
                    metadataSize += LEB128_SIZE + sequenceHeader.length;
                }

                byte[] out = new byte[Math.min(mtu, payloadDataRemaining + metadataSize)];
                int outOffset = AV1_PAYLOADER_HEADER_SIZE;
                out[0] = (byte) (obuCount << W_BITSHIFT);

                if (obuCount == 2) {
                    out[0] ^= N_MASK;
                    out[1] = (byte) LEB128.encode(sequenceHeader.length);
                    System.arraycopy(sequenceHeader, 0, out, 2, sequenceHeader.length);
                    outOffset += LEB128_SIZE + sequenceHeader.length;
                    sequenceHeader = null;
                }

                int outBufferRemaining = out.length - outOffset;
                System.arraycopy(payload, payloadDataIndex, out, outOffset, outBufferRemaining);
                payloadDataRemaining -= outBufferRemaining;
                payloadDataIndex += outBufferRemaining;

                if (!payloads.isEmpty()) {
                    out[0] ^= Z_MASK;
                }

                if (payloadDataRemaining != 0) {
                    out[0] ^= Y_MASK;
                }

                payloads.add(out);
            }

            return payloads;
        }

    }

    public static class AV1Packet {

        public boolean Z, Y, N;

        public byte W;

        public List<byte[]> OBUElements;

        public byte[] unmarshal(byte[] payload) throws Exception {
            if (payload == null) {
                throw new Exception("Nil packet");
            } else if (payload.length < 2) {
                throw new Exception("Short packet");
            }

            Z = ((payload[0] & Z_MASK) >> Z_BITSHIFT) != 0;
            Y = ((payload[0] & Y_MASK) >> Y_BITSHIFT) != 0;
            N = ((payload[0] & N_MASK) >> N_BITSHIFT) != 0;
            W = (byte) ((payload[0] & W_MASK) >> W_BITSHIFT);

            if (Z && N) {
                throw new Exception("Packet cannot be both a keyframe and a fragment");
            }

            OBUElements = parseBody(payload);

            byte[] result = new byte[payload.length - 1];
            System.arraycopy(payload, 1, result, 0, result.length);
            return result;
        }

        private List<byte[]> parseBody(byte[] payload) throws Exception {
            List<byte[]> obuElements = new ArrayList<>();

            int currentIndex = 1;
            for (int i = 1; currentIndex < payload.length; i++) {
                int obuElementLength;
                int bytesRead = 0;

                if (i == W) {
                    bytesRead = 0;
                    obuElementLength = payload.length - currentIndex;
                } else {
                    obuElementLength = LEB128.decode(payload[currentIndex]);
                    bytesRead++;
                }

                currentIndex += bytesRead;
                if (payload.length < currentIndex + obuElementLength) {
                    throw new Exception("Short packet");
                }
                byte[] obuElement = new byte[obuElementLength];
                System.arraycopy(payload, currentIndex, obuElement, 0, obuElementLength);
                obuElements.add(obuElement);
                currentIndex += obuElementLength;
            }

            return obuElements;
        }

    }

}
