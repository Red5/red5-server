package org.red5.codec;

public enum VideoPacketType {

    SequenceStart((byte) 0),
    CodedFrames((byte) 0x01),
    SequenceEnd((byte) 0x02),
    // CompositionTime Offset is implicitly set to zero. This optimization avoids transmitting an SI24 composition
    // time value of zero over the wire. See the ExVideoTagBody section below for corresponding pseudocode.
    CodedFramesX((byte) 0x03),
    // ExVideoTagBody does not contain video data. Instead, it contains an AMF-encoded metadata. Refer to the
    // Metadata Frame section for an illustration of its usage. For example, the metadata might include HDR
    // information. This also enables future possibilities for expressing additional metadata meant for subsequent
    // video sequences.
    // If VideoPacketType.Metadata is present, the FrameType flags at the top of this table should be ignored.
    Metadata((byte) 0x04),
    // Carriage of bitstream in MPEG-2 TS format PacketTypeSequenceStart and PacketTypeMPEG2TSSequenceStart are
    // mutually exclusive.
    MPEG2TSSequenceStart((byte) 0x05),
    // Turns on multitrack mode
    Multitrack((byte) 0x06);

    private final byte packetType;

    VideoPacketType(byte packetType) {
        this.packetType = packetType;
    }

    public byte getPacketType() {
        return packetType;
    }

    public static VideoPacketType valueOf(int packetType) {
        for (VideoPacketType vpt : values()) {
            if (vpt.getPacketType() == packetType) {
                return vpt;
            }
        }
        return null;
    }

}