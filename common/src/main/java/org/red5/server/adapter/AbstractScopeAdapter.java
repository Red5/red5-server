/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.adapter;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.red5.server.api.IAttributeStore;
import org.red5.server.api.ICastingAttributeStore;
import org.red5.server.api.IClient;
import org.red5.server.api.IConnection;
import org.red5.server.api.Red5;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.scope.IBasicScope;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.IScopeAware;
import org.red5.server.api.scope.IScopeHandler;
import org.red5.server.api.service.IServiceCall;

/**
 * Base scope handler implementation. Meant to be subclassed.
 *
 * @author mondain
 */
public abstract class AbstractScopeAdapter implements IScopeAware, IScopeHandler, ICastingAttributeStore {

    /**
     * Wrapped scope
     */
    protected volatile IScope scope;

    /**
     * Can start flag.
     *
     * <code>
     * true
     * </code>
     *
     * if scope is ready to be activated,
     *
     * <code>
     * false
     * </code>
     *
     * otherwise
     */
    private boolean canStart = true;

    /**
     * Can connect flag.
     *
     * <code>
     * true
     * </code>
     *
     * if connections to scope are allowed,
     *
     * <code>
     * false
     * </code>
     *
     * otherwise
     */
    private boolean canConnect;

    /**
     * Can join flag.
     *
     * <code>
     * true
     * </code>
     *
     * if scope may be joined by users,
     *
     * <code>
     * false
     * </code>
     *
     * otherwise
     */
    private boolean canJoin = true;

    /**
     * Can call service flag.
     *
     * <code>
     * true
     * </code>
     *
     * if remote service calls are allowed for the scope,
     *
     * <code>
     * false
     * </code>
     *
     * otherwise
     */
    private boolean canCallService = true;

    /**
     * Can add child scope flag.
     *
     * <code>
     * true
     * </code>
     *
     * if scope is allowed to add child scopes,
     *
     * <code>
     * false
     * </code>
     *
     * otherwise
     */
    private boolean canAddChildScope = true;

    /**
     * Can handle event flag.
     *
     * <code>
     * true
     * </code>
     *
     * if events handling is allowed,
     *
     * <code>
     * false
     * </code>
     *
     * otherwise
     */
    private boolean canHandleEvent = true;

    /**
     * Setter for wrapped scope
     *
     * @param scope
     *            Scope to wrap
     */
    @Override
    public void setScope(IScope scope) {
        this.scope = scope;
    }

    /**
     * Getter for wrapped scope
     *
     * @return Wrapped scope
     */
    @Override
    public IScope getScope() {
        return scope;
    }

    /**
     * Setter for can start flag.
     *
     * @param canStart
     *            <code>
     * true
     * </code>
     *
     *            if scope is ready to be activated,
     *
     *            <code>
     * false
     * </code>
     *
     *            otherwise
     */
    public void setCanStart(boolean canStart) {
        this.canStart = canStart;
    }

    /**
     * Setter for can call service flag
     *
     * @param canCallService
     *            <code>
     * true
     * </code>
     *
     *            if remote service calls are allowed for the scope,
     *
     *            <code>
     * false
     * </code>
     *
     *            otherwise
     */
    public void setCanCallService(boolean canCallService) {
        //log.trace("setCanCallService: {}", canCallService);
        this.canCallService = canCallService;
    }

    /**
     * Setter for can connect flag
     *
     * @param canConnect
     *            <code>
     * true
     * </code>
     *
     *            if connections to scope are allowed,
     *
     *            <code>
     * false
     * </code>
     *
     *            otherwise
     */
    public void setCanConnect(boolean canConnect) {
        this.canConnect = canConnect;
    }

    /**
     * Setter for 'can join' flag
     *
     * @param canJoin
     *            <code>
     * true
     * </code>
     *
     *            if scope may be joined by users,
     *
     *            <code>
     * false
     * </code>
     *
     *            otherwise
     */
    public void setJoin(boolean canJoin) {
        this.canJoin = canJoin;
    }

    /** {@inheritDoc} */
    public boolean start(IScope scope) {
        return canStart;
    }

    /** {@inheritDoc} */
    public void stop(IScope scope) {
        // nothing
    }

    /**
     * {@inheritDoc}
     *
     * @param conn a {@link org.red5.server.api.IConnection} object
     * @param scope a {@link org.red5.server.api.scope.IScope} object
     * @param params an array of {@link java.lang.Object} objects
     * @return a boolean
     */
    public boolean connect(IConnection conn, IScope scope, Object[] params) {
        return canConnect;
    }

    /** {@inheritDoc} */
    public void disconnect(IConnection conn, IScope scope) {
        // nothing
    }

    /** {@inheritDoc} */
    public boolean join(IClient client, IScope scope) {
        return canJoin;
    }

    /** {@inheritDoc} */
    public void leave(IClient client, IScope scope) {
        // nothing
    }

    /** {@inheritDoc} */
    public boolean serviceCall(IConnection conn, IServiceCall call) {
        //log.trace("serviceCall - canCallService: {} scope: {} method: {}", canCallService, conn.getScope().getName(), call.getServiceMethodName());
        return canCallService;
    }

    /** {@inheritDoc} */
    public boolean addChildScope(IBasicScope scope) {
        return canAddChildScope;
    }

    /** {@inheritDoc} */
    public void removeChildScope(IBasicScope scope) {
    }

    /** {@inheritDoc} */
    public boolean handleEvent(IEvent event) {
        return canHandleEvent;
    }

    /**
     * Calls the checkBandwidth method on the current client.
     *
     * @param o
     *            Object passed from Flash, not used at the moment
     */
    public void checkBandwidth(Object o) {
        //Incoming object should be null
        IClient client = Red5.getConnectionLocal().getClient();
        if (client != null) {
            client.checkBandwidth();
        }
    }

    /**
     * Calls the checkBandwidthUp method on the current client.
     *
     * @param params
     *            Object passed from Flash
     * @return bandwidth results map
     */
    public Map<String, Object> checkBandwidthUp(Object[] params) {
        //Incoming object should be null
        IClient client = Red5.getConnectionLocal().getClient();
        if (client != null) {
            return client.checkBandwidthUp(params);
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Object getAttribute(String name) {
        return scope.getAttribute(name);
    }

    /** {@inheritDoc} */
    @Override
    public Object getAttribute(Enum<?> enm) {
        return getAttribute(enm.name());
    }

    /** {@inheritDoc} */
    @Override
    public Object getAttribute(String name, Object defaultValue) {
        Object value = scope.getAttribute(name);
        if (value == null) {
            value = defaultValue;
        }
        return value;
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> getAttributeNames() {
        return scope.getAttributeNames();
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> getAttributes() {
        return scope.getAttributes();
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasAttribute(String name) {
        return scope.hasAttribute(name);
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasAttribute(Enum<?> enm) {
        return hasAttribute(enm.name());
    }

    /** {@inheritDoc} */
    @Override
    public boolean removeAttribute(String name) {
        return scope.removeAttribute(name);
    }

    /** {@inheritDoc} */
    @Override
    public boolean removeAttribute(Enum<?> enm) {
        return removeAttribute(enm.name());
    }

    /** {@inheritDoc} */
    @Override
    public void removeAttributes() {
        scope.removeAttributes();
    }

    /** {@inheritDoc} */
    @Override
    public boolean setAttribute(String name, Object value) {
        return scope.setAttribute(name, value);
    }

    /** {@inheritDoc} */
    @Override
    public boolean setAttribute(Enum<?> enm, Object value) {
        return setAttribute(enm.name(), value);
    }

    /** {@inheritDoc} */
    @Override
    public boolean setAttributes(IAttributeStore attributes) {
        int successes = 0;
        for (Map.Entry<String, Object> entry : attributes.getAttributes().entrySet()) {
            if (scope.setAttribute(entry.getKey(), entry.getValue())) {
                successes++;
            }
        }
        // expect every value to have been added
        return (successes == attributes.size());
    }

    /**
     * {@inheritDoc}
     *
     * @param attributes a {@link java.util.Map} object
     * @return a boolean
     */
    @Override
    public boolean setAttributes(Map<String, Object> attributes) {
        int successes = 0;
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (scope.setAttribute(entry.getKey(), entry.getValue())) {
                successes++;
            }
        }
        // expect every value to have been added
        return (successes == attributes.size());
    }

    /** {@inheritDoc} */
    @Override
    public Object setAttributeIfAbsent(String name, Object value) {
        return scope.setAttributeIfAbsent(name, value);
    }

    /** {@inheritDoc} */
    @Override
    public Boolean getBoolAttribute(String name) {
        return scope.getBoolAttribute(name);
    }

    /** {@inheritDoc} */
    @Override
    public Byte getByteAttribute(String name) {
        return scope.getByteAttribute(name);
    }

    /** {@inheritDoc} */
    @Override
    public Double getDoubleAttribute(String name) {
        return scope.getDoubleAttribute(name);
    }

    /** {@inheritDoc} */
    @Override
    public Integer getIntAttribute(String name) {
        return scope.getIntAttribute(name);
    }

    /** {@inheritDoc} */
    @Override
    public List<?> getListAttribute(String name) {
        return scope.getListAttribute(name);
    }

    /** {@inheritDoc} */
    @Override
    public Long getLongAttribute(String name) {
        return scope.getLongAttribute(name);
    }

    /** {@inheritDoc} */
    @Override
    public Map<?, ?> getMapAttribute(String name) {
        return scope.getMapAttribute(name);
    }

    /** {@inheritDoc} */
    @Override
    public Set<?> getSetAttribute(String name) {
        return scope.getSetAttribute(name);
    }

    /** {@inheritDoc} */
    @Override
    public Short getShortAttribute(String name) {
        return scope.getShortAttribute(name);
    }

    /** {@inheritDoc} */
    @Override
    public String getStringAttribute(String name) {
        return scope.getStringAttribute(name);
    }

    /** {@inheritDoc} */
    @Override
    public int size() {
        return scope != null ? scope.getAttributeNames().size() : 0;
    }

}
