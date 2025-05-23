package org.red5.server.api.listeners;

import org.red5.server.api.scope.IBasicScope;
import org.red5.server.api.scope.IScope;

/**
 * Adapter class impl for IScopeListener.
 *
 * @author Paul Gregoire
 */
public class ScopeListenerAdapter implements IScopeListener {

    /** {@inheritDoc} */
    @Override
    public void notifyScopeCreated(IScope scope) {
    }

    /** {@inheritDoc} */
    @Override
    public void notifyScopeRemoved(IScope scope) {
    }

    /** {@inheritDoc} */
    @Override
    public void notifyBasicScopeAdded(IBasicScope scope) {
    }

    /** {@inheritDoc} */
    @Override
    public void notifyBasicScopeRemoved(IBasicScope scope) {
    }

}
