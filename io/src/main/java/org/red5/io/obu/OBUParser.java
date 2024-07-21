package org.red5.io.obu;

import static org.red5.io.obu.OBUType.FRAME;
import static org.red5.io.obu.OBUType.FRAME_HEADER;
import static org.red5.io.obu.OBUType.METADATA;
import static org.red5.io.obu.OBUType.PADDING;
import static org.red5.io.obu.OBUType.REDUNDANT_FRAME_HEADER;
import static org.red5.io.obu.OBUType.SEQUENCE_HEADER;
import static org.red5.io.obu.OBUType.TEMPORAL_DELIMITER;
import static org.red5.io.obu.OBUType.TILE_GROUP;
import static org.red5.io.obu.OBUType.TILE_LIST;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.red5.io.utils.LEB128;
import org.red5.io.utils.LEB128.LEB128Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parsers OBU providing headers and extract relevant data. Logic is derived from the C code in the obuparser project.
 *
 * @author Paul Gregoire
 */
public class OBUParser {

    private static final Logger log = LoggerFactory.getLogger(OBUParser.class);

    private static final Set<Integer> VALID_OBU_TYPES = Set.of(SEQUENCE_HEADER.getValue(), TEMPORAL_DELIMITER.getValue(), FRAME_HEADER.getValue(), TILE_GROUP.getValue(), METADATA.getValue(), FRAME.getValue(), REDUNDANT_FRAME_HEADER.getValue(), TILE_LIST.getValue(), PADDING.getValue());

    public static final byte OBU_SIZE_PRESENT_BIT = 0b0000_0010;

    public static final byte OBU_EXT_BIT = 0b0000_0100;

    public static final byte OBU_START_FRAGMENT_BIT = (byte) 0b1000_0000;

    public static final byte OBU_END_FRAGMENT_BIT = 0b0100_0000;

    public static final byte OBU_START_SEQUENCE_BIT = 0b0000_1000;

    public static final byte OBU_COUNT_MASK = 0b0011_0000;

    public static final byte OBU_TYPE_MASK = 0b0111_1000;

    /*
    * obp_get_next_obu parses the next OBU header in a packet containing a set of one or more OBUs
    * (e.g. an IVF or ISOBMFF packet) and returns its location in the buffer, as well as all
    * relevant data from the header.
    *
    * Input:
    *     buf      - Input packet buffer.
    *     buf_size - Size of the input packet buffer.
    *     err      - An error buffer and buffer size to write any error messages into.
    *
    * Output:
    *     obu_type    - The type of OBU.
    *     offset      - The offset into the buffer where this OBU starts, excluding the OBU header.
    *     obu_size    - The size of the OBU, excluding the size of the OBU header.
    *     temporal_id - The temporal ID of the OBU.
    *     spatial_id  - The spatial ID of the OBU.
    *
    * Returns:
    *     0 on success, -1 on error.
    */
    public static OBUInfo getNextObu(byte[] buf, int offset, int bufSize) throws OBUParseException {
        if (bufSize < 1) {
            throw new OBUParseException("Buffer is too small to contain an OBU");
        }
        log.trace("Buffer length: {} size: {} offset: {}", buf.length, bufSize, offset);
        if (buf.length < (offset + 1)) {
            throw new OBUParseException("Buffer is too small for given offset");
        }
        int pos = offset;
        int obuType = (buf[pos] & 0x78) >> 3;
        if (!isValidObu(obuType)) {
            throw new OBUParseException("OBU header contains invalid OBU type: " + obuType);
        }
        OBUInfo info = new OBUInfo();
        info.obuType = OBUType.fromValue(obuType);
        boolean obuExtensionFlag = obuHasExtension(buf[pos]);
        boolean obuHasSizeField = obuHasSize(buf[pos]);
        log.trace("OBU type: {} extension? {} size field? {}", info.obuType, obuExtensionFlag, obuHasSizeField);
        pos++; // move past the OBU header
        if (obuExtensionFlag) {
            if (bufSize < pos + 1) {
                throw new OBUParseException("Buffer is too small to contain an OBU extension header");
            }
            info.temporalId = (buf[pos] & 0xE0) >> 5;
            info.spatialId = (buf[pos] & 0x18) >> 3;
            pos++; // move past the OBU extension header
        }
        if (obuHasSizeField) {
            LEB128Result result = LEB128.decode(Arrays.copyOfRange(buf, pos, pos + 1));
            pos += result.bytesRead;
            info.size = result.value;
        } else {
            info.size = bufSize - pos;
        }
        log.trace("OBU size: {}", info.size);
        info.data = ByteBuffer.wrap(Arrays.copyOfRange(buf, pos, (pos + info.size)));
        if (info.size > bufSize - pos) {
            throw new OBUParseException("Invalid OBU size: larger than remaining buffer");
        }
        return info;
    }

    /*
     * obp_parse_sequence_header parses a sequence header OBU and fills out the fields in a
     * user-provided OBPSequenceHeader structure.
     *
     * Input:
     *     buf      - Input OBU buffer. This is expected to *NOT* contain the OBU header.
     *     buf_size - Size of the input OBU buffer.
     *     err      - An error buffer and buffer size to write any error messages into.
     *
     * Output:
     *     seq_header - A user provided structure that will be filled in with all the parsed data.
     *
     * Returns:
     *     0 on success, -1 on error.
     */
    public static OBPSequenceHeader parseSequenceHeader(byte[] buf, int bufSize) throws OBUParseException {
        BitReader br = new BitReader(buf, bufSize);
        OBPSequenceHeader seq = new OBPSequenceHeader();

        seq.seqProfile = (byte) br.readBits(3);
        seq.stillPicture = br.readBits(1) != 0;
        seq.reducedStillPictureHeader = br.readBits(1) != 0;

        if (seq.reducedStillPictureHeader) {
            seq.timingInfoPresentFlag = false;
            seq.decoderModelInfoPresentFlag = false;
            seq.initialDisplayDelayPresentFlag = false;
            seq.operatingPointsCntMinus1 = 0;
            seq.operatingPointIdc[0] = 0;
            seq.seqLevelIdx[0] = 0;
            seq.seqTier[0] = 0;
            seq.decoderModelPresentForThisOp[0] = false;
            seq.initialDisplayDelayPresentForThisOp[0] = false;
        } else {
            seq.timingInfoPresentFlag = br.readBits(1) != 0;
            if (seq.timingInfoPresentFlag) {
                seq.timingInfo = new OBPSequenceHeader.TimingInfo();
                seq.timingInfo.numUnitsInDisplayTick = br.readBits(32);
                seq.timingInfo.timeScale = br.readBits(32);
                seq.timingInfo.equalPictureInterval = br.readBits(1) != 0;
                if (seq.timingInfo.equalPictureInterval) {
                    seq.timingInfo.numTicksPerPictureMinus1 = readUvlc(br);
                }
                seq.decoderModelInfoPresentFlag = br.readBits(1) != 0;
                if (seq.decoderModelInfoPresentFlag) {
                    seq.decoderModelInfo = new OBPSequenceHeader.DecoderModelInfo();
                    seq.decoderModelInfo.bufferDelayLengthMinus1 = (byte) br.readBits(5);
                    seq.decoderModelInfo.numUnitsInDecodingTick = br.readBits(32);
                    seq.decoderModelInfo.bufferRemovalTimeLengthMinus1 = (byte) br.readBits(5);
                    seq.decoderModelInfo.framePresentationTimeLengthMinus1 = (byte) br.readBits(5);
                }
            } else {
                seq.decoderModelInfoPresentFlag = false;
            }
            seq.initialDisplayDelayPresentFlag = br.readBits(1) != 0;
            seq.operatingPointsCntMinus1 = (byte) br.readBits(5);
            for (int i = 0; i <= seq.operatingPointsCntMinus1; i++) {
                seq.operatingPointIdc[i] = (byte) br.readBits(12);
                seq.seqLevelIdx[i] = (byte) br.readBits(5);
                if (seq.seqLevelIdx[i] > 7) {
                    seq.seqTier[i] = (byte) br.readBits(1);
                } else {
                    seq.seqTier[i] = 0;
                }
                if (seq.decoderModelInfoPresentFlag) {
                    seq.decoderModelPresentForThisOp[i] = br.readBits(1) != 0;
                    if (seq.decoderModelPresentForThisOp[i]) {
                        seq.operatingParametersInfo[i] = new OBPSequenceHeader.OperatingParametersInfo();
                        int n = seq.decoderModelInfo.bufferDelayLengthMinus1 + 1;
                        seq.operatingParametersInfo[i].decoderBufferDelay = br.readBits(n);
                        seq.operatingParametersInfo[i].encoderBufferDelay = br.readBits(n);
                        seq.operatingParametersInfo[i].lowDelayModeFlag = br.readBits(1) != 0;
                    }
                } else {
                    seq.decoderModelPresentForThisOp[i] = false;
                }
                if (seq.initialDisplayDelayPresentFlag) {
                    seq.initialDisplayDelayPresentForThisOp[i] = br.readBits(1) != 0;
                    if (seq.initialDisplayDelayPresentForThisOp[i]) {
                        seq.initialDisplayDelayMinus1[i] = (byte) br.readBits(4);
                    }
                }
            }
        }

        seq.frameWidthBitsMinus1 = (byte) br.readBits(4);
        seq.frameHeightBitsMinus1 = (byte) br.readBits(4);
        seq.maxFrameWidthMinus1 = (int) br.readBits(seq.frameWidthBitsMinus1 + 1);
        seq.maxFrameHeightMinus1 = (int) br.readBits(seq.frameHeightBitsMinus1 + 1);

        if (seq.reducedStillPictureHeader) {
            seq.frameIdNumbersPresentFlag = false;
        } else {
            seq.frameIdNumbersPresentFlag = br.readBits(1) != 0;
        }

        if (seq.frameIdNumbersPresentFlag) {
            seq.deltaFrameIdLengthMinus2 = (byte) br.readBits(4);
            seq.additionalFrameIdLengthMinus1 = (byte) br.readBits(3);
        }

        seq.use128x128Superblock = br.readBits(1) != 0;
        seq.enableFilterIntra = br.readBits(1) != 0;
        seq.enableIntraEdgeFilter = br.readBits(1) != 0;

        if (seq.reducedStillPictureHeader) {
            seq.enableInterintraCompound = false;
            seq.enableMaskedCompound = false;
            seq.enableWarpedMotion = false;
            seq.enableDualFilter = false;
            seq.enableOrderHint = false;
            seq.enableJntComp = false;
            seq.enableRefFrameMvs = false;
            seq.seqForceScreenContentTools = 2;
            seq.seqForceIntegerMv = 2;
            seq.OrderHintBits = 0;
        } else {
            seq.enableInterintraCompound = br.readBits(1) != 0;
            seq.enableMaskedCompound = br.readBits(1) != 0;
            seq.enableWarpedMotion = br.readBits(1) != 0;
            seq.enableDualFilter = br.readBits(1) != 0;
            seq.enableOrderHint = br.readBits(1) != 0;
            if (seq.enableOrderHint) {
                seq.enableJntComp = br.readBits(1) != 0;
                seq.enableRefFrameMvs = br.readBits(1) != 0;
            } else {
                seq.enableJntComp = false;
                seq.enableRefFrameMvs = false;
            }
            seq.seqChooseScreenContentTools = br.readBits(1) != 0;
            if (seq.seqChooseScreenContentTools) {
                seq.seqForceScreenContentTools = 2;
            } else {
                seq.seqForceScreenContentTools = (int) br.readBits(1);
            }
            if (seq.seqForceScreenContentTools > 0) {
                seq.seqChooseIntegerMv = br.readBits(1) != 0;
                if (seq.seqChooseIntegerMv) {
                    seq.seqForceIntegerMv = 2;
                } else {
                    seq.seqForceIntegerMv = (int) br.readBits(1);
                }
            } else {
                seq.seqForceIntegerMv = 2;
            }
            if (seq.enableOrderHint) {
                seq.orderHintBitsMinus1 = (byte) br.readBits(3);
                seq.OrderHintBits = (byte) (seq.orderHintBitsMinus1 + 1);
            } else {
                seq.OrderHintBits = 0;
            }
        }

        seq.enableSuperres = br.readBits(1) != 0;
        seq.enableCdef = br.readBits(1) != 0;
        seq.enableRestoration = br.readBits(1) != 0;

        seq.colorConfig = new OBPSequenceHeader.ColorConfig();
        seq.colorConfig.highBitdepth = br.readBits(1) != 0;
        if (seq.seqProfile == 2 && seq.colorConfig.highBitdepth) {
            seq.colorConfig.twelveBit = br.readBits(1) != 0;
            seq.colorConfig.BitDepth = seq.colorConfig.twelveBit ? (byte) 12 : (byte) 10;
        } else {
            seq.colorConfig.BitDepth = seq.colorConfig.highBitdepth ? (byte) 10 : (byte) 8;
        }
        if (seq.seqProfile == 1) {
            seq.colorConfig.monoChrome = false;
        } else {
            seq.colorConfig.monoChrome = br.readBits(1) != 0;
        }
        seq.colorConfig.NumPlanes = seq.colorConfig.monoChrome ? (byte) 1 : (byte) 3;
        seq.colorConfig.colorDescriptionPresentFlag = br.readBits(1) != 0;
        if (seq.colorConfig.colorDescriptionPresentFlag) {
            seq.colorConfig.colorPrimaries = OBPColorPrimaries.values()[br.readBits(8)];
            seq.colorConfig.transferCharacteristics = OBPTransferCharacteristics.values()[br.readBits(8)];
            seq.colorConfig.matrixCoefficients = OBPMatrixCoefficients.values()[br.readBits(8)];
        } else {
            seq.colorConfig.colorPrimaries = OBPColorPrimaries.CP_UNSPECIFIED;
            seq.colorConfig.transferCharacteristics = OBPTransferCharacteristics.TC_UNSPECIFIED;
            seq.colorConfig.matrixCoefficients = OBPMatrixCoefficients.MC_UNSPECIFIED;
        }
        if (seq.colorConfig.monoChrome) {
            seq.colorConfig.colorRange = br.readBits(1) != 0;
            seq.colorConfig.subsamplingX = true;
            seq.colorConfig.subsamplingY = true;
            seq.colorConfig.chromaSamplePosition = OBPChromaSamplePosition.CSP_UNKNOWN;
            seq.colorConfig.separateUvDeltaQ = false;
        } else if (seq.colorConfig.colorPrimaries == OBPColorPrimaries.CP_BT_709 && seq.colorConfig.transferCharacteristics == OBPTransferCharacteristics.TC_SRGB && seq.colorConfig.matrixCoefficients == OBPMatrixCoefficients.MC_IDENTITY) {
            seq.colorConfig.colorRange = true;
            seq.colorConfig.subsamplingX = false;
            seq.colorConfig.subsamplingY = false;
        } else {
            seq.colorConfig.colorRange = br.readBits(1) != 0;
            if (seq.seqProfile == 0) {
                seq.colorConfig.subsamplingX = true;
                seq.colorConfig.subsamplingY = true;
            } else if (seq.seqProfile == 1) {
                seq.colorConfig.subsamplingX = false;
                seq.colorConfig.subsamplingY = false;
            } else {
                if (seq.colorConfig.BitDepth == 12) {
                    seq.colorConfig.subsamplingX = br.readBits(1) != 0;
                    if (seq.colorConfig.subsamplingX) {
                        seq.colorConfig.subsamplingY = br.readBits(1) != 0;
                    } else {
                        seq.colorConfig.subsamplingY = false;
                    }
                } else {
                    seq.colorConfig.subsamplingX = true;
                    seq.colorConfig.subsamplingY = false;
                }
            }
            if (seq.colorConfig.subsamplingX && seq.colorConfig.subsamplingY) {
                seq.colorConfig.chromaSamplePosition = OBPChromaSamplePosition.values()[br.readBits(2)];
            }
        }
        seq.colorConfig.separateUvDeltaQ = br.readBits(1) != 0;

        seq.filmGrainParamsPresent = br.readBits(1) != 0;

        return seq;
    }

    /*
    * obp_parse_frame_header parses a frame header OBU and fills out the fields in a user-provided
    * OBPFrameHeader structure.
    *
    * Input:
    *     buf          - Input OBU buffer. This is expected to *NOT* contain the OBU header.
    *     buf_size     - Size of the input OBU buffer.
    *     state        - An opaque state structure. Must be zeroed by the user on first use.
    *     temporal_id  - A temporal ID previously obtained from obu_parse_sequence header.
    *     spatial_id   - A spatial ID previously obtained from obu_parse_sequence header.
    *     err          - An error buffer and buffer size to write any error messages into.
    *
    * Output:
    *     frame_header    - A user provided structure that will be filled in with all the parsed data.
    *     SeenFrameHeader - Whether or not a frame header has beee seen. Tracking variable as per AV1 spec.
    *
    * Returns:
    *     0 on success, -1 on error.
    */
    private static int parseFrameHeader(byte[] buf, int bufSize, OBPSequenceHeader seq, OBPState state, int temporalId, int spatialId, OBPFrameHeader fh, AtomicBoolean seenFrameHeader) throws OBUParseException {
        BitReader br = new BitReader(buf, bufSize);
        if (seenFrameHeader.get()) {
            if (!state.prevFilled) {
                throw new OBUParseException("SeenFrameHeader is true, but no previous header exists in state");
            }
            copyFrameHeader(state.prev, fh);
            return 0;
        }
        seenFrameHeader.set(true);
        int idLen = 0;
        if (seq.frameIdNumbersPresentFlag) {
            idLen = seq.additionalFrameIdLengthMinus1 + seq.deltaFrameIdLengthMinus2 + 3;
        }
        byte allFrames = (byte) 255; // (1 << 8) - 1
        boolean frameIsIntra = false;
        if (seq.reducedStillPictureHeader) {
            fh.showExistingFrame = false;
            fh.frameType = OBPFrameType.KEYFRAME;
            frameIsIntra = true;
            fh.showFrame = true;
            fh.showableFrame = true;
        } else {
            fh.showExistingFrame = br.readBits(1) != 0;
            if (fh.showExistingFrame) {
                fh.frameToShowMapIdx = (byte) br.readBits(3);
                if (seq.decoderModelInfoPresentFlag && !seq.timingInfo.equalPictureInterval) {
                    int n = seq.decoderModelInfo.framePresentationTimeLengthMinus1 + 1;
                    fh.temporalPointInfo.framePresentationTime = br.readBits(n);
                }
                fh.refreshFrameFlags = 0;
                if (seq.frameIdNumbersPresentFlag) {
                    fh.displayFrameId = br.readBits(idLen);
                }
                fh.frameType = state.refFrameType[fh.frameToShowMapIdx];
                if (fh.frameType == OBPFrameType.KEYFRAME) {
                    fh.refreshFrameFlags = allFrames;
                }
                if (seq.filmGrainParamsPresent) {
                    copyFilmGrainParams(state.refGrainParams[fh.frameToShowMapIdx], fh.filmGrainParams);
                }
                return 0;
            }
            fh.frameType = OBPFrameType.values()[br.readBits(2)];
            frameIsIntra = (fh.frameType == OBPFrameType.INTRA_ONLY_FRAME || fh.frameType == OBPFrameType.KEYFRAME);
            fh.showFrame = br.readBits(1) != 0;
            if (fh.showFrame && seq.decoderModelInfoPresentFlag && !seq.timingInfo.equalPictureInterval) {
                int n = seq.decoderModelInfo.framePresentationTimeLengthMinus1 + 1;
                fh.temporalPointInfo.framePresentationTime = br.readBits(n);
            }
            if (fh.showFrame) {
                fh.showableFrame = (fh.frameType != OBPFrameType.KEYFRAME);
            } else {
                fh.showableFrame = br.readBits(1) != 0;
            }
            if (fh.frameType == OBPFrameType.SWITCH_FRAME || (fh.frameType == OBPFrameType.KEYFRAME && fh.showFrame)) {
                fh.errorResilientMode = true;
            } else {
                fh.errorResilientMode = br.readBits(1) != 0;
            }
        }
        if (fh.frameType == OBPFrameType.KEYFRAME && fh.showFrame) {
            for (int i = 0; i < 8; i++) {
                state.refValid[i] = 0;
                state.refOrderHint[i] = 0;
            }
            for (int i = 0; i < 7; i++) {
                state.orderHint[1 + i] = 0;
            }
        }
        fh.disableCdfUpdate = br.readBits(1) != 0;
        if (seq.seqForceScreenContentTools == 2) {
            fh.allowScreenContentTools = br.readBits(1) != 0;
        } else {
            fh.allowScreenContentTools = seq.seqForceScreenContentTools != 0;
        }
        if (fh.allowScreenContentTools) {
            if (seq.seqForceIntegerMv == 2) {
                fh.forceIntegerMv = br.readBits(1) != 0;
            } else {
                fh.forceIntegerMv = seq.seqForceIntegerMv != 0;
            }
        } else {
            fh.forceIntegerMv = false;
        }
        if (frameIsIntra) {
            fh.forceIntegerMv = true;
        }
        if (seq.frameIdNumbersPresentFlag) {
            fh.currentFrameId = br.readBits(idLen);
            byte diffLen = (byte) (seq.deltaFrameIdLengthMinus2 + 2);
            for (int i = 0; i < 8; i++) {
                if (fh.currentFrameId > (1 << diffLen)) {
                    if (state.refFrameId[i] > fh.currentFrameId || state.refFrameId[i] < (fh.currentFrameId - (1 << diffLen))) {
                        state.refValid[i] = 0;
                    }
                } else {
                    if (state.refFrameId[i] > fh.currentFrameId && state.refFrameId[i] < ((1 << idLen) + fh.currentFrameId - (1 << diffLen))) {
                        state.refValid[i] = 0;
                    }
                }
            }
        } else {
            fh.currentFrameId = 0;
        }
        if (fh.frameType == OBPFrameType.SWITCH_FRAME) {
            fh.frameSizeOverrideFlag = true;
        } else if (seq.reducedStillPictureHeader) {
            fh.frameSizeOverrideFlag = false;
        } else {
            fh.frameSizeOverrideFlag = br.readBits(1) != 0;
        }
        if (seq.OrderHintBits != 0) {
            fh.orderHint = (byte) br.readBits(seq.OrderHintBits);
        } else {
            fh.orderHint = 0;
        }
        byte orderHint = fh.orderHint;
        if (frameIsIntra || fh.errorResilientMode) {
            fh.primaryRefFrame = 7;
        } else {
            fh.primaryRefFrame = (byte) br.readBits(3);
        }
        if (seq.decoderModelInfoPresentFlag) {
            fh.bufferRemovalTimePresentFlag = br.readBits(1) != 0;
            if (fh.bufferRemovalTimePresentFlag) {
                for (int opNum = 0; opNum <= seq.operatingPointsCntMinus1; opNum++) {
                    if (seq.decoderModelPresentForThisOp[opNum]) {
                        int opPtIdc = seq.operatingPointIdc[opNum];
                        int inTemporalLayer = (opPtIdc >> temporalId) & 1;
                        int inSpatialLayer = (opPtIdc >> (spatialId + 8)) & 1;
                        if (opPtIdc == 0 || (inTemporalLayer != 0 && inSpatialLayer != 0)) {
                            int n = seq.decoderModelInfo.bufferRemovalTimeLengthMinus1 + 1;
                            fh.bufferRemovalTime[opNum] = br.readBits(n);
                        }
                    }
                }
            }
        }
        fh.allowHighPrecisionMv = false;
        fh.useRefFrameMvs = false;
        fh.allowIntrabc = false;
        if (fh.frameType == OBPFrameType.SWITCH_FRAME || (fh.frameType == OBPFrameType.KEYFRAME && fh.showFrame)) {
            fh.refreshFrameFlags = allFrames;
        } else {
            fh.refreshFrameFlags = (byte) br.readBits(8);
        }
        if (!frameIsIntra || fh.refreshFrameFlags != allFrames) {
            if (fh.errorResilientMode && seq.enableOrderHint) {
                for (int i = 0; i < 8; i++) {
                    fh.refOrderHint[i] = (byte) br.readBits(seq.OrderHintBits);
                    if (fh.refOrderHint[i] != state.refOrderHint[i]) {
                        state.refValid[i] = 0;
                    }
                }
            }
        }
        // Frame size
        if (frameIsIntra) {
            parseFrameSize(br, seq, fh);
            parseRenderSize(br, fh);
            if (fh.allowScreenContentTools && fh.upscaledWidth == fh.frameWidth) {
                fh.allowIntrabc = br.readBits(1) != 0;
            }
        } else {
            if (!seq.enableOrderHint) {
                fh.frameRefsShortSignaling = false;
            } else {
                fh.frameRefsShortSignaling = br.readBits(1) != 0;
                if (fh.frameRefsShortSignaling) {
                    fh.lastFrameIdx = (byte) br.readBits(3);
                    fh.goldFrameIdx = (byte) br.readBits(3);
                    setFrameRefs(fh, seq, state);
                }
            }
            for (int i = 0; i < 7; i++) {
                if (!fh.frameRefsShortSignaling) {
                    fh.refFrameIdx[i] = (byte) br.readBits(3);
                }
                if (seq.frameIdNumbersPresentFlag) {
                    int n = seq.deltaFrameIdLengthMinus2 + 2;
                    fh.deltaFrameIdMinus1[i] = (byte) br.readBits(n);
                    int DeltaFrameId = fh.deltaFrameIdMinus1[i] + 1;
                    int expectedFrameId = (fh.currentFrameId + (1 << idLen) - DeltaFrameId) % (1 << idLen);
                    if (state.refFrameId[fh.refFrameIdx[i]] != expectedFrameId) {
                        throw new OBUParseException("Reference frame id mismatch");
                    }
                }
            }
            if (fh.frameSizeOverrideFlag && !fh.errorResilientMode) {
                parseSuperresParams(br, seq, fh);
            } else {
                parseFrameSize(br, seq, fh);
                parseRenderSize(br, fh);
            }
            if (fh.forceIntegerMv) {
                fh.allowHighPrecisionMv = false;
            } else {
                fh.allowHighPrecisionMv = br.readBits(1) != 0;
            }
            parseInterpolationFilter(br, fh);
            fh.isMotionModeSwitchable = br.readBits(1) != 0;
            if (fh.errorResilientMode || !seq.enableRefFrameMvs) {
                fh.useRefFrameMvs = false;
            } else {
                fh.useRefFrameMvs = br.readBits(1) != 0;
            }
            for (int i = 0; i < 7; i++) {
                int refFrame = 1 + i;
                byte hint = state.refOrderHint[fh.refFrameIdx[i]];
                state.orderHint[refFrame] = hint;
                if (!seq.enableOrderHint) {
                    state.refFrameSignBias[refFrame] = 0;
                } else {
                    state.refFrameSignBias[refFrame] = getRelativeDist(hint, orderHint, seq) > 0 ? 1 : 0;
                }
            }
        }
        if (seq.reducedStillPictureHeader || fh.disableCdfUpdate) {
            fh.disableFrameEndUpdateCdf = true;
        } else {
            fh.disableFrameEndUpdateCdf = br.readBits(1) != 0;
        }
        if (fh.primaryRefFrame == 7) {
            setupPastIndependence(fh);
        } else {
            loadPrevious(fh, state);
        }
        // Tile info; after parsing frame size and other related parameters
        parseTileInfo(br, fh, seq);
        // Quantization params
        parseQuantizationParams(br, fh, seq);
        // Segmentation params
        parseSegmentationParams(br, fh, seq);
        // Delta Q params
        parseDeltaQParams(br, fh);
        // Delta LF params
        parseDeltaLfParams(br, fh, seq);
        boolean codedLossless = computeCodedLossless(fh, seq);
        fh.codedLossless = codedLossless;
        fh.allLossless = codedLossless && (fh.frameWidth == fh.upscaledWidth);
        // Loop filter params
        parseLoopFilterParams(br, fh, seq);
        // CDEF params
        parseCdefParams(br, fh, seq);
        // LR params
        parseLrParams(br, fh, seq);
        // Read TX mode
        if (codedLossless) {
            // TxMode implicitly set to ONLY_4X4
            fh.txMode = OBPTxMode.ONLY_4X4;
        } else {
            fh.txModeSelect = br.readBits(1) != 0;
            fh.txMode = fh.txModeSelect ? OBPTxMode.SELECT : OBPTxMode.LARGEST;
        }
        // Frame reference mode
        parseFrameReferenceMode(br, fh, frameIsIntra);
        // Skip mode params
        parseSkipModeParams(br, fh, seq, state, frameIsIntra);
        if (frameIsIntra || fh.errorResilientMode || !seq.enableWarpedMotion) {
            fh.allowWarpedMotion = false;
        } else {
            fh.allowWarpedMotion = br.readBits(1) != 0;
        }
        fh.reducedTxSet = br.readBits(1) != 0;
        // Global motion params
        parseGlobalMotionParams(br, fh, frameIsIntra);
        // Film grain params
        parseFilmGrainParams(br, fh, seq, state);
        br.byteAlignment();
        state.frameHeaderEndPos = br.getPosition();
        // Stash refs for future frame use
        for (int i = 0; i < 8; i++) {
            if ((fh.refreshFrameFlags & (1 << i)) != 0) {
                state.refOrderHint[i] = fh.orderHint;
                state.refFrameType[i] = fh.frameType;
                state.refUpscaledWidth[i] = fh.upscaledWidth;
                state.refFrameHeight[i] = fh.frameHeight;
                state.refRenderWidth[i] = fh.renderWidth;
                state.refRenderHeight[i] = fh.renderHeight;
                state.refFrameId[i] = fh.currentFrameId;
                copyFilmGrainParams(fh.filmGrainParams, state.refGrainParams[i]);
                // save_grain_params()
                for (int j = 0; j < 8; j++) {
                    System.arraycopy(fh.globalMotionParams.gmParams[j], 0, state.savedGmParams[i][j], 0, 6);
                }
                // save_segmentation_params()
                for (int j = 0; j < 8; j++) {
                    System.arraycopy(fh.segmentationParams.featureEnabled[j], 0, state.savedFeatureEnabled[i][j], 0, 8);
                    System.arraycopy(fh.segmentationParams.featureData[j], 0, state.savedFeatureData[i][j], 0, 8);
                }
                // save_loop_filter_params()
                System.arraycopy(fh.loopFilterParams.loopFilterRefDeltas, 0, state.savedLoopFilterRefDeltas[i], 0, 8);
                System.arraycopy(fh.loopFilterParams.loopFilterModeDeltas, 0, state.savedLoopFilterModeDeltas[i], 0, 2);
            }
        }
        // Handle show_existing_frame semantics
        if (fh.showExistingFrame && fh.frameType == OBPFrameType.KEYFRAME) {
            fh.orderHint = state.refOrderHint[fh.frameToShowMapIdx];
            for (int i = 0; i < 8; i++) {
                System.arraycopy(state.savedGmParams[fh.frameToShowMapIdx][i], 0, fh.globalMotionParams.gmParams[i], 0, 6);
            }
        }
        if (fh.showExistingFrame) {
            seenFrameHeader.set(false);
            state.prevFilled = false;
        } else {
            copyFrameHeader(fh, state.prev);
            state.prevFilled = true;
        }
        return 0;
    }

    /*
     * This method parses the interpolation filter information from the bitstream.
     */
    private static void parseInterpolationFilter(BitReader br, OBPFrameHeader fh) throws OBUParseException {
        fh.interpolationFilter.isFilterSwitchable = br.readBits(1) != 0;
        if (fh.interpolationFilter.isFilterSwitchable) {
            fh.interpolationFilter.interpolationFilter = OBPInterpolationFilter.SWITCHABLE;
        } else {
            fh.interpolationFilter.interpolationFilter = OBPInterpolationFilter.values()[br.readBits(2)];
        }
    }

    /*
     * This method handles parsing the frame size, either from the bitstream (if frameSizeOverrideFlag is set) or using
     * the values from the sequence header.
     */
    private static void parseFrameSize(BitReader br, OBPSequenceHeader seq, OBPFrameHeader fh) throws OBUParseException {
        if (fh.frameSizeOverrideFlag) {
            int n = seq.frameWidthBitsMinus1 + 1;
            fh.frameWidthMinus1 = br.readBits(n);
            n = seq.frameHeightBitsMinus1 + 1;
            fh.frameHeightMinus1 = br.readBits(n);
            fh.frameWidth = fh.frameWidthMinus1 + 1;
            fh.frameHeight = fh.frameHeightMinus1 + 1;
        } else {
            fh.frameWidth = seq.maxFrameWidthMinus1 + 1;
            fh.frameHeight = seq.maxFrameHeightMinus1 + 1;
        }
        parseSuperresParams(br, seq, fh);
        fh.miCols = 2 * ((fh.frameWidth + 7) >> 3);
        fh.miRows = 2 * ((fh.frameHeight + 7) >> 3);
    }

    /*
     * This method parses the superresolution parameters if superresolution is enabled.
     */
    private static void parseSuperresParams(BitReader br, OBPSequenceHeader seq, OBPFrameHeader fh) throws OBUParseException {
        if (seq.enableSuperres) {
            fh.superresParams.useSuperres = br.readBits(1) != 0;
        } else {
            fh.superresParams.useSuperres = false;
        }
        if (fh.superresParams.useSuperres) {
            fh.superresParams.codedDenom = (byte) br.readBits(3);
            fh.superresParams.superresDenom = fh.superresParams.codedDenom + 9;
        } else {
            fh.superresParams.superresDenom = 8;
        }
        fh.upscaledWidth = fh.frameWidth;
        fh.frameWidth = (fh.upscaledWidth * 8 + (fh.superresParams.superresDenom / 2)) / fh.superresParams.superresDenom;
    }

    /*
     * This method parses the render size, which may be different from the frame size.
     */
    private static void parseRenderSize(BitReader br, OBPFrameHeader fh) throws OBUParseException {
        fh.renderAndFrameSizeDifferent = br.readBits(1) != 0;
        if (fh.renderAndFrameSizeDifferent) {
            fh.renderWidthMinus1 = br.readBits(16);
            fh.renderHeightMinus1 = br.readBits(16);
            fh.renderWidth = fh.renderWidthMinus1 + 1;
            fh.renderHeight = fh.renderHeightMinus1 + 1;
        } else {
            fh.renderWidth = fh.upscaledWidth;
            fh.renderHeight = fh.frameHeight;
        }
    }

    private static void parseQuantizationParams(BitReader br, OBPFrameHeader fh, OBPSequenceHeader seq) throws OBUParseException {
        fh.quantizationParams.baseQIdx = br.readBits(8);
        fh.quantizationParams.deltaQYDc = readDeltaQ(br);
        if (seq.colorConfig.NumPlanes > 1) {
            if (seq.colorConfig.separateUvDeltaQ) {
                fh.quantizationParams.diffUvDelta = br.readBits(1) != 0;
            } else {
                fh.quantizationParams.diffUvDelta = false;
            }
            fh.quantizationParams.deltaQUDc = readDeltaQ(br);
            fh.quantizationParams.deltaQUAc = readDeltaQ(br);
            if (fh.quantizationParams.diffUvDelta) {
                fh.quantizationParams.deltaQVDc = readDeltaQ(br);
                fh.quantizationParams.deltaQVAc = readDeltaQ(br);
            } else {
                fh.quantizationParams.deltaQVDc = fh.quantizationParams.deltaQUDc;
                fh.quantizationParams.deltaQVAc = fh.quantizationParams.deltaQUAc;
            }
        } else {
            fh.quantizationParams.deltaQUDc = 0;
            fh.quantizationParams.deltaQUAc = 0;
            fh.quantizationParams.deltaQVDc = 0;
            fh.quantizationParams.deltaQVAc = 0;
        }
        fh.quantizationParams.usingQmatrix = br.readBits(1) != 0;
        if (fh.quantizationParams.usingQmatrix) {
            fh.quantizationParams.qmY = br.readBits(4);
            fh.quantizationParams.qmU = br.readBits(4);
            if (!seq.colorConfig.separateUvDeltaQ) {
                fh.quantizationParams.qmV = fh.quantizationParams.qmU;
            } else {
                fh.quantizationParams.qmV = br.readBits(4);
            }
        }
    }

    /*
     * This method is quite straightforward. It sets the referenceSelectInter flag in the frame header based on whether
     * the frame is intra or not. If the frame is not intra, it reads a single bit from the bitstream to determine the
     * value of referenceSelectInter.
     */
    private static void parseFrameReferenceMode(BitReader br, OBPFrameHeader fh, boolean FrameIsIntra) throws OBUParseException {
        if (FrameIsIntra) {
            fh.referenceSelectInter = false;
        } else {
            fh.referenceSelectInter = br.readBits(1) != 0;
        }
    }

    private static void parseSkipModeParams(BitReader br, OBPFrameHeader fh, OBPSequenceHeader seq, OBPState state, boolean frameIsIntra) throws OBUParseException {
        boolean skipModeAllowed;
        if (frameIsIntra || !fh.referenceSelectInter || !seq.enableOrderHint) {
            skipModeAllowed = false;
        } else {
            int forwardIdx = -1;
            int backwardIdx = -1;
            int forwardHint = 0;
            int backwardHint = 0;
            for (int i = 0; i < OBPConstants.REFS_PER_FRAME; i++) {
                int refHint = state.refOrderHint[fh.refFrameIdx[i]];
                if (getRelativeDist(refHint, fh.orderHint, seq) < 0) {
                    if (forwardIdx < 0 || getRelativeDist(refHint, forwardHint, seq) > 0) {
                        forwardIdx = i;
                        forwardHint = refHint;
                    }
                } else if (getRelativeDist(refHint, fh.orderHint, seq) > 0) {
                    if (backwardIdx < 0 || getRelativeDist(refHint, backwardHint, seq) < 0) {
                        backwardIdx = i;
                        backwardHint = refHint;
                    }
                }
            }
            if (forwardIdx < 0) {
                skipModeAllowed = false;
            } else if (backwardIdx >= 0) {
                skipModeAllowed = true;
                // SkipModeFrame not used in parsing, so we don't set it here
            } else {
                int secondForwardIdx = -1;
                int secondForwardHint = 0;
                for (int i = 0; i < OBPConstants.REFS_PER_FRAME; i++) {
                    int refHint = state.refOrderHint[fh.refFrameIdx[i]];
                    if (getRelativeDist(refHint, forwardHint, seq) < 0) {
                        if (secondForwardIdx < 0 || getRelativeDist(refHint, secondForwardHint, seq) > 0) {
                            secondForwardIdx = i;
                            secondForwardHint = refHint;
                        }
                    }
                }
                skipModeAllowed = secondForwardIdx >= 0;
                // SkipModeFrame not used in parsing, so we don't set it here
            }
        }
        if (skipModeAllowed) {
            fh.skipModePresent = br.readBits(1) != 0;
        } else {
            fh.skipModePresent = false;
        }
    }

    /*
     * This method performs a deep copy of a frame header.
     */
    private static void copyFrameHeader(OBPFrameHeader source, OBPFrameHeader dest) {
        // Implement a deep copy of all fields from source to dest
        // This is a simplified version, you'll need to copy all relevant fields
        dest.showExistingFrame = source.showExistingFrame;
        dest.frameType = source.frameType;
        dest.showFrame = source.showFrame;
        // TODO(paul) copy all other fields

        // For complex objects like filmGrainParams, use the copy method we defined earlier
        copyFilmGrainParams(source.filmGrainParams, dest.filmGrainParams);

        // For arrays, use System.arraycopy
        System.arraycopy(source.refFrameIdx, 0, dest.refFrameIdx, 0, source.refFrameIdx.length);
        // TODO(paul) copy other arrays

    }

    /*
     * This method computes whether the frame is losslessly coded.
     */
    private static boolean computeCodedLossless(OBPFrameHeader fh, OBPSequenceHeader seq) {
        for (int segmentId = 0; segmentId < 8; segmentId++) {
            int qindex = getQIndex(true, segmentId, fh.quantizationParams.baseQIdx, fh, seq);
            if (qindex != 0 || fh.quantizationParams.deltaQYDc != 0 || fh.quantizationParams.deltaQUAc != 0 || fh.quantizationParams.deltaQUDc != 0 || fh.quantizationParams.deltaQVAc != 0 || fh.quantizationParams.deltaQVDc != 0) {
                return false;
            }
        }
        return true;
    }

    /*
     * This method computes the quantization index for a given segment.
     */
    private static int getQIndex(boolean ignoreDeltaQ, int segmentId, int currentQIndex, OBPFrameHeader fh, OBPSequenceHeader seq) {
        if (fh.segmentationParams.segmentationEnabled && fh.segmentationParams.featureEnabled[segmentId][0]) {
            int data = fh.segmentationParams.featureData[segmentId][0];
            int qindex = fh.quantizationParams.baseQIdx + data;
            if (!ignoreDeltaQ && fh.deltaQParams.deltaQPresent) {
                qindex = currentQIndex + data;
            }
            return Math.max(0, Math.min(255, qindex));
        }
        if (!ignoreDeltaQ && fh.deltaQParams.deltaQPresent) {
            return currentQIndex;
        }
        return fh.quantizationParams.baseQIdx;
    }

    /*
     * obp_parse_frame parses a frame OBU and fills out the fields in user-provided OBPFrameHeader
     * and OBPTileGroup structures.
     *
     * Input:
     *     buf          - Input OBU buffer. This is expected to *NOT* contain the OBU header.
     *     buf_size     - Size of the input OBU buffer.
     *     state        - An opaque state structure. Must be zeroed by the user on first use.
     *     temporal_id  - A temporal ID previously obtained from obu_parse_sequence header.
     *     spatial_id   - A spatial ID previously obtained from obu_parse_sequence header.
     *     err          - An error buffer and buffer size to write any error messages into.
     *
     * Output:
     *     frame_header    - A user provided structure that will be filled in with all the parsed data.
     *     tile_group      - A user provided structure that will be filled in with all the parsed data.
     *     SeenFrameHeader - Whether or not a frame header has been seen. Tracking variable as per AV1 spec.
     *
     * Returns:
     *     0 on success, -1 on error.
     */
    public static void parseFrame(byte[] buf, int bufSize, OBPSequenceHeader seq, OBPState state, int temporalId, int spatialId, OBPFrameHeader fh, OBPTileGroup tileGroup, AtomicBoolean SeenFrameHeader) throws OBUParseException {
        int startBitPos = 0, endBitPos, headerBytes;
        int ret = parseFrameHeader(buf, bufSize, seq, state, temporalId, spatialId, fh, SeenFrameHeader);
        if (ret < 0) {
            throw new OBUParseException("Failed to parse frame header");
        }
        endBitPos = state.frameHeaderEndPos;
        headerBytes = (endBitPos - startBitPos) / 8;
        parseTileGroup(buf, headerBytes, bufSize - headerBytes, fh, tileGroup, SeenFrameHeader);
    }

    /*
    * obp_parse_tile_group parses a tile group OBU and fills out the fields in a
    * user-provided OBPTileGroup structure.
    *
    * Input:
    *     buf          - Input OBU buffer. This is expected to *NOT* contain the OBU header.
    *     buf_size     - Size of the input OBU buffer.
    *     frame_header - A filled in frame header OBU previously seen.
    *     err          - An error buffer and buffer size to write any error messages into.
    *
    * Output:
    *     tile_group      - A user provided structure that will be filled in with all the parsed data.
    *     SeenFrameHeader - Whether or not a frame header has been seen. Tracking variable as per AV1 spec.
    *
    * Returns:
    *     0 on success, -1 on error.
    */
    private static void parseTileGroup(byte[] buf, int offset, int size, OBPFrameHeader fh, OBPTileGroup tileGroup, AtomicBoolean SeenFrameHeader) throws OBUParseException {
        BitReader br = new BitReader(buf, offset, size);
        tileGroup.numTiles = (short) (fh.tileInfo.tileCols * fh.tileInfo.tileRows);
        long startBitPos = br.getPosition();
        tileGroup.tileStartAndEndPresentFlag = false;
        if (tileGroup.numTiles > 1) {
            tileGroup.tileStartAndEndPresentFlag = br.readBits(1) != 0;
        }
        if (tileGroup.numTiles == 1 || !tileGroup.tileStartAndEndPresentFlag) {
            tileGroup.tgStart = 0;
            tileGroup.tgEnd = (short) (tileGroup.numTiles - 1);
        } else {
            int tileBits = tileLog2(1, fh.tileInfo.tileCols) + tileLog2(1, fh.tileInfo.tileRows);
            tileGroup.tgStart = (short) br.readBits(tileBits);
            tileGroup.tgEnd = (short) br.readBits(tileBits);
        }
        br.byteAlignment();
        long endBitPos = br.getPosition();
        long headerBytes = (endBitPos - startBitPos) / 8;
        long sz = size - headerBytes;
        long pos = headerBytes;

        for (int tileNum = tileGroup.tgStart; tileNum <= tileGroup.tgEnd; tileNum++) {
            boolean lastTile = (tileNum == tileGroup.tgEnd);
            if (lastTile) {
                tileGroup.tileSize[tileNum] = sz;
            } else {
                int TileSizeBytes = fh.tileInfo.tileSizeBytesMinus1 + 1;
                long tileSizeMinus1;
                if (sz < TileSizeBytes) {
                    throw new OBUParseException("Not enough bytes left to read tile size for tile " + tileNum);
                }
                tileSizeMinus1 = readLe(buf, (int) (offset + pos), TileSizeBytes);
                tileGroup.tileSize[tileNum] = tileSizeMinus1 + 1;
                if (sz < tileGroup.tileSize[tileNum]) {
                    throw new OBUParseException("Not enough bytes to contain TileSize for tile " + tileNum);
                }
                sz -= tileGroup.tileSize[tileNum] + TileSizeBytes;
                pos += tileGroup.tileSize[tileNum] + TileSizeBytes;
            }
        }
        if (tileGroup.tgEnd == tileGroup.numTiles - 1) {
            SeenFrameHeader.set(false);
        }
    }

    /*
    * obp_parse_metadata parses a metadata OBU and fills out the fields in a user-provided OBPMetadata
    * structure. This OBU's returned payload is *NOT* safe to use once the user-provided 'buf' has
    * been freed, since it may fill the structure with pointers to offsets in that data.
    *
    * Input:
    *     buf      - Input OBU buffer. This is expected to *NOT* contain the OBU header.
    *     buf_size - Size of the input OBU buffer.
    *     err      - An error buffer and buffer size to write any error messages into.
    *
    * Output:
    *     metadata - A user provided structure that will be filled in with all the parsed data.
    *
    * Returns:
    *     0 on success, -1 on error.
    */
    public static OBPMetadata parseMetadata(byte[] buf, int bufSize) throws OBUParseException {
        OBPMetadata metadata = new OBPMetadata();
        int consumed;
        // int will only be 4 bytes long max
        int leb = LEB128.encode(Arrays.copyOfRange(buf, 0, 4));
        metadata.metadataType = OBPMetadataType.fromValue(leb);
        consumed = 4;
        BitReader br = new BitReader(buf, consumed, bufSize - consumed);
        switch (metadata.metadataType) {
            case HDR_CLL:
                metadata.metadataHdrCll = new OBPMetadata.MetadataHdrCll();
                metadata.metadataHdrCll.maxCll = (short) br.readBits(16);
                metadata.metadataHdrCll.maxFall = (short) br.readBits(16);
                break;
            case HDR_MDCV:
                metadata.metadataHdrMdcv = new OBPMetadata.MetadataHdrMdcv();
                for (int i = 0; i < 3; i++) {
                    metadata.metadataHdrMdcv.primaryChromaticityX[i] = (short) br.readBits(16);
                    metadata.metadataHdrMdcv.primaryChromaticityY[i] = (short) br.readBits(16);
                }
                metadata.metadataHdrMdcv.whitePointChromaticityX = (short) br.readBits(16);
                metadata.metadataHdrMdcv.whitePointChromaticityY = (short) br.readBits(16);
                metadata.metadataHdrMdcv.luminanceMax = br.readBits(32);
                metadata.metadataHdrMdcv.luminanceMin = br.readBits(32);
                break;
            case SCALABILITY:
                metadata.metadataScalability = new OBPMetadata.MetadataScalability();
                metadata.metadataScalability.scalabilityModeIdc = (byte) br.readBits(8);
                if (metadata.metadataScalability.scalabilityModeIdc != 0) {
                    metadata.metadataScalability.scalabilityStructure = new OBPMetadata.MetadataScalability.ScalabilityStructure();
                    parseScalabilityStructure(br, metadata.metadataScalability.scalabilityStructure);
                }
                break;
            case ITUT_T35:
                metadata.metadataItutT35 = new OBPMetadata.MetadataItutT35();
                metadata.metadataItutT35.ituTT35CountryCode = (byte) br.readBits(8);
                long offset = 1;
                if (metadata.metadataItutT35.ituTT35CountryCode == 0xFF) {
                    metadata.metadataItutT35.ituTT35CountryCodeExtensionByte = (byte) br.readBits(8);
                    offset++;
                }
                metadata.metadataItutT35.ituTT35PayloadBytes = Arrays.copyOfRange(buf, (int) (consumed + offset), buf.length);
                metadata.metadataItutT35.ituTT35PayloadBytesSize = findItuT35PayloadSize(metadata.metadataItutT35.ituTT35PayloadBytes);
                break;
            case TIMECODE:
                metadata.metadataTimecode = new OBPMetadata.MetadataTimecode();
                metadata.metadataTimecode.countingType = (byte) br.readBits(5);
                metadata.metadataTimecode.fullTimestampFlag = br.readBits(1) != 0;
                metadata.metadataTimecode.discontinuityFlag = br.readBits(1) != 0;
                metadata.metadataTimecode.cntDroppedFlag = br.readBits(1) != 0;
                metadata.metadataTimecode.nFrames = (short) br.readBits(9);
                if (metadata.metadataTimecode.fullTimestampFlag) {
                    metadata.metadataTimecode.secondsValue = (byte) br.readBits(6);
                    metadata.metadataTimecode.minutesValue = (byte) br.readBits(6);
                    metadata.metadataTimecode.hoursValue = (byte) br.readBits(5);
                } else {
                    metadata.metadataTimecode.secondsFlag = br.readBits(1) != 0;
                    if (metadata.metadataTimecode.secondsFlag) {
                        metadata.metadataTimecode.secondsValue = (byte) br.readBits(6);
                        metadata.metadataTimecode.minutesFlag = br.readBits(1) != 0;
                        if (metadata.metadataTimecode.minutesFlag) {
                            metadata.metadataTimecode.minutesValue = (byte) br.readBits(6);
                            metadata.metadataTimecode.hoursFlag = br.readBits(1) != 0;
                            if (metadata.metadataTimecode.hoursFlag) {
                                metadata.metadataTimecode.hoursValue = (byte) br.readBits(5);
                            }
                        }
                    }
                }
                metadata.metadataTimecode.timeOffsetLength = (byte) br.readBits(5);
                if (metadata.metadataTimecode.timeOffsetLength > 0) {
                    metadata.metadataTimecode.timeOffsetValue = br.readBits(metadata.metadataTimecode.timeOffsetLength);
                }
                break;
            default:
                if (metadata.metadataType.getValue() >= 6 && metadata.metadataType.getValue() <= 31) {
                    metadata.unregistered = new OBPMetadata.Unregistered();
                    metadata.unregistered.buf = Arrays.copyOfRange(buf, (int) consumed, buf.length);
                    metadata.unregistered.bufSize = bufSize - consumed;
                } else {
                    throw new OBUParseException("Invalid metadata type: " + metadata.metadataType.getValue());
                }
        }
        return metadata;
    }

    /*
    * obp_parse_tile_list parses a tile list OBU and fills out the fields in a user-provided OBPTileList
    * structure. This OBU's returned payload is *NOT* safe to use once the user-provided 'buf' has
    * been freed, since it may fill the structure with pointers to offsets in that data.
    *
    * Input:
    *     buf      - Input OBU buffer. This is expected to *NOT* contain the OBU header.
    *     buf_size - Size of the input OBU buffer.
    *     err      - An error buffer and buffer size to write any error messages into.
    *
    * Output:
    *     tile_list - A user provided structure that will be filled in with all the parsed data.
    *
    * Returns:
    *     0 on success, -1 on error.
    */
    public static OBPTileList parseTileList(byte[] buf, long bufSize) throws OBUParseException {
        OBPTileList tileList = new OBPTileList();
        int pos = 0;
        if (bufSize < 4) {
            throw new OBUParseException("Tile list OBU must be at least 4 bytes");
        }
        tileList.outputFrameWidthInTilesMinus1 = buf[0];
        tileList.outputFrameHeightInTilesMinus1 = buf[1];
        tileList.tileCountMinus1 = (short) (((buf[2] & 0xFF) << 8) | (buf[3] & 0xFF));
        pos += 4;
        tileList.tileListEntry = new OBPTileList.TileListEntry[tileList.tileCountMinus1 + 1];
        for (int i = 0; i <= tileList.tileCountMinus1; i++) {
            if (pos + 5 > bufSize) {
                throw new OBUParseException("Tile list OBU malformed: Not enough bytes for next tile_list_entry()");
            }
            OBPTileList.TileListEntry entry = new OBPTileList.TileListEntry();
            entry.anchorFrameIdx = buf[pos];
            entry.anchorTileRow = buf[pos + 1];
            entry.anchorTileCol = buf[pos + 2];
            entry.tileDataSizeMinus1 = (short) (((buf[pos + 3] & 0xFF) << 8) | (buf[pos + 4] & 0xFF));
            pos += 5;
            int N = 8 * (entry.tileDataSizeMinus1 + 1);
            if (pos + N > bufSize) {
                throw new OBUParseException("Tile list OBU malformed: Not enough bytes for next tile_list_entry()'s data");
            }
            entry.codedTileData = Arrays.copyOfRange(buf, pos, (pos + N));
            entry.codedTileDataSize = N;
            pos += N;
            tileList.tileListEntry[i] = entry;
        }
        return tileList;
    }

    private static void copyFilmGrainParams(OBPFilmGrainParameters source, OBPFilmGrainParameters dest) {
        dest.applyGrain = source.applyGrain;
        dest.grainSeed = source.grainSeed;
        dest.updateGrain = source.updateGrain;
        dest.filmGrainParamsRefIdx = source.filmGrainParamsRefIdx;
        dest.numYPoints = source.numYPoints;
        System.arraycopy(source.pointYValue, 0, dest.pointYValue, 0, source.pointYValue.length);
        System.arraycopy(source.pointYScaling, 0, dest.pointYScaling, 0, source.pointYScaling.length);
        dest.chromaScalingFromLuma = source.chromaScalingFromLuma;
        dest.numCbPoints = source.numCbPoints;
        System.arraycopy(source.pointCbValue, 0, dest.pointCbValue, 0, source.pointCbValue.length);
        System.arraycopy(source.pointCbScaling, 0, dest.pointCbScaling, 0, source.pointCbScaling.length);
        dest.numCrPoints = source.numCrPoints;
        System.arraycopy(source.pointCrValue, 0, dest.pointCrValue, 0, source.pointCrValue.length);
        System.arraycopy(source.pointCrScaling, 0, dest.pointCrScaling, 0, source.pointCrScaling.length);
        dest.grainScalingMinus8 = source.grainScalingMinus8;
        dest.arCoeffLag = source.arCoeffLag;
        System.arraycopy(source.arCoeffsYPlus128, 0, dest.arCoeffsYPlus128, 0, source.arCoeffsYPlus128.length);
        System.arraycopy(source.arCoeffsCbPlus128, 0, dest.arCoeffsCbPlus128, 0, source.arCoeffsCbPlus128.length);
        System.arraycopy(source.arCoeffsCrPlus128, 0, dest.arCoeffsCrPlus128, 0, source.arCoeffsCrPlus128.length);
        dest.arCoeffShiftMinus6 = source.arCoeffShiftMinus6;
        dest.grainScaleShift = source.grainScaleShift;
        dest.cbMult = source.cbMult;
        dest.cbLumaMult = source.cbLumaMult;
        dest.cbOffset = source.cbOffset;
        dest.crMult = source.crMult;
        dest.crLumaMult = source.crLumaMult;
        dest.crOffset = source.crOffset;
        dest.overlapFlag = source.overlapFlag;
        dest.clipToRestrictedRange = source.clipToRestrictedRange;
    }

    private static void setFrameRefs(OBPFrameHeader fh, OBPSequenceHeader seq, OBPState state) throws OBUParseException {
        int[] usedFrame = new int[8];
        long curFrameHint, lastOrderHint, goldOrderHint, latestOrderHint, earliestOrderHint;
        int ref;
        byte[] shiftedOrderHints = new byte[8];
        final int[] Ref_Frame_List = { 2, 3, 5, 6, 7 }; // LAST2_FRAME, LAST3_FRAME, BWDREF_FRAME, ALTREF2_FRAME, ALTREF_FRAME
        int[] refFrameIdx = new int[8];

        for (int i = 0; i < 7; i++) {
            refFrameIdx[i] = -1;
        }
        refFrameIdx[0] = fh.lastFrameIdx;
        refFrameIdx[3] = fh.goldFrameIdx;
        for (int i = 0; i < 8; i++) {
            usedFrame[i] = 0;
        }
        usedFrame[fh.lastFrameIdx] = 1;
        usedFrame[fh.goldFrameIdx] = 2;
        curFrameHint = 1L << (seq.OrderHintBits - 1);
        for (int i = 0; i < 8; i++) {
            shiftedOrderHints[i] = (byte) (curFrameHint + getRelativeDist(state.refOrderHint[i], fh.orderHint, seq));
        }
        lastOrderHint = shiftedOrderHints[fh.lastFrameIdx];
        goldOrderHint = shiftedOrderHints[fh.goldFrameIdx];
        if (lastOrderHint >= curFrameHint || goldOrderHint >= curFrameHint) {
            throw new OBUParseException("Invalid order hints");
        }

        // find_latest_backward()
        ref = -1;
        latestOrderHint = 0;
        for (int i = 0; i < 8; i++) {
            long hint = shiftedOrderHints[i];
            if (usedFrame[i] == 0 && hint >= curFrameHint && (ref < 0 || hint >= latestOrderHint)) {
                ref = i;
                latestOrderHint = hint;
            }
        }
        if (ref >= 0) {
            refFrameIdx[6] = ref;
            usedFrame[ref] = 1;
        }

        // find_earliest_backward()
        ref = -1;
        earliestOrderHint = 0;
        for (int i = 0; i < 8; i++) {
            long hint = shiftedOrderHints[i];
            if (usedFrame[i] == 0 && hint >= curFrameHint && (ref < 0 || hint < earliestOrderHint)) {
                ref = i;
                earliestOrderHint = hint;
            }
        }
        if (ref >= 0) {
            refFrameIdx[4] = ref;
            usedFrame[ref] = 1;
        }

        // find_earliest_backward()
        ref = -1;
        earliestOrderHint = 0;
        for (int i = 0; i < 8; i++) {
            long hint = shiftedOrderHints[i];
            if (usedFrame[i] == 0 && hint >= curFrameHint && (ref < 0 || hint < earliestOrderHint)) {
                ref = i;
                earliestOrderHint = hint;
            }
        }
        if (ref >= 0) {
            refFrameIdx[5] = ref;
            usedFrame[ref] = 1;
        }

        for (int i = 0; i < 5; i++) {
            int refFrame = Ref_Frame_List[i];
            if (refFrameIdx[refFrame - 1] < 0) {
                ref = -1;
                long latestOrderHintSubRef = 0;
                for (int j = 0; j < 8; j++) {
                    long hint = shiftedOrderHints[j];
                    if (usedFrame[j] == 0 && hint < curFrameHint && (ref < 0 || hint >= latestOrderHintSubRef)) {
                        ref = j;
                        latestOrderHintSubRef = hint;
                    }
                }
                if (ref >= 0) {
                    refFrameIdx[refFrame - 1] = ref;
                    usedFrame[ref] = 1;
                }
            }
        }

        ref = -1;
        for (int i = 0; i < 8; i++) {
            long hint = shiftedOrderHints[i];
            if (ref < 0 || hint < earliestOrderHint) {
                ref = i;
                earliestOrderHint = hint;
            }
        }
        for (int i = 0; i < 7; i++) {
            if (refFrameIdx[i] < 0) {
                refFrameIdx[i] = ref;
            }
        }
        for (int i = 0; i < 7; i++) {
            fh.refFrameIdx[i] = (byte) refFrameIdx[i];
        }
    }

    private static void setupPastIndependence(OBPFrameHeader fh) {
        for (int i = 1; i < 7; i++) {
            fh.globalMotionParams.gmType[i] = 0;
            for (int j = 0; j < 6; j++) {
                fh.globalMotionParams.gmParams[i][j] = (j % 3 == 2) ? (1 << 16) : 0;
            }
        }
        fh.loopFilterParams.loopFilterDeltaEnabled = true;
        fh.loopFilterParams.loopFilterRefDeltas[0] = 1;
        fh.loopFilterParams.loopFilterRefDeltas[1] = 0;
        fh.loopFilterParams.loopFilterRefDeltas[2] = 0;
        fh.loopFilterParams.loopFilterRefDeltas[3] = 0;
        fh.loopFilterParams.loopFilterRefDeltas[4] = 0;
        fh.loopFilterParams.loopFilterRefDeltas[5] = -1;
        fh.loopFilterParams.loopFilterRefDeltas[6] = -1;
        fh.loopFilterParams.loopFilterRefDeltas[7] = -1;
        for (int i = 0; i < 2; i++) {
            fh.loopFilterParams.loopFilterModeDeltas[i] = 0;
        }
    }

    /*
     * This method loads the previous frame's parameters into the current frame header.
     */
    private static void loadPrevious(OBPFrameHeader fh, OBPState state) {
        int prevFrame = fh.refFrameIdx[fh.primaryRefFrame];
        // Load global motion parameters
        for (int i = 0; i < OBPConstants.REFS_PER_FRAME; i++) {
            for (int j = 0; j < 6; j++) {
                fh.globalMotionParams.prevGmParams[i][j] = state.savedGmParams[prevFrame][i][j];
            }
        }
        // Load loop filter parameters
        for (int i = 0; i < OBPConstants.TOTAL_REFS_PER_FRAME; i++) {
            fh.loopFilterParams.loopFilterRefDeltas[i] = state.savedLoopFilterRefDeltas[prevFrame][i];
        }
        for (int i = 0; i < 2; i++) {
            fh.loopFilterParams.loopFilterModeDeltas[i] = state.savedLoopFilterModeDeltas[prevFrame][i];
        }
        // Load segmentation parameters
        fh.segmentationParams.segmentationEnabled = state.savedSegmentationParams[prevFrame].segmentationEnabled;
        fh.segmentationParams.segmentationUpdateMap = state.savedSegmentationParams[prevFrame].segmentationUpdateMap;
        fh.segmentationParams.segmentationTemporalUpdate = state.savedSegmentationParams[prevFrame].segmentationTemporalUpdate;
        fh.segmentationParams.segmentationUpdateData = state.savedSegmentationParams[prevFrame].segmentationUpdateData;
        for (int i = 0; i < OBPConstants.MAX_SEGMENTS; i++) {
            for (int j = 0; j < OBPConstants.SEG_LVL_MAX; j++) {
                fh.segmentationParams.featureEnabled[i][j] = state.savedFeatureEnabled[prevFrame][i][j];
                fh.segmentationParams.featureData[i][j] = state.savedFeatureData[prevFrame][i][j];
            }
        }
    }

    private static void parseGlobalMotionParams(BitReader br, OBPFrameHeader fh, boolean frameIsIntra) throws OBUParseException {
        for (int ref = 1; ref < 7; ref++) {
            fh.globalMotionParams.gmType[ref] = 0;
            for (int i = 0; i < 6; i++) {
                fh.globalMotionParams.gmParams[ref][i] = (i % 3 == 2) ? (1 << 16) : 0;
            }
        }
        if (!frameIsIntra) {
            for (int ref = 1; ref <= 7; ref++) {
                boolean isGlobal = br.readBits(1) != 0;
                if (isGlobal) {
                    boolean isRotZoom = br.readBits(1) != 0;
                    if (isRotZoom) {
                        fh.globalMotionParams.gmType[ref] = 2;
                    } else {
                        boolean isTranslation = br.readBits(1) != 0;
                        fh.globalMotionParams.gmType[ref] = (byte) (isTranslation ? 1 : 3);
                    }
                }
                if (fh.globalMotionParams.gmType[ref] >= 2) {
                    readGlobalParam(br, fh, fh.globalMotionParams.gmType[ref], ref, 2);
                    readGlobalParam(br, fh, fh.globalMotionParams.gmType[ref], ref, 3);
                    if (fh.globalMotionParams.gmType[ref] == 3) {
                        readGlobalParam(br, fh, fh.globalMotionParams.gmType[ref], ref, 4);
                        readGlobalParam(br, fh, fh.globalMotionParams.gmType[ref], ref, 5);
                    } else {
                        fh.globalMotionParams.gmParams[ref][4] = -fh.globalMotionParams.gmParams[ref][3];
                        fh.globalMotionParams.gmParams[ref][5] = fh.globalMotionParams.gmParams[ref][2];
                    }
                }
                if (fh.globalMotionParams.gmType[ref] >= 1) {
                    readGlobalParam(br, fh, fh.globalMotionParams.gmType[ref], ref, 0);
                    readGlobalParam(br, fh, fh.globalMotionParams.gmType[ref], ref, 1);
                }
            }
        }
    }

    private static void readGlobalParam(BitReader br, OBPFrameHeader fh, int type, int ref, int idx) throws OBUParseException {
        int absBits = 12;
        int precBits = 15;
        if (idx < 2) {
            if (type == 1) { // TRANSLATION
                absBits = 9 - (fh.allowHighPrecisionMv ? 0 : 1);
                precBits = 3 - (fh.allowHighPrecisionMv ? 0 : 1);
            } else {
                absBits = 12;
                precBits = 6;
            }
        }
        int precDiff = 16 - precBits;
        int round = (idx % 3) == 2 ? (1 << 16) : 0;
        int sub = (idx % 3) == 2 ? (1 << precBits) : 0;
        int mx = (1 << absBits);
        int r = (fh.globalMotionParams.prevGmParams[ref][idx] >> precDiff) - sub;
        int val = decodeSignedSubexpWithRef(br, -mx, mx + 1, r);
        if (val < 0) {
            val = -val;
            fh.globalMotionParams.gmParams[ref][idx] = -(val << precDiff) + round;
        } else {
            fh.globalMotionParams.gmParams[ref][idx] = (val << precDiff) + round;
        }
    }

    private static void parseFilmGrainParams(BitReader br, OBPFrameHeader fh, OBPSequenceHeader seq, OBPState state) throws OBUParseException {
        if (!seq.filmGrainParamsPresent || (!fh.showFrame && !fh.showableFrame)) {
            resetGrainParams(fh.filmGrainParams);
            return;
        }

        fh.filmGrainParams.applyGrain = br.readBits(1) != 0;

        if (!fh.filmGrainParams.applyGrain) {
            resetGrainParams(fh.filmGrainParams);
            return;
        }

        fh.filmGrainParams.grainSeed = (short) br.readBits(16);

        if (fh.frameType == OBPFrameType.INTERFRAME) {
            fh.filmGrainParams.updateGrain = br.readBits(1) != 0;
        } else {
            fh.filmGrainParams.updateGrain = true;
        }

        if (!fh.filmGrainParams.updateGrain) {
            fh.filmGrainParams.filmGrainParamsRefIdx = (byte) br.readBits(3);
            short tempGrainSeed = fh.filmGrainParams.grainSeed;
            fh.filmGrainParams = state.refGrainParams[fh.filmGrainParams.filmGrainParamsRefIdx];
            fh.filmGrainParams.grainSeed = tempGrainSeed;
            return;
        }

        fh.filmGrainParams.numYPoints = (byte) br.readBits(4);
        for (int i = 0; i < fh.filmGrainParams.numYPoints; i++) {
            fh.filmGrainParams.pointYValue[i] = (byte) br.readBits(8);
            fh.filmGrainParams.pointYScaling[i] = (byte) br.readBits(8);
        }

        if (seq.colorConfig.monoChrome) {
            fh.filmGrainParams.chromaScalingFromLuma = false;
        } else {
            fh.filmGrainParams.chromaScalingFromLuma = br.readBits(1) != 0;
        }

        if (seq.colorConfig.monoChrome || fh.filmGrainParams.chromaScalingFromLuma || (seq.colorConfig.subsamplingX && seq.colorConfig.subsamplingY && fh.filmGrainParams.numYPoints == 0)) {
            fh.filmGrainParams.numCbPoints = 0;
            fh.filmGrainParams.numCrPoints = 0;
        } else {
            fh.filmGrainParams.numCbPoints = (byte) br.readBits(4);
            for (int i = 0; i < fh.filmGrainParams.numCbPoints; i++) {
                fh.filmGrainParams.pointCbValue[i] = (byte) br.readBits(8);
                fh.filmGrainParams.pointCbScaling[i] = (byte) br.readBits(8);
            }
            fh.filmGrainParams.numCrPoints = (byte) br.readBits(4);
            for (int i = 0; i < fh.filmGrainParams.numCrPoints; i++) {
                fh.filmGrainParams.pointCrValue[i] = (byte) br.readBits(8);
                fh.filmGrainParams.pointCrScaling[i] = (byte) br.readBits(8);
            }
        }

        fh.filmGrainParams.grainScalingMinus8 = (byte) br.readBits(2);
        fh.filmGrainParams.arCoeffLag = (byte) br.readBits(2);

        int numPosLuma = 2 * fh.filmGrainParams.arCoeffLag * (fh.filmGrainParams.arCoeffLag + 1);
        int numPosChroma = numPosLuma;
        if (fh.filmGrainParams.numYPoints > 0) {
            numPosChroma = numPosLuma + 1;
            for (int i = 0; i < numPosLuma; i++) {
                fh.filmGrainParams.arCoeffsYPlus128[i] = (byte) br.readBits(8);
            }
        }
        if (fh.filmGrainParams.chromaScalingFromLuma || fh.filmGrainParams.numCbPoints > 0) {
            for (int i = 0; i < numPosChroma; i++) {
                fh.filmGrainParams.arCoeffsCbPlus128[i] = (byte) br.readBits(8);
            }
        }
        if (fh.filmGrainParams.chromaScalingFromLuma || fh.filmGrainParams.numCrPoints > 0) {
            for (int i = 0; i < numPosChroma; i++) {
                fh.filmGrainParams.arCoeffsCrPlus128[i] = (byte) br.readBits(8);
            }
        }

        fh.filmGrainParams.arCoeffShiftMinus6 = (byte) br.readBits(2);
        fh.filmGrainParams.grainScaleShift = (byte) br.readBits(2);

        if (fh.filmGrainParams.numCbPoints > 0) {
            fh.filmGrainParams.cbMult = (byte) br.readBits(8);
            fh.filmGrainParams.cbLumaMult = (byte) br.readBits(8);
            fh.filmGrainParams.cbOffset = (short) br.readBits(9);
        }

        if (fh.filmGrainParams.numCrPoints > 0) {
            fh.filmGrainParams.crMult = (byte) br.readBits(8);
            fh.filmGrainParams.crLumaMult = (byte) br.readBits(8);
            fh.filmGrainParams.crOffset = (short) br.readBits(9);
        }

        fh.filmGrainParams.overlapFlag = br.readBits(1) != 0;
        fh.filmGrainParams.clipToRestrictedRange = br.readBits(1) != 0;
    }

    private static void parseTileInfo(BitReader br, OBPFrameHeader fh, OBPSequenceHeader seq) throws OBUParseException {
        int sbCols = seq.use128x128Superblock ? ((fh.miCols + 31) >> 5) : ((fh.miCols + 15) >> 4);
        int sbRows = seq.use128x128Superblock ? ((fh.miRows + 31) >> 5) : ((fh.miRows + 15) >> 4);
        int sbShift = seq.use128x128Superblock ? 5 : 4;
        int sbSize = sbShift + 2;
        int maxTileWidthSb = 4096 >> sbSize;
        int maxTileAreaSb = (4096 * 2304) >> (2 * sbSize);
        int minLog2TileCols = tileLog2(maxTileWidthSb, sbCols);
        int maxLog2TileCols = tileLog2(1, Math.min(sbCols, 64));
        int maxLog2TileRows = tileLog2(1, Math.min(sbRows, 64));
        int minLog2Tiles = Math.max(minLog2TileCols, tileLog2(maxTileAreaSb, sbRows * sbCols));
        fh.tileInfo.uniformTileSpacingFlag = br.readBits(1) != 0;
        if (fh.tileInfo.uniformTileSpacingFlag) {
            fh.tileInfo.tileColsLog2 = minLog2TileCols;
            while (fh.tileInfo.tileColsLog2 < maxLog2TileCols) {
                int incrementTileColsLog2 = br.readBits(1);
                if (incrementTileColsLog2 == 1) {
                    fh.tileInfo.tileColsLog2++;
                } else {
                    break;
                }
            }
            int tileWidthSb = (sbCols + (1 << fh.tileInfo.tileColsLog2) - 1) >> fh.tileInfo.tileColsLog2;
            int i = 0;
            for (int startSb = 0; startSb < sbCols; startSb += tileWidthSb) {
                i++;
            }
            fh.tileInfo.tileCols = i;
            int minLog2TileRows = Math.max(minLog2Tiles - fh.tileInfo.tileColsLog2, 0);
            fh.tileInfo.tileRowsLog2 = minLog2TileRows;
            while (fh.tileInfo.tileRowsLog2 < maxLog2TileRows) {
                int incrementTileRowsLog2 = br.readBits(1);
                if (incrementTileRowsLog2 == 1) {
                    fh.tileInfo.tileRowsLog2++;
                } else {
                    break;
                }
            }
            int tileHeightSb = (sbRows + (1 << fh.tileInfo.tileRowsLog2) - 1) >> fh.tileInfo.tileRowsLog2;
            i = 0;
            for (int startSb = 0; startSb < sbRows; startSb += tileHeightSb) {
                i++;
            }
            fh.tileInfo.tileRows = i;
        } else {
            int widestTileSb = 0;
            int startSb = 0;
            int i;
            for (i = 0; startSb < sbCols; i++) {
                int maxWidth = Math.min(sbCols - startSb, maxTileWidthSb);
                int widthInSbs = (int) readNs(br, maxWidth) + 1;
                widestTileSb = Math.max(widestTileSb, widthInSbs);
                startSb += widthInSbs;
            }
            fh.tileInfo.tileCols = i;
            fh.tileInfo.tileColsLog2 = tileLog2(1, fh.tileInfo.tileCols);

            if (minLog2Tiles > 0) {
                maxTileAreaSb = (sbRows * sbCols) >> (minLog2Tiles + 1);
            } else {
                maxTileAreaSb = sbRows * sbCols;
            }
            int maxTileHeightSb = Math.max(maxTileAreaSb / widestTileSb, 1);

            startSb = 0;
            for (i = 0; startSb < sbRows; i++) {
                int maxHeight = Math.min(sbRows - startSb, maxTileHeightSb);
                int heightInSbs = (int) readNs(br, maxHeight) + 1;
                startSb += heightInSbs;
            }
            fh.tileInfo.tileRows = i;
            fh.tileInfo.tileRowsLog2 = tileLog2(1, fh.tileInfo.tileRows);
        }
        if (fh.tileInfo.tileColsLog2 > 0 || fh.tileInfo.tileRowsLog2 > 0) {
            fh.tileInfo.contextUpdateTileId = br.readBits(fh.tileInfo.tileColsLog2 + fh.tileInfo.tileRowsLog2);
            fh.tileInfo.tileSizeBytesMinus1 = br.readBits(2);
        } else {
            fh.tileInfo.contextUpdateTileId = 0;
        }
    }

    private static void resetGrainParams(OBPFilmGrainParameters params) {
        params.applyGrain = false;
        params.grainSeed = 0;
        params.updateGrain = false;
        params.filmGrainParamsRefIdx = 0;
        params.numYPoints = 0;
        Arrays.fill(params.pointYValue, (byte) 0);
        Arrays.fill(params.pointYScaling, (byte) 0);
        params.chromaScalingFromLuma = false;
        params.numCbPoints = 0;
        Arrays.fill(params.pointCbValue, (byte) 0);
        Arrays.fill(params.pointCbScaling, (byte) 0);
        params.numCrPoints = 0;
        Arrays.fill(params.pointCrValue, (byte) 0);
        Arrays.fill(params.pointCrScaling, (byte) 0);
        params.grainScalingMinus8 = 0;
        params.arCoeffLag = 0;
        Arrays.fill(params.arCoeffsYPlus128, (byte) 0);
        Arrays.fill(params.arCoeffsCbPlus128, (byte) 0);
        Arrays.fill(params.arCoeffsCrPlus128, (byte) 0);
        params.arCoeffShiftMinus6 = 0;
        params.grainScaleShift = 0;
        params.cbMult = 0;
        params.cbLumaMult = 0;
        params.cbOffset = 0;
        params.crMult = 0;
        params.crLumaMult = 0;
        params.crOffset = 0;
        params.overlapFlag = false;
        params.clipToRestrictedRange = false;
    }

    private static int decodeSignedSubexpWithRef(BitReader br, int low, int high, int r) throws OBUParseException {
        int x = decodeUnsignedSubexpWithRef(br, high - low, r - low);
        return x + low;
    }

    private static int decodeUnsignedSubexpWithRef(BitReader br, int mx, int r) throws OBUParseException {
        int v;
        if ((r << 1) <= mx) {
            v = decodeSubexp(br, mx);
            if (v < r) {
                return v;
            } else {
                return mx - 1 - v + r;
            }
        } else {
            v = decodeSubexp(br, mx);
            if (v < (mx - r)) {
                return r + v;
            } else {
                return v - (mx - r);
            }
        }
    }

    private static int decodeSubexp(BitReader br, int numSyms) throws OBUParseException {
        int i = 0;
        int mk = 0;
        int k = 3;
        while (true) {
            int b2 = i != 0 ? k + i - 1 : k;
            int a = 1 << b2;
            if (numSyms <= mk + 3 * a) {
                return (int) readNs(br, numSyms - mk) + mk;
            } else {
                boolean subexpMoreBits = br.readBits(1) != 0;
                if (subexpMoreBits) {
                    i++;
                    mk += a;
                } else {
                    return (int) br.readBits(b2) + mk;
                }
            }
        }
    }

    private static long readNs(BitReader br, long n) throws OBUParseException {
        if (n == 0) {
            return 0;
        }
        int w = floorLog2(n) + 1;
        long m = (1L << w) - n;
        long v = br.readBits(w - 1);
        if (v < m) {
            return v;
        }
        long extraBit = br.readBits(1);
        return (v << 1) - m + extraBit;
    }

    private static int floorLog2(long n) {
        return 63 - Long.numberOfLeadingZeros(n);
    }

    private static int tileLog2(int blkSize, int target) {
        int k = 0;
        for (; (blkSize << k) < target; k++)
            ;
        return k;
    }

    private static void parseScalabilityStructure(BitReader br, OBPMetadata.MetadataScalability.ScalabilityStructure structure) throws OBUParseException {
        structure.spatialLayersCntMinus1 = (byte) br.readBits(2);
        structure.spatialLayerDimensionsPresentFlag = br.readBits(1) != 0;
        structure.spatialLayerDescriptionPresentFlag = br.readBits(1) != 0;
        structure.temporalGroupDescriptionPresentFlag = br.readBits(1) != 0;
        structure.scalabilityStructureReserved3bits = (byte) br.readBits(3);
        if (structure.spatialLayerDimensionsPresentFlag) {
            for (int i = 0; i <= structure.spatialLayersCntMinus1; i++) {
                structure.spatialLayerMaxWidth[i] = (short) br.readBits(16);
                structure.spatialLayerMaxHeight[i] = (short) br.readBits(16);
            }
        }
        if (structure.spatialLayerDescriptionPresentFlag) {
            for (int i = 0; i <= structure.spatialLayersCntMinus1; i++) {
                structure.spatialLayerRefId[i] = (byte) br.readBits(8);
            }
        }
        if (structure.temporalGroupDescriptionPresentFlag) {
            structure.temporalGroupSize = (byte) br.readBits(8);
            for (int i = 0; i < structure.temporalGroupSize; i++) {
                structure.temporalGroupTemporalId[i] = (byte) br.readBits(3);
                structure.temporalGroupTemporalSwitchingUpPointFlag[i] = br.readBits(1) != 0;
                structure.temporalGroupSpatialSwitchingUpPointFlag[i] = br.readBits(1) != 0;
                structure.temporalGroupRefCnt[i] = (byte) br.readBits(3);
                for (int j = 0; j < structure.temporalGroupRefCnt[i]; j++) {
                    structure.temporalGroupRefPicDiff[i][j] = (byte) br.readBits(8);
                }
            }
        }
    }

    private static long findItuT35PayloadSize(byte[] payload) {
        int nonZeroBytesCount = 0;
        for (int i = payload.length - 1; i >= 0; i--) {
            if (payload[i] != 0) {
                nonZeroBytesCount++;
                if (nonZeroBytesCount == 2) {
                    return i + 1;
                }
            }
        }
        return payload.length;
    }

    private static long readLe(byte[] buf, int offset, int n) {
        long t = 0;
        for (int i = 0; i < n; i++) {
            t |= ((long) (buf[offset + i] & 0xFF)) << (i * 8);
        }
        return t;
    }

    private static void parseSegmentationParams(BitReader br, OBPFrameHeader fh, OBPSequenceHeader seq) throws OBUParseException {
        fh.segmentationParams.segmentationEnabled = br.readBits(1) != 0;
        if (fh.segmentationParams.segmentationEnabled) {
            if (fh.primaryRefFrame == 7) {
                fh.segmentationParams.segmentationUpdateMap = true;
                fh.segmentationParams.segmentationTemporalUpdate = false;
                fh.segmentationParams.segmentationUpdateData = true;
            } else {
                fh.segmentationParams.segmentationUpdateMap = br.readBits(1) != 0;
                if (fh.segmentationParams.segmentationUpdateMap) {
                    fh.segmentationParams.segmentationTemporalUpdate = br.readBits(1) != 0;
                }
                fh.segmentationParams.segmentationUpdateData = br.readBits(1) != 0;
            }
            if (fh.segmentationParams.segmentationUpdateData) {
                for (int i = 0; i < 8; i++) {
                    for (int j = 0; j < 8; j++) {
                        int featureEnabled = br.readBits(1);
                        fh.segmentationParams.featureEnabled[i][j] = featureEnabled != 0;
                        if (featureEnabled != 0) {
                            int bitsToRead = OBPConstants.Segmentation_Feature_Bits[j];
                            int limit = OBPConstants.Segmentation_Feature_Max[j];
                            int featureValue;
                            if (OBPConstants.Segmentation_Feature_Signed[j] != 0) {
                                featureValue = br.readBits(1 + bitsToRead);
                                if ((featureValue & (1 << bitsToRead)) != 0) {
                                    featureValue = featureValue - (1 << (bitsToRead + 1));
                                }
                            } else {
                                featureValue = br.readBits(bitsToRead);
                            }
                            fh.segmentationParams.featureData[i][j] = (short) Math.max(-limit, Math.min(limit, featureValue));
                        } else {
                            fh.segmentationParams.featureData[i][j] = 0;
                        }
                    }
                }
            }
        } else {
            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 8; j++) {
                    fh.segmentationParams.featureEnabled[i][j] = false;
                    fh.segmentationParams.featureData[i][j] = 0;
                }
            }
        }
    }

    /*
     * This method reads a delta Q value from the bitstream. It first reads a single bit to determine if the delta is
     * coded. If it is, it reads a signed value using 7 bits. If not, it returns 0.
     */
    private static int readDeltaQ(BitReader br) throws OBUParseException {
        int delta_coded = br.readBits(1);
        if (delta_coded != 0) {
            return br.readBits(7) - 1;
        } else {
            return 0;
        }
    }

    private static void parseDeltaQParams(BitReader br, OBPFrameHeader fh) throws OBUParseException {
        fh.deltaQParams.deltaQRes = 0;
        fh.deltaQParams.deltaQPresent = false;
        if (fh.quantizationParams.baseQIdx > 0) {
            fh.deltaQParams.deltaQPresent = br.readBits(1) != 0;
        }
        if (fh.deltaQParams.deltaQPresent) {
            fh.deltaQParams.deltaQRes = (byte) br.readBits(2);
        }
    }

    private static void parseDeltaLfParams(BitReader br, OBPFrameHeader fh, OBPSequenceHeader seq) throws OBUParseException {
        fh.deltaLfParams.deltaLfPresent = false;
        fh.deltaLfParams.deltaLfRes = 0;
        fh.deltaLfParams.deltaLfMulti = false;
        if (fh.deltaQParams.deltaQPresent) {
            if (!fh.allowIntrabc) {
                fh.deltaLfParams.deltaLfPresent = br.readBits(1) != 0;
            }
            if (fh.deltaLfParams.deltaLfPresent) {
                fh.deltaLfParams.deltaLfRes = (byte) br.readBits(2);
                fh.deltaLfParams.deltaLfMulti = br.readBits(1) != 0;
            }
        }
    }

    private static void parseLoopFilterParams(BitReader br, OBPFrameHeader fh, OBPSequenceHeader seq) throws OBUParseException {
        if (fh.codedLossless || fh.allowIntrabc) {
            fh.loopFilterParams.loopFilterDeltaEnabled = true;
            fh.loopFilterParams.loopFilterRefDeltas[0] = 1;
            fh.loopFilterParams.loopFilterRefDeltas[1] = 0;
            fh.loopFilterParams.loopFilterRefDeltas[2] = 0;
            fh.loopFilterParams.loopFilterRefDeltas[3] = 0;
            fh.loopFilterParams.loopFilterRefDeltas[4] = 0;
            fh.loopFilterParams.loopFilterRefDeltas[5] = -1;
            fh.loopFilterParams.loopFilterRefDeltas[6] = -1;
            fh.loopFilterParams.loopFilterRefDeltas[7] = -1;
            for (int i = 0; i < 2; i++) {
                fh.loopFilterParams.loopFilterModeDeltas[i] = 0;
            }
            return;
        }

        fh.loopFilterParams.loopFilterLevel[0] = (byte) br.readBits(6);
        fh.loopFilterParams.loopFilterLevel[1] = (byte) br.readBits(6);
        if (seq.colorConfig.NumPlanes > 1) {
            if (fh.loopFilterParams.loopFilterLevel[0] != 0 || fh.loopFilterParams.loopFilterLevel[1] != 0) {
                fh.loopFilterParams.loopFilterLevel[2] = (byte) br.readBits(6);
                fh.loopFilterParams.loopFilterLevel[3] = (byte) br.readBits(6);
            }
        }
        fh.loopFilterParams.loopFilterSharpness = (byte) br.readBits(3);
        fh.loopFilterParams.loopFilterDeltaEnabled = br.readBits(1) != 0;
        if (fh.loopFilterParams.loopFilterDeltaEnabled) {
            fh.loopFilterParams.loopFilterDeltaUpdate = br.readBits(1) != 0;
            if (fh.loopFilterParams.loopFilterDeltaUpdate) {
                for (int i = 0; i < 8; i++) {
                    fh.loopFilterParams.updateRefDelta[i] = br.readBits(1) != 0;
                    if (fh.loopFilterParams.updateRefDelta[i]) {
                        fh.loopFilterParams.loopFilterRefDeltas[i] = (byte) br.readBits(7);
                        if ((fh.loopFilterParams.loopFilterRefDeltas[i] & 0x40) != 0) {
                            fh.loopFilterParams.loopFilterRefDeltas[i] -= 128;
                        }
                    }
                }
                for (int i = 0; i < 2; i++) {
                    fh.loopFilterParams.updateModeDelta[i] = br.readBits(1) != 0;
                    if (fh.loopFilterParams.updateModeDelta[i]) {
                        fh.loopFilterParams.loopFilterModeDeltas[i] = (byte) br.readBits(7);
                        if ((fh.loopFilterParams.loopFilterModeDeltas[i] & 0x40) != 0) {
                            fh.loopFilterParams.loopFilterModeDeltas[i] -= 128;
                        }
                    }
                }
            }
        }
    }

    private static void parseCdefParams(BitReader br, OBPFrameHeader fh, OBPSequenceHeader seq) throws OBUParseException {
        if (fh.codedLossless || fh.allowIntrabc || !seq.enableCdef) {
            fh.cdefParams.cdefBits = 0;
            fh.cdefParams.cdefYPriStrength[0] = 0;
            fh.cdefParams.cdefYSecStrength[0] = 0;
            fh.cdefParams.cdefUvPriStrength[0] = 0;
            fh.cdefParams.cdefUvSecStrength[0] = 0;
            return;
        }

        fh.cdefParams.cdefDampingMinus3 = (byte) br.readBits(2);
        fh.cdefParams.cdefBits = (byte) br.readBits(2);
        for (int i = 0; i < (1 << fh.cdefParams.cdefBits); i++) {
            fh.cdefParams.cdefYPriStrength[i] = (byte) br.readBits(4);
            fh.cdefParams.cdefYSecStrength[i] = (byte) br.readBits(2);
            if (fh.cdefParams.cdefYSecStrength[i] == 3) {
                fh.cdefParams.cdefYSecStrength[i] += 1;
            }
            if (seq.colorConfig.NumPlanes > 1) {
                fh.cdefParams.cdefUvPriStrength[i] = (byte) br.readBits(4);
                fh.cdefParams.cdefUvSecStrength[i] = (byte) br.readBits(2);
                if (fh.cdefParams.cdefUvSecStrength[i] == 3) {
                    fh.cdefParams.cdefUvSecStrength[i] += 1;
                }
            }
        }
    }

    private static void parseLrParams(BitReader br, OBPFrameHeader fh, OBPSequenceHeader seq) throws OBUParseException {
        if (fh.allLossless || fh.allowIntrabc || !seq.enableRestoration) {
            fh.lrParams.lrType[0] = 0;
            fh.lrParams.lrType[1] = 0;
            fh.lrParams.lrType[2] = 0;
            return;
        }
        boolean usesLr = false;
        boolean usesChromaLr = false;
        for (int i = 0; i < seq.colorConfig.NumPlanes; i++) {
            fh.lrParams.lrType[i] = (byte) br.readBits(2);
            if (fh.lrParams.lrType[i] != 0) {
                usesLr = true;
                if (i > 0) {
                    usesChromaLr = true;
                }
            }
        }
        if (usesLr) {
            if (seq.use128x128Superblock) {
                fh.lrParams.lrUnitShift = (byte) (br.readBits(1) + 1);
            } else {
                fh.lrParams.lrUnitShift = (byte) br.readBits(1);
                if (fh.lrParams.lrUnitShift != 0) {
                    fh.lrParams.lrUnitShift += br.readBits(1);
                }
            }
            // LoopRestorationSize is not directly used in parsing, so we don't set it here
            if (seq.colorConfig.subsamplingX && seq.colorConfig.subsamplingY && usesChromaLr) {
                fh.lrParams.lrUvShift = br.readBits(1) != 0;
            } else {
                fh.lrParams.lrUvShift = false;
            }
        }
    }

    private static int getRelativeDist(int a, int b, OBPSequenceHeader seq) {
        if (!seq.enableOrderHint) {
            return 0;
        }
        int diff = a - b;
        int m = 1 << (seq.OrderHintBits - 1);
        diff = (diff & (m - 1)) - (diff & m);
        return diff;
    }

    /**
     * Returns true if the given OBU type value is valid.
     *
     * @param type the OBU type value
     * @return true if the given OBU type value is valid
     */
    public static boolean isValidObu(int type) {
        return VALID_OBU_TYPES.contains(type);
    }

    /**
     * Returns true if the given OBU type is valid.
     *
     * @param type the OBU type
     * @return true if the given OBU type is valid
     */
    public static boolean isValidObu(OBUType type) {
        switch (type) {
            case SEQUENCE_HEADER:
            case TEMPORAL_DELIMITER:
            case FRAME_HEADER:
            case TILE_GROUP:
            case METADATA:
            case FRAME:
            case REDUNDANT_FRAME_HEADER:
            case TILE_LIST:
            case PADDING:
                return true;
            default:
                return false;
        }
    }

    private static long readUvlc(BitReader br) throws OBUParseException {
        int leadingZeros = 0;
        while (leadingZeros < 32 && br.readBits(1) == 0) {
            leadingZeros++;
        }
        if (leadingZeros == 32) {
            throw new OBUParseException("Invalid UVLC code");
        }
        return br.readBits(leadingZeros) + ((1L << leadingZeros) - 1);
    }

    /**
     * Returns true if the given byte starts a fragment.
     *
     * @param aggregationHeader
     * @return
     */
    public static boolean startsWithFragment(byte aggregationHeader) {
        return (aggregationHeader & OBU_START_FRAGMENT_BIT) != 0;
    }

    /**
     * Returns true if the given byte ends a fragment.
     *
     * @param aggregationHeader
     * @return
     */
    public static boolean endsWithFragment(byte aggregationHeader) {
        return (aggregationHeader & OBU_END_FRAGMENT_BIT) != 0;
    }

    /**
     * Returns true if the given byte is the starts a new sequence.
     *
     * @param aggregationHeader
     * @return
     */
    public static boolean startsNewCodedVideoSequence(byte aggregationHeader) {
        return (aggregationHeader & OBU_START_SEQUENCE_BIT) != 0;
    }

    /**
     * Returns expected number of OBU's.
     *
     * @param aggregationHeader
     * @return
     */
    public static int obuCount(byte aggregationHeader) {
        return (aggregationHeader & OBU_COUNT_MASK) >> 4;
    }

    /**
     * Returns the OBU type from the given byte.
     *
     * @param obuHeader
     * @return
     */
    public static int obuType(byte obuHeader) {
        return (obuHeader & OBU_TYPE_MASK) >>> 3;
    }

    /**
     * Returns whether or not the OBU has an extension.
     *
     * @param obuHeader
     * @return
     */
    public static boolean obuHasExtension(byte obuHeader) {
        return (obuHeader & OBU_EXT_BIT) != 0;
    }

    /**
     * Returns whether or not the OBU has a size.
     *
     * @param obuHeader
     * @return
     */
    public static boolean obuHasSize(byte obuHeader) {
        return (obuHeader & OBU_SIZE_PRESENT_BIT) != 0;
    }

    public static int readUVarint(ByteBuffer buffer) {
        int value = 0;
        int shift = 0;
        while (buffer.hasRemaining()) {
            byte b = buffer.get();
            value |= (b & 0x7F) << shift;
            if ((b & LEB128.MSB_BITMASK) == 0) {
                return value;
            }
            shift += 7;
            if (shift >= 64) {
                return -1; // overflow
            }
        }
        return -1; // incomplete
    }

    public static int[] readUVarint(byte[] buffer) {
        // index 0 is the value, index 1 is the number of bytes read
        int[] result = new int[] { 0, -1 };
        int shift = 0;
        for (int i = 0; i < buffer.length; i++) {
            byte b = buffer[i];
            // value entry
            result[0] |= (b & 0x7F) << shift;
            if ((b & LEB128.MSB_BITMASK) == 0) {
                result[1] = i + 1;
                break;
            }
            shift += 7;
            if (shift >= 64) {
                result[1] = -1; // overflow
                break;
            }
        }
        return result; // if index 1 == -1 we're incomplete or overflowed
    }

    public static int[] readUVarint(byte[] buffer, int offset) {
        // index 0 is the value, index 1 is the number of bytes read
        int[] result = new int[] { 0, -1 };
        int shift = 0;
        for (int i = offset; i < buffer.length; i++) {
            byte b = buffer[i];
            // value entry
            result[0] |= (b & 0x7F) << shift;
            if ((b & LEB128.MSB_BITMASK) == 0) {
                result[1] = i + 1;
                break;
            }
            shift += 7;
            if (shift >= 64) {
                result[1] = -1; // overflow
                break;
            }
        }
        return result; // if index 1 == -1 we're incomplete or overflowed
    }

}