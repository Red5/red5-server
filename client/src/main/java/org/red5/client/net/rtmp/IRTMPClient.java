package org.red5.client.net.rtmp;

import java.util.Map;

import org.red5.server.api.event.IEventDispatcher;
import org.red5.server.api.service.IPendingServiceCallback;
import org.red5.server.api.so.IClientSharedObject;
import org.red5.server.messaging.IMessage;
import org.red5.server.net.rtmp.RTMPConnection;

/**
 * <p>IRTMPClient interface.</p>
 *
 * @author mondain
 */
public interface IRTMPClient {

    /**
     * <p>setConnectionClosedHandler.</p>
     *
     * @param connectionClosedHandler a {@link java.lang.Runnable} object
     */
    public void setConnectionClosedHandler(Runnable connectionClosedHandler);

    /**
     * <p>setExceptionHandler.</p>
     *
     * @param exceptionHandler a {@link org.red5.client.net.rtmp.ClientExceptionHandler} object
     */
    public void setExceptionHandler(ClientExceptionHandler exceptionHandler);

    /**
     * <p>setStreamEventDispatcher.</p>
     *
     * @param streamEventDispatcher a {@link org.red5.server.api.event.IEventDispatcher} object
     */
    public void setStreamEventDispatcher(IEventDispatcher streamEventDispatcher);

    /**
     * <p>setServiceProvider.</p>
     *
     * @param serviceProvider a {@link java.lang.Object} object
     */
    public void setServiceProvider(Object serviceProvider);

    /**
     * <p>connect.</p>
     *
     * @param server a {@link java.lang.String} object
     * @param port a int
     * @param application a {@link java.lang.String} object
     */
    public void connect(String server, int port, String application);

    /**
     * <p>connect.</p>
     *
     * @param server a {@link java.lang.String} object
     * @param port a int
     * @param application a {@link java.lang.String} object
     * @param connectCallback a {@link org.red5.server.api.service.IPendingServiceCallback} object
     */
    public void connect(String server, int port, String application, IPendingServiceCallback connectCallback);

    /**
     * <p>connect.</p>
     *
     * @param server a {@link java.lang.String} object
     * @param port a int
     * @param connectionParams a {@link java.util.Map} object
     */
    public void connect(String server, int port, Map<String, Object> connectionParams);

    /**
     * <p>connect.</p>
     *
     * @param server a {@link java.lang.String} object
     * @param port a int
     * @param connectionParams a {@link java.util.Map} object
     * @param connectCallback a {@link org.red5.server.api.service.IPendingServiceCallback} object
     */
    public void connect(String server, int port, Map<String, Object> connectionParams, IPendingServiceCallback connectCallback);

    /**
     * <p>connect.</p>
     *
     * @param server a {@link java.lang.String} object
     * @param port a int
     * @param connectionParams a {@link java.util.Map} object
     * @param connectCallback a {@link org.red5.server.api.service.IPendingServiceCallback} object
     * @param connectCallArguments an array of {@link java.lang.Object} objects
     */
    public void connect(String server, int port, Map<String, Object> connectionParams, IPendingServiceCallback connectCallback, Object[] connectCallArguments);

    /**
     * <p>invoke.</p>
     *
     * @param method a {@link java.lang.String} object
     * @param callback a {@link org.red5.server.api.service.IPendingServiceCallback} object
     */
    public void invoke(String method, IPendingServiceCallback callback);

    /**
     * <p>invoke.</p>
     *
     * @param method a {@link java.lang.String} object
     * @param params an array of {@link java.lang.Object} objects
     * @param callback a {@link org.red5.server.api.service.IPendingServiceCallback} object
     */
    public void invoke(String method, Object[] params, IPendingServiceCallback callback);

    /**
     * <p>disconnect.</p>
     */
    public void disconnect();

    /**
     * <p>createStream.</p>
     *
     * @param callback a {@link org.red5.server.api.service.IPendingServiceCallback} object
     */
    public void createStream(IPendingServiceCallback callback);

    /**
     * <p>publish.</p>
     *
     * @param streamId a {@link java.lang.Number} object
     * @param name a {@link java.lang.String} object
     * @param mode a {@link java.lang.String} object
     * @param handler a {@link org.red5.client.net.rtmp.INetStreamEventHandler} object
     */
    public void publish(Number streamId, String name, String mode, INetStreamEventHandler handler);

    /**
     * <p>unpublish.</p>
     *
     * @param streamId a {@link java.lang.Number} object
     */
    public void unpublish(Number streamId);

    /**
     * <p>publishStreamData.</p>
     *
     * @param streamId a {@link java.lang.Number} object
     * @param message a {@link org.red5.server.messaging.IMessage} object
     */
    public void publishStreamData(Number streamId, IMessage message);

    /**
     * <p>play.</p>
     *
     * @param streamId a {@link java.lang.Number} object
     * @param name a {@link java.lang.String} object
     * @param start a int
     * @param length a int
     */
    public void play(Number streamId, String name, int start, int length);

    /**
     * <p>play2.</p>
     *
     * @param streamId a {@link java.lang.Number} object
     * @param playOptions a {@link java.util.Map} object
     */
    public void play2(Number streamId, Map<String, ?> playOptions);

    /**
     * <p>getSharedObject.</p>
     *
     * @param name a {@link java.lang.String} object
     * @param persistent a boolean
     * @return a {@link org.red5.server.api.so.IClientSharedObject} object
     */
    public IClientSharedObject getSharedObject(String name, boolean persistent);

    /**
     * <p>makeDefaultConnectionParams.</p>
     *
     * @param server a {@link java.lang.String} object
     * @param port a int
     * @param application a {@link java.lang.String} object
     * @return a {@link java.util.Map} object
     */
    public Map<String, Object> makeDefaultConnectionParams(String server, int port, String application);

    /**
     * <p>getConnection.</p>
     *
     * @return a {@link org.red5.server.net.rtmp.RTMPConnection} object
     */
    public RTMPConnection getConnection();
}
