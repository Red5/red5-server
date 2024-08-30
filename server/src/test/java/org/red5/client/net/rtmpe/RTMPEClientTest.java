package org.red5.client.net.rtmpe;

import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.red5.client.Red5Client;
import org.red5.client.net.rtmp.ClientExceptionHandler;
import org.red5.client.net.rtmpe.RTMPEClient.ClientCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RTMPEClientTest {

    private static final Logger log = LoggerFactory.getLogger(RTMPEClientTest.class);

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void test() {
        log.info("Starting RTMPE: {}", Red5Client.getVersion());
        // source
        String host = "localhost";
        int port = 1935;
        String app = "oflaDemo";
        String streamName = "the_rise_of_skywalker_360p.mp4";
        // create a client
        final RTMPEClient client = new RTMPEClient();
        // set the stream we want to consume
        client.setStreamName(streamName);
        // create a callback handler for our client
        ClientCallback clientCallback = client.new ClientCallback(client);
        // connect
        client.connect(host, port, app, clientCallback);
        try {
            // sleep this main thread for 10 minutes
            Thread.sleep(TimeUnit.MILLISECONDS.convert(10, TimeUnit.MINUTES));
        } catch (Exception e) {
            log.warn("Exception during sleep", e);
        }
        log.info("Client exiting");
    }

    //@Test
    public void testWriter() {
        log.info("Starting RTMPE with writer: {}", Red5Client.getVersion());
        // source
        String host = "localhost";
        int port = 1935;
        String app = "oflaDemo";
        String streamName = "the_rise_of_skywalker_360p.mp4";
        // create a client
        final RTMPEClient client = new RTMPEClient();
        client.setConnectionClosedHandler(() -> {
            System.out.println("Connection closed");
        });
        client.setExceptionHandler(new ClientExceptionHandler() {
            @Override
            public void handleException(Throwable throwable) {
                throwable.printStackTrace();
            }
        });
        // set the stream we want to consume
        client.setStreamName(streamName);
        // Test writer
        Writer writer = RTMPEClient.createWriter(client);
        // create a callback handler for our client
        ClientCallback clientCallback = client.new ClientCallback(client);
        // connect
        client.connect(host, port, app, clientCallback);
        try {
            // sleep this main thread for 10 minutes
            Thread.sleep(TimeUnit.MILLISECONDS.convert(10, TimeUnit.MINUTES));
        } catch (Exception e) {
            log.warn("Exception during sleep", e);
        } finally {
            writer.stopListening();
        }
        log.info("Client exiting");
    }

    //@Test
    public void testProxy() {
        log.info("Starting RTMPE with proxy: {}", Red5Client.getVersion());
        // source
        String host = "localhost";
        int port = 1935;
        String app = "oflaDemo";
        String streamName = "the_rise_of_skywalker_360p.mp4";
        // destination
        String destinationHost = "localhost";
        int destinationPort = 1936;
        String destinationApp = "oflaDemo";
        String destinationStreamName = "the_rise_of_skywalker_live";
        // create a client
        final RTMPEClient client = new RTMPEClient();
        client.setConnectionClosedHandler(() -> {
            System.out.println("Connection closed");
        });
        client.setExceptionHandler(new ClientExceptionHandler() {
            @Override
            public void handleException(Throwable throwable) {
                throwable.printStackTrace();
            }
        });
        // set the stream we want to consume
        client.setStreamName(streamName);
        // Test proxy
        Proxy proxy = RTMPEClient.createProxy(client, destinationHost, destinationPort, destinationApp);
        // create a callback handler for our client
        ClientCallback clientCallback = client.new ClientCallback(client);
        // connect
        client.connect(host, port, app, clientCallback);
        // start the proxy before the rtmpe client so it will be ready
        proxy.start(destinationStreamName, "live");
        try {
            // sleep this main thread for 10 minutes
            Thread.sleep(TimeUnit.MILLISECONDS.convert(10, TimeUnit.MINUTES));
        } catch (Exception e) {
            log.warn("Exception during sleep", e);
        }
        log.info("Client exiting");
    }
}
