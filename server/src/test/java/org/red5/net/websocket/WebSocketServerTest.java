/*
 * RED5 Open Source Flash Server - https://github.com/red5 Copyright 2006-2018 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.net.websocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.catalina.LifecycleException;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilter.NextFilter;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.apache.tomcat.websocket.WsSession;
import org.red5.server.adapter.MultiThreadedApplicationAdapter;
import org.red5.server.api.scope.IScope;
import org.red5.server.plugin.PluginRegistry;
import org.red5.server.scope.GlobalScope;
import org.red5.server.tomcat.EmbeddedTomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnMessage;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;

/**
 * Tests for websocket operations.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class WebSocketServerTest {

    protected static Logger log = LoggerFactory.getLogger(WebSocketServerTest.class);

    @SuppressWarnings("unused")
    private static Object writtenResult;

    private static WebSocketScope scope;

    /*
     * Test data from the rfc <pre> A single-frame unmasked text message (contains "Hello") 0x81 0x05 0x48 0x65 0x6c 0x6c 0x6f A single-frame masked text message (contains "Hello")
     * 0x81 0x85 0x37 0xfa 0x21 0x3d 0x7f 0x9f 0x4d 0x51 0x58 A fragmented unmasked text message 0x01 0x03 0x48 0x65 0x6c (contains "Hel") 0x80 0x02 0x6c 0x6f (contains "lo") Unmasked
     * Ping request and masked Ping response 0x89 0x05 0x48 0x65 0x6c 0x6c 0x6f (contains a body of "Hello", but the contents of the body are arbitrary) 0x8a 0x85 0x37 0xfa 0x21 0x3d
     * 0x7f 0x9f 0x4d 0x51 0x58 (contains a body of "Hello", matching the body of the ping) A 256 bytes binary message in a single unmasked frame 0x82 0x7E 0x0100 [256 bytes of binary
     * data] A 64KiB binary message in a single unmasked frame 0x82 0x7F 0x0000000000010000 [65536 bytes of binary data] </pre>
     */
    @SuppressWarnings("unused")
    //@Test
    public void testMultiThreaded() throws Throwable {
        log.info("testMultiThreaded enter");
        // create the server instance
        Thread server = new Thread() {
            @Override
            public void run() {
                log.debug("Server thread run");
                try {
                    WSServer.main(null);
                } catch (Exception e) {
                    log.error("Error in server thread", e);
                }
                log.debug("Server thread exit");
            }
        };
        server.setDaemon(true);
        server.start();
        // add plugin to the registry
        WebSocketPlugin plugin = new WebSocketPlugin();
        PluginRegistry.register(plugin);
        // start plugin
        plugin.doStart();
        // create a scope for the manager
        IScope appScope = new GlobalScope();
        // create an app
        MultiThreadedApplicationAdapter app = new MultiThreadedApplicationAdapter();
        app.setScope(appScope);
        // add the app
        plugin.setApplication(app);
        // get the manager
        WebSocketScopeManager manager = plugin.getManager(appScope);
        manager.setApplication(appScope);
        // wait for server
        while (!WSServer.isListening()) {
            Thread.sleep(10L);
        }
        // how many threads
        int threads = 1;
        List<Worker> tasks = new ArrayList<Worker>(threads);
        for (int t = 0; t < threads; t++) {
            tasks.add(new Worker());
        }
        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        // invokeAll() blocks until all tasks have run...
        long start = System.nanoTime();
        List<Future<Object>> futures = executorService.invokeAll(tasks);
        log.info("Runtime: {} ns", (System.nanoTime() - start));
        for (Worker r : tasks) {
            // loop through and check results

        }
        Thread.sleep(2000L);
        // stop server
        server.interrupt();
        WSServer.stop();
        // stop plugin
        PluginRegistry.shutdown();
        log.info("testMultiThreaded exit");
    }

    //	@Test
    //	public void testDecodingErrorJuneSixth() throws Throwable {
    //		log.info("-------------------------------------------------------test66 enter");
    //		// masked
    //		IoBuffer in = IoBuffer.wrap(new byte[] { (byte) 0x81, (byte) 0xFE, (byte) 0x00, (byte) 0xAE, (byte) 0x97, (byte) 0x6A, (byte) 0xAD, (byte) 0x23, (byte) 0xEC, (byte) 0x48, (byte) 0xC9, (byte) 0x42, (byte) 0xE3, (byte) 0x0B, (byte) 0x8F, (byte) 0x19, (byte) 0xEC, (byte) 0x48, (byte) 0xDE, (byte) 0x46,
    //				(byte) 0xE4, (byte) 0x19, (byte) 0xC4, (byte) 0x4C, (byte) 0xF9, (byte) 0x03, (byte) 0xC9, (byte) 0x01, (byte) 0xAD, (byte) 0x48, (byte) 0x9F, (byte) 0x12, (byte) 0xA3, (byte) 0x5A, (byte) 0x98, (byte) 0x16, (byte) 0xA4, (byte) 0x5E, (byte) 0x9E, (byte) 0x1A, (byte) 0xAE, (byte) 0x5C, (byte) 0x9A,
    //				(byte) 0x10, (byte) 0xA4, (byte) 0x5E, (byte) 0x9E, (byte) 0x01, (byte) 0xBB, (byte) 0x48, (byte) 0xD8, (byte) 0x50, (byte) 0xF2, (byte) 0x18, (byte) 0xC4, (byte) 0x47, (byte) 0xB5, (byte) 0x50, (byte) 0x8F, (byte) 0x4B, (byte) 0xE0, (byte) 0x03, (byte) 0xDF, (byte) 0x4D, (byte) 0xA4, (byte) 0x5B,
    //				(byte) 0x9D, (byte) 0x4F, (byte) 0xA3, (byte) 0x5F, (byte) 0x9F, (byte) 0x46, (byte) 0xA6, (byte) 0x53, (byte) 0xDF, (byte) 0x10, (byte) 0xFE, (byte) 0x09, (byte) 0xDC, (byte) 0x01, (byte) 0xBB, (byte) 0x48, (byte) 0xDE, (byte) 0x46, (byte) 0xE4, (byte) 0x19, (byte) 0xC4, (byte) 0x4C, (byte) 0xF9,
    //				(byte) 0x48, (byte) 0x97, (byte) 0x58, (byte) 0xB5, (byte) 0x0E, (byte) 0xCC, (byte) 0x57, (byte) 0xF6, (byte) 0x48, (byte) 0x97, (byte) 0x57, (byte) 0xE5, (byte) 0x1F, (byte) 0xC8, (byte) 0x5E, (byte) 0xBB, (byte) 0x48, (byte) 0xC8, (byte) 0x5B, (byte) 0xE3, (byte) 0x18, (byte) 0xCC, (byte) 0x01,
    //				(byte) 0xAD, (byte) 0x11, (byte) 0x8F, (byte) 0x56, (byte) 0xE4, (byte) 0x0F, (byte) 0xDF, (byte) 0x4D, (byte) 0xF6, (byte) 0x07, (byte) 0xC8, (byte) 0x01, (byte) 0xAD, (byte) 0x48, (byte) 0xFD, (byte) 0x42 });
    //		// get results
    //		WSMessage result = WebSocketDecoder.decodeIncommingData(in, null);
    //		assertTrue(result.getMessageType() == MessageType.TEXT);
    //		log.info("{}", result.getMessageAsString());
    //		assertEquals("Hello", result.getMessageAsString());
    //		log.info("-------------------------------------------------------test66 exit");
    //	}

    @SuppressWarnings("unused")
    //@Test
    public void testMasked() throws Throwable {
        log.info("testMasked enter");
        // masked
        IoBuffer in = IoBuffer.wrap(new byte[] { (byte) 0x81, (byte) 0x85, (byte) 0x37, (byte) 0xfa, (byte) 0x21, (byte) 0x3d, (byte) 0x7f, (byte) 0x9f, (byte) 0x4d, (byte) 0x51, (byte) 0x58 });
        // create session and conn
        DummySession sess = new DummySession();
        WebSocketConnection conn = new WebSocketConnection(scope, sess);
        //session.setAttribute(Constants.CONNECTION, conn);
        // decode
        //        DummyDecoder decoder = new DummyDecoder();
        //        decoder.dummyDecode(session, in, new DummyOutput());
        //        assertTrue(((WSMessage) writtenResult).getMessageType() == WSMessage.MessageType.TEXT);
        //        assertEquals("Hello", ((WSMessage) writtenResult).getMessageAsString());
        //        log.info("testMasked exit");
    }

    @SuppressWarnings("unused")
    //@Test
    public void testUnmasked() throws Throwable {
        log.info("testUnmasked enter");
        // unmasked
        IoBuffer in = IoBuffer.wrap(new byte[] { (byte) 0x81, (byte) 0x05, (byte) 0x48, (byte) 0x65, (byte) 0x6c, (byte) 0x6c, (byte) 0x6f });
        // create session and conn
        DummySession sess = new DummySession();
        WebSocketConnection conn = new WebSocketConnection(scope, sess);
        //session.setAttribute(Constants.CONNECTION, conn);
        // decode
        //        DummyDecoder decoder = new DummyDecoder();
        //        decoder.dummyDecode(session, in, new DummyOutput());
        //        assertTrue(((WSMessage) writtenResult).getMessageType() == WSMessage.MessageType.TEXT);
        //        assertEquals("Hello", ((WSMessage) writtenResult).getMessageAsString());
        //        log.info("testUnmasked exit");
    }

    @SuppressWarnings("unused")
    //@Test
    public void testFragmented() throws Throwable {
        log.info("testFragmented enter");
        // fragments
        byte[] part1 = new byte[] { (byte) 0x01, (byte) 0x03, (byte) 0x48, (byte) 0x65, (byte) 0x6c };
        byte[] part2 = new byte[] { (byte) 0x80, (byte) 0x02, (byte) 0x6c, (byte) 0x6f };
        // create session and conn
        DummySession sess = new DummySession();
        WebSocketConnection conn = new WebSocketConnection(scope, sess);
        //session.setAttribute(Constants.CONNECTION, conn);
        // decode
        //        DummyDecoder decoder = new DummyDecoder();
        //        DummyOutput out = new DummyOutput();
        //        // create io buffer
        //        IoBuffer in = IoBuffer.allocate(5, false);
        //        // add part 1
        //        in.put(part1);
        //        in.flip();
        //        // decode with first fragment
        //        decoder.dummyDecode(session, in, out);
        //        // add part 2
        //        in = IoBuffer.allocate(4, false);
        //        in.put(part2);
        //        in.flip();
        //        // decode with second fragment
        //        decoder.dummyDecode(session, in, out);
        //        // check result
        //        assertTrue(((WSMessage) writtenResult).getMessageType() == WSMessage.MessageType.TEXT);
        //        assertEquals("Hello", ((WSMessage) writtenResult).getMessageAsString());
        log.info("testFragmented exit");
    }

    //    @Test
    //    public void testUnmaskedPing() throws Throwable {
    //        log.info("testUnmaskedPing enter");
    //        // unmasked ping
    //        IoBuffer in = IoBuffer.wrap(new byte[] { (byte) 0x89, (byte) 0x05, (byte) 0x48, (byte) 0x65, (byte) 0x6c, (byte) 0x6c, (byte) 0x6f });
    //        // create session and conn
    //        DummySession session = new DummySession();
    //        WebSocketConnection conn = new WebSocketConnection(session);
    //        session.setAttribute(Constants.CONNECTION, conn);
    //        // decode
    //        DummyDecoder decoder = new DummyDecoder();
    //        decoder.dummyDecode(session, in, new DummyOutput());
    //        assertTrue(((WSMessage) writtenResult).getMessageType() == MessageType.PING);
    //        assertEquals("Hello", ((WSMessage) writtenResult).getMessageAsString());
    //        log.info("testUnmaskedPing exit");
    //    }

    //    @Test
    //    public void testMaskedPong() throws Throwable {
    //        log.info("testMaskedPong enter");
    //        // masked pong
    //        IoBuffer in = IoBuffer.wrap(new byte[] { (byte) 0x8a, (byte) 0x85, (byte) 0x37, (byte) 0xfa, (byte) 0x21, (byte) 0x3d, (byte) 0x7f, (byte) 0x9f, (byte) 0x4d, (byte) 0x51, (byte) 0x58 });
    //        // create session and conn
    //        DummySession session = new DummySession();
    //        WebSocketConnection conn = new WebSocketConnection(session);
    //        session.setAttribute(Constants.CONNECTION, conn);
    //        // decode
    //        DummyDecoder decoder = new DummyDecoder();
    //        decoder.dummyDecode(session, in, new DummyOutput());
    //        assertTrue(((WSMessage) writtenResult).getMessageType() == MessageType.PONG);
    //        assertEquals("Hello", ((WSMessage) writtenResult).getMessageAsString());
    //        log.info("testMaskedPong exit");
    //    }

    //    @Test
    //    public void testUnmaskedRoundTrip() throws Throwable {
    //        log.info("testUnmaskedRoundTrip enter");
    //        // create session and conn
    //        DummySession session = new DummySession();
    //        WebSocketConnection conn = new WebSocketConnection(session);
    //        session.setAttribute(Constants.CONNECTION, conn);
    //        // encode
    //        DummyEncoder encoder = new DummyEncoder();
    //        encoder.dummyEncode(session, Packet.build("Hello".getBytes(), WSMessage.MessageType.TEXT), new DummyOutput());
    //        // decode
    //        DummyDecoder decoder = new DummyDecoder();
    //        decoder.dummyDecode(session, (IoBuffer) writtenResult, new DummyOutput());
    //        assertTrue(((WSMessage) writtenResult).getMessageType() == WSMessage.MessageType.TEXT);
    //        assertEquals("Hello", ((WSMessage) writtenResult).getMessageAsString());
    //        log.info("testUnmaskedRoundTrip exit");
    //    }

    //    @Test
    //    public void testUnmaskedPingRoundTrip() throws Throwable {
    //        log.info("testUnmaskedPingRoundTrip enter");
    //        // create session and conn
    //        DummySession session = new DummySession();
    //        WebSocketConnection conn = new WebSocketConnection(session);
    //        session.setAttribute(Constants.CONNECTION, conn);
    //        // encode
    //        DummyEncoder encoder = new DummyEncoder();
    //        encoder.dummyEncode(session, Packet.build("Hello".getBytes(), MessageType.PING), new DummyOutput());
    //        // decode
    //        DummyDecoder decoder = new DummyDecoder();
    //        decoder.dummyDecode(session, (IoBuffer) writtenResult, new DummyOutput());
    //        assertTrue(((WSMessage) writtenResult).getMessageType() == MessageType.PING);
    //        assertEquals("Hello", ((WSMessage) writtenResult).getMessageAsString());
    //        log.info("testUnmaskedPingRoundTrip exit");
    //    }

    //@Test
    public void testUriWithParams() throws Throwable {
        log.info("\ntestUriWithParams enter");
        // create the server instance
        Thread server = new Thread() {
            @Override
            public void run() {
                log.debug("Server thread run");
                try {
                    WSServer.main(null);
                } catch (Exception e) {
                    log.error("Error in server thread", e);
                }
                log.debug("Server thread exit");
            }
        };
        server.setDaemon(true);
        server.start();
        // add plugin to the registry
        WebSocketPlugin plugin = new WebSocketPlugin();
        PluginRegistry.register(plugin);
        // start plugin
        plugin.doStart();
        // create a scope for the manager
        IScope appScope = new GlobalScope();
        // create an app
        MultiThreadedApplicationAdapter app = new MultiThreadedApplicationAdapter();
        app.setScope(appScope);
        // add the app
        plugin.setApplication(app);
        // get the manager
        WebSocketScopeManager manager = plugin.getManager(appScope);
        manager.setApplication(appScope);
        // wait for server
        while (!WSServer.isListening()) {
            Thread.sleep(10L);
        }
        // create the client
        final TyrusWSClient client = new TyrusWSClient();
        //final TyrusWSClient client = new TyrusWSClient(8192 * 10);
        Thread t = new Thread(new Runnable() {
            public void run() {
                client.start();
            }
        }, "tyrus");
        t.start();
        t.join(5000);
        // send a message
        //client.sendMessage("This is a test");
        // terminate client
        client.terminate();
        // stop server
        server.interrupt();
        WSServer.stop();
        // stop plugin
        PluginRegistry.shutdown();
        log.info("testUriWithParams exit");
    }

    private class Worker implements Callable<Object> {

        boolean failed;

        public Object call() throws Exception {
            //            WSClient client = new WSClient("localhost", 8888);
            //            //WSClient client = new WSClient("localhost", 8888, 8192 * 10);
            //            client.connect();
            //            if (client.isConnected()) {
            //                client.send("This is a test: " + System.currentTimeMillis());
            //            } else {
            //                failed = true;
            //            }
            return failed;
        }

    }

    public static class WSServer {

        private static EmbeddedTomcat tomcat;

        private static boolean listening;

        public static void stop() {
            try {
                tomcat.stop();
            } catch (LifecycleException e) {
                e.printStackTrace();
            }
            listening = false;
        }

        public static boolean isListening() {
            return listening;
        }

        public static void main(String[] args) throws IOException, LifecycleException {

            // loop through the addresses and bind
            Set<InetSocketAddress> socketAddresses = new HashSet<InetSocketAddress>();
            socketAddresses.add(new InetSocketAddress("0.0.0.0", 8888));
            //socketAddresses.add(new InetSocketAddress("localhost", 8888));
            log.debug("Binding to {}", socketAddresses.toString());
            tomcat.start();
            System.out.println("WS server started listening");
            listening = true;
            while (true) {
                try {
                    Thread.sleep(2000L);
                } catch (InterruptedException e) {
                    System.out.println("WS server stopped listening");
                }
            }
        }

    }

    @ClientEndpoint
    public class TyrusWSClient extends Endpoint {

        private WebSocketContainer container = null;

        private Session session = null;

        private Object waitLock = new Object();

        private String cookie = null;

        public TyrusWSClient() {
        }

        public TyrusWSClient(int cookieLength) {
            this.cookie = RandomStringUtils.randomAscii(cookieLength);
            log.debug("Cookie length: {}", cookie.length());
        }

        @Override
        public void onOpen(Session session, EndpointConfig config) {
            log.debug("Opened: {} config: {}", session, config);
        }

        @OnMessage
        public void onMessage(String message) {
            log.debug("Received msg: {}", message);
        }

        public void sendMessage(String message) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void wait4TerminateSignal() {
            synchronized (waitLock) {
                try {
                    waitLock.wait();
                } catch (InterruptedException e) {
                }
            }
        }

        public void terminate() {
            synchronized (waitLock) {
                waitLock.notifyAll();
            }
        }

        // https://tyrus-project.github.io/
        public void start() {
            //ClientManager mgr = ClientManager.createClient(); //org.glassfish.tyrus.client.ClientManager
            //mgr.connectToServer(TyrusWSClient.class, "ws://localhost:8888/app?id=cafebeef0123");
            try {
                // Tyrus is plugged via ServiceLoader API. See notes above
                container = ContainerProvider.getWebSocketContainer();
                if (cookie != null) {
                    ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().configurator(new ClientEndpointConfig.Configurator() {
                        @Override
                        public void beforeRequest(Map<String, List<String>> headers) {
                            super.beforeRequest(headers);
                            List<String> cookieList = headers.get("Cookie");
                            if (null == cookieList) {
                                cookieList = new ArrayList<>();
                            }
                            cookieList.add(String.format("monster=\"%s\"", cookie)); // set your cookie value here
                            headers.put("Cookie", cookieList);
                        }
                    }).build();
                    session = container.connectToServer(TyrusWSClient.class, cec, URI.create("ws://localhost:8888/default?id=cafebeef0123"));
                } else {
                    session = container.connectToServer(TyrusWSClient.class, URI.create("ws://localhost:8888/default?id=cafebeef0123"));
                }
                wait4TerminateSignal();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (session != null) {
                    try {
                        session.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            log.debug("exit");
        }

    }

    @SuppressWarnings("unused")
    private class DummyOutput implements ProtocolDecoderOutput, ProtocolEncoderOutput {

        @Override
        public void mergeAll() {
        }

        @Override
        public WriteFuture flush() {
            return null;
        }

        @Override
        public void write(Object message) {
            log.debug("out: {}", message);
            WebSocketServerTest.writtenResult = message;
        }

        @Override
        public void flush(NextFilter nextFilter, IoSession session) {
        }

    }

    private class DummySession extends WsSession {

        //localEndpoint, wsRemoteEndpoint, wsWebSocketContainer, requestUri, requestParameterMap, queryString, userPrincipal, httpSessionId, negotiatedExtensions, subProtocol, pathParameters, secure, endpointConfig;

        public DummySession() throws DeploymentException {
            super(null, null, null, null, null, null, false, null);
        }

    }

}
