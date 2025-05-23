package org.red5.server.net.rtmp.event;

import java.util.Arrays;

/**
 * Represents an notify to be executed on a connected client.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class ClientNotifyEvent extends BaseEvent {

    private final String method;

    private final Object[] params;

    /**
     * <p>Constructor for ClientNotifyEvent.</p>
     *
     * @param method a {@link java.lang.String} object
     * @param params an array of {@link java.lang.Object} objects
     */
    public ClientNotifyEvent(String method, Object[] params) {
        super(Type.CLIENT_NOTIFY);
        this.method = method;
        this.params = params;
    }

    /**
     * <p>build.</p>
     *
     * @param method a {@link java.lang.String} object
     * @param params an array of {@link java.lang.Object} objects
     * @return a {@link org.red5.server.net.rtmp.event.ClientNotifyEvent} object
     */
    public final static ClientNotifyEvent build(String method, Object[] params) {
        ClientNotifyEvent event = new ClientNotifyEvent(method, params);
        return event;
    }

    /** {@inheritDoc} */
    @Override
    public byte getDataType() {
        return TYPE_NOTIFY;
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

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "ClientNotifyEvent [method=" + method + ", params=" + Arrays.toString(params) + "]";
    }

}
