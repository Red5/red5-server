package org.red5.server.api.listeners;

import org.red5.server.api.scope.IBasicScope;
import org.red5.server.api.scope.IScope;

/**
 * Adapter class impl for IScopeListener.
 *
 * @author Paul Gregoire
 *
 */
public class ScopeListenerAdapter implements IScopeListener {

    @Override
    public void notifyScopeCreated(IScope scope) {
    }

    @Override
    public void notifyScopeRemoved(IScope scope) {
    }

    @Override
    public void notifyBasicScopeAdded(IBasicScope scope) {
    }

    @Override
    public void notifyBasicScopeRemoved(IBasicScope scope) {
    }

}
