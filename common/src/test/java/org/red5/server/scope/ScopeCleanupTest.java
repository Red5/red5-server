package org.red5.server.scope;

import static org.junit.Assert.*;

import java.util.Collection;
import java.lang.reflect.Proxy;
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
