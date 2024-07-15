package org.red5.io.obu;

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

    @Override
    public String toString() {
        if (data != null) {
            return "OBUInfo [obuType=" + obuType + ", size=" + size + ", temporalId=" + temporalId + ", spatialId=" + spatialId + ", prefix=" + Arrays.toString(prefix) + ", data=" + data + "]";
        }
        return "OBUInfo [obuType=" + obuType + ", size=" + size + ", temporalId=" + temporalId + ", spatialId=" + spatialId + ", prefix=" + Arrays.toString(prefix) + "]";
    }

}