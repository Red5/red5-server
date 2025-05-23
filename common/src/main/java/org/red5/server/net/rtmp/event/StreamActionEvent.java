package org.red5.server.net.rtmp.event;

import org.red5.io.object.StreamAction;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.event.IEventListener;

/**
 * Represents a stream action occurring on a connection or stream. This event is used to notify an IEventHandler; it is not meant to be sent over the wire to clients.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class StreamActionEvent implements IEvent {

    private final StreamAction action;

    /**
     * <p>Constructor for StreamActionEvent.</p>
     *
     * @param action a {@link org.red5.io.object.StreamAction} object
     */
    public StreamActionEvent(StreamAction action) {
        this.action = action;
    }

    /**
     * <p>getType.</p>
     *
     * @return a Type object
     */
    public Type getType() {
        return Type.STREAM_ACTION;
    }

    /**
     * <p>getObject.</p>
     *
     * @return a {@link java.lang.Object} object
     */
    public Object getObject() {
        return action;
    }

    /**
     * <p>hasSource.</p>
     *
     * @return a boolean
     */
    public boolean hasSource() {
        return false;
    }

    /**
     * <p>getSource.</p>
     *
     * @return a {@link org.red5.server.api.event.IEventListener} object
     */
    public IEventListener getSource() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "StreamActionEvent [action=" + action + "]";
    }

}
