package org.red5.server.api.listeners;

import java.beans.PropertyChangeEvent;

import org.red5.server.api.IConnection;

/**
 * Abstract implementation / adapter for connection listeners.
 * 
 * @author Paul Gregoire (mondain@gmail.com)
 *
 */
public abstract class AbstractConnectionListener implements IConnectionListener {

    @Override
    public abstract void notifyConnected(IConnection conn);

    @Override
    public abstract void notifyDisconnected(IConnection conn);

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // empty implementation to prevent breaking previous apps. 
    }

}
