package org.red5.client;

import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.red5.server.api.so.IClientSharedObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteSOTest {

    private static Logger log = LoggerFactory.getLogger(RemoteSOTest.class);

    private static boolean skipTest;

    private int threads = 500;

    static {
        System.setProperty("red5.deployment.type", "junit");
    }

    @Before
    public void setUp() throws Exception {
        // skip the tests if red5 isnt listening
        Socket s = null;
        try {
            s = new Socket();
            SocketAddress sa = new InetSocketAddress("localhost", 1935);
            s.connect(sa, 1000);
        } catch (Exception e) {
            //System.err.println(e.getMessage());
            skipTest = true;
        } finally {
            if (s.isConnected()) {
                s.close();
            }
        }
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testRemoteSO() throws Throwable {
        // check for the presence of a red5 server or skip the test
        if (!skipTest) {
            // test runnables represent clients
            List<SOClientWorker> tasks = new ArrayList<SOClientWorker>(threads);
            for (int t = 0; t < threads; t++) {
                tasks.add(new SOClientWorker(t));
            }
            ExecutorService executorService = Executors.newFixedThreadPool(threads);
            // fires off threads
            long start = System.nanoTime();
            // invokeAll() blocks until all tasks have run...
            List<Future<Object>> futures = executorService.invokeAll(tasks);
            assertTrue(futures.size() == threads);
            System.out.println("Runtime: " + (System.nanoTime() - start) + "ns");
            for (SOClientWorker r : tasks) {
                SOClientWorker cl = r;
                log.debug("Worker: {}", cl.getId());
            }
        } else {
            System.out.println("No red5 server detected for testing against");
        }
    }

    private class SOClientWorker implements Callable<Object> {

        int id;

        volatile boolean running;

        SharedObjectClient client;

        IClientSharedObject so;

        public SOClientWorker(int id) {
            this.id = id;
        }

        @Override
        public Object call() throws Exception {
            log.debug("runTest#{}", id);
            running = true;
            client = new SharedObjectClient("localhost", 1935, "myapp", "myroom");
            while (!client.isBandwidthCheckDone()) {
                Thread.sleep(100L);
            }
            so = client.getSharedObject();
            if (so != null) {
                log.debug("Current so 'text' attribute: {}", so.getAttribute("text"));
                so.beginUpdate();
                so.setAttribute("text", RandomStringUtils.randomAlphabetic(16));
                so.endUpdate();
            } else {
                log.debug("SO was null for client: {}", id);
            }
            Thread.sleep(100L);
            client.disconnect();
            running = false;
            return null;
        }

        public int getId() {
            return id;
        }

        @SuppressWarnings("unused")
        public boolean isRunning() {
            return running;
        }
    }

}
