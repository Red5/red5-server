package org.red5.server.so;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Before;
import org.junit.Test;
import org.red5.server.api.IConnection;
import org.red5.server.api.IContext;
import org.red5.server.api.scheduling.ISchedulingService;
import org.red5.server.api.scope.IBasicScope;
import org.red5.server.api.scope.IScope;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * Client-specific shared object creation limits: name depth, client persistence and per-connection creation count (issue #454).
 */
public class SharedObjectQuotaTest {

    private SharedObjectService service;

    private IScope scope;

    private final Map<String, IBasicScope> children = new ConcurrentHashMap<>();

    private final Map<String, Object> scopeAttributes = new ConcurrentHashMap<>();

    @Before
    public void setUp() {
        service = new SharedObjectService();
        ISchedulingService schedulingService = (ISchedulingService) Proxy.newProxyInstance(ISchedulingService.class.getClassLoader(), new Class<?>[] { ISchedulingService.class }, (proxy, method, args) -> method.getReturnType() == String.class ? "job" : null);
        IContext context = (IContext) Proxy.newProxyInstance(IContext.class.getClassLoader(), new Class<?>[] { IContext.class }, (proxy, method, args) -> "getBean".equals(method.getName()) ? schedulingService : null);
        // the application scope also acts as the resource resolver of its persistence stores
        scope = (IScope) Proxy.newProxyInstance(IScope.class.getClassLoader(), new Class<?>[] { IScope.class, ResourcePatternResolver.class }, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getContextPath":
                    return "/app";
                case "getContext":
                    return context;
                case "getName":
                    return "app";
                case "hasChildScope":
                    return args.length == 2 && children.containsKey((String) args[1]);
                case "getBasicScopeNames":
                    return children.keySet();
                case "getBasicScope":
                    return children.get((String) args[1]);
                case "addChildScope":
                    IBasicScope child = (IBasicScope) args[0];
                    return children.putIfAbsent(child.getName(), child) == null;
                case "hasAttribute":
                    return scopeAttributes.containsKey((String) args[0]);
                case "getAttribute":
                    return scopeAttributes.get((String) args[0]);
                case "setAttribute":
                    scopeAttributes.put((String) args[0], args[1]);
                    return true;
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return "scope:app";
                default:
                    return method.getReturnType() == boolean.class ? false : null;
            }
        });
    }

    private static IConnection connection(String id) {
        Map<String, Object> attributes = new ConcurrentHashMap<>();
        return (IConnection) Proxy.newProxyInstance(IConnection.class.getClassLoader(), new Class<?>[] { IConnection.class }, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getSessionId":
                    return id;
                case "getAttribute":
                    return args[0] instanceof String ? attributes.get((String) args[0]) : null;
                case "setAttribute":
                    attributes.put((String) args[0], args[1]);
                    return true;
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return "conn:" + id;
                default:
                    return method.getReturnType() == boolean.class ? false : null;
            }
        });
    }

    private static String name(int segments) {
        StringBuilder sb = new StringBuilder("s");
        for (int i = 1; i < segments; i++) {
            sb.append("/s");
        }
        return sb.toString();
    }

    @Test
    public void testNameDepthCap() {
        // default
        assertTrue(service.createSharedObject(scope, name(SharedObjectService.DEFAULT_MAX_NAME_DEPTH), true));
        assertFalse(service.createSharedObject(scope, "x/" + name(SharedObjectService.DEFAULT_MAX_NAME_DEPTH), true));
        service.setMaxNameDepth(3);
        assertTrue(service.createSharedObject(scope, "a/b/c", false));
        assertFalse(service.createSharedObject(scope, "a/b/c/d", false));
        assertFalse(service.createClientSharedObject(connection("c1"), scope, "a/b/c/d", true));
        assertFalse(children.containsKey("a/b/c/d"));
        // 0 means unlimited
        service.setMaxNameDepth(0);
        assertTrue(service.createSharedObject(scope, name(100), true));
    }

    @Test
    public void testClientPersistentRefusedWhenDisabled() {
        IConnection conn = connection("c1");
        // allowed by default
        assertTrue(service.createClientSharedObject(conn, scope, "p1", true));
        assertTrue(children.get("p1") instanceof SharedObjectScope && ((SharedObjectScope) children.get("p1")).isPersistent());
        service.setAllowClientPersistent(false);
        assertFalse(service.createClientSharedObject(conn, scope, "p2", true));
        assertFalse(children.containsKey("p2"));
        // transient objects are still allowed
        assertTrue(service.createClientSharedObject(conn, scope, "t1", false));
        assertTrue(children.containsKey("t1"));
        // server-side code may still create persistent objects, and clients may join them
        assertTrue(service.createSharedObject(scope, "server", true));
        assertTrue(service.createClientSharedObject(conn, scope, "server", true));
        assertEquals(3, children.size());
    }

    @Test
    public void testPerConnectionCreationCap() {
        service.setMaxSharedObjectsPerConnection(2);
        IConnection first = connection("c1"), second = connection("c2");
        assertTrue(service.createClientSharedObject(first, scope, "a", false));
        // a failed creation is not counted
        assertFalse(service.createClientSharedObject(first, scope, "../bad", false));
        assertTrue(service.createClientSharedObject(first, scope, "b", true));
        assertFalse(service.createClientSharedObject(first, scope, "c", false));
        assertFalse(children.containsKey("c"));
        // joining an existing shared object is not a creation
        assertTrue(service.createClientSharedObject(first, scope, "a", false));
        // the count is per connection
        assertTrue(service.createClientSharedObject(second, scope, "c", false));
        assertTrue(service.createClientSharedObject(first, scope, "c", false));
        // server-side creation is not counted or limited
        assertTrue(service.createSharedObject(scope, "d", false));
        assertTrue(service.createClientSharedObject(null, scope, "e", false));
        assertEquals(5, children.size());
    }

    @Test
    public void testPerConnectionCapUnlimitedByDefault() {
        IConnection conn = connection("c1");
        for (int i = 0; i < 50; i++) {
            assertTrue(service.createClientSharedObject(conn, scope, "so" + i, false));
        }
        assertEquals(50, children.size());
    }

}
