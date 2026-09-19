package org.red5.server.scope;

import static org.junit.Assert.*;

import java.util.Collection;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.red5.server.Server;
import org.red5.server.api.IConnection;
import org.red5.server.api.IContext;
import org.red5.server.api.IServer;
import org.red5.server.api.event.IEventListener;
import org.red5.server.api.scheduling.IScheduledJob;
import org.red5.server.api.scheduling.ISchedulingService;
import org.red5.server.api.scope.IBasicScope;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.IScopeHandler;
import org.red5.server.api.scope.ScopeType;

public class ScopeCleanupTest {
    private static class App extends Scope {
        private final Server server = new Server() {
            public void notifyBasicScopeAdded(IBasicScope scope) {
            }

            public void notifyBasicScopeRemoved(IBasicScope scope) {
            }

            public void notifyScopeCreated(IScope scope) {
            }
        };

        App() {
            super(null, ScopeType.APPLICATION, "app", false);
        }

        public boolean hasParent() {
            return false;
        }

        public int getDepth() {
            return 1;
        }

        public String getPath() {
            return "";
        }

        public IServer getServer() {
            return server;
        }
    }

    private Scope room(App app, String name) {
        Scope room = new Scope(app, ScopeType.ROOM, name, false);
        assertTrue(app.addChildScope(room));
        return room;
    }

    @Test
    public void reapsNeverConnectedAndStoppedRooms() {
        App app = new App();
        for (int i = 0; i < 100; i++) {
            Scope room = room(app, "room" + i);
            room.stop();
            assertTrue(room.removeIfIdle(System.currentTimeMillis() + 31000, 30000));
            assertNull(app.getScope("room" + i));
            assertFalse(room.addEventListener(event -> {
            }));
            assertFalse(room.addChildScope(new Scope(room, ScopeType.ROOM, "late", false)));
            assertFalse(room.connect(null));
        }
        assertTrue(app.getBasicScopes().isEmpty());
    }

    @Test
    public void protectsListenersChildrenRetentionAndYoungRooms() {
        App app = new App();
        long now = System.currentTimeMillis();
        Scope young = room(app, "young");
        assertFalse(young.removeIfIdle(now, 30000));
        Scope listening = room(app, "listening");
        listening.addEventListener(event -> {
        });
        assertFalse(listening.removeIfIdle(now + 600000, 30000));
        Scope parent = room(app, "parent");
        assertTrue(parent.addChildScope(new Scope(parent, ScopeType.ROOM, "child", false)));
        assertFalse(parent.removeIfIdle(now + 600000, 30000));
        Scope persistent = room(app, "persistent");
        persistent.keepOnDisconnect = true;
        assertFalse(persistent.removeIfIdle(now + 600000, 30000));
        Scope delayed = room(app, "delayed");
        delayed.setKeepDelay(120);
        assertFalse(delayed.removeIfIdle(now + 60000, 30000));
        assertTrue(delayed.removeIfIdle(now + 121000, 30000));
    }

    @Test
    public void protectsConnectionBeingAdmitted() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        App app = new App() {
            public boolean connect(IConnection conn, Object[] params) {
                entered.countDown();
                try {
                    return release.await(5, TimeUnit.SECONDS) && false;
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
            }
        };
        Scope room = room(app, "connecting");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread connect = new Thread(() -> {
            try {
                assertFalse(room.connect(null));
            } catch (Throwable e) {
                failure.set(e);
            }
        });
        connect.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertFalse(room.removeIfIdle(System.currentTimeMillis() + 60000, 30000));
        } finally {
            release.countDown();
            connect.join(5000);
        }
        assertNull(failure.get());
        assertFalse(connect.isAlive());
        assertTrue(room.removeIfIdle(System.currentTimeMillis() + 60000, 30000));
    }

    @Test
    public void lastDisconnectReapsUnlessAnotherListenerArrives() throws Exception {
        AtomicReference<IScheduledJob> job = new AtomicReference<>();
        ISchedulingService scheduler = (ISchedulingService) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { ISchedulingService.class }, (proxy, method, args) -> {
            if (method.getName().equals("addScheduledOnceJob")) {
                assertEquals(100, ((Number) args[0]).intValue());
                job.set((IScheduledJob) args[1]);
                return "cleanup";
            }
            throw new AssertionError(method.getName());
        });
        IContext context = (IContext) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { IContext.class }, (proxy, method, args) -> scheduler);
        App app = new App() {
            public IContext getContext() {
                return context;
            }
        };
        Scope room = room(app, "room");
        IEventListener first = event -> {
        };
        IEventListener second = event -> {
        };
        assertTrue(room.addEventListener(first));
        assertTrue(room.removeEventListener(first));
        assertNotNull(job.get());
        assertTrue(room.addEventListener(second));
        job.get().execute(scheduler);
        assertSame(room, app.getScope("room"));
        assertTrue(room.removeEventListener(second));
        job.get().execute(scheduler);
        assertNull(app.getScope("room"));
    }

    /** Records scheduled jobs so tests can run them on demand. */
    private static class Jobs {
        final List<IScheduledJob> scheduled = new CopyOnWriteArrayList<>();

        final List<Long> delays = new CopyOnWriteArrayList<>();

        final ISchedulingService scheduler = (ISchedulingService) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { ISchedulingService.class }, (proxy, method, args) -> {
            switch (method.getName()) {
                case "addScheduledOnceJob":
                    delays.add(((Number) args[0]).longValue());
                    scheduled.add((IScheduledJob) args[1]);
                    return "job" + scheduled.size();
                case "removeScheduledJob":
                    return null;
                default:
                    throw new AssertionError(method.getName());
            }
        });

        final IContext context = (IContext) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { IContext.class }, (proxy, method, args) -> scheduler);

        void runLast() throws Exception {
            scheduled.get(scheduled.size() - 1).execute(scheduler);
        }
    }

    @Test
    public void rejectedConnectToNewRoomIsRemovedAfterGrace() throws Exception {
        Jobs jobs = new Jobs();
        App app = new App() {
            public IContext getContext() {
                return jobs.context;
            }

            public boolean connect(IConnection conn, Object[] params) {
                return false;
            }
        };
        Scope room = room(app, "rejected");
        room.scheduleIdleCheck(Scope.NEW_ROOM_IDLE_GRACE_MILLIS);
        assertEquals(Long.valueOf(Scope.NEW_ROOM_IDLE_GRACE_MILLIS), jobs.delays.get(0));
        assertFalse(room.connect(null));
        // still inside the grace period, so the check is rescheduled rather than dropped
        jobs.runLast();
        assertSame(room, app.getScope("rejected"));
        assertEquals(2, jobs.scheduled.size());
        room.lastActivityTime -= Scope.NEW_ROOM_IDLE_GRACE_MILLIS;
        jobs.runLast();
        assertNull(app.getScope("rejected"));
    }

    @Test
    public void sharedObjectChildDoesNotPinRoom() throws Exception {
        Jobs jobs = new Jobs();
        App app = new App() {
            public IContext getContext() {
                return jobs.context;
            }
        };
        Scope room = room(app, "room");
        assertTrue(room.addChildScope(new BasicScope(room, ScopeType.SHARED_OBJECT, "so", false) {
        }));
        IEventListener listener = event -> {
        };
        assertTrue(room.addEventListener(listener));
        assertTrue(room.removeEventListener(listener));
        jobs.runLast();
        assertNull(app.getScope("room"));
    }

    @Test
    public void nestedRoomsAreRemovedChildFirst() throws Exception {
        Jobs jobs = new Jobs();
        App app = new App() {
            public IContext getContext() {
                return jobs.context;
            }
        };
        Scope parent = room(app, "parent");
        Scope child = new Scope(parent, ScopeType.ROOM, "child", false);
        assertTrue(parent.addChildScope(child));
        IEventListener conn = event -> {
        };
        assertTrue(parent.addEventListener(conn));
        assertTrue(child.addEventListener(conn));
        assertTrue(child.removeEventListener(conn));
        IScheduledJob childJob = jobs.scheduled.get(jobs.scheduled.size() - 1);
        assertTrue(parent.removeEventListener(conn));
        // parent check runs first and is deferred while the child room is attached
        jobs.runLast();
        assertSame(parent, app.getScope("parent"));
        int scheduled = jobs.scheduled.size();
        childJob.execute(jobs.scheduler);
        assertNull(parent.getScope("child"));
        assertEquals(scheduled + 1, jobs.scheduled.size());
        jobs.runLast();
        assertNull(app.getScope("parent"));
    }

    @Test
    public void idleCheckDuringConnectResumesWhenConnectFails() throws Exception {
        Jobs jobs = new Jobs();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        App app = new App() {
            public IContext getContext() {
                return jobs.context;
            }

            public boolean connect(IConnection conn, Object[] params) {
                entered.countDown();
                try {
                    return release.await(5, TimeUnit.SECONDS) && false;
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
            }
        };
        Scope room = room(app, "room");
        IEventListener listener = event -> {
        };
        assertTrue(room.addEventListener(listener));
        assertTrue(room.removeEventListener(listener));
        Thread connect = new Thread(() -> room.connect(null));
        connect.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            jobs.runLast();
            assertSame(room, app.getScope("room"));
            assertEquals(1, jobs.scheduled.size());
        } finally {
            release.countDown();
            connect.join(5000);
        }
        assertEquals(2, jobs.scheduled.size());
        jobs.runLast();
        assertNull(app.getScope("room"));
    }

    @Test
    public void childScopeCallbacksRunOutsideMonitor() {
        AtomicReference<Boolean> listenerAdded = new AtomicReference<>();
        App app = new App();
        Scope room = room(app, "room");
        IEventListener listener = event -> {
        };
        app.setHandler((IScopeHandler) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { IScopeHandler.class }, (proxy, method, args) -> {
            if (method.getName().equals("addChildScope") && "child".equals(((IBasicScope) args[0]).getName())) {
                Thread other = new Thread(() -> room.addEventListener(listener));
                other.start();
                other.join(2000);
                listenerAdded.set(!other.isAlive());
            }
            return method.getReturnType() == boolean.class ? Boolean.TRUE : null;
        }));
        assertTrue(room.addChildScope(new Scope(room, ScopeType.ROOM, "child", false)));
        assertEquals(Boolean.TRUE, listenerAdded.get());
        assertTrue(room.hasEventListeners());
    }

    @Test
    public void snapshotDoesNotUseNameLookupsAndSurvivesRemoval() {
        App app = new App() {
            public IBasicScope getBasicScope(ScopeType type, String name) {
                throw new AssertionError("Snapshot must not look up names");
            }
        };
        for (int i = 0; i < 1000; i++) {
            room(app, "room" + i);
        }
        Collection<IBasicScope> snapshot = app.getBasicScopes();
        assertEquals(1000, snapshot.size());
        for (IBasicScope child : snapshot) {
            app.removeChildScope(child);
        }
        assertEquals(1000, snapshot.size());
        assertTrue(app.getBasicScopes().isEmpty());
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
    }
}
