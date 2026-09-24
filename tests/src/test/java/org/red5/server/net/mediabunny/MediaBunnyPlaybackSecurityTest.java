package org.red5.server.net.mediabunny;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;

import org.junit.Test;
import org.red5.server.adapter.MultiThreadedApplicationAdapter;
import org.red5.server.api.Red5;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.stream.IStreamPlaybackSecurity;

/**
 * Verifies that MediaBunny subscriptions consult the same stream playback security handlers as RTMP playback (issue #460).
 */
public class MediaBunnyPlaybackSecurityTest {

    @Test
    public void testNoHandlersAllowsPlayback() {
        assertTrue(MediaBunnyServlet.isPlaybackAllowed(scopeWithHandler(new MultiThreadedApplicationAdapter()), "stream1"));
    }

    @Test
    public void testDenyingHandlerDeniesPlayback() {
        MultiThreadedApplicationAdapter app = new MultiThreadedApplicationAdapter();
        app.registerStreamPlaybackSecurity((scope, name, start, length, flush) -> !"secret".equals(name));
        IScope scope = scopeWithHandler(app);
        assertFalse(MediaBunnyServlet.isPlaybackAllowed(scope, "secret"));
        assertTrue(MediaBunnyServlet.isPlaybackAllowed(scope, "public"));
    }

    @Test
    public void testHandlerThatNeedsAConnectionFailsClosed() {
        MultiThreadedApplicationAdapter app = new MultiThreadedApplicationAdapter();
        // RTMP-oriented handlers commonly read the current connection, which is null for HTTP subscriptions
        IStreamPlaybackSecurity handler = (scope, name, start, length, flush) -> Red5.getConnectionLocal().getAttribute("token") != null;
        app.registerStreamPlaybackSecurity(handler);
        assertFalse(MediaBunnyServlet.isPlaybackAllowed(scopeWithHandler(app), "stream1"));
    }

    private static IScope scopeWithHandler(MultiThreadedApplicationAdapter handler) {
        return (IScope) Proxy.newProxyInstance(IScope.class.getClassLoader(), new Class<?>[] { IScope.class }, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getHandler":
                    return handler;
                case "hasAttribute":
                case "hasParent":
                    return false;
                case "getName":
                    return "live";
                case "toString":
                    return "scope:live";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    return method.getReturnType() == boolean.class ? false : null;
            }
        });
    }

}
