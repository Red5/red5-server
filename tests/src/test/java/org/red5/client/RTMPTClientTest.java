package org.red5.client;

import java.util.Map;

import org.red5.client.net.rtmp.ClientExceptionHandler;
import org.red5.client.net.rtmpt.RTMPTClient;
import org.red5.client.util.PropertiesReader;
import org.red5.io.utils.ObjectMap;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.event.IEventDispatcher;
import org.red5.server.api.service.IPendingServiceCall;
import org.red5.server.api.service.IPendingServiceCallback;
import org.red5.server.net.rtmp.Channel;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.event.Notify;
import org.red5.server.net.rtmp.event.Ping;
import org.red5.server.net.rtmp.event.Ping.PingType;
import org.red5.server.net.rtmp.message.Header;
import org.red5.server.net.rtmp.status.StatusCodes;
import org.junit.experimental.categories.Category;
import org.red5.test.IntegrationTest;

@Category(IntegrationTest.class)
public class RTMPTClientTest extends RTMPTClient {

    private static boolean finished = false;

    public static void main(String[] args) throws InterruptedException {

        final RTMPTClientTest player = new RTMPTClientTest();
        // decide whether or not the source is live or vod
        //player.setLive(true);
        // connect
        player.connect();

        synchronized (RTMPTClientTest.class) {
            if (!finished) {
                RTMPTClientTest.class.wait();
            }
        }

        System.out.println("Ended");
    }

    public void connect() {
        setExceptionHandler(new ClientExceptionHandler() {
            @Override
            public void handleException(Throwable throwable) {
                throwable.printStackTrace();
            }
        });
        setStreamEventDispatcher(streamEventDispatcher);
        connect(PropertiesReader.getProperty("rtmpt.server"), Integer.valueOf(PropertiesReader.getProperty("rtmpt.port")), PropertiesReader.getProperty("rtmpt.app"), connectCallback);
    }

    private IEventDispatcher streamEventDispatcher = new IEventDispatcher() {
        @Override
        public void dispatchEvent(IEvent event) {
            System.out.println("ClientStream.dispachEvent()" + event.toString());
        }
    };

    private IPendingServiceCallback methodCallCallback = new IPendingServiceCallback() {
        @Override
        public void resultReceived(IPendingServiceCall call) {
            System.out.println("methodCallCallback");
            Map<?, ?> map = (Map<?, ?>) call.getResult();
            System.out.printf("Response %s\n", map);
        }
    };

    public void getFlvList() {
        invoke("demoService.getListOfAvailableFLVs", new Object[] {}, methodCallCallback);
    }

    public void test() {
        new Thread() {
            @Override
            public void run() {
                for (int i = 0; i < 100; ++i) {
                    getFlvList();
                }
            }
        }.start();
    }

    private IPendingServiceCallback connectCallback = new IPendingServiceCallback() {
        @Override
        public void resultReceived(IPendingServiceCall call) {
            System.out.println("connectCallback");
            ObjectMap<?, ?> map = (ObjectMap<?, ?>) call.getResult();
            String code = (String) map.get("code");
            System.out.printf("Response code: %s\n", code);
            if ("NetConnection.Connect.Rejected".equals(code)) {
                System.out.printf("Rejected: %s\n", map.get("description"));
                disconnect();
                synchronized (RTMPTClientTest.class) {
                    finished = true;
                    RTMPTClientTest.class.notifyAll();
                }
            } else if ("NetConnection.Connect.Failed".equals(code)) {
                System.out.printf("Failed: %s\n", map.get("description"));
                disconnect();
                synchronized (RTMPTClientTest.class) {
                    finished = true;
                    RTMPTClientTest.class.notifyAll();
                }
            } else if ("NetConnection.Connect.Success".equals(code)) {
                test();
                createStream(createStreamCallback);
            } else {
                System.out.printf("Unhandled response code: %s\n", code);
            }
        }
    };

    private IPendingServiceCallback createStreamCallback = new IPendingServiceCallback() {
        @Override
        public void resultReceived(IPendingServiceCall call) {
            int streamId = (Integer) call.getResult();
            // live buffer 0.5s / vod buffer 4s
            if (Boolean.valueOf(PropertiesReader.getProperty("rtmpt.live"))) {
                conn.ping(new Ping(PingType.CLIENT_BUFFER, streamId, 500));
                play(streamId, PropertiesReader.getProperty("rtmpt.name"), -1, -1);
            } else {
                conn.ping(new Ping(PingType.CLIENT_BUFFER, streamId, 4000));
                play(streamId, PropertiesReader.getProperty("rtmpt.name"), 0, -1);
            }
        }
    };

    @SuppressWarnings("unchecked")
    protected void onCommand(RTMPConnection conn, Channel channel, Header header, Notify notify) {
        super.onCommand(conn, channel, header, notify);
        System.out.println("onInvoke, header = " + header.toString());
        System.out.println("onInvoke, notify = " + notify.toString());
        Object obj = notify.getCall().getArguments().length > 0 ? notify.getCall().getArguments()[0] : null;
        if (obj instanceof Map) {
            Map<String, String> map = (Map<String, String>) obj;
            String code = map.get("code");
            if (StatusCodes.NS_PLAY_STOP.equals(code)) {
                synchronized (RTMPTClientTest.class) {
                    finished = true;
                    RTMPTClientTest.class.notifyAll();
                }
                disconnect();
                System.out.println("Disconnected");
            }
        }

    }

}
