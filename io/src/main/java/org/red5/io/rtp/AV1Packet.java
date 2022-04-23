/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2022 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.rtp;

import java.util.LinkedList;

import org.bouncycastle.util.Arrays;
import org.red5.io.utils.LEB128;

// AV1Packet represents a depacketized AV1 RTP Packet
//
// 0 1 2 3 4 5 6 7
// +-+-+-+-+-+-+-+-+
// |Z|Y| W |N|-|-|-|
// +-+-+-+-+-+-+-+-+
//
// https://aomediacodec.github.io/av1-rtp-spec/#44-av1-aggregation-header
public class AV1Packet {

    private final static byte zMask = (byte) 0b10000000;

    private final static byte zBitshift = (byte) 7;

    private final static byte yMask = (byte) 0b01000000;

    private final static byte yBitshift = (byte) 6;

    private final static byte wMask = (byte) 0b00110000;

    private final static byte wBitshift = (byte) 4;

    private final static byte nMask = (byte) 0b00001000;

    private final static byte nBitshift = (byte) 3;

    private final static int av1PayloaderHeadersize = 1;

    // Z: MUST be set to 1 if the first OBU element is an
    //    OBU fragment that is a continuation of an OBU fragment
    //    from the previous packet, and MUST be set to 0 otherwise.
    public boolean Z;

    // Y: MUST be set to 1 if the last OBU element is an OBU fragment
    //    that will continue in the next packet, and MUST be set to 0 otherwise.
    public boolean Y;

    // W: two bit field that describes the number of OBU elements in the packet.
    //    This field MUST be set equal to 0 or equal to the number of OBU elements
    //    contained in the packet. If set to 0, each OBU element MUST be preceded by
    //    a length field. If not set to 0 (i.e., W = 1, 2 or 3) the last OBU element
    //    MUST NOT be preceded by a length field. Instead, the length of the last OBU
    //    element contained in the packet can be calculated as follows:
    // Length of the last OBU element =
    //    length of the RTP payload
    //  - length of aggregation header
    //  - length of previous OBU elements including length fields
    public int W;

    // N: MUST be set to 1 if the packet is the first packet of a coded video sequence, and MUST be set to 0 otherwise.
    public boolean N;

    // Each AV1 RTP Packet is a collection of OBU Elements. Each OBU Element may be a full OBU, or just a fragment of one.
    // AV1Frame provides the tools to construct a collection of OBUs from a collection of OBU Elements
    public LinkedList<byte[]> OBUElements = new LinkedList<>();

    // Payload fragments a AV1 packet across one or more byte arrays
    // See AV1Packet for description of AV1 Payload Header
    public static LinkedList<byte[]> marshal(int mtu, byte[] payload) {
        LinkedList<byte[]> payloads = new LinkedList<>();
        int maxFragmentSize = mtu - av1PayloaderHeadersize - 2;
        int payloadDataRemaining = payload.length;
        int payloadDataIndex = 0;
        // Make sure the fragment/payload size is correct
        if (Math.min(maxFragmentSize, payloadDataRemaining) > 0) {
            while (payloadDataRemaining > 0) {
                int currentFragmentSize = Math.min(maxFragmentSize, payloadDataRemaining);
                int leb128Size = 1;
                if (currentFragmentSize >= 127) {
                    leb128Size = 2;
                }
                byte[] out = new byte[av1PayloaderHeadersize + leb128Size + currentFragmentSize];
                int leb128Value = LEB128.encode(currentFragmentSize);
                if (leb128Size == 1) {
                    out[1] = (byte) leb128Value;
                } else {
                    out[1] = (byte) (leb128Value >> 8);
                    out[2] = (byte) leb128Value;
                }
                System.arraycopy(payload, payloadDataIndex, out, av1PayloaderHeadersize + leb128Size, currentFragmentSize);
                payloads.add(out);
                payloadDataRemaining -= currentFragmentSize;
                payloadDataIndex += currentFragmentSize;
                if (payloads.size() > 1) {
                    out[0] ^= zMask;
                }
                if (payloadDataRemaining != 0) {
                    out[0] ^= yMask;
                }
            }
        }
        return payloads;
    }

    // Unmarshal parses the passed byte slice and stores the result in the AV1Packet this method is called upon
    public boolean unmarshal(byte[] payload) {
        System.out.printf("Payload length: %d%n", payload.length);
        if (payload != null && payload.length > 1) {
            Z = ((payload[0] & zMask) >> zBitshift) != 0;
            Y = ((payload[0] & yMask) >> yBitshift) != 0;
            N = ((payload[0] & nMask) >> nBitshift) != 0;
            W = ((payload[0] & wMask) >> wBitshift);
            System.out.printf("Payload Z: %b Y: %b N: %b W: %d%n", Z, Y, N, W);
            // if its a fragment (Z) and a keyframe (N)
            if (!Z && !N) {
                int currentIndex = 1, bytesRead = 0;
                // read the length
                int obuElementLength = (payload[currentIndex] & 0x3f) << 8 | payload[currentIndex + 1];
                bytesRead = 2;
                System.out.printf("Index: %d and obu element length: %d bytes read: %d%n", currentIndex, obuElementLength, bytesRead);
                for (int i = 1; currentIndex < payload.length; i++) {
                    // If W bit is set the last OBU Element will have no length header
                    if (i == W) {
                        bytesRead = 0;
                        obuElementLength = payload.length - currentIndex;
                    } else {
                        int encodedLength = 0;
                        for (; currentIndex < payload.length; currentIndex++) {
                            encodedLength |= payload[currentIndex] & 0xff;
                            if ((payload[currentIndex] & LEB128.MSB_BITMASK) == 0) {
                                obuElementLength = LEB128.decode(encodedLength);
                                bytesRead = currentIndex + 1;
                                break;
                            }
                            encodedLength <<= 8;
                        }
                    }
                    currentIndex += bytesRead;
                    if (currentIndex + obuElementLength > payload.length) {
                        System.out.println("Index and obu element length exceed payload size");
                        return false;
                    }
                    // copy the obu into the obu's list
                    OBUElements.add(Arrays.copyOfRange(payload, currentIndex, currentIndex + obuElementLength));
                    System.out.println("OBU element added");
                    currentIndex += obuElementLength;
                }
                // obu's are ready to read
                return true;
            } else {
                System.out.println("Non-fragment, non-keyframe");
            }
        }
        return false;
    }

    public LinkedList<byte[]> getOBUElements() {
        return OBUElements;
    }

    public static void main(String[] args) {
        // packetizing
        int mtu = 5;

        byte[] in = new byte[] { 0x01 };
        byte[][] out = new byte[][] { { 0x00, 0x01, 0x01 } };

        LinkedList<byte[]> outList1 = AV1Packet.marshal(mtu, in);
        System.out.printf("Equal? %b%n", Arrays.areEqual(out[0], outList1.get(0)));

        byte[] in2 = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x04, 0x05 };
        byte[][] out2 = new byte[][] { { 0x40, 0x02, 0x00, 0x01 }, { (byte) 0xc0, 0x02, 0x02, 0x03 }, { (byte) 0xc0, 0x02, 0x04, 0x04 }, { (byte) 0x80, 0x01, 0x05 } };

        LinkedList<byte[]> outList2 = AV1Packet.marshal(mtu, in2);
        System.out.printf("Marshaled entries: %d%n", outList2.size());
        System.out.printf("Equal? %b%n", Arrays.areEqual(out2[0], outList2.get(0)));
        System.out.printf("Equal? %b%n", Arrays.areEqual(out2[1], outList2.get(1)));
        System.out.printf("Equal? %b%n", Arrays.areEqual(out2[2], outList2.get(2)));
        System.out.printf("Equal? %b%n", Arrays.areEqual(out2[3], outList2.get(3)));

        //LinkedList<byte[]> outList3 = AV1Packet.marshal(0, new byte[] { 0x0a, 0x0b, 0x0c });
        //System.out.println("Zero mtu payload: " + outList3);
        // depacketizing
        AV1Packet p = new AV1Packet();
        byte[] marshaled = new byte[] { (byte) 0x68, (byte) 0x0c, (byte) 0x08, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x2c, (byte) 0xd6, (byte) 0xd3, (byte) 0x0c, (byte) 0xd5, (byte) 0x02, (byte) 0x00, (byte) 0x80, (byte) 0x30, (byte) 0x10, (byte) 0xc3, (byte) 0xc0, (byte) 0x07, (byte) 0xff, (byte) 0xff, (byte) 0xf8, (byte) 0xb7, (byte) 0x30, (byte) 0xc0, (byte) 0x00, (byte) 0x00, (byte) 0x88, (byte) 0x17,
                (byte) 0xf9, (byte) 0x0c, (byte) 0xcf, (byte) 0xc6, (byte) 0x7b, (byte) 0x9c, (byte) 0x0d, (byte) 0xda, (byte) 0x55, (byte) 0x82, (byte) 0x82, (byte) 0x67, (byte) 0x2f, (byte) 0xf0, (byte) 0x07, (byte) 0x26, (byte) 0x5d, (byte) 0xf6, (byte) 0xc6, (byte) 0xe3, (byte) 0x12, (byte) 0xdd, (byte) 0xf9, (byte) 0x71, (byte) 0x77, (byte) 0x43, (byte) 0xe6, (byte) 0xba, (byte) 0xf2, (byte) 0xce, (byte) 0x36,
                (byte) 0x08, (byte) 0x63, (byte) 0x92, (byte) 0xac, (byte) 0xbb, (byte) 0xbd, (byte) 0x26, (byte) 0x4c, (byte) 0x05, (byte) 0x52, (byte) 0x91, (byte) 0x09, (byte) 0xf5, (byte) 0x37, (byte) 0xb5, (byte) 0x18, (byte) 0xbe, (byte) 0x5c, (byte) 0x95, (byte) 0xb1, (byte) 0x2c, (byte) 0x13, (byte) 0x27, (byte) 0x81, (byte) 0xc2, (byte) 0x52, (byte) 0x8c, (byte) 0xaf, (byte) 0x27, (byte) 0xca, (byte) 0xf2,
                (byte) 0x93, (byte) 0xd6, (byte) 0x2e, (byte) 0x46, (byte) 0x32, (byte) 0xed, (byte) 0x71, (byte) 0x87, (byte) 0x90, (byte) 0x1d, (byte) 0x0b, (byte) 0x84, (byte) 0x46, (byte) 0x7f, (byte) 0xd1, (byte) 0x57, (byte) 0xc1, (byte) 0x0d, (byte) 0xc7, (byte) 0x5b, (byte) 0x41, (byte) 0xbb, (byte) 0x8a, (byte) 0x7d, (byte) 0xe9, (byte) 0x2c, (byte) 0xae, (byte) 0x36, (byte) 0x98, (byte) 0x13, (byte) 0x39,
                (byte) 0xb9, (byte) 0x0c, (byte) 0x66, (byte) 0x47, (byte) 0x05, (byte) 0xa2, (byte) 0xdf, (byte) 0x55, (byte) 0xc4, (byte) 0x09, (byte) 0xab, (byte) 0xe4, (byte) 0xfb, (byte) 0x11, (byte) 0x52, (byte) 0x36, (byte) 0x27, (byte) 0x88, (byte) 0x86, (byte) 0xf3, (byte) 0x4a, (byte) 0xbb, (byte) 0xef, (byte) 0x40, (byte) 0xa7, (byte) 0x85, (byte) 0x2a, (byte) 0xfe, (byte) 0x92, (byte) 0x28, (byte) 0xe4,
                (byte) 0xce, (byte) 0xce, (byte) 0xdc, (byte) 0x4b, (byte) 0xd0, (byte) 0xaa, (byte) 0x3c, (byte) 0xd5, (byte) 0x16, (byte) 0x76, (byte) 0x74, (byte) 0xe2, (byte) 0xfa, (byte) 0x34, (byte) 0x91, (byte) 0x4f, (byte) 0xdc, (byte) 0x2b, (byte) 0xea, (byte) 0xae, (byte) 0x71, (byte) 0x36, (byte) 0x74, (byte) 0xe1, (byte) 0x2a, (byte) 0xf3, (byte) 0xd3, (byte) 0x53, (byte) 0xe8, (byte) 0xec, (byte) 0xd6,
                (byte) 0x63, (byte) 0xf6, (byte) 0x6a, (byte) 0x75, (byte) 0x95, (byte) 0x68, (byte) 0xcc, (byte) 0x99, (byte) 0xbe, (byte) 0x17, (byte) 0xd8, (byte) 0x3b, (byte) 0x87, (byte) 0x5b, (byte) 0x94, (byte) 0xdc, (byte) 0xec, (byte) 0x32, (byte) 0x09, (byte) 0x18, (byte) 0x4b, (byte) 0x37, (byte) 0x58, (byte) 0xb5, (byte) 0x67, (byte) 0xfb, (byte) 0xdf, (byte) 0x66, (byte) 0x6c, (byte) 0x16, (byte) 0x9e,
                (byte) 0xba, (byte) 0x72, (byte) 0xc6, (byte) 0x21, (byte) 0xac, (byte) 0x02, (byte) 0x6d, (byte) 0x6b, (byte) 0x17, (byte) 0xf9, (byte) 0x68, (byte) 0x22, (byte) 0x2e, (byte) 0x10, (byte) 0xd7, (byte) 0xdf, (byte) 0xfb, (byte) 0x24, (byte) 0x69, (byte) 0x7c, (byte) 0xaf, (byte) 0x11, (byte) 0x64, (byte) 0x80, (byte) 0x7a, (byte) 0x9d, (byte) 0x09, (byte) 0xc4, (byte) 0x1f, (byte) 0xf1, (byte) 0xd7,
                (byte) 0x3c, (byte) 0x5a, (byte) 0xc2, (byte) 0x2c, (byte) 0x8e, (byte) 0xf5, (byte) 0xff, (byte) 0xee, (byte) 0xc2, (byte) 0x7c, (byte) 0xa1, (byte) 0xe4, (byte) 0xcb, (byte) 0x1c, (byte) 0x6d, (byte) 0xd8, (byte) 0x15, (byte) 0x0e, (byte) 0x40, (byte) 0x36, (byte) 0x85, (byte) 0xe7, (byte) 0x04, (byte) 0xbb, (byte) 0x64, (byte) 0xca, (byte) 0x6a, (byte) 0xd9, (byte) 0x21, (byte) 0x8e, (byte) 0x95,
                (byte) 0xa0, (byte) 0x83, (byte) 0x95, (byte) 0x10, (byte) 0x48, (byte) 0xfa, (byte) 0x00, (byte) 0x54, (byte) 0x90, (byte) 0xe9, (byte) 0x81, (byte) 0x86, (byte) 0xa0, (byte) 0x4a, (byte) 0x6e, (byte) 0xbe, (byte) 0x9b, (byte) 0xf0, (byte) 0x73, (byte) 0x0a, (byte) 0x17, (byte) 0xbb, (byte) 0x57, (byte) 0x81, (byte) 0x17, (byte) 0xaf, (byte) 0xd6, (byte) 0x70, (byte) 0x1f, (byte) 0xe8, (byte) 0x6d,
                (byte) 0x32, (byte) 0x59, (byte) 0x14, (byte) 0x39, (byte) 0xd8, (byte) 0x1d, (byte) 0xec, (byte) 0x59, (byte) 0xe4, (byte) 0x98, (byte) 0x4d, (byte) 0x44, (byte) 0xf3, (byte) 0x4f, (byte) 0x7b, (byte) 0x47, (byte) 0xd9, (byte) 0x92, (byte) 0x3b, (byte) 0xd9, (byte) 0x5c, (byte) 0x98, (byte) 0xd5, (byte) 0xf1, (byte) 0xc9, (byte) 0x8b, (byte) 0x9d, (byte) 0xb1, (byte) 0x65, (byte) 0xb3, (byte) 0xe1,
                (byte) 0x87, (byte) 0xa4, (byte) 0x6a, (byte) 0xcc, (byte) 0x42, (byte) 0x96, (byte) 0x66, (byte) 0xdb, (byte) 0x5f, (byte) 0xf9, (byte) 0xe1, (byte) 0xa1, (byte) 0x72, (byte) 0xb6, (byte) 0x05, (byte) 0x02, (byte) 0x1f, (byte) 0xa3, (byte) 0x14, (byte) 0x3e, (byte) 0xfe, (byte) 0x99, (byte) 0x7f, (byte) 0xeb, (byte) 0x42, (byte) 0xcf, (byte) 0x76, (byte) 0x09, (byte) 0x19, (byte) 0xd2, (byte) 0xd2,
                (byte) 0x99, (byte) 0x75, (byte) 0x1c, (byte) 0x67, (byte) 0xda, (byte) 0x4d, (byte) 0xf4, (byte) 0x87, (byte) 0xe5, (byte) 0x55, (byte) 0x8b, (byte) 0xed, (byte) 0x01, (byte) 0x82, (byte) 0xf6, (byte) 0xd6, (byte) 0x1c, (byte) 0x5c, (byte) 0x05, (byte) 0x96, (byte) 0x96, (byte) 0x79, (byte) 0xc1, (byte) 0x61, (byte) 0x87, (byte) 0x74, (byte) 0xcd, (byte) 0x29, (byte) 0x83, (byte) 0x27, (byte) 0xae,
                (byte) 0x47, (byte) 0x87, (byte) 0x36, (byte) 0x34, (byte) 0xab, (byte) 0xc4, (byte) 0x73, (byte) 0x76, (byte) 0x58, (byte) 0x1b, (byte) 0x4a, (byte) 0xec, (byte) 0x0e, (byte) 0x4c, (byte) 0x2f, (byte) 0xb1, (byte) 0x76, (byte) 0x08, (byte) 0x7f, (byte) 0xaf, (byte) 0xfa, (byte) 0x6d, (byte) 0x8c, (byte) 0xde, (byte) 0xe4, (byte) 0xae, (byte) 0x58, (byte) 0x87, (byte) 0xe7, (byte) 0xa0, (byte) 0x27,
                (byte) 0x05, (byte) 0x0d, (byte) 0xf5, (byte) 0xa7, (byte) 0xfb, (byte) 0x2a, (byte) 0x75, (byte) 0x33, (byte) 0xd9, (byte) 0x3b, (byte) 0x65, (byte) 0x60, (byte) 0xa4, (byte) 0x13, (byte) 0x27, (byte) 0xa5, (byte) 0xe5, (byte) 0x1b, (byte) 0x83, (byte) 0x78, (byte) 0x7a, (byte) 0xd7, (byte) 0xec, (byte) 0x0c, (byte) 0xed, (byte) 0x8b, (byte) 0xe6, (byte) 0x4e, (byte) 0x8f, (byte) 0xfe, (byte) 0x6b,
                (byte) 0x5d, (byte) 0xbb, (byte) 0xa8, (byte) 0xee, (byte) 0x38, (byte) 0x81, (byte) 0x6f, (byte) 0x09, (byte) 0x23, (byte) 0x08, (byte) 0x8f, (byte) 0x07, (byte) 0x21, (byte) 0x09, (byte) 0x39, (byte) 0xf0, (byte) 0xf8, (byte) 0x03, (byte) 0x17, (byte) 0x24, (byte) 0x2a, (byte) 0x22, (byte) 0x44, (byte) 0x84, (byte) 0xe1, (byte) 0x5c, (byte) 0xf3, (byte) 0x4f, (byte) 0x20, (byte) 0xdc, (byte) 0xc1,
                (byte) 0xe7, (byte) 0xeb, (byte) 0xbc, (byte) 0x0b, (byte) 0xfb, (byte) 0x7b, (byte) 0x20, (byte) 0x66, (byte) 0xa4, (byte) 0x27, (byte) 0xe2, (byte) 0x01, (byte) 0xb3, (byte) 0x5f, (byte) 0xb7, (byte) 0x47, (byte) 0xa1, (byte) 0x88, (byte) 0x4b, (byte) 0x8c, (byte) 0x47, (byte) 0xda, (byte) 0x36, (byte) 0x98, (byte) 0x60, (byte) 0xd7, (byte) 0x46, (byte) 0x92, (byte) 0x0b, (byte) 0x7e, (byte) 0x5b,
                (byte) 0x4e, (byte) 0x34, (byte) 0x50, (byte) 0x12, (byte) 0x67, (byte) 0x50, (byte) 0x8d, (byte) 0xe7, (byte) 0xc9, (byte) 0xe4, (byte) 0x96, (byte) 0xef, (byte) 0xae, (byte) 0x2b, (byte) 0xc7, (byte) 0xfa, (byte) 0x36, (byte) 0x29, (byte) 0x05, (byte) 0xf5, (byte) 0x92, (byte) 0xbd, (byte) 0x62, (byte) 0xb7, (byte) 0xbb, (byte) 0x90, (byte) 0x66, (byte) 0xe0, (byte) 0xad, (byte) 0x14, (byte) 0x3e,
                (byte) 0xe7, (byte) 0xb4, (byte) 0x24, (byte) 0xf3, (byte) 0x04, (byte) 0xcf, (byte) 0x22, (byte) 0x14, (byte) 0x86, (byte) 0xa4, (byte) 0xb8, (byte) 0xfb, (byte) 0x83, (byte) 0x56, (byte) 0xce, (byte) 0xaa, (byte) 0xb4, (byte) 0x87, (byte) 0x5a, (byte) 0x9e, (byte) 0xf2, (byte) 0x0b, (byte) 0xaf, (byte) 0xad, (byte) 0x40, (byte) 0xe1, (byte) 0xb5, (byte) 0x5c, (byte) 0x6b, (byte) 0xa7, (byte) 0xee,
                (byte) 0x9f, (byte) 0xbb, (byte) 0x1a, (byte) 0x68, (byte) 0x4d, (byte) 0xc3, (byte) 0xbf, (byte) 0x22, (byte) 0x4d, (byte) 0xbe, (byte) 0x58, (byte) 0x52, (byte) 0xc9, (byte) 0xcc, (byte) 0x0d, (byte) 0x88, (byte) 0x04, (byte) 0xf1, (byte) 0xf8, (byte) 0xd4, (byte) 0xfb, (byte) 0xd6, (byte) 0xad, (byte) 0xcf, (byte) 0x13, (byte) 0x84, (byte) 0xd6, (byte) 0x2f, (byte) 0x90, (byte) 0x0c, (byte) 0x5f,
                (byte) 0xb4, (byte) 0xe2, (byte) 0xd8, (byte) 0x29, (byte) 0x26, (byte) 0x8d, (byte) 0x7c, (byte) 0x6b, (byte) 0xab, (byte) 0x91, (byte) 0x91, (byte) 0x3c, (byte) 0x25, (byte) 0x39, (byte) 0x9c, (byte) 0x86, (byte) 0x08, (byte) 0x39, (byte) 0x54, (byte) 0x59, (byte) 0x0d, (byte) 0xa4, (byte) 0xa8, (byte) 0x31, (byte) 0x9f, (byte) 0xa3, (byte) 0xbc, (byte) 0xc2, (byte) 0xcb, (byte) 0xf9, (byte) 0x30,
                (byte) 0x49, (byte) 0xc3, (byte) 0x68, (byte) 0x0e, (byte) 0xfc, (byte) 0x2b, (byte) 0x9f, (byte) 0xce, (byte) 0x59, (byte) 0x02, (byte) 0xfa, (byte) 0xd4, (byte) 0x4e, (byte) 0x11, (byte) 0x49, (byte) 0x0d, (byte) 0x93, (byte) 0x0c, (byte) 0xae, (byte) 0x57, (byte) 0xd7, (byte) 0x74, (byte) 0xdd, (byte) 0x13, (byte) 0x1a, (byte) 0x15, (byte) 0x79, (byte) 0x10, (byte) 0xcc, (byte) 0x99, (byte) 0x32,
                (byte) 0x9b, (byte) 0x57, (byte) 0x6d, (byte) 0x53, (byte) 0x75, (byte) 0x1f, (byte) 0x6d, (byte) 0xbb, (byte) 0xe4, (byte) 0xbc, (byte) 0xa9, (byte) 0xd4, (byte) 0xdb, (byte) 0x06, (byte) 0xe7, (byte) 0x09, (byte) 0xb0, (byte) 0x6f, (byte) 0xca, (byte) 0xb3, (byte) 0xb1, (byte) 0xed, (byte) 0xc5, (byte) 0x0b, (byte) 0x8d, (byte) 0x8e, (byte) 0x70, (byte) 0xb0, (byte) 0xbf, (byte) 0x8b, (byte) 0xad,
                (byte) 0x2f, (byte) 0x29, (byte) 0x92, (byte) 0xdd, (byte) 0x5a, (byte) 0x19, (byte) 0x3d, (byte) 0xca, (byte) 0xca, (byte) 0xed, (byte) 0x05, (byte) 0x26, (byte) 0x25, (byte) 0xee, (byte) 0xee, (byte) 0xa9, (byte) 0xdd, (byte) 0xa0, (byte) 0xe3, (byte) 0x78, (byte) 0xe0, (byte) 0x56, (byte) 0x99, (byte) 0x2f, (byte) 0xa1, (byte) 0x3f, (byte) 0x07, (byte) 0x5e, (byte) 0x91, (byte) 0xfb, (byte) 0xc4,
                (byte) 0xb3, (byte) 0xac, (byte) 0xee, (byte) 0x07, (byte) 0xa4, (byte) 0x6a, (byte) 0xcb, (byte) 0x42, (byte) 0xae, (byte) 0xdf, (byte) 0x09, (byte) 0xe7, (byte) 0xd0, (byte) 0xbb, (byte) 0xc6, (byte) 0xd4, (byte) 0x38, (byte) 0x58, (byte) 0x7d, (byte) 0xb4, (byte) 0x45, (byte) 0x98, (byte) 0x38, (byte) 0x21, (byte) 0xc8, (byte) 0xc1, (byte) 0x3c, (byte) 0x81, (byte) 0x12, (byte) 0x7e, (byte) 0x37,
                (byte) 0x03, (byte) 0xa8, (byte) 0xcc, (byte) 0xf3, (byte) 0xf9, (byte) 0xd9, (byte) 0x9d, (byte) 0x8f, (byte) 0xc1, (byte) 0xa1, (byte) 0xcc, (byte) 0xc1, (byte) 0x1b, (byte) 0xe3, (byte) 0xa8, (byte) 0x93, (byte) 0x91, (byte) 0x2c, (byte) 0x0a, (byte) 0xe8, (byte) 0x1f, (byte) 0x28, (byte) 0x13, (byte) 0x44, (byte) 0x07, (byte) 0x68, (byte) 0x5a, (byte) 0x8f, (byte) 0x27, (byte) 0x41, (byte) 0x18,
                (byte) 0xc9, (byte) 0x31, (byte) 0xc4, (byte) 0xc1, (byte) 0x71, (byte) 0xe2, (byte) 0xf0, (byte) 0xc4, (byte) 0xf4, (byte) 0x1e, (byte) 0xac, (byte) 0x29, (byte) 0x49, (byte) 0x2f, (byte) 0xd0, (byte) 0xc0, (byte) 0x98, (byte) 0x13, (byte) 0xa6, (byte) 0xbc, (byte) 0x5e, (byte) 0x34, (byte) 0x28, (byte) 0xa7, (byte) 0x30, (byte) 0x13, (byte) 0x8d, (byte) 0xb4, (byte) 0xca, (byte) 0x91, (byte) 0x26,
                (byte) 0x6c, (byte) 0xda, (byte) 0x35, (byte) 0xb5, (byte) 0xf1, (byte) 0xbf, (byte) 0x3f, (byte) 0x35, (byte) 0x3b, (byte) 0x87, (byte) 0x37, (byte) 0x63, (byte) 0x40, (byte) 0x59, (byte) 0x73, (byte) 0x49, (byte) 0x06, (byte) 0x59, (byte) 0x04, (byte) 0xe0, (byte) 0x84, (byte) 0x16, (byte) 0x3a, (byte) 0xe8, (byte) 0xc4, (byte) 0x28, (byte) 0xd1, (byte) 0xf5, (byte) 0x11, (byte) 0x9c, (byte) 0x34,
                (byte) 0xf4, (byte) 0x5a, (byte) 0xc0, (byte) 0xf8, (byte) 0x67, (byte) 0x47, (byte) 0x1c, (byte) 0x90, (byte) 0x63, (byte) 0xbc, (byte) 0x06, (byte) 0x39, (byte) 0x2e, (byte) 0x8a, (byte) 0xa5, (byte) 0xa0, (byte) 0xf1, (byte) 0x6b, (byte) 0x41, (byte) 0xb1, (byte) 0x16, (byte) 0xbd, (byte) 0xb9, (byte) 0x50, (byte) 0x78, (byte) 0x72, (byte) 0x91, (byte) 0x8e, (byte) 0x8c, (byte) 0x99, (byte) 0x0f,
                (byte) 0x7d, (byte) 0x99, (byte) 0x7e, (byte) 0x77, (byte) 0x36, (byte) 0x85, (byte) 0x87, (byte) 0x1f, (byte) 0x2e, (byte) 0x47, (byte) 0x13, (byte) 0x55, (byte) 0xf8, (byte) 0x07, (byte) 0xba, (byte) 0x7b, (byte) 0x1c, (byte) 0xaa, (byte) 0xbf, (byte) 0x20, (byte) 0xd0, (byte) 0xfa, (byte) 0xc4, (byte) 0xe1, (byte) 0xd0, (byte) 0xb3, (byte) 0xe4, (byte) 0xf4, (byte) 0xf9, (byte) 0x57, (byte) 0x8d,
                (byte) 0x56, (byte) 0x19, (byte) 0x4a, (byte) 0xdc, (byte) 0x4c, (byte) 0x83, (byte) 0xc8, (byte) 0xf1, (byte) 0x30, (byte) 0xc0, (byte) 0xb5, (byte) 0xdf, (byte) 0x67, (byte) 0x25, (byte) 0x58, (byte) 0xd8, (byte) 0x09, (byte) 0x41, (byte) 0x37, (byte) 0x2e, (byte) 0x0b, (byte) 0x47, (byte) 0x2b, (byte) 0x86, (byte) 0x4b, (byte) 0x73, (byte) 0x38, (byte) 0xf0, (byte) 0xa0, (byte) 0x6b, (byte) 0x83,
                (byte) 0x30, (byte) 0x80, (byte) 0x3e, (byte) 0x46, (byte) 0xb5, (byte) 0x09, (byte) 0xc8, (byte) 0x6d, (byte) 0x3e, (byte) 0x97, (byte) 0xaa, (byte) 0x70, (byte) 0x4e, (byte) 0x8c, (byte) 0x75, (byte) 0x29, (byte) 0xec, (byte) 0x8a, (byte) 0x37, (byte) 0x4a, (byte) 0x81, (byte) 0xfd, (byte) 0x92, (byte) 0xf1, (byte) 0x29, (byte) 0xf0, (byte) 0xe8, (byte) 0x9d, (byte) 0x8c, (byte) 0xb4, (byte) 0x39,
                (byte) 0x2d, (byte) 0x67, (byte) 0x06, (byte) 0xcd, (byte) 0x5f, (byte) 0x25, (byte) 0x02, (byte) 0x30, (byte) 0xbb, (byte) 0x6b, (byte) 0x41, (byte) 0x93, (byte) 0x55, (byte) 0x1e, (byte) 0x0c, (byte) 0xc9, (byte) 0x6e, (byte) 0xb5, (byte) 0xd5, (byte) 0x9f, (byte) 0x80, (byte) 0xf4, (byte) 0x7d, (byte) 0x9d, (byte) 0x8a, (byte) 0x0d, (byte) 0x8d, (byte) 0x3b, (byte) 0x15, (byte) 0x14, (byte) 0xc9,
                (byte) 0xdf, (byte) 0x03, (byte) 0x9c, (byte) 0x78, (byte) 0x39, (byte) 0x4e, (byte) 0xa0, (byte) 0xdc, (byte) 0x3a, (byte) 0x1b, (byte) 0x8c, (byte) 0xdf, (byte) 0xaa, (byte) 0xed, (byte) 0x25, (byte) 0xda, (byte) 0x60, (byte) 0xdd, (byte) 0x30, (byte) 0x64, (byte) 0x09, (byte) 0xcc, (byte) 0x94, (byte) 0x53, (byte) 0xa1, (byte) 0xad, (byte) 0xfd, (byte) 0x9e, (byte) 0xe7, (byte) 0x65, (byte) 0x15,
                (byte) 0xb8, (byte) 0xb1, (byte) 0xda, (byte) 0x9a, (byte) 0x28, (byte) 0x80, (byte) 0x51, (byte) 0x88, (byte) 0x93, (byte) 0x92, (byte) 0xe3, (byte) 0x03, (byte) 0xdf, (byte) 0x70, (byte) 0xba, (byte) 0x1b, (byte) 0x59, (byte) 0x3b, (byte) 0xb4, (byte) 0x8a, (byte) 0xb6, (byte) 0x0b, (byte) 0x0a, (byte) 0xa8, (byte) 0x48, (byte) 0xdf, (byte) 0xcc, (byte) 0x74, (byte) 0x4c, (byte) 0x71, (byte) 0x80,
                (byte) 0x08, (byte) 0xec, (byte) 0xc8, (byte) 0x8a, (byte) 0x73, (byte) 0xf5, (byte) 0x0e, (byte) 0x3d, (byte) 0xec, (byte) 0x16, (byte) 0xf6, (byte) 0x32, (byte) 0xfd, (byte) 0xf3, (byte) 0x6b, (byte) 0xba, (byte) 0xa9, (byte) 0x65, (byte) 0xd1, (byte) 0x87, (byte) 0xe2, (byte) 0x56, (byte) 0xcd, (byte) 0xde, (byte) 0x2c, (byte) 0xa4, (byte) 0x1b, (byte) 0x25, (byte) 0x81, (byte) 0xb2, (byte) 0xed,
                (byte) 0xea, (byte) 0xe9, (byte) 0x11, (byte) 0x07, (byte) 0xf5, (byte) 0x17, (byte) 0xd0, (byte) 0xca, (byte) 0x5d, (byte) 0x07, (byte) 0xb9, (byte) 0xb2, (byte) 0xa9, (byte) 0xa9, (byte) 0xee, (byte) 0x42, (byte) 0x33, (byte) 0x93, (byte) 0x21, (byte) 0x30, (byte) 0x5e, (byte) 0xd2, (byte) 0x58, (byte) 0xfd, (byte) 0xdd, (byte) 0x73, (byte) 0x0d, (byte) 0xb2, (byte) 0x93, (byte) 0x58, (byte) 0x77,
                (byte) 0x78, (byte) 0x40, (byte) 0x69, (byte) 0xba, (byte) 0x3c, (byte) 0x95, (byte) 0x1c, (byte) 0x61, (byte) 0xc6, (byte) 0xc6, (byte) 0x97, (byte) 0x1c, (byte) 0xef, (byte) 0x4d, (byte) 0x91, (byte) 0x0a, (byte) 0x42, (byte) 0x91, (byte) 0x1d, (byte) 0x14, (byte) 0x93, (byte) 0xf5, (byte) 0x78, (byte) 0x41, (byte) 0x32, (byte) 0x8a, (byte) 0x0a, (byte) 0x43, (byte) 0xd4, (byte) 0x3e, (byte) 0x6b,
                (byte) 0xb0, (byte) 0xd8, (byte) 0x0e, (byte) 0x04 };

        System.out.printf("Unmarshaled: %b%n", p.unmarshal(marshaled));
        System.out.println("Depacketized payload: " + p.getOBUElements());
        if (!p.Z && p.Y && p.N && p.W == 2) {
            System.out.println("Unmarshaled flags are correct");
        }
    }

}