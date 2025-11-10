<%@ page import="
org.apache.mina.core.buffer.IoBuffer,
org.springframework.context.ApplicationContext,
org.springframework.web.context.WebApplicationContext,
com.red5pro.media.SourceType,
com.red5pro.override.IProStream,
com.red5pro.override.ISideStream,
com.red5pro.override.ProStream,
com.red5pro.override.io.ConnectorShell,
com.red5pro.plugin.Red5ProPlugin,
com.red5pro.server.stream.Red5ProConnManager,
com.red5pro.server.stream.webrtc.IRTCStreamSession,
com.red5pro.webrtc.plugin.WebRTCPlugin,
com.red5pro.webrtc.RTCConnection,
com.red5pro.server.stream.restreamer.IConnectorShell,
org.red5.server.Client,
org.red5.server.ClientRegistry,
org.red5.server.adapter.MultiThreadedApplicationAdapter,
org.red5.server.api.IClient,
org.red5.server.api.IClientRegistry,
org.red5.server.api.IConnection,
org.red5.server.api.Red5,
org.red5.server.api.listeners.AbstractConnectionListener,
org.red5.server.api.scope.IScope,
org.red5.server.api.scope.IBroadcastScope,
org.red5.server.api.scope.ScopeType,
org.red5.server.api.stream.IStreamCapableConnection,
org.red5.server.api.stream.IStreamService,
org.red5.server.net.rtmp.RTMPConnection,
org.red5.server.net.rtmp.codec.RTMP,
org.red5.server.net.rtmp.event.VideoData,
org.red5.server.net.rtmp.message.Packet,
org.red5.server.scope.BroadcastScope,
org.red5.server.plugin.PluginRegistry,
org.red5.server.util.ScopeUtils,
java.beans.PropertyChangeEvent,
java.util.HashMap,
java.util.Set,
java.util.LinkedList,
java.util.Optional,
java.util.concurrent.atomic.AtomicInteger,
java.util.concurrent.atomic.AtomicReference,
java.util.concurrent.Future" %>
<html>
    <head>
        <title>Publisher and Subscriber Stats</title>
    </head>
<body>
<h1>Publisher and Subscriber Stats</h1>
<%
// message storage
LinkedList<String> messages = new LinkedList<String>();

// application context
ApplicationContext appCtx = (ApplicationContext) getServletContext().getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
// web scope / app scope
IScope webScope = (IScope) appCtx.getBean("web.scope");
// web application
MultiThreadedApplicationAdapter app = (MultiThreadedApplicationAdapter) appCtx.getBean("web.handler");
// webrtc plugin
WebRTCPlugin plugin = (WebRTCPlugin) PluginRegistry.getPlugin("Red5Pro-Web-RTC");
// this should be equal to the web scope
IScope scope = app.getScope();
if (scope != webScope) {
    // scope mismatch
    messages.add("Scope mismatch: " + scope + " != " + webScope);
}

String contextPath = scope.getContextPath();

String streamName = request.getParameter("streamName");
if (streamName == null || streamName.isEmpty()) {
    streamName = "stream1";
}

ProStream proStream = null;

try {
    messages.add(String.format("Is %s available at %s?", streamName, contextPath));
    // this process will start the rtc session creation if its not already completed from previous request
    BroadcastScope bc = (BroadcastScope) scope.getBasicScope(ScopeType.BROADCAST, streamName);
    if (bc != null) {
        // get the prostream
        proStream = (ProStream) bc.getClientBroadcastStream();
        if (proStream != null) {
            // generate a side stream id for this is available check
            // use streams published name here to prevent id lookup issue when an alias was requested
            String sideStreamId = String.format(ISideStream.RTC_URI_FORMAT, scope.getName(), proStream.getPublishedName());
            // go a step further and make sure theres an rtc side stream so RTCBroadcastStream doesnt fail on init
            messages.add(String.format("Looking up side stream id: %s", sideStreamId));
            if (proStream.getSideStream(sideStreamId) != null) {
                // playback may proceed, name is available for subscribers
                messages.add(String.format("Prostream %s is %s, RTC side stream available", streamName,
                        proStream.isClosed() ? "closed" : "live"));
                // collect stats (this is only for those that use the live pipe registration)
                int subscriberCount = proStream.getSubscriberCount();
                messages.add(String.format("Prostream %s has %d subscribers", streamName, subscriberCount));
                messages.add(String.format("Scope %s has %d clients:", webScope.getName(), webScope.getClients().size()));
                Set<IClient> clients = webScope.getClients();
                for (IClient client : clients) {
                    for (IConnection conn : client.getConnections()) {
                        if (conn instanceof IStreamCapableConnection) {
                            String wsId = conn.hasAttribute("ws-session-id") ? conn.getStringAttribute("ws-session-id") : "N/A";
                            IStreamCapableConnection streamConn = (IStreamCapableConnection) conn;
                            if (conn.getDuty().equals(IConnection.Duty.SUBSCRIBER)) {
                                IRTCStreamSession sess = ((RTCConnection) conn).getSession();
                                if (sess != null) {
                                    String subStreamName = sess.getProStream().getPublishedName();
                                    messages.add(String.format("Subscriber Client ID: %s, Session ID: %s, WebSocket session ID: %s, RTC Session ID: %s, Sub Stream Name: %s",
                                            client.getId(), conn.getSessionId(), wsId, sess.getSessionId(), subStreamName));
                                } else {
                                    messages.add(String.format("Subscriber Client ID: %s, Session ID: %s, WebSocket session ID: %s, RTC Session is null",
                                            client.getId(), conn.getSessionId(), wsId));
                                }
                            } else {
                                messages.add(String.format("Non-subscriber Client ID: %s, Session ID: %s, WebSocket session ID: %s, Connection Class: %s",
                                        client.getId(), conn.getSessionId(), wsId, conn.getClass().getName()));
                                //messages.add("Checking if publisher..." + conn.getAttributeNames());
                                messages.add(String.format("STREAM_NAME attribute: %s", conn.getStringAttribute("STREAM_NAME")));
                                // the expected way to detect a publisher is via duty, but we can't count on it
                                //if (conn.getDuty().equals(IConnection.Duty.PUBLISHER)) {
                                if (conn instanceof RTCConnection) {
                                    IRTCStreamSession sess = ((RTCConnection) conn).getSession();
                                    if (sess != null) {
                                        ProStream pubStream = (ProStream) sess.getProStream();
                                        String pubStreamName = pubStream.getPublishedName();
                                        int pubSubscriberCount = pubStream.getSubscriberCount();
                                        messages.add(String.format("Publisher RTC Session ID: %s, Pub Stream Name: %s, Subscriber Count: %d",
                                                sess.getSessionId(), pubStreamName, pubSubscriberCount));
                                    } else {
                                        messages.add(String.format("Publisher RTC Session is null"));
                                    }
                                } else if (conn.hasAttribute("STREAM_NAME")) { // if duty is not set, we can detect publisher by attribute
                                    // get the stream name from the connection attribute
                                    String pubStreamName = conn.getStringAttribute("STREAM_NAME");
                                    messages.add(String.format("Publisher Conn ID: %s, Pub Stream Name: %s", conn.getSessionId(), pubStreamName));
                                    // the stream name can be used to count subscribers, but this requires looking at each
                                    // connection that is a subscriber to see if they are subscribed to this stream
                                    // this is not efficient for large numbers of connections, but this is just a test page
                                    int pubSubscriberCount = 0;
                                    for (IClient cclient : webScope.getClients()) {
                                        for (IConnection cconn : cclient.getConnections()) {
                                            if (cconn instanceof IStreamCapableConnection && cconn.getDuty().equals(IConnection.Duty.SUBSCRIBER)) {
                                                IRTCStreamSession sess = ((RTCConnection) cconn).getSession();
                                                if (sess != null) {
                                                    String subStreamName = sess.getProStream().getPublishedName();
                                                    if (subStreamName.equals(pubStreamName)) {
                                                        pubSubscriberCount++;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    messages.add(String.format("Publisher ConnectorShell Conn ID: %s, Pub Stream Name: %s, Subscriber Count: %d",
                                            conn.getSessionId(), pubStreamName, pubSubscriberCount));
                                } else {
                                    messages.add(String.format("Publisher connection is not RTCConnection"));
                                }
                            }
                        }
                    }
                }
            } else {
                messages.add(String.format("Prostream %s is %s, no RTC side stream", streamName, proStream.isClosed() ? "closed" : "live"));
            }
        } else {
            messages.add(String.format("Prostream not in-scope yet, name: %s", streamName));
        }
    } else {
        messages.add(String.format("Prostream not available, name: %s", streamName));
    }
} catch (Exception e) {
    messages.add(String.format("Exception encoding string: %s", e.getMessage()));
}

%>
<br />
<h3>Messages:</h3>
<ul>
<%
for (String message : messages) {
%>
    <li><%= message %></li>
<%
}
%>
</ul>
<br />

</body>
</html>