package org.red5.codec;

/**
 * <p>VideoPacketType class.</p>
 *
 * @author mondain
 */
public enum VideoPacketType {

    /** Sequence start packet, carrying the codec's decoder configuration data. */
    SequenceStart((byte) 0),
    /** A packet of one or more coded video frames, with an explicit SI24 composition time offset. */
    CodedFrames((byte) 0x01),
    /** Sequence end packet, signalling that no further frames follow for this track. */
    SequenceEnd((byte) 0x02),
    // CompositionTime Offset is implicitly set to zero. This optimization avoids transmitting an SI24 composition
    // time value of zero over the wire. See the ExVideoTagBody section below for corresponding pseudocode.
    /** A packet of one or more coded video frames with an implicit composition time offset of zero (no SI24 value transmitted). */
    CodedFramesX((byte) 0x03),
    // ExVideoTagBody does not contain video data. Instead, it contains an AMF-encoded metadata. Refer to the
    // Metadata Frame section for an illustration of its usage. For example, the metadata might include HDR
    // information. This also enables future possibilities for expressing additional metadata meant for subsequent
    // video sequences.
    // If VideoPacketType.Metadata is present, the FrameType flags at the top of this table should be ignored.
    /** Packet carries AMF-encoded metadata (such as HDR information) instead of video data. */
    Metadata((byte) 0x04),
    // Carriage of bitstream in MPEG-2 TS format PacketTypeSequenceStart and PacketTypeMPEG2TSSequenceStart are
    // mutually exclusive.
    /** Sequence start packet carrying the bitstream in MPEG-2 TS format; mutually exclusive with {@link #SequenceStart}. */
    MPEG2TSSequenceStart((byte) 0x05),
    // Turns on multitrack mode
    /** Enables multitrack mode, indicating multiple video tracks are present in the packet. */
    Multitrack((byte) 0x06),
    // Modifier/extension signal. Wraps modifier data around another packet type.
    // The unwrap loop peels off ModEx layers until a non-ModEx packet type is reached.
    /** Modifier/extension wrapper around another packet type; unwrapped iteratively until a non-ModEx packet type is reached. */
    ModEx((byte) 0x07);

    private final byte packetType;

    VideoPacketType(byte packetType) {
        this.packetType = packetType;
    }

    /**
     * <p>Getter for the field <code>packetType</code>.</p>
     *
     * @return a byte
     */
    public byte getPacketType() {
        return packetType;
    }

    /**
     * <p>valueOf.</p>
     *
     * @param packetType a int
     * @return a {@link org.red5.codec.VideoPacketType} object
     */
    public static VideoPacketType valueOf(int packetType) {
        for (VideoPacketType vpt : values()) {
            if (vpt.getPacketType() == packetType) {
                return vpt;
            }
        }
        return null;
    }

}
