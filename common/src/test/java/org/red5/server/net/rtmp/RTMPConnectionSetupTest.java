package org.red5.server.net.rtmp;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.red5.server.api.IConnection.Encoding;

/**
 * Verifies that connection setup reads the connect command object as AMF decodes it.
 */
public class RTMPConnectionSetupTest {

    @Test
    public void testObjectEncodingDecodedAsDouble() {
        // AMF numbers decode as doubles, so objectEncoding 3 arrives as 3.0
        StubRTMPConnection conn = new StubRTMPConnection("setup");
        Map<String, Object> params = new HashMap<>();
        params.put("objectEncoding", 3.0d);
        conn.setup("localhost", "live", params);
        assertEquals(Encoding.AMF3, conn.getEncoding());
    }

    @Test
    public void testObjectEncodingZeroStaysAmf0() {
        StubRTMPConnection conn = new StubRTMPConnection("setup");
        Map<String, Object> params = new HashMap<>();
        params.put("objectEncoding", 0.0d);
        conn.setup("localhost", "live", params);
        assertEquals(Encoding.AMF0, conn.getEncoding());
    }

    @Test
    public void testEnhancedRtmpPropertiesAsDecoded() {
        // fourCcList as an immutable list, the info maps with numeric flags and capsEx as a double, as a decoder may deliver them
        StubRTMPConnection conn = new StubRTMPConnection("setup");
        Map<String, Object> params = new HashMap<>();
        params.put("fourCcList", List.of("hvc1", "av01"));
        params.put("videoFourCcInfoMap", Map.of("*", 4.0d));
        params.put("audioFourCcInfoMap", Map.of("*", 4.0d));
        params.put("capsEx", 15.0d);
        conn.setup("localhost", "live", params);
        assertEquals(Encoding.AMF0, conn.getEncoding());
    }

}
