package org.red5.client.net.rtmp;

import java.util.Timer;
import java.util.TimerTask;

import org.junit.Test;
import org.red5.io.utils.ObjectMap;
import org.red5.server.api.service.IPendingServiceCall;
import org.red5.server.api.service.IPendingServiceCallback;
import org.red5.server.net.rtmp.event.Notify;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RTMPClientTest {

    private Logger log = LoggerFactory.getLogger(RTMPClientTest.class);

    private static RTMPClient client = new RTMPClient();

    // task timer
    private static Timer timer = new Timer();

    // application name
    private static String app = "live"; //oflaDemo, vod;

    // AMS sample
    //    private static String sourceStreamName = "mp4:sample1_1500kbps.f4v";

    // sample video under oflaDemo example app
    private static String sourceStreamName = "mp4:thx_deep_note_360p.mp4";

    // local sample
    //private static String sourceStreamName = "flashContent";

    // https://github.com/Red5/red5-client/issues/26
    @Test
    public void test26() throws InterruptedException {
        client.setStreamEventHandler(new INetStreamEventHandler() {
            @Override
            public void onStreamEvent(Notify notify) {
                log.info("ClientStream.dispachEvent: {}", notify);
            }
        });
        client.setServiceProvider(new ClientMethodHander());
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

        IPendingServiceCallback connectCallback = new IPendingServiceCallback() {
            @Override
            public void resultReceived(IPendingServiceCall call) {
                log.info("connectCallback");
                ObjectMap<?, ?> map = (ObjectMap<?, ?>) call.getResult();
                String code = (String) map.get("code");
                log.info("Response code: {}", code);
                if ("NetConnection.Connect.Rejected".equals(code)) {
                    System.out.printf("Rejected: %s\n", map.get("description"));
                    client.disconnect();
                } else if ("NetConnection.Connect.Success".equals(code)) {
                    // 1. Wait for onBWDone
                    timer.schedule(new BandwidthStatusTask(), 2000L);
                }
            }
        };

        /*
         * client.connect("localhost", 1935, "live/remote/0586e318-6277-11e3-adc2-22000a1d91fe", new IPendingServiceCallback() {
         * @Override public void resultReceived(IPendingServiceCall result) { System.out.println("resultReceived: " + result); ObjectMap<?, ?> map = (ObjectMap<?, ?>) result.getResult();
         * String code = (String) map.get("code"); System.out.printf("Response code: %s\n", code); if ("NetConnection.Connect.Rejected".equals(code)) { System.out.printf("Rejected: %s\n",
         * map.get("description")); client.disconnect(); } else if ("NetConnection.Connect.Success".equals(code)) { System.out.println("success: " + result.isSuccess()); ArrayList<Object>
         * list = new ArrayList<>(); list.add(new Object[] { "fujifilm-x100s-video-test-1080p-full-hd-hdmp4_720.mp4" }); list.add(new Object[] {
         * "canon-500d-test-video-720-hd-30-fr-hdmp4_720.mp4" }); Object[] params = { "64", "cc-video-processed/", list }; //Object[] params = { "64", "cc-video-processed/" };
         * client.invoke("loadPlaylist", params, new IPendingServiceCallback() {
         * @Override public void resultReceived(IPendingServiceCall result) { System.out.println(result); } }); } } });
         */
        client.connect("localhost", 1935, app, connectCallback);

        do {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
            }
        } while (client.conn != null && !client.conn.isClosed());
        log.debug("Client not connected: {}", client.conn);
        timer.cancel();
        log.info("Exit");
    }

    /**
     * Handles result from subscribe call.
     */
    private static final class SubscribeStreamCallBack implements IPendingServiceCallback {

        @Override
        public void resultReceived(IPendingServiceCall call) {
            System.out.println("resultReceived: " + call);
        }

    }

    /**
     * Creates a "stream" via playback, this is the source stream.
     */
    private static final class CreateStreamCallback implements IPendingServiceCallback {

        @Override
        public void resultReceived(IPendingServiceCall call) {
            System.out.println("resultReceived: " + call);
            Double streamId = (Double) call.getResult();
            System.out.println("stream id: " + streamId);
            // send our buffer size request
            client.play(streamId, sourceStreamName, 0, -1);
        }

    }

    /**
     * Continues to check for onBWDone
     */
    private static final class BandwidthStatusTask extends TimerTask {

        @Override
        public void run() {
            // check for onBWDone
            System.out.println("Bandwidth check done: " + client.isBandwidthCheckDone());
            // cancel this task
            this.cancel();
            // create a task to wait for subscribed
            timer.schedule(new PlayStatusTask(), 1000L);
            // 2. send FCSubscribe
            client.subscribe(new SubscribeStreamCallBack(), new Object[] { sourceStreamName });
        }

    }

    private static final class PlayStatusTask extends TimerTask {

        @Override
        public void run() {
            // checking subscribed
            System.out.println("Subscribed: " + client.isSubscribed());
            // cancel this task
            this.cancel();
            // 3. create stream
            client.createStream(new CreateStreamCallback());
        }

    }

}
