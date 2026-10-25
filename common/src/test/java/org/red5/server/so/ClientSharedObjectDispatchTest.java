package org.red5.server.so;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;
import org.red5.server.api.IAttributeStore;
import org.red5.server.api.so.ISharedObjectBase;
import org.red5.server.api.so.ISharedObjectListener;
import org.red5.server.net.rtmp.codec.RTMPProtocolDecoder;
import org.red5.server.net.rtmp.codec.RTMPProtocolEncoder;
import org.red5.server.so.ISharedObjectEvent.Type;

/**
 * A client shared object receives what the server sends through the encoder and the decoder.
 */
public class ClientSharedObjectDispatchTest {

    /**
     * The send message marker (0x06) is the same in both directions and the decoder names it SERVER_SEND_MESSAGE; the client shared object
     * must deliver it to its listeners.
     */
    @Test
    public void testSendMessageReachesListeners() {
        SharedObjectMessage sent = new SharedObjectMessage(null, "so", 1, false);
        sent.addEvent(Type.CLIENT_INITIAL_DATA, null, null);
        sent.addEvent(Type.CLIENT_UPDATE_DATA, "color", "red");
        sent.addEvent(Type.CLIENT_SEND_MESSAGE, "hello", Arrays.asList("arg", 1.5d));
        IoBuffer out = new RTMPProtocolEncoder().encodeSharedObject(sent).flip();
        ISharedObjectMessage received = new RTMPProtocolDecoder().decodeSharedObject(out);
        ClientSharedObject so = new ClientSharedObject("so", false);
        List<String> calls = new ArrayList<>();
        so.addSharedObjectListener(new ISharedObjectListener() {

            public void onSharedObjectConnect(ISharedObjectBase so) {
                calls.add("connect");
            }

            public void onSharedObjectDisconnect(ISharedObjectBase so) {
            }

            public void onSharedObjectUpdate(ISharedObjectBase so, String key, Object value) {
                calls.add("update " + key + "=" + value);
            }

            public void onSharedObjectUpdate(ISharedObjectBase so, IAttributeStore values) {
            }

            public void onSharedObjectUpdate(ISharedObjectBase so, Map<String, Object> values) {
                calls.add("update " + values);
            }

            public void onSharedObjectDelete(ISharedObjectBase so, String key) {
            }

            public void onSharedObjectClear(ISharedObjectBase so) {
            }

            public void onSharedObjectSend(ISharedObjectBase so, String method, List<?> params) {
                calls.add("send " + method + " " + params);
            }
        });
        so.dispatchEvent(received);
        assertEquals(Arrays.asList("connect", "update color=red", "send hello [arg, 1.5]"), calls);
        assertEquals("red", so.getAttribute("color"));
    }

}
