package org.red5.server.net.rtmp.event;

import java.util.Arrays;

import org.red5.server.api.service.IPendingServiceCallback;

/**
 * Represents an invoke to be executed on a connected client.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class ClientInvokeEvent extends BaseEvent {

    private final String method;

    private final Object[] params;

    private final IPendingServiceCallback callback;

    /**
     * <p>Constructor for ClientInvokeEvent.</p>
     *
     * @param method a {@link java.lang.String} object
     * @param params an array of {@link java.lang.Object} objects
     * @param callback a {@link org.red5.server.api.service.IPendingServiceCallback} object
     */
    public ClientInvokeEvent(String method, Object[] params, IPendingServiceCallback callback) {
        super(Type.CLIENT_INVOKE);
        this.method = method;
        this.params = params;
        this.callback = callback;
    }

    /**
     * <p>build.</p>
     *
     * @param method a {@link java.lang.String} object
     * @param params an array of {@link java.lang.Object} objects
     * @param callback a {@link org.red5.server.api.service.IPendingServiceCallback} object
     * @return a {@link org.red5.server.net.rtmp.event.ClientInvokeEvent} object
     */
    public final static ClientInvokeEvent build(String method, Object[] params, IPendingServiceCallback callback) {
        ClientInvokeEvent event = new ClientInvokeEvent(method, params, callback);
        return event;
    }

    /** {@inheritDoc} */
    @Override
    public byte getDataType() {
        return TYPE_INVOKE;
    }

    /** {@inheritDoc} */
    @Override
    protected void releaseInternal() {
    }

    /**
     * <p>Getter for the field <code>method</code>.</p>
     *
     * @return the method
     */
    public String getMethod() {
        return method;
    }

    /**
     * <p>Getter for the field <code>params</code>.</p>
     *
     * @return the params
     */
    public Object[] getParams() {
        return params;
    }

    /**
     * <p>Getter for the field <code>callback</code>.</p>
     *
     * @return the callback
     */
    public IPendingServiceCallback getCallback() {
        return callback;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "ClientInvokeEvent [method=" + method + ", params=" + Arrays.toString(params) + ", callback=" + callback + "]";
    }

}
