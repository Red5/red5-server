package org.red5.io.obu;

import static org.red5.io.obu.OBPConstants.OBU_FRAME_TYPE_BITSHIFT;
import static org.red5.io.obu.OBPConstants.OBU_FRAME_TYPE_MASK;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * OBU info
 */
public class OBUInfo {

    // OBU type
    public OBUType obuType;

    // general OBU info
    public int size, temporalId, spatialId;

    // OBU header info
    public byte[] prefix = new byte[7];

    // OBU data
    public ByteBuffer data;

    public OBUInfo(OBUType obuType, int size, int temporalId, int spatialId, byte[] prefix, ByteBuffer data) {
        this.obuType = obuType;
        this.size = size;
        this.temporalId = temporalId;
        this.spatialId = spatialId;
        this.prefix = prefix;
        this.data = data;
    }

    public OBUInfo(OBUType obuType, int size, int temporalId, int spatialId, byte[] prefix) {
        this.obuType = obuType;
        this.size = size;
        this.temporalId = temporalId;
        this.spatialId = spatialId;
        this.prefix = prefix;
    }

    public OBUInfo(OBUType obuType, int size, int temporalId, int spatialId) {
        this.obuType = obuType;
        this.size = size;
        this.temporalId = temporalId;
        this.spatialId = spatialId;
    }

    public OBUInfo(OBUType obuType, ByteBuffer data) {
        this.obuType = obuType;
        this.data = data;
    }

    public static OBUInfo build(byte[] data, int offset, int length) {
        OBUType obuType = OBUType.fromValue((data[0] & OBU_FRAME_TYPE_MASK) >>> OBU_FRAME_TYPE_BITSHIFT);
        ByteBuffer buffer = ByteBuffer.wrap(data, offset, length);
        return new OBUInfo(obuType, buffer);
    }

    @Override
    public String toString() {
        if (data != null) {
            return "OBUInfo [obuType=" + obuType + ", size=" + size + ", temporalId=" + temporalId + ", spatialId=" + spatialId + ", prefix=" + Arrays.toString(prefix) + ", data=" + data + "]";
        }
        return "OBUInfo [obuType=" + obuType + ", size=" + size + ", temporalId=" + temporalId + ", spatialId=" + spatialId + ", prefix=" + Arrays.toString(prefix) + "]";
    }

}