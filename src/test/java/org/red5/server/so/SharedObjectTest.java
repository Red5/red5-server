/*
 * RED5 Open Source Flash Server - http://code.google.com/p/red5/
 * 
 * Copyright 2006-2014 by respective authors (see below). All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.red5.server.so;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.red5.server.api.IAttributeStore;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.so.ISharedObject;
import org.red5.server.api.so.ISharedObjectBase;
import org.red5.server.api.so.ISharedObjectListener;
import org.red5.server.scope.WebScope;
import org.red5.server.util.ScopeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import com.google.gson.Gson;

/**
 * This is for testing SharedObject issues.
 * 
 * http://help.adobe.com/en_US/FlashMediaServer/3.5_SS_ASD/WS5b3ccc516d4fbf351e63e3d11a11afc95e-7e63.html
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@ContextConfiguration(locations = { "SharedObjectTest.xml" })
public class SharedObjectTest extends AbstractJUnit4SpringContextTests {

    protected static Logger log = LoggerFactory.getLogger(SharedObjectTest.class);

    private static ExecutorService executorService;

    private static WebScope appScope;

    private static List<SOClientWorker> tasks;

    private static AtomicInteger workerCounter = new AtomicInteger(1);

    // shared pass counter
    private AtomicInteger pass = new AtomicInteger();

    // shared fail counter
    private AtomicInteger fail = new AtomicInteger();

    private static Random rnd = new Random();

    private enum AttributeKey {
        counter, json;
    }

    @SuppressWarnings("unused")
    private String host = "localhost";

    @SuppressWarnings("unused")
    private String appPath = "junit";

    @SuppressWarnings("unused")
    private String roomPath = "/junit/room1";

    static {
        System.setProperty("red5.deployment.type", "junit");
    }

    @Before
    public void setUp() throws Exception {
        SharedObjectTest.executorService = Executors.newCachedThreadPool();
        // reset pass/fail counters
        pass.set(0);
        fail.set(0);
    }

    @After
    public void tearDown() throws Exception {
        SharedObjectTest.executorService.shutdownNow();
    }

    @Test
    public void testSharedObject() {
        log.info("testSharedObject");
        if (appScope == null) {
            appScope = (WebScope) applicationContext.getBean("web.scope");
            log.debug("Application / web scope: {}", appScope);
            assertTrue(appScope.getDepth() == 1);
        }
        SOApplication app = (SOApplication) applicationContext.getBean("web.handler");
        String soName = "foo";
        //Room 1
        // /default/junit/room1
        assertNotNull(appScope.getScope("room1"));
        IScope room1 = appScope.getScope("room1");
        log.debug("Room 1: {}", room1);
        assertTrue(room1.getDepth() == 2);
        // get the SO
        ISharedObject sharedObject = app.getSharedObject(room1, soName, true);
        log.debug("SO: {}", sharedObject);
        assertNotNull(sharedObject);
        log.info("testSharedObject-end");
    }

    @Test
    public void testGetSONames() throws Exception {
        log.info("testGetSONames");
        if (appScope == null) {
            appScope = (WebScope) applicationContext.getBean("web.scope");
            log.debug("Application / web scope: {}", appScope);
            assertTrue(appScope.getDepth() == 1);
        }
        IScope room1 = ScopeUtils.resolveScope(appScope, "/junit/room1");
        log.debug("Room 1 scope: {}", room1);
        Set<String> names = room1.getScopeNames();
        log.debug("Names: {}", names);
        assertTrue(names.size() > 0);
        log.info("testGetSONames-end");
    }

    @Test
    public void zzzRemoveSO() throws Exception {
        log.info("testRemoveSO");
        if (appScope == null) {
            appScope = (WebScope) applicationContext.getBean("web.scope");
            log.debug("Application / web scope: {}", appScope);
            assertTrue(appScope.getDepth() == 1);
        }
        String soName = "foo";
        IScope room1 = ScopeUtils.resolveScope(appScope, "/junit/room1");
        room1.removeChildren();
        log.debug("Child exists: {}", room1.hasChildScope(soName));
        log.info("testRemoveSO-end");
    }

    /**
     * Test for Issue 209 http://code.google.com/p/red5/issues/detail?id=209
     */
    @Test
    public void testPersistentCreation() throws Exception {
        log.info("testPersistentCreation");
        if (appScope == null) {
            appScope = (WebScope) applicationContext.getBean("web.scope");
            log.debug("Application / web scope: {}", appScope);
            assertTrue(appScope.getDepth() == 1);
        }
        SOApplication app = (SOApplication) applicationContext.getBean("web.handler");
        String soName = "foo";
        // get our room
        IScope room1 = ScopeUtils.resolveScope(appScope, "/junit/room1");
        // create the SO
        app.createSharedObject(room1, soName, true);
        // get the SO
        ISharedObject sharedObject = app.getSharedObject(room1, soName, true);
        assertTrue(sharedObject != null);
        log.info("testPersistentCreation-end");
    }

    @Test
    public void testDeepDirty() throws Throwable {
        log.info("testDeepDirty");
        if (appScope == null) {
            appScope = (WebScope) applicationContext.getBean("web.scope");
            log.debug("Application / web scope: {}", appScope);
        }
        SOApplication app = (SOApplication) applicationContext.getBean("web.handler");
        try {
            // get our room
            IScope room = ScopeUtils.resolveScope(appScope, "/junit/room99");
            if (room != null) {
                // create the SO
                app.createSharedObject(room, "dirtySO", true);
                // test runnables represent clients
                int threads = 2;
                tasks = new ArrayList<SOClientWorker>(threads);
                for (int t = 0; t < threads; t++) {
                    tasks.add(new SOClientWorker(t, app, room));
                }
                // fires off threads
                long start = System.nanoTime();
                // invokeAll() blocks until all tasks have run...
                @SuppressWarnings("unused")
                List<Future<Object>> futures = executorService.invokeAll(tasks);
                log.info("Runtime: {}ms", TimeUnit.MILLISECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS));
                for (SOClientWorker r : tasks) {
                    log.debug("Worker: {} shared object: {}", r.getId(), r.getSO().getAttributes());
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        log.info("testDeepDirty-end");
    }

    @Test
    public void testSharedObjectWithListener() {
        log.info("testSharedObjectWithListener");
        if (appScope == null) {
            appScope = (WebScope) applicationContext.getBean("web.scope");
            log.debug("Application / web scope: {}", appScope);
            assertTrue(appScope.getDepth() == 1);
        }
        SOApplication app = (SOApplication) applicationContext.getBean("web.handler");
        app.initTSOwithListener();
        // go to sleep
        try {
            Thread.sleep(500);
        } catch (Exception e) {
        }
        // set something on the so
        ISharedObject so = app.getSharedObject(appScope, "statusSO");
        so.setAttribute("testing", true);
        // go to sleep
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
        }
        log.debug("Attribute names: {}", so.getAttributeNames());
        // [status, testing]
        assertTrue(so.getAttributeNames().size() == 2);
    }

    @Test
    public void testSharedObjectWithGetAndClose() {
        log.info("testSharedObjectWithGetAndClose");
        if (appScope == null) {
            appScope = (WebScope) applicationContext.getBean("web.scope");
            log.debug("Application / web scope: {}", appScope);
            assertTrue(appScope.getDepth() == 1);
        }
        SOApplication app = (SOApplication) applicationContext.getBean("web.handler");
        app.getAndCloseSO();
        // go to sleep
        try {
            Thread.sleep(500);
        } catch (Exception e) {
        }
        // set something on the so
        assertFalse(app.hasSharedObject(appScope, "issue323"));
    }

    @Test
    public void testMissingHandler() throws Throwable {
        log.info("testMissingHandler");
        String soName = "messager";
        SOApplication app = (SOApplication) applicationContext.getBean("web.handler");
        assertTrue(appScope.hasHandler());
        IScope top = ScopeUtils.resolveScope(appScope, "/junit");
        assertTrue(top.hasHandler());
        IScope room = ScopeUtils.resolveScope(appScope, "/junit/room13");
        if (room == null) {
            assertTrue(top.createChildScope("room13"));
            room = ScopeUtils.resolveScope(appScope, "/junit/room13");
            assertNotNull(room);
        }
        assertTrue(room.hasHandler());
        // get rooms
        IScope room1 = ScopeUtils.resolveScope(appScope, "/junit/room13/subroomA");
        if (room1 == null) {
            assertTrue(room.createChildScope("subroomA"));
            room1 = ScopeUtils.resolveScope(appScope, "/junit/room13/subroomA");
            assertNotNull(room1);
        }
        IScope room2 = ScopeUtils.resolveScope(appScope, "/junit/room13/subroomB");
        if (room2 == null) {
            assertTrue(room.createChildScope("subroomB"));
            room2 = ScopeUtils.resolveScope(appScope, "/junit/room13/subroomB");
            assertNotNull(room2);
        }
        Thread.sleep(100L);
        // create the SOs
        if (!app.hasSharedObject(room1, soName)) {
            app.createSharedObject(room1, soName, false);
        }
        assertNotNull(app.getSharedObject(room1, soName, false));
        if (!app.hasSharedObject(room2, soName)) {
            app.createSharedObject(room2, soName, false);
        }
        assertNotNull(app.getSharedObject(room2, soName, false));
        // test runnables represent clients
        tasks = new ArrayList<SOClientWorker>();
        tasks.add(new SOClientWorkerA(0, app, room));
        tasks.add(new SOClientWorkerB(1, app, room2));
        // fires off threads
        long start = System.nanoTime();
        // invokeAll() blocks until all tasks have run...
        @SuppressWarnings("unused")
        List<Future<Object>> futures = executorService.invokeAll(tasks);
        log.info("Runtime: {}ms", TimeUnit.MILLISECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS));
        SOClientWorkerA soa = (SOClientWorkerA) tasks.get(0);
        log.debug("Worker: {} shared object: {}", soa.getId(), soa.getSO().getAttributes());
        SOClientWorkerB sob = (SOClientWorkerB) tasks.get(1);
        log.debug("Worker: {} shared object: {}", sob.getId(), sob.getSO().getAttributes());
        Thread.sleep(300L);
        log.info("testMissingHandler-end");
    }

    @Test
    public void testAttributeBlasting() throws Throwable {
        log.info("testAttributeBlasting");
        SOApplication app = (SOApplication) applicationContext.getBean("web.handler");
        IScope scope = app.getScope();
        // get the SO
        ISharedObject so = app.getSharedObject(scope, "ablasted", false);
        assertNotNull(so);
        // add the listener
        so.addSharedObjectListener(singletonSOListener);
        so.acquire();
        // test runnables represent clients
        int workerCount = 100, loops = 1000;
        List<SOWorker> workers = new ArrayList<>();
        for (int s = 0; s < workerCount; s++) {
            workers.add(new SOWorker(app, scope, "ablasted", loops));
        }
        // fires off threads
        long start = System.nanoTime();
        // invokeAll() blocks until all tasks have run...
        @SuppressWarnings("unused")
        List<Future<Integer>> futures = executorService.invokeAll(workers);
        log.info("Runtime: {}ms", TimeUnit.MILLISECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS));
        workers.forEach(worker -> {
            log.debug("{}", worker);
        });
        log.info("Pass: {} fail: {}", pass.get(), fail.get());
        // get latest version
        int version = so.getVersion();
        // get latest attribute
        int attr = ((CounterObject) so.getAttribute(AttributeKey.counter)).get();
        log.info("Shared object version: {} attribute: {}", version, attr);
        // calculate expected attribute
        int expectedAttr = 1 + (loops * workerCount);
        assertTrue(Math.abs(expectedAttr - attr) <= 10); // allow variance of 10
        // calculate expected version
        int expectedVersion = 2 + (loops * workerCount); //(start version (1) + first entry (1) + (loops x workerCount))
        assertTrue(expectedVersion <= version);
        assertTrue(pass.get() <= version);
        // dispose of it
        so.release();
        so.close();
        so.removeSharedObjectListener(singletonSOListener);
        log.info("testAttributeBlasting-end");
    }

    @Test
    public void testAttributeBlastingWithPrimitive() throws Throwable {
        log.info("testAttributeBlastingWithPrimitive");
        SOApplication app = (SOApplication) applicationContext.getBean("web.handler");
        IScope scope = app.getScope();
        // get the SO
        ISharedObject so = app.getSharedObject(scope, "pblasted", false);
        assertNotNull(so);
        // add the listener
        so.addSharedObjectListener(singletonSOListener);
        so.acquire();
        // test runnables represent clients
        int workerCount = 100, loops = 1000;
        List<SOWorkerPrimitive> workers = new ArrayList<>();
        for (int s = 0; s < workerCount; s++) {
            workers.add(new SOWorkerPrimitive(app, scope, "pblasted", loops));
        }
        // fires off threads
        long start = System.nanoTime();
        // invokeAll() blocks until all tasks have run...
        @SuppressWarnings("unused")
        List<Future<Integer>> futures = executorService.invokeAll(workers);
        log.info("Runtime: {}ms", TimeUnit.MILLISECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS));
        workers.forEach(worker -> {
            log.debug("{}", worker);
        });
        log.info("Pass: {} fail: {}", pass.get(), fail.get());
        // get latest version
        int version = so.getVersion();
        // get latest attribute
        int attr = (int) so.getAttribute(AttributeKey.counter.name());
        log.info("Shared object version: {} attribute: {}", version, attr);
        // calculate expected attribute
        int expectedAttr = 1 + (loops * workerCount); //first entry (1) + (loops x workerCount))
        // we can only expect about 1/4 to 1/3 due to collisions
        assertTrue((expectedAttr / 4) <= attr);
        //assertEquals(expectedAttr, attr);
        // calculate expected version
        //int expectedVersion = 2 + (loops * workerCount); //(start version (1) + first entry (1) + (loops x workerCount))
        // the next assertion will fail due to thread collisions trying to update the same key with the same value
        //assertEquals(expectedVersion, version);
        assertTrue(pass.get() >= version);
        // dispose of it
        so.release();
        so.close();
        so.removeSharedObjectListener(singletonSOListener);
        log.info("testAttributeBlastingWithPrimitive-end");
    }

    @Test
    public void testAttributeBlastingJSON() throws Throwable {
        log.info("testAttributeBlastingJSON");
        SOApplication app = (SOApplication) applicationContext.getBean("web.handler");
        IScope scope = app.getScope();
        // get the SO
        ISharedObject so = app.getSharedObject(scope, "json", false);
        assertNotNull(so);
        // add the listener
        so.addSharedObjectListener(singletonSOListener);
        so.acquire();
        // test runnables represent clients
        int workerCount = 10, loops = 100;
        List<SOWorkerJSON> workers = new ArrayList<>();
        for (int s = 0; s < workerCount; s++) {
            workers.add(new SOWorkerJSON(app, scope, "json", loops));
        }
        // fires off threads
        long start = System.nanoTime();
        // invokeAll() blocks until all tasks have run...
        @SuppressWarnings("unused")
        List<Future<Integer>> futures = executorService.invokeAll(workers);
        log.info("Runtime: {}ms", TimeUnit.MILLISECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS));
        workers.forEach(worker -> {
            log.debug("{}", worker);
        });
        log.info("Pass: {} fail: {}", pass.get(), fail.get());
        // pass+fail should equal loops * workers + workers (since starting the worker causes an extra pass or fail)
        assertTrue(((loops * workerCount) + workerCount) <= (pass.get() + fail.get()));
        // get latest version
        int version = so.getVersion();
        Gson gson = new Gson();
        // get latest attribute
        StreamInfo attr = gson.fromJson((String) so.getAttribute(AttributeKey.json), StreamInfo.class);
        log.info("Shared object version: {} attribute: {}", version, attr);
        // duration should be less than or equal to pass since we increment it in each worker
        assertTrue(pass.get() >= attr.duration);
        // calculate expected attribute
        // expect timestamp to be less than now but not by a lot of ms
        long expectedAttr = System.currentTimeMillis();
        assertTrue(expectedAttr - attr.timestamp < 500L);
        // check version against updates
        assertTrue(Math.abs(pass.get() - version) <= 10); // allow variance of 10
        // calculate expected version
        int expectedVersion = 2 + (loops * workerCount); // 1002
        assertTrue(expectedVersion >= version);
        // dispose of it
        so.release();
        so.close();
        so.removeSharedObjectListener(singletonSOListener);
        log.info("testAttributeBlastingJSON-end");
    }

    // Used to ensure all the test-runnables are in "runTest" block.
    private static boolean allThreadsRunning() {
        for (SOClientWorker worker : tasks) {
            if (!((SOClientWorker) worker).isRunning()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Simple object for incrementing a counter shared in the shared object.
     */
    class CounterObject {

        int value;

        CounterObject(int value) {
            this.value = value;
        }

        void inc() {
            value++;
        }

        int get() {
            return value;
        }

        @Override
        public int hashCode() {
            return value;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            CounterObject other = (CounterObject) obj;
            if (value != other.value)
                return false;
            return true;
        }

        public String toString() {
            return value + "";
        }

    }

    // uses a primitive counter object
    private class SOWorkerPrimitive implements Callable<Integer> {

        protected final int id;

        protected SOApplication app;

        protected IScope scope;

        protected String soName;

        protected ISharedObject so;

        protected int loops, updates, failures;

        public SOWorkerPrimitive(SOApplication app, IScope scope, String soName, int loops) {
            id = workerCounter.getAndIncrement();
            this.app = app;
            this.scope = scope;
            this.soName = soName;
            this.loops = loops;
        }

        public Integer call() throws Exception {
            log.debug("Worker#{} start", id);
            try {
                // get the SO inside execution
                so = app.getSharedObject(scope, soName, false);
                log.debug("Worker#{} {}", id, so);
                // check for null SO which shouldn't happen
                if (so != null) {
                    if (!so.hasAttribute(AttributeKey.counter)) {
                        if (so.setAttribute(AttributeKey.counter, 1)) {
                            log.debug("Worker#{} attribute added", id);
                            updates++;
                        } else {
                            failures++;
                            int waitLoops = 100;
                            do {
                                log.debug("Worker#{} going to sleep waiting for attribute to exist", id);
                                Thread.sleep(10L);
                            } while (!so.hasAttribute(AttributeKey.counter) && --waitLoops > 0);
                            log.debug("Worker#{} wait loop exit", id);
                        }
                    }
                    do {
                        Optional<Object> opt = Optional.ofNullable(so.getAttribute(AttributeKey.counter.name()));
                        if (opt.isPresent()) {
                            // get attribute
                            int c = (int) opt.get();
                            log.debug("Worker#{} attr: {}", id, c);
                            // increment and set attribute instead of setDirty since this is a primitive
                            if (so.setAttribute(AttributeKey.counter, ++c)) {
                                updates++;
                            } else {
                                // the reason for most failures is a collision of n threads trying to set the same value
                                // only one of the threads will succeed
                                failures++;
                            }
                        } else {
                            // failure when the attribute isn't present
                            failures++;
                        }
                        // sleep a random amount of time between 1 and 13 ms
                        Thread.sleep(Math.max(1, rnd.nextInt(13)));
                    } while (--loops > 0);
                } else {
                    log.debug("Shared object was null! Worker: {}", id);
                }
            } catch (Throwable t) {
                log.warn("Worker#{} exception", id, t);
                failures++;
            } finally {
                pass.addAndGet(updates);
                fail.addAndGet(failures);
                log.debug("Worker#{} end", id);
            }
            return updates;
        }

        @Override
        public String toString() {
            return "SOWorkerPrimitive [id=" + id + ", updates=" + updates + ", failures=" + failures + "]";
        }
    }

    // uses a Counter object
    private class SOWorker extends SOWorkerPrimitive {

        public SOWorker(SOApplication app, IScope scope, String soName, int loops) {
            super(app, scope, soName, loops);
        }

        public Integer call() throws Exception {
            log.debug("Worker#{} start", id);
            try {
                // get the SO inside execution
                so = app.getSharedObject(scope, soName, false);
                log.debug("Worker#{} {}", id, so);
                // check for null SO which shouldn't happen
                if (so != null) {
                    if (!so.hasAttribute(AttributeKey.counter)) {
                        if (so.setAttribute(AttributeKey.counter, new CounterObject(1))) {
                            log.debug("Worker#{} attribute added", id);
                            updates++;
                        } else {
                            failures++;
                            int waitLoops = 100;
                            do {
                                log.debug("Worker#{} going to sleep waiting for attribute to exist", id);
                                Thread.sleep(10L);
                            } while (!so.hasAttribute(AttributeKey.counter) && --waitLoops > 0);
                            log.debug("Worker#{} wait loop exit", id);
                        }
                    }
                    do {
                        Optional<Object> opt = Optional.ofNullable(so.getAttribute(AttributeKey.counter));
                        if (opt.isPresent()) {
                            // get attribute
                            CounterObject c = (CounterObject) opt.get();
                            log.debug("Worker#{} attr: {}", id, c.get());
                            // get
                            int p = c.get();
                            // increment
                            c.inc();
                            // set dirty flag on the key / name itself!!
                            so.setDirty(AttributeKey.counter.name());
                            // get for check
                            int u = c.get();
                            if (p < u) {
                                updates++;
                            } else {
                                failures++;
                            }
                        } else {
                            failures++;
                        }
                        // sleep a random amount of time between 1 and 13 ms
                        Thread.sleep(Math.max(1, rnd.nextInt(13)));
                    } while (--loops > 0);
                } else {
                    log.debug("Shared object was null! Worker: {}", id);
                }
            } catch (Throwable t) {
                log.warn("Worker#{} exception", id, t);
                failures++;
            } finally {
                pass.addAndGet(updates);
                fail.addAndGet(failures);
                log.debug("Worker#{} end", id);
            }
            return updates;
        }

        @Override
        public String toString() {
            return "SOWorker [id=" + id + ", updates=" + updates + ", failures=" + failures + "]";
        }
    }

    // class for serialize/deserialze with json
    private class StreamInfo {
        // {duration=1, preencoded=true, fileName=test5.flv, streamName=test5, record=true, startTime=1558112375249, viewerCount=0, fileSize=0, timestamp=1558112375252}
        int duration;

        boolean preencoded;

        String fileName;

        String streamName;

        // do recording
        boolean record;

        long startTime, timestamp;

        long fileSize;

        int viewerCount;

        @Override
        public String toString() {
            return "StreamInfo [duration=" + duration + ", preencoded=" + preencoded + ", fileName=" + fileName + ", streamName=" + streamName + ", record=" + record + ", startTime=" + startTime + ", timestamp=" + timestamp + ", fileSize=" + fileSize + ", viewerCount=" + viewerCount + "]";
        }
    }

    // uses a Json object
    private class SOWorkerJSON extends SOWorkerPrimitive {

        private Gson gson = new Gson();

        public SOWorkerJSON(SOApplication app, IScope scope, String soName, int loops) {
            super(app, scope, soName, loops);
        }

        public Integer call() throws Exception {
            log.debug("Worker#{} start", id);
            try {
                // get the SO inside execution
                so = app.getSharedObject(scope, soName, false);
                log.debug("Worker#{} {}", id, so);
                // check for null SO which shouldn't happen
                if (so != null) {
                    if (!so.hasAttribute(AttributeKey.json)) {
                        StreamInfo obj = new StreamInfo();
                        obj.streamName = "junit";
                        obj.startTime = System.currentTimeMillis();
                        obj.timestamp = System.currentTimeMillis();
                        // increment the duration
                        obj.duration = obj.duration + 1;
                        String json = gson.toJson(obj);
                        if (so.setAttribute(AttributeKey.json, json)) {
                            log.debug("Worker#{} attribute added", id);
                            updates++;
                        } else {
                            failures++;
                            int waitLoops = 100;
                            do {
                                log.debug("Worker#{} going to sleep waiting for attribute to exist", id);
                                Thread.sleep(10L);
                            } while (!so.hasAttribute(AttributeKey.json) && --waitLoops > 0);
                            log.debug("Worker#{} wait loop exit", id);
                        }
                    }
                    do {
                        // get attribute
                        StreamInfo info = gson.fromJson((String) so.getAttribute(AttributeKey.json), StreamInfo.class);
                        log.debug("Worker#{} attr: {}", id, info);
                        // update timestamp
                        info.timestamp = System.currentTimeMillis();
                        // increment the duration
                        info.duration = info.duration + 1;
                        // set attribute instead of setDirty since this is a primitive (String)
                        if (so.setAttribute(AttributeKey.json, gson.toJson(info))) {
                            updates++;
                        } else {
                            // the reason for most failures is a collision of n threads trying to set the same value
                            // only one of the threads will succeed
                            failures++;
                        }
                        // sleep a random amount of time between 1 and 13 ms
                        Thread.sleep(Math.max(1, rnd.nextInt(13)));
                    } while (--loops > 0);
                } else {
                    log.debug("Shared object was null! Worker: {}", id);
                }
            } catch (Throwable t) {
                log.warn("Worker#{} exception", id, t);
                failures++;
            } finally {
                pass.addAndGet(updates);
                fail.addAndGet(failures);
                log.debug("Worker#{} end", id);
            }
            return updates;
        }

        @Override
        public String toString() {
            return "SOWorkerJSON [id=" + id + ", updates=" + updates + ", failures=" + failures + "]";
        }
    }

    private class SOClientWorker implements Callable<Object> {

        protected int id;

        protected ISharedObject so;

        private volatile boolean running = false;

        public SOClientWorker(int id, SOApplication app, IScope room) {
            this.id = id;
            if (app != null) {
                this.so = app.getSharedObject(room, "dirtySO", true);
                ISharedObjectListener listener = new SOListener(id);
                so.addSharedObjectListener(listener);
            }
        }

        @SuppressWarnings("unchecked")
        public Object call() throws Exception {
            log.debug("runTest#{}", id);
            running = true;
            do {
                Thread.sleep(100);
            } while (!allThreadsRunning());
            // create complex type object
            Complex complex = (Complex) so.getAttribute("complex");
            if (complex == null) {
                complex = new Complex();
                complex.getMap().put("myId", id);
                so.setAttribute("complex", complex);
            }
            Thread.sleep(500);
            log.debug("runTest-end#{}", id);
            running = false;
            return id;
        }

        public int getId() {
            return id;
        }

        public ISharedObject getSO() {
            return so;
        }

        public boolean isRunning() {
            return running;
        }
    }

    /** Used for handler test */
    private class SOClientWorkerA extends SOClientWorker {

        private IScope room;

        public SOClientWorkerA(int id, SOApplication app, IScope room) {
            super(id, null, null);
            this.room = room;
            this.so = app.getSharedObject(room, "messager", false);
            ISharedObjectListener listener = new SOListener(id);
            so.addSharedObjectListener(listener);
        }

        public Object call() throws Exception {
            log.debug("runTest#{}", id);
            Thread.sleep(50);
            so.setAttribute("client-id", id);
            // sleep 100 ms for client A
            Thread.sleep(50);
            assertTrue(so.getIntAttribute("client-id") == id);
            // remove the room we used for this client; hopefully this will cause the
            // handler "missing" error to surface
            room.removeChildScope(so);
            room.getParent().removeChildScope(room);
            log.debug("runTest-end#{}", id);
            return id;
        }

    }

    /** Used for handler test */
    private class SOClientWorkerB extends SOClientWorker {

        public SOClientWorkerB(int id, SOApplication app, IScope room) {
            super(id, null, null);
            this.so = app.getSharedObject(room, "messager", false);
            ISharedObjectListener listener = new SOListener(id);
            so.addSharedObjectListener(listener);
        }

        public Object call() throws Exception {
            log.debug("runTest#{}", id);
            Thread.sleep(50);
            so.setAttribute("client-id", id);
            // sleep 200 ms for client B
            Thread.sleep(200);
            assertTrue(so.getIntAttribute("client-id") == id);
            so.sendMessage("sendMessage", null);
            Thread.sleep(50);
            log.debug("runTest-end#{}", id);
            return id;
        }

    }

    private final ISharedObjectListener singletonSOListener = new ISharedObjectListener() {

        private long id = System.currentTimeMillis();

        @Override
        public void onSharedObjectConnect(ISharedObjectBase so) {
            log.trace("Connected {} {}", id, so.toString());
        }

        @Override
        public void onSharedObjectDisconnect(ISharedObjectBase so) {
            log.trace("Disconnected {} {}", id, so.toString());
        }

        @Override
        public void onSharedObjectUpdate(ISharedObjectBase so, String key, Object value) {
            log.trace("onSharedObjectUpdate {}:{} {}", key, value, so.toString());
        }

        @Override
        public void onSharedObjectUpdate(ISharedObjectBase so, IAttributeStore values) {
            log.trace("onSharedObjectUpdate {}", values);
        }

        @Override
        public void onSharedObjectUpdate(ISharedObjectBase so, Map<String, Object> values) {
            log.trace("onSharedObjectUpdate {}", values);
        }

        @Override
        public void onSharedObjectDelete(ISharedObjectBase so, String key) {
            log.trace("onSharedObjectDelete {} {}", key, so.toString());
        }

        @Override
        public void onSharedObjectClear(ISharedObjectBase so) {
            log.trace("onSharedObjectClear {}", so.toString());
        }

        @Override
        public void onSharedObjectSend(ISharedObjectBase so, String method, List<?> params) {
        }

    };
}
