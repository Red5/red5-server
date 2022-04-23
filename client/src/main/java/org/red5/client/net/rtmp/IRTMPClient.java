package org.red5.client.net.rtmp;

import java.util.Map;

import org.red5.server.api.event.IEventDispatcher;
import org.red5.server.api.service.IPendingServiceCallback;
import org.red5.server.api.so.IClientSharedObject;
import org.red5.server.messaging.IMessage;
import org.red5.server.net.rtmp.RTMPConnection;

public interface IRTMPClient {

    public void setConnectionClosedHandler(Runnable connectionClosedHandler);

    public void setExceptionHandler(ClientExceptionHandler exceptionHandler);

    public void setStreamEventDispatcher(IEventDispatcher streamEventDispatcher);

    public void setServiceProvider(Object serviceProvider);

    public void connect(String server, int port, String application);

    public void connect(String server, int port, String application, IPendingServiceCallback connectCallback);

    public void connect(String server, int port, Map<String, Object> connectionParams);

    public void connect(String server, int port, Map<String, Object> connectionParams, IPendingServiceCallback connectCallback);

    public void connect(String server, int port, Map<String, Object> connectionParams, IPendingServiceCallback connectCallback, Object[] connectCallArguments);

    public void invoke(String method, IPendingServiceCallback callback);

    public void invoke(String method, Object[] params, IPendingServiceCallback callback);

    public void disconnect();

    public void createStream(IPendingServiceCallback callback);

    public void publish(Number streamId, String name, String mode, INetStreamEventHandler handler);

    public void unpublish(Number streamId);

    public void publishStreamData(Number streamId, IMessage message);

    public void play(Number streamId, String name, int start, int length);

    public void play2(Number streamId, Map<String, ?> playOptions);

    public IClientSharedObject getSharedObject(String name, boolean persistent);

    public Map<String, Object> makeDefaultConnectionParams(String server, int port, String application);

    public RTMPConnection getConnection();
}
