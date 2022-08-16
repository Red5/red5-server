package org.red5.client.net.rtmps;

import java.util.Map;

import org.junit.Test;
import org.red5.client.net.rtmp.ClientExceptionHandler;
import org.red5.client.util.PropertiesReader;
import org.red5.io.utils.ObjectMap;
import org.red5.server.api.service.IPendingServiceCall;
import org.red5.server.api.service.IPendingServiceCallback;
import org.red5.server.net.rtmp.event.Ping;

public class RTMPSClientTest {

    // https://github.com/Red5/red5-client/pull/31
    @Test
    public void test31() throws InterruptedException {
        final RTMPSClient client = new RTMPSClient();
        client.setConnectionClosedHandler(new Runnable() {
            @Override
            public void run() {
                System.out.println("Connection closed");
            }
        });
        client.setExceptionHandler(new ClientExceptionHandler() {
            @Override
            public void handleException(Throwable throwable) {
                throwable.printStackTrace();
            }
        });
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                client.connect(PropertiesReader.getProperty("rtmps.server"), Integer.valueOf(PropertiesReader.getProperty("rtmps.port")), PropertiesReader.getProperty("rtmps.app"), new IPendingServiceCallback() {
                    @Override
                    public void resultReceived(IPendingServiceCall result) {
                        System.out.println("resultReceived: " + result);
                        ObjectMap<?, ?> map = (ObjectMap<?, ?>) result.getResult();
                        String code = (String) map.get("code");
                        System.out.printf("Response code: %s\n", code);
                        if ("NetConnection.Connect.Rejected".equals(code)) {
                            System.out.printf("Rejected: %s\n", map.get("description"));
                            client.disconnect();
                        } else if ("NetConnection.Connect.Success".equals(code)) {
                            System.out.println("Success: " + result.isSuccess());
                            // if its oflaDemo, get the list of flvs
                            if ("oflaDemo".equals(PropertiesReader.getProperty("rtmps.app"))) {
                                client.invoke("demoService.getListOfAvailableFLVs", new Object[] {}, new IPendingServiceCallback() {
                                    @Override
                                    public void resultReceived(IPendingServiceCall call) {
                                        System.out.println("methodCallCallback");
                                        Map<?, ?> map = (Map<?, ?>) call.getResult();
                                        System.out.printf("Response %s\n", map);
                                    }
                                });
                            }
                            client.createStream(new IPendingServiceCallback() {
                                @Override
                                public void resultReceived(IPendingServiceCall call) {
                                    Number streamId = (Number) call.getResult();
                                    // live buffer 0.5s / vod buffer 4s
                                    if (Boolean.valueOf(PropertiesReader.getProperty("rtmps.live"))) {
                                        client.ping(Ping.CLIENT_BUFFER, streamId, 500);
                                        client.play(streamId, PropertiesReader.getProperty("rtmps.name"), -1, -1);
                                    } else {
                                        client.ping(Ping.CLIENT_BUFFER, streamId, 4000);
                                        client.play(streamId, PropertiesReader.getProperty("rtmps.name"), 0, -1);
                                    }
                                }
                            });
                        }
                    }
                });
            }
        });
        t.start();
        t.join();
        System.out.println("Joined");
        Thread.sleep(60000L);
        // disconnect
        client.disconnect();
    }
}
