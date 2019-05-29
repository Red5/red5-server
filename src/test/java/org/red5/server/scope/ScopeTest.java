package org.red5.server.scope;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
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
import org.red5.server.api.scope.IBasicScope;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.ScopeType;
import org.red5.server.persistence.RamPersistence;
import org.red5.server.so.SharedObjectScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@ContextConfiguration(locations = { "ScopeTest.xml" })
public class ScopeTest extends AbstractJUnit4SpringContextTests {

    protected static Logger log = LoggerFactory.getLogger(ScopeTest.class);

    private static ExecutorService executorService;

    private static WebScope appScope;

    private static AtomicInteger workerCounter = new AtomicInteger(1);

    // shared pass counter
    private AtomicInteger pass = new AtomicInteger();

    // shared fail counter
    private AtomicInteger fail = new AtomicInteger();

    private static Random rnd = new Random();

    static {
        System.setProperty("red5.deployment.type", "junit");
    }

    @Before
    public void setUp() throws Exception {
        ScopeTest.executorService = Executors.newCachedThreadPool();
        // reset pass/fail counters
        pass.set(0);
        fail.set(0);
    }

    @After
    public void tearDown() throws Exception {
        ScopeTest.executorService.shutdownNow();
    }

    @Test
    public void testScopeCreation() throws InterruptedException {
        log.info("testScopeCreation");
        if (appScope == null) {
            appScope = (WebScope) applicationContext.getBean("web.scope");
            log.debug("Application / web scope: {}", appScope);
            assertTrue(appScope.getDepth() == 1);
        }
        //Room 0 /default/junit/room0 (created in the spring config)
        assertNotNull(appScope.getScope("room0"));
        IScope room0 = appScope.getScope("room0");
        log.debug("Room#0: {}", room0);
        assertTrue(room0.getDepth() == 2);
        // test runnables represent worker threads creating scopes
        int workerCount = 10, loops = 100;
        List<Worker> workers = new ArrayList<>();
        for (int s = 0; s < workerCount; s++) {
            workers.add(new Worker(appScope, loops));
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
        // walk the scope tree to see what we've got
        Set<String> roomNames1 = appScope.getBasicScopeNames(ScopeType.ROOM);
        log.info("First level rooms: {}", roomNames1);
        assertTrue(roomNames1.size() >= (workerCount + 1));
        roomNames1.forEach(name -> {
            IScope room = appScope.getScope(name);
            log.info("First level room: {}", room);
            assertNotNull(room);
            // each room is expected to have a minimum of 2 child scopes
            Set<String> childNames = room.getScopeNames();
            log.info("{} child rooms: {}", name, childNames);
            // except for room0
            if (!"room0".equals(name)) {
                assertTrue(childNames.size() >= 2);
            }
        });
        // remove them all except 0
        for (String name : roomNames1) {
            // except for room0
            if (!"room0".equals(name)) {
                appScope.removeChildScope(appScope.getScope(name));
            }
        }
        assertTrue(appScope.getBasicScopeNames(ScopeType.ROOM).size() == 1);
        //appScope.removeChildren();
        //assertTrue(appScope.getBasicScopeNames(ScopeType.ROOM).size() == 0);
        log.info("testScopeCreation-end");
    }

    @Test
    public void testScopeCreationTypes() throws InterruptedException {
        log.info("testScopeCreationTypes");
        log.debug("Application / web scope: {}", appScope);
        //Room 0 /default/junit/room0 (created in the spring config)
        assertNotNull(appScope.getScope("room0"));
        IScope room0 = appScope.getScope("room0");
        log.debug("Room#0: {}", room0);
        assertTrue(room0.getDepth() == 2);
        RamPersistence store = new RamPersistence(appScope);
        // test room names
        String[] names = new String[] { "rmSO", "rmStream", "rmPriv" };
        // test runnables represent worker threads creating scopes
        int workerCount = 4;
        List<Callable<Boolean>> workers = new ArrayList<>();
        for (int s = 0; s < workerCount; s++) {
            workers.add(new Callable<Boolean>() {

                @Override
                public Boolean call() throws Exception {
                    int updates = 0, failures = 0;
                    try {
                        // all callables will try to create the same scope and add children to it
                        final String name = "room99";
                        if (appScope.createChildScope(name)) {
                            updates++;
                        } else {
                            failures++;
                        }
                        // sleep a random amount of time between 1 and 13 ms
                        Thread.sleep(Math.max(1, rnd.nextInt(7)));
                        IScope room = appScope.getScope(name);
                        log.info("Created room: {}", room);
                        for (String rn : names) {
                            log.info("Create child: {}", rn);
                            IBasicScope scope = null;
                            switch (rn) {
                                case "rmSO":
                                    scope = new SharedObjectScope(room, rn, false, store);
                                    break;
                                case "rmStream":
                                    scope = new BroadcastScope(room, rn);
                                    break;
                                case "rmPriv":
                                    scope = new Scope(room, ScopeType.ROOM, rn, false);
                                    break;
                            }
                            if (room.addChildScope(scope)) {
                                updates++;
                            } else {
                                failures++;
                            }
                        }
                    } catch (Throwable t) {
                        log.warn("Worker exception", t);
                        failures++;
                    } finally {
                        pass.addAndGet(updates);
                        fail.addAndGet(failures);
                    }
                    return Boolean.TRUE;
                }

            });
        }
        // fires off threads
        long start = System.nanoTime();
        // invokeAll() blocks until all tasks have run...
        @SuppressWarnings("unused")
        List<Future<Boolean>> futures = executorService.invokeAll(workers);
        log.info("Runtime: {}ms", TimeUnit.MILLISECONDS.convert(System.nanoTime() - start, TimeUnit.NANOSECONDS));
        log.info("Pass: {} fail: {}", pass.get(), fail.get());
        // 4 tests x workerCount - 1 (passing jobs) == fails
        assertEquals(fail.get(), (4 * (workerCount - 1)));
        assertEquals(pass.get(), 4);
        // walk the scope tree to see what we've got
        Set<String> roomNames1 = appScope.getBasicScopeNames(ScopeType.ROOM);
        log.info("First level rooms: {}", roomNames1);
        assertTrue(roomNames1.size() >= 2);
        roomNames1.forEach(name -> {
            IScope room = appScope.getScope(name);
            log.info("First level room: {}", room);
            assertNotNull(room);
            // each room is expected to have a minimum of 2 child scopes
            Set<String> childNames = room.getScopeNames();
            log.info("{} child rooms: {}", name, childNames);
            // except for room0
            if (!"room0".equals(name)) {
                assertTrue(childNames.size() >= 2);
                // list children and their types
                childNames.forEach(childName -> {
                    IBasicScope child = room.getBasicScope(childName);
                    log.info("{}", child);
                });
            }
        });
        // remove them all except 0
        for (String name : roomNames1) {
            // except for room0
            if (!"room0".equals(name)) {
                appScope.removeChildScope(appScope.getScope(name));
            }
        }
        assertTrue(appScope.getBasicScopeNames(ScopeType.ROOM).size() == 1);
        log.info("testScopeCreationTypes-end");
    }

    private class Worker implements Callable<Integer> {

        protected final int id;

        protected IScope scope;

        @SuppressWarnings("unused")
        protected int loops, updates, failures;

        public Worker(IScope appScope, int loops) {
            id = workerCounter.getAndIncrement();
            this.scope = appScope;
            this.loops = loops;
        }

        public Integer call() throws Exception {
            log.debug("Worker#{} start scope: {}", id, scope);
            try {
                final String name = String.format("room%d", id);
                if (scope.createChildScope(name)) {
                    updates++;
                } else {
                    failures++;
                }
                IScope room = scope.getScope(name);
                log.debug("Worker#{} {}", id, room);
                // child room counter
                int children = 10;
                do {
                    String roomName = String.format("child%d", rnd.nextInt(10));
                    if (room.createChildScope(roomName)) {
                        updates++;
                    } else {
                        failures++;
                    }
                    // sleep a random amount of time between 1 and 13 ms
                    Thread.sleep(Math.max(1, rnd.nextInt(13)));
                } while (--children > 0);
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
            return getClass().getName() + " [id=" + id + ", updates=" + updates + ", failures=" + failures + "]";
        }
    }
}
