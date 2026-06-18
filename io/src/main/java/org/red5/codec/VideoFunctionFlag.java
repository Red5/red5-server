package org.red5.codec;

/**
 * Video function flags sent during NetConnection connect to advertise client video capabilities.
 * Combined via bitwise OR in the 'videoFunction' connect parameter.
 *
 * @author Paul Gregoire
 */
public final class VideoFunctionFlag {

    /** Client can perform frame-accurate seeks. */
    public static final int SUPPORT_VID_CLIENT_SEEK = 0x0001;

    /** Client supports HDR video. Implies support for colorInfo within VideoPacketType.Metadata. */
    public static final int SUPPORT_VID_CLIENT_HDR = 0x0002;

    /** Client supports VideoPacketType.Metadata. */
    public static final int SUPPORT_VID_CLIENT_VIDEO_PACKET_TYPE_METADATA = 0x0004;

    /** Decoder can extract a section of a frame without decompressing the entire frame. */
    public static final int SUPPORT_VID_CLIENT_LARGE_SCALE_TILE = 0x0008;

    private VideoFunctionFlag() {
    }
}
