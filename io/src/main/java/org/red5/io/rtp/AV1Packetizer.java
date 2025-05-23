package org.red5.io.rtp;

import static org.red5.io.obu.OBPConstants.N_MASK;
import static org.red5.io.obu.OBPConstants.OBU_FRAME_TYPE_BITSHIFT;
import static org.red5.io.obu.OBPConstants.OBU_FRAME_TYPE_MASK;
import static org.red5.io.obu.OBPConstants.OBU_FRAME_TYPE_SEQUENCE_HEADER;
import static org.red5.io.obu.OBPConstants.W_BITSHIFT;
import static org.red5.io.obu.OBPConstants.Y_MASK;
import static org.red5.io.obu.OBPConstants.Z_MASK;

import java.util.LinkedList;
import java.util.List;

import org.bouncycastle.util.Arrays;
import org.red5.io.obu.OBUInfo;
import org.red5.io.obu.OBUParser;
import org.red5.io.obu.OBUType;
import org.red5.io.utils.LEB128;
import org.red5.io.utils.LEB128.LEB128Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AV1 packetizer provides methods to packetize and depacketize AV1 payloads.
 *
 * @see <a href="https://aomediacodec.github.io/av1-rtp-spec/">AV1 RTP Specification</a>
 * @see <a href="https://chromium.googlesource.com/external/webrtc/+/HEAD/modules/rtp_rtcp/source/video_rtp_depacketizer_av1.cc">WebRTC AV1 RTP Depacketizer</a>
 *
 * Thanks to the Alliance for Open Media for providing the AV1 RTP specification and Pion for the Go implementation.
 * @author Paul Gregoire
 */
public class AV1Packetizer {

    private static final Logger logger = LoggerFactory.getLogger(AV1Packetizer.class);

    private static final boolean isTrace = logger.isTraceEnabled(), isDebug = logger.isDebugEnabled();

    /*
        AV1 format:

        RTP payload syntax:
             0 1 2 3 4 5 6 7
            +-+-+-+-+-+-+-+-+
            |Z|Y| W |N|-|-|-| (REQUIRED)
            +=+=+=+=+=+=+=+=+ (REPEATED W-1 times, or any times if W = 0)
            |1|             |
            +-+ OBU fragment|
            |1|             | (REQUIRED, leb128 encoded)
            +-+    size     |
            |0|             |
            +-+-+-+-+-+-+-+-+
            |  OBU fragment |
            |     ...       |
            +=+=+=+=+=+=+=+=+
            |     ...       |
            +=+=+=+=+=+=+=+=+ if W > 0, last fragment MUST NOT have size field
            |  OBU fragment |
            |     ...       |
            +=+=+=+=+=+=+=+=+

        OBU syntax:
             0 1 2 3 4 5 6 7
            +-+-+-+-+-+-+-+-+
            |0| type  |X|S|-| (REQUIRED)
            +-+-+-+-+-+-+-+-+
         X: | TID |SID|-|-|-| (OPTIONAL)
            +-+-+-+-+-+-+-+-+
            |1|             |
            +-+ OBU payload |
         S: |1|             | (OPTIONAL, variable length leb128 encoded)
            +-+    size     |
            |0|             |
            +-+-+-+-+-+-+-+-+
            |  OBU payload  |
            |     ...       |
    */

    /*
      Z: MUST be set to 1 if the first OBU element is an OBU fragment that is a continuation of an OBU fragment from
         the previous packet, and MUST be set to 0 otherwise.
      Y: MUST be set to 1 if the last OBU element is an OBU fragment that will continue in the next packet, and MUST be
         set to 0 otherwise.
      N: MUST be set to 1 if the packet is the first packet of a coded video sequence, and MUST be set to 0 otherwise.
    */
    public boolean Z, Y, N;

    /*
      W: Two bit field that describes the number of OBU elements in the packet. This field MUST be set equal to 0 or
          equal to the number of OBU elements contained in the packet. If set to 0, each OBU element MUST be preceded
          by a length field. If not set to 0 (W = 1, 2 or 3) the last OBU element MUST NOT be preceded by length field.
          Instead, the length of the last OBU element contained in the packet can be calculated as follows:
            Length of the last OBU element =
              length of the RTP payload
              length of aggregation header
              length of previous OBU elements including length fields
    */
    public byte W;

    // Collection of OBU Elements; each OBU Element may be a full OBU, or just a fragment of one.
    private List<byte[]> OBUElements;

    // Aggregation item: Z first packet in frame is fragment
    private boolean firstPacketInFrame;

    // Aggregation item: Y last packet in frame is fragment
    private boolean lastPacketInFrame;

    // Aggregation item: N start sequence
    private boolean startSequence;

    private byte[] sequenceHeader;

    /**
     * Depacketizes an AV1 payload; this contains an aggregation header and one or more OBU elements.
     *
     * @param payload AV1 payload
     * @return number of OBU elements
     * @throws java.lang.Exception on error
     */
    public int depacketize(byte[] payload) throws Exception {
        if (payload == null) {
            throw new Exception("Null packet");
        } else if (payload.length < 2) {
            throw new Exception("Short packet");
        }
        // aggregate header byte is used to indicate if the first and/or last OBU element in the payload is a fragment
        // of an OBU
        firstPacketInFrame = OBUParser.startsWithFragment(payload[0]); // Z first packet in frame is fragment
        lastPacketInFrame = OBUParser.endsWithFragment(payload[0]); // Y last packet in frame is fragment
        startSequence = OBUParser.startsNewCodedVideoSequence(payload[0]); // N
        // obu's in the payload
        int obuCount = OBUParser.obuCount(payload[0]); // W
        if (isDebug) {
            String bits = String.format("%8s", Integer.toBinaryString(0xff & payload[0])).replace(' ', '0');
            logger.debug("Depacketize - first packet in frame: {}, last packet in frame: {}, start sequence: {} count: {} bits: {}", firstPacketInFrame, lastPacketInFrame, startSequence, obuCount, bits);
        }
        if (firstPacketInFrame && startSequence) {
            throw new Exception("Packet cannot be both a fragment and keyframe");
        }
        // ensure storage for the OBU elements
        if (OBUElements == null) {
            OBUElements = new LinkedList<>();
        } else if (startSequence) {
            // if we start a new frame, clear the OBU elements
            OBUElements.clear();
        }
        // example: [ SH MD MD(0,0) FH(0,0) TG(0,0) ] [ MD(0,1) FH(0,1) TG(0,1) ]
        // parse the bodies
        int currentIndex = 1; // skip the aggregation header
        // first obu is index 1
        for (int obuIndex = 1; currentIndex < payload.length; obuIndex++) {
            OBUType obuType = null;
            int obuFragmentLength = (payload.length - currentIndex);
            // W is the obu count expected in the packet
            logger.debug("OBU element #{} of {} array index: {}", obuIndex, obuCount, currentIndex);
            if (obuCount == 0 || obuIndex < obuCount) {
                // read the length of the OBU fragment and if its 127 expect to read 2 bytes
                byte[] fragmentLen = new byte[payload[currentIndex] == 127 ? 2 : 1];
                System.arraycopy(payload, currentIndex, fragmentLen, 0, fragmentLen.length);
                LEB128Result result = LEB128.decode(fragmentLen);
                obuFragmentLength = result.value;
                //logger.trace("Index: {} new index: {}", currentIndex, (currentIndex + result[1]));
                currentIndex += result.bytesRead;
            }
            obuType = OBUType.fromValue((payload[currentIndex] & OBU_FRAME_TYPE_MASK) >>> OBU_FRAME_TYPE_BITSHIFT);
            if (obuType == null && !OBUElements.isEmpty()) {
                byte[] lastFragment = OBUElements.removeLast();
                // read the type for the last fragment
                obuType = OBUType.fromValue((lastFragment[0] & OBU_FRAME_TYPE_MASK) >>> OBU_FRAME_TYPE_BITSHIFT);
                logger.warn("Unknown OBU type, appending data of the last packet fragment: {}", obuType);
                byte[] newLastFragment = new byte[lastFragment.length + obuFragmentLength];
                System.arraycopy(lastFragment, 0, newLastFragment, 0, lastFragment.length);
                System.arraycopy(payload, currentIndex, newLastFragment, lastFragment.length, obuFragmentLength);
                OBUElements.add(newLastFragment);
            } else {
                if (isTrace) {
                    boolean obuExtensionFlag = OBUParser.obuHasExtension(payload[currentIndex]);
                    boolean obuHasSizeField = OBUParser.obuHasSize(payload[currentIndex]);
                    logger.trace("OBU type: {} extension? {} size field? {}, length: {}", obuType, obuExtensionFlag, obuHasSizeField, obuFragmentLength);
                }
                //if (payload.length < (currentIndex + obuElementLength)) {
                //    throw new Exception("Short packet");
                //}
                byte[] obuFragment = new byte[obuFragmentLength];
                System.arraycopy(payload, currentIndex, obuFragment, 0, obuFragmentLength);
                // if we have a sequence header, store it
                if (obuType == OBUType.SEQUENCE_HEADER) {
                    sequenceHeader = Arrays.clone(obuFragment);
                }
                // we don't store temporal delimiter, tile list, or padding
                if (OBUParser.isValidObu(obuType)) {
                    OBUElements.add(obuFragment);
                } else {
                    logger.debug("Skip OBU type for RTP: {}", obuType);
                }
            }
            currentIndex += obuFragmentLength;
        }
        if (isTrace) {
            logger.trace("OBU read completely? {}", (currentIndex == payload.length));
        }
        return OBUElements.size();
    }

    /**
     * Packetizes an AV1 payload.
     *
     * @param payload AV1 payload
     * @param mtu     maximum transmission unit
     * @return list of packets
     */
    public List<byte[]> packetize(byte[] payload, int mtu) {
        List<byte[]> payloads = new LinkedList<>();
        byte frameType = (byte) ((payload[0] & OBU_FRAME_TYPE_MASK) >> OBU_FRAME_TYPE_BITSHIFT);
        if (frameType == OBU_FRAME_TYPE_SEQUENCE_HEADER) {
            sequenceHeader = payload;
        } else {
            int payloadDataIndex = 0;
            int payloadDataRemaining = payload.length;
            byte obuCount = 0;
            // no meta no need to add to metadata size
            int metadataSize = 0;
            if (sequenceHeader != null && sequenceHeader.length > 0) {
                // metadata size is small so 1 byte to hold its leb128 encoded length
                metadataSize += sequenceHeader.length + 1;
                obuCount++;
            }
            do {
                int outOffset = 1; // AV1_PAYLOADER_HEADER_SIZE
                byte[] out = new byte[Math.min(mtu, payloadDataRemaining + metadataSize)];
                out[0] = (byte) (obuCount << W_BITSHIFT);
                if (obuCount == 2) {
                    out[0] ^= N_MASK;
                    out[1] = (byte) LEB128.encode(sequenceHeader.length);
                    System.arraycopy(sequenceHeader, 0, out, 2, sequenceHeader.length);
                    outOffset += sequenceHeader.length + 1; // 1 byte for LEB128
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
            } while (payloadDataRemaining > 0);
        }
        return payloads;
    }

    /**
     * Packetizes a list of AV1 OBU, consisting of a sequence header and one or more OBU elements.
     *
     * @param mtu         maximum transmission unit
     * @return list of packets
     * @param obuInfos a {@link java.util.List} object
     */
    public List<byte[]> packetize(List<OBUInfo> obuInfos, int mtu) {
        LinkedList<byte[]> payloads = new LinkedList<>();
        int aggregationHeaderLength = 1;
        int maxFragmentSize = mtu - aggregationHeaderLength - 2;
        for (OBUInfo obuInfo : obuInfos) {
            // obuInfo.data includes the OBU header, but no aggregation header nor size field
            byte frameType = (byte) ((obuInfo.obuType.getValue() & OBU_FRAME_TYPE_MASK) >> OBU_FRAME_TYPE_BITSHIFT);
            byte[] payload = obuInfo.data.array();
            int payloadDataRemaining = payload.length, payloadDataIndex = 0;
            logger.debug("Frame type: {}", frameType);
            // Make sure the fragment/payload size is correct
            if (Math.min(maxFragmentSize, payloadDataRemaining) > 0) {
                while (payloadDataRemaining > 0) {
                    int currentFragmentSize = Math.min(maxFragmentSize, payloadDataRemaining);
                    int leb128Value = LEB128.encode(currentFragmentSize);
                    byte[] out;
                    if (currentFragmentSize >= 127) { // leb takes at least 2 bytes
                        int outLen = aggregationHeaderLength + 2 + currentFragmentSize;
                        out = new byte[outLen];
                        out[1] = (byte) (leb128Value >> 8);
                        out[2] = (byte) leb128Value;
                        System.arraycopy(payload, payloadDataIndex, out, aggregationHeaderLength + 2, currentFragmentSize);
                    } else { // leb expected to be 1 byte
                        out = new byte[aggregationHeaderLength + 1 + currentFragmentSize];
                        out[1] = (byte) leb128Value;
                        System.arraycopy(payload, payloadDataIndex, out, aggregationHeaderLength + 1, currentFragmentSize);
                    }
                    payloads.add(out);
                    payloadDataRemaining -= currentFragmentSize;
                    payloadDataIndex += currentFragmentSize;
                    if (payloads.size() > 1) {
                        out[0] ^= Z_MASK;
                    }
                    if (payloadDataRemaining != 0) {
                        out[0] ^= Y_MASK;
                    }
                }
            }
        }
        return payloads;
    }

    /**
     * Resets the packetizer.
     */
    public void reset() {
        Z = Y = N = false;
        W = 0;
        if (OBUElements != null) {
            OBUElements.clear();
        }
        sequenceHeader = null;
        // reset aggregation items; also done at depacketize
        firstPacketInFrame = lastPacketInFrame = startSequence = false;
    }

    /**
     * <p>getOBUElements.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<byte[]> getOBUElements() {
        return OBUElements;
    }

    /**
     * <p>isFirstPacketInFrame.</p>
     *
     * @return a boolean
     */
    public boolean isFirstPacketInFrame() {
        return firstPacketInFrame;
    }

    /**
     * <p>Setter for the field <code>firstPacketInFrame</code>.</p>
     *
     * @param firstPacketInFrame a boolean
     */
    public void setFirstPacketInFrame(boolean firstPacketInFrame) {
        this.firstPacketInFrame = firstPacketInFrame;
    }

    /**
     * <p>isLastPacketInFrame.</p>
     *
     * @return a boolean
     */
    public boolean isLastPacketInFrame() {
        return lastPacketInFrame;
    }

    /**
     * <p>Setter for the field <code>lastPacketInFrame</code>.</p>
     *
     * @param lastPacketInFrame a boolean
     */
    public void setLastPacketInFrame(boolean lastPacketInFrame) {
        this.lastPacketInFrame = lastPacketInFrame;
    }

    /**
     * <p>isStartSequence.</p>
     *
     * @return a boolean
     */
    public boolean isStartSequence() {
        return startSequence;
    }

    /**
     * <p>Setter for the field <code>startSequence</code>.</p>
     *
     * @param startSequence a boolean
     */
    public void setStartSequence(boolean startSequence) {
        this.startSequence = startSequence;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "AV1Packetizer [OBUElements=" + OBUElements + "]";
    }

}
