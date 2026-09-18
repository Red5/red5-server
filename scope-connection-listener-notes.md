# IScopeListener and IConnectionListener behaviour in Red5

Answer to a user question about registering `IScopeListener` and `IConnectionListener`
implementations via `server.addListener(...)` from a plugin, in order to obtain the
`ClientBroadcastStream` without touching application code.

Reference trees:

- red5-server v2.0.16.46 (open source, this repository)
- red5pro-plugins, which depends on red5-server 2.0.16.46 (`pom.xml` property `red5.version`)

## Short version

The API is not being misused. Two separate things are going on:

1. In open-source Red5 a `BroadcastScope` is never removed from its parent scope in
   the normal publish and unpublish flow. So `notifyBasicScopeAdded` fires once per
   stream name and `notifyBasicScopeRemoved` never fires. Red5 Pro does not have this
   problem because `ProStream.close` removes the scope itself.
2. `IConnectionListener` registered on the server should fire in the current code. Its
   silence is not explained by the tree and needs the user's version and setup.

More importantly, `IScopeListener` and `IConnectionListener` are not what our own
plugins use to get at broadcast streams. The Red5 Pro plugins use a small set of other
hooks, listed below, and the user should be pointed at those.

## Which product are they on

The symptoms match open-source Red5, not Red5 Pro. If they are on Red5 Pro, the
"removed never fires" part should not reproduce, and the answer changes. Ask for the
version string and whether `red5pro-mega` is on the classpath.

## Why notifyBasicScopeAdded fires only once and removed never fires (open source)

- On the first publish of a name, `ProviderService.registerBroadcastStream` creates a
  `BroadcastScope`, adds it as a child of the app scope, and that add triggers the
  notification. See `server/src/main/java/org/red5/server/stream/ProviderService.java:126`.
- On unpublish, the pipe fires a provider-disconnect event and
  `BroadcastScope.onPipeConnectionEvent` calls the two-argument
  `unregisterBroadcastStream(parent, name)`. See
  `server/src/main/java/org/red5/server/scope/BroadcastScope.java:235`.
- That two-argument overload passes a null stream, and the only branch that calls
  `removeChildScope` is guarded by `bs != null`. With a null stream it logs
  "Broadcast scope was null" and returns. See `ProviderService.java:163-176`.
- The open-source `ClientBroadcastStream.close` does not touch the broadcast scope
  either. See `common/src/main/java/org/red5/server/stream/ClientBroadcastStream.java:215`.
- Result: the `BroadcastScope` stays a child of the app scope until the app itself is
  torn down. No removal event is ever posted. The next publish with the same name finds
  the existing scope, swaps in the new `ClientBroadcastStream` via
  `setClientBroadcastStream`, and never re-adds it, so no second "added" event. A
  publish under a different name fires "added" again.
- Notifications are dispatched from the scheduling service a few milliseconds later on
  another thread, not inline. See `common/src/main/java/org/red5/server/Server.java:324-335`.

This is a defect worth fixing in red5-server: the provider-disconnect path should
remove the broadcast scope once it has no event listeners left. That is a small change
in `ProviderService` and `BroadcastScope`.

## How Red5 Pro avoids it

`ProStream.close` in `red5pro-mega` does the cleanup the open-source stream does not:
it calls the three-argument `unregisterBroadcastStream(scope, name, this)`, nulls the
stream on the scope, and then calls `scope.removeChildScope(bsScope)` directly. See
`red5pro-mega/src/com/red5pro/override/ProStream.java:1507-1520`.

`Red5ProConnManager` also sweeps idle broadcast scopes and removes them once they have
no event listeners. See
`red5pro-mega/src/com/red5pro/server/stream/Red5ProConnManager.java:503-517`.

So on Red5 Pro the sequence per publish is: scope added, stream published, stream
closed, scope removed, and the `IScopeListener` basic-scope callbacks fire every time.

## Why IConnectionListener may be silent

In the current tree `Scope.connect` posts `notifyConnected` when a client is added and
the scope being connected is the connection's own scope, and `Scope.disconnect` posts
`notifyDisconnected` the same way. See
`common/src/main/java/org/red5/server/scope/Scope.java:380` and `Scope.java:477`. Both
go through the same scheduled dispatch as the scope events, so if scope events arrive
the plumbing works.

The cluster plugin relies on exactly this and it works there: it calls
`server.addListener(new AbstractConnectionListener() {...})` and counts connections in
`notifyConnected` and `notifyDisconnected`. See
`cluster-plugin/src/com/red5pro/cluster/plugin/ClusterPlugin.java:714-905`.

Questions for the user:

- Which Red5 version they run. This dispatch was consolidated in April 2022; older
  builds may differ.
- Whether they register on the real server bean, the one handed to the plugin via
  `setServer` (bean id `red5.server`), and whether registration happens before the
  first client connects.
- Whether their listener overrides `notifyConnected` and not just `propertyChange`.

Two things to know about `IConnectionListener`:

- Server-level listeners only get `notifyConnected` and `notifyDisconnected`. The
  `propertyChange` half of the interface is only delivered to listeners added on the
  connection itself with `conn.addListener(...)`.
- A listener added on the connection never sees `notifyConnected`, because the
  connect has already happened by the time you have the connection. `Red5ProLive`
  has a comment saying exactly that. See
  `webapps/live/src/main/java/com/infrared5/red5pro/live/Red5ProLive.java:222-226`.

## How our plugins actually get at streams

None of the plugins in red5pro-plugins use `notifyBasicScopeAdded` to find streams.
The patterns in use are:

### Pattern 1: IScopeListener for APPLICATION scopes, then register with the adapter

`notifyScopeCreated` is used only to spot new APPLICATION scopes and hook the
`MultiThreadedApplicationAdapter`. Then the plugin iterates
`server.getGlobalScopes()` to catch apps that started before the listener was added.
Examples:

- `ClusterPlugin.java:627-700`: adds an `IApplication` listener with
  `adapter.addListener(monitor)`, registers publish and playback security handlers, and
  walks existing child scopes to pick up already-running streams via
  `BroadcastScope.getClientBroadcastStream()`.
- `Red5ProPlugin.java:268-300` and `RTSPPlugin.java:236-262`: same shape, registering
  `IStreamPublishSecurity` and `IStreamPlaybackSecurity` on each app.

`IApplication` covers connect, join, leave and disconnect, not stream events. The
security handler `isPublishAllowed(scope, name, mode)` fires on every publish attempt
and is the cheapest per-publish hook a plugin has, but it runs before the stream is
registered so it does not give you the stream object.

### Pattern 2: SideStreamFactory (Red5 Pro only, the recommended one)

This is the hook built for exactly what the user wants. A plugin registers a factory
once with `ProStream.addSideStreamFactory(factory)`, and `ProStream.start` calls
`factory.create(scope, streamName, proStream)` for every new publish, handing over the
live `ProStream`. The returned `ISideStream` extends `IProStreamListener`, so it
receives packets, and it can also implement `ProStreamTerminationEventListener` and
`IProStreamCodecStateListener` for close and codec-ready callbacks. See
`ProStream.java:1021-1038` and `ProStream.java:2812`.

Users of it: `RTSPPlugin.java:183`, `InspectorPlugin.java:71`,
`WebRTCPlugin.java:368`, `ClusterPlugin.java:568`.

If the factory only wants to observe, it can call `proStream.addStreamListener(...)`
inside `create` and return null, though `ProStream` logs a warning for a null sidecar,
so returning a lightweight `ISideStream` is cleaner.

### Pattern 3: look the stream up by name when you need it

`scope.getBroadcastScope(name).getClientBroadcastStream()` is used throughout
(`Red5ProLive.java:620-630`, `ConnectorShell.java:137-141`,
`ClusterPlugin.java:1005`). It works on both open source and Pro and needs no
listener, but you need a trigger to know when to look.

### Pattern 4: per-connection propertyChange (Red5 Pro only)

`ProStream.sendPublishStartNotify` fires a `PropertyChangeEvent` on the publishing
connection with property name `publish` and a JSON payload naming the stream, and
`unpublish` on stop. See `ProStream.java:2206-2229`. A plugin can combine this with
the server-level listener: in `notifyConnected(conn)` call `conn.addListener(...)`,
then on the `publish` property look the stream up with pattern 3. `Red5ProLive` uses
the same property events for WebRTC signalling.

## Recommendation for the user

- On Red5 Pro: register a `SideStreamFactory`. It is the supported way to attach to
  every broadcast stream from a plugin, it is what our own plugins do, and it needs no
  application code. Do not build on `notifyBasicScopeAdded`.
- On open-source Red5: there is no equivalent hook today. Until the removal bug is
  fixed, the broadcast scope from the single "added" callback is long-lived and its
  pipe sees every publisher of that name. Subscribe a consumer to the
  `IBroadcastScope` once; a consumer that also implements `IPipeConnectionListener`
  receives `PROVIDER_CONNECT_PUSH` for every new publish and `event.getProvider()` is
  the new `ClientBroadcastStream`. The consumer's push method also receives every
  packet directly. Alternatively, use the per-publish security handler as the trigger
  and then look the stream up by name shortly after.
- Either way, `IConnectionListener` on the server is only useful for connect and
  disconnect counting, not for stream discovery.

## Next steps on our side

1. Fix broadcast-scope removal in red5-server so add and remove events fire per
   publish on open source, matching what `ProStream.close` already does in Pro.
2. Consider documenting `SideStreamFactory` as the public plugin hook for stream
   attachment, since it is currently only discoverable by reading plugin sources.
