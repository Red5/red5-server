/*
 * RED5 Open Source Flash Server - https://github.com/Red5/ Copyright 2006-2015 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.client;

import java.io.IOException;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.red5.client.net.rtmp.ClientExceptionHandler;
import org.red5.client.net.rtmp.INetStreamEventHandler;
import org.red5.client.net.rtmp.RTMPClient;
import org.red5.io.utils.ObjectMap;
import org.red5.proxy.StreamingProxy;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.event.IEventDispatcher;
import org.red5.server.api.service.IPendingServiceCall;
import org.red5.server.api.service.IPendingServiceCallback;
import org.red5.server.net.rtmp.event.IRTMPEvent;
import org.red5.server.net.rtmp.event.Notify;
import org.red5.server.net.rtmp.status.StatusCodes;
import org.red5.server.stream.message.RTMPMessage;

/**
 * Relay a stream from one location to another via RTMP.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class StreamRelay {

    // our consumer
    private static RTMPClient client;

    // our publisher
    private static StreamingProxy proxy;

    // task timer
    private static Timer timer;

    // the source being relayed
    private static String sourceStreamName;

    /**
     * Creates a stream client to consume a stream from an end point and a proxy to relay the stream to another end point.
     *
     * @param args
     *            application arguments
     */
    public static void main(String... args) {
        // handle the args
        if (args == null || args.length < 7) {
            System.out.println("Not enough args supplied. Usage: <source uri> <source app> <source stream name> <destination uri> <destination app> <destination stream name> <publish mode>");
        } else {
            // parse the args
            String sourceHost = args[0], destHost = args[3];
            String sourceApp = args[1], destApp = args[4];
            int sourcePort = 1935, destPort = 1935;
            sourceStreamName = args[2];
            String destStreamName = args[5];
            String publishMode = args[6]; //live, record, or append
            // look to see if port was included in host string
            int colonIdx = sourceHost.indexOf(':');
            if (colonIdx > 0) {
                sourcePort = Integer.valueOf(sourceHost.substring(colonIdx + 1));
                sourceHost = sourceHost.substring(0, colonIdx);
                System.out.printf("Source host: %s port: %d\n", sourceHost, sourcePort);
            }
            colonIdx = destHost.indexOf(':');
            if (colonIdx > 0) {
                destPort = Integer.valueOf(destHost.substring(colonIdx + 1));
                destHost = destHost.substring(0, colonIdx);
                System.out.printf("Destination host: %s port: %d\n", destHost, destPort);
            }
            // create a timer
            timer = new Timer();
            // create our publisher
            proxy = new StreamingProxy();
            proxy.setHost(destHost);
            proxy.setPort(destPort);
            proxy.setApp(destApp);
            proxy.init();
            proxy.setConnectionClosedHandler(new Runnable() {
                @Override
                public void run() {
                    System.out.println("Publish connection has been closed, source will be disconnected");
                    client.disconnect();
                }
            });
            proxy.setExceptionHandler(new ClientExceptionHandler() {
                @Override
                public void handleException(Throwable throwable) {
                    throwable.printStackTrace();
                    System.exit(2);
                }
            });
            proxy.start(destStreamName, publishMode, new Object[] {});
            // wait for the publish state
            do {
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } while (!proxy.isPublished());
            System.out.println("Publishing...");

            // create the consumer
            client = new RTMPClient();
            client.setStreamEventDispatcher(new StreamEventDispatcher());
            client.setStreamEventHandler(new INetStreamEventHandler() {
                @Override
                public void onStreamEvent(Notify notify) {
                    System.out.printf("onStreamEvent: %s\n", notify);
                    ObjectMap<?, ?> map = (ObjectMap<?, ?>) notify.getCall().getArguments()[0];
                    String code = (String) map.get("code");
                    System.out.printf("<:%s\n", code);
                    if (StatusCodes.NS_PLAY_STREAMNOTFOUND.equals(code)) {
                        System.out.println("Requested stream was not found");
                        client.disconnect();
                    } else if (StatusCodes.NS_PLAY_UNPUBLISHNOTIFY.equals(code) || StatusCodes.NS_PLAY_COMPLETE.equals(code)) {
                        System.out.println("Source has stopped publishing or play is complete");
                        client.disconnect();
                    }
                }
            });
            client.setConnectionClosedHandler(new Runnable() {
                @Override
                public void run() {
                    System.out.println("Source connection has been closed, proxy will be stopped");
                    proxy.stop();
                }
            });
            client.setExceptionHandler(new ClientExceptionHandler() {
                @Override
                public void handleException(Throwable throwable) {
                    throwable.printStackTrace();
                    System.exit(1);
                }
            });
            // connect the consumer
            Map<String, Object> defParams = client.makeDefaultConnectionParams(sourceHost, sourcePort, sourceApp);
            // add pageurl and swfurl
            defParams.put("pageUrl", "");
            defParams.put("swfUrl", "app:/Red5-StreamRelay.swf");
            // indicate for the handshake to generate swf verification data
            client.setSwfVerification(true);
            // connect the client
            client.connect(sourceHost, sourcePort, defParams, new IPendingServiceCallback() {
                @Override
                public void resultReceived(IPendingServiceCall call) {
                    System.out.println("connectCallback");
                    ObjectMap<?, ?> map = (ObjectMap<?, ?>) call.getResult();
                    String code = (String) map.get("code");
                    if ("NetConnection.Connect.Rejected".equals(code)) {
                        System.out.printf("Rejected: %s\n", map.get("description"));
                        client.disconnect();
                        proxy.stop();
                    } else if ("NetConnection.Connect.Success".equals(code)) {
                        // 1. Wait for onBWDone
                        timer.schedule(new BandwidthStatusTask(), 2000L);
                    } else {
                        System.out.printf("Unhandled response code: %s\n", code);
                    }
                }
            });
            // keep sleeping main thread while the proxy runs
            do {
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } while (!proxy.isRunning());
            // kill the timer
            //timer.cancel();
            System.out.println("Stream relay exit");
        }

    }

    /**
     * Dispatches consumer events.
     */
    private static final class StreamEventDispatcher implements IEventDispatcher {

        @Override
        public void dispatchEvent(IEvent event) {
            System.out.println("ClientStream.dispachEvent()" + event.toString());
            try {
                proxy.pushMessage(null, RTMPMessage.build((IRTMPEvent) event));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

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
            if (sourceStreamName.endsWith(".flv") || sourceStreamName.endsWith(".f4v") || sourceStreamName.endsWith(".mp4")) {
                client.play(streamId, sourceStreamName, 0, -1);
            } else {
                client.play(streamId, sourceStreamName, -1, 0);
            }
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
