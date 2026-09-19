package org.red5.io.obu;

import static org.red5.io.obu.OBPConstants.OBU_FRAME_TYPE_BITSHIFT;
import static org.red5.io.obu.OBPConstants.OBU_FRAME_TYPE_MASK;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * OBU info
 *
 * @author mondain
 */
public class OBUInfo {

    // OBU type
    /** The type of this Open Bitstream Unit, per the AV1 bitstream specification. */
    public OBUType obuType;

    // general OBU info
    /** Size in bytes of the OBU payload ({@code size}), and the temporal ({@code temporal_id}) and spatial ({@code spatial_id}) layer identifiers from the OBU extension header. */
    public int size, temporalId, spatialId;

    // OBU header info
    /** Up to 7 raw header bytes preceding the OBU payload (obu_header plus optional obu_extension_header and leb128 size). */
    public byte[] prefix = new byte[7];

    // OBU data
    /** Buffer wrapping the OBU payload bytes. */
    public ByteBuffer data;

    /**
     * <p>Constructor for OBUInfo.</p>
     *
     * @param obuType a {@link org.red5.io.obu.OBUType} object
     * @param size a int
     * @param temporalId a int
     * @param spatialId a int
     * @param prefix an array of {@link byte} objects
     * @param data a {@link java.nio.ByteBuffer} object
     */
    public OBUInfo(OBUType obuType, int size, int temporalId, int spatialId, byte[] prefix, ByteBuffer data) {
        this.obuType = obuType;
        this.size = size;
        this.temporalId = temporalId;
        this.spatialId = spatialId;
        this.prefix = prefix;
        this.data = data;
    }

    /**
     * <p>Constructor for OBUInfo.</p>
     *
     * @param obuType a {@link org.red5.io.obu.OBUType} object
     * @param size a int
     * @param temporalId a int
     * @param spatialId a int
     * @param prefix an array of {@link byte} objects
     */
    public OBUInfo(OBUType obuType, int size, int temporalId, int spatialId, byte[] prefix) {
        this.obuType = obuType;
        this.size = size;
        this.temporalId = temporalId;
        this.spatialId = spatialId;
        this.prefix = prefix;
    }

    /**
     * <p>Constructor for OBUInfo.</p>
     *
     * @param obuType a {@link org.red5.io.obu.OBUType} object
     * @param size a int
     * @param temporalId a int
     * @param spatialId a int
     */
    public OBUInfo(OBUType obuType, int size, int temporalId, int spatialId) {
        this.obuType = obuType;
        this.size = size;
        this.temporalId = temporalId;
        this.spatialId = spatialId;
    }

    /**
     * <p>Constructor for OBUInfo.</p>
     *
     * @param obuType a {@link org.red5.io.obu.OBUType} object
     * @param data a {@link java.nio.ByteBuffer} object
     */
    public OBUInfo(OBUType obuType, ByteBuffer data) {
        this.obuType = obuType;
        this.data = data;
    }

    /**
     * <p>build.</p>
     *
     * @param data an array of {@link byte} objects
     * @param offset a int
     * @param length a int
     * @return a {@link org.red5.io.obu.OBUInfo} object
     */
    public static OBUInfo build(byte[] data, int offset, int length) {
        OBUType obuType = OBUType.fromValue((data[0] & OBU_FRAME_TYPE_MASK) >>> OBU_FRAME_TYPE_BITSHIFT);
        ByteBuffer buffer = ByteBuffer.wrap(data, offset, length);
        OBUInfo info = new OBUInfo(obuType, buffer);
        // the wrapped buffer holds the whole OBU element; record its length so callers
        // (e.g. the AV1 depacketizer's AVCC-style size prefix) receive a correct size
        info.size = length;
        return info;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        if (data != null) {
            return "OBUInfo [obuType=" + obuType + ", size=" + size + ", temporalId=" + temporalId + ", spatialId=" + spatialId + ", prefix=" + Arrays.toString(prefix) + ", data=" + data + "]";
        }
        return "OBUInfo [obuType=" + obuType + ", size=" + size + ", temporalId=" + temporalId + ", spatialId=" + spatialId + ", prefix=" + Arrays.toString(prefix) + "]";
    }

}
