/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2022 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.codec;

import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.mina.core.buffer.IoBuffer;

/**
 * Red5 video codec for the AV1 video format. Stores AV1CodecConfigurationRecord and last keyframe.
 * <p>
 * Per the E-RTMP v2 spec, AV1 uses OBUs (Open Bitstream Units) not NALUs. AV1 does NOT use
 * compositionTimeOffset (SI24) in CodedFrames -- the body goes straight to OBU data. Consequently,
 * CodedFramesX is not a distinct packet type for AV1 (both CodedFrames and CodedFramesX carry raw
 * OBU data without any composition time offset).
 * </p>
 * <p>
 * AV1 is the only codec that supports MPEG2TSSequenceStart, carrying an AV1VideoDescriptor.
 * </p>
 *
 * @author The Red5 Project
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class AV1Video extends AbstractVideo implements IEnhancedRTMPVideoCodec {

    /*
       AV1CodecConfigurationRecord per AOM av1-isobmff spec:
       https://aomediacodec.github.io/av1-isobmff/v1.3.0.html#av1codecconfigurationbox-definition

       aligned(8) class AV1CodecConfigurationRecord {
            unsigned int(1) marker = 1;
            unsigned int(7) version = 1;
            unsigned int(3) seq_profile;
            unsigned int(5) seq_level_idx_0;
            unsigned int(1) seq_tier_0;
            unsigned int(1) high_bitdepth;
            unsigned int(1) twelve_bit;
            unsigned int(1) monochrome;
            unsigned int(1) chroma_subsampling_x;
            unsigned int(1) chroma_subsampling_y;
            unsigned int(2) chroma_sample_position;
            unsigned int(3) reserved = 0;
            unsigned int(1) initial_presentation_delay_present;
            if (initial_presentation_delay_present) {
                unsigned int(4) initial_presentation_delay_minus_one;
            } else {
                unsigned int(4) reserved = 0;
            }
            unsigned int(8) configOBUs[];
        }
    */
    private FrameData decoderConfiguration;

    /**
     * MPEG-2 TS sequence start descriptor (AV1VideoDescriptor), if received.
     */
    private FrameData mpeg2tsDescriptor;

    {
        codec = VideoCodec.AV1;
    }

    /** {@inheritDoc} */
    @Override
    public boolean canDropFrames() {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public void reset() {
        softReset();
    }

    /** {@inheritDoc} */
    public void handleNonEnhanced(VideoFrameType type, IoBuffer data, int timestamp) {
        // AV1 has no legacy (non-enhanced) RTMP codec ID; non-enhanced is not supported
        if (isDebug) {
            log.debug("AV1 non-enhanced not supported");
        }
    }

    /** {@inheritDoc} */
    @SuppressWarnings("incomplete-switch")
    public void handleFrame(VideoPacketType packetType, VideoFrameType frameType, IoBuffer data, int timestamp) {
        switch (packetType) {
            case SequenceStart:
                if (frameType == VideoFrameType.KEYFRAME) {
                    if (isDebug) {
                        log.debug("Decoder configuration");
                    }
                    // Store AV1CodecConfigurationRecord data
                    if (decoderConfiguration == null) {
                        decoderConfiguration = new FrameData(data);
                    } else {
                        decoderConfiguration.setData(data);
                    }
                    // new sequence, clear keyframe and interframe collections
                    softReset();
                }
                break;
            case MPEG2TSSequenceStart:
                // AV1 is the only codec that supports MPEG-2 TS sequence start
                if (isDebug) {
                    log.debug("MPEG-2 TS sequence start (AV1VideoDescriptor)");
                }
                if (mpeg2tsDescriptor == null) {
                    mpeg2tsDescriptor = new FrameData(data);
                } else {
                    mpeg2tsDescriptor.setData(data);
                }
                softReset();
                break;
            case CodedFrames:
            case CodedFramesX:
                // Per E-RTMP v2 spec: AV1 does NOT use compositionTimeOffset (SI24).
                // Both CodedFrames and CodedFramesX carry raw OBU data directly.
                // (CodedFramesX is technically not defined for AV1 in the spec, but
                // we handle it identically to CodedFrames for robustness.)
                switch (frameType) {
                    case KEYFRAME:
                        if (isDebug) {
                            log.debug("Keyframe - keyframeTimestamp: {}", keyframeTimestamp);
                        }
                        if (timestamp != keyframeTimestamp) {
                            keyframeTimestamp = timestamp;
                            softReset();
                        }
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
                                if (lastInterframe < interframes.size()) {
                                    interframes.get(lastInterframe).setData(data);
                                } else {
                                    interframes.add(new FrameData(data));
                                }
                            } catch (Throwable e) {
                                log.warn("Failed to buffer interframe", e);
                            }
                        }
                        break;
                }
                break;
            default:
                break;
        }
        data.rewind();
    }

    /** {@inheritDoc} */
    @Override
    public IoBuffer getDecoderConfiguration() {
        return decoderConfiguration != null ? decoderConfiguration.getFrame() : null;
    }
}
