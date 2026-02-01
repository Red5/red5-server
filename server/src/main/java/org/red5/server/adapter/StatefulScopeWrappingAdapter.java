/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.adapter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.red5.server.api.IClient;
import org.red5.server.api.IConnection;
import org.red5.server.api.IContext;
import org.red5.server.api.scope.IScope;
import org.red5.server.plugin.PluginDescriptor;
import org.springframework.core.io.Resource;

/**
 * StatefulScopeWrappingAdapter class wraps stateful IScope functionality. That is, it has attributes that you can work with, subscopes, associated resources and connections.
 *
 * @author mondain
 */
public class StatefulScopeWrappingAdapter extends AbstractScopeAdapter {

    /**
     * List of plug-in descriptors
     */
    protected List<PluginDescriptor> plugins;

    /**
     * Returns any plug-ins descriptors added
     *
     * @return plug-in descriptor list
     */
    public List<PluginDescriptor> getPlugins() {
        return plugins;
    }

    /**
     * Adds a list of plug-in descriptors
     *
     * @param plugins
     *            plugins
     */
    public void setPlugins(List<PluginDescriptor> plugins) {
        this.plugins = plugins;
    }

    /**
     * Creates child scope
     *
     * @param name
     *            Child scope name
     * @return true on success, false otherwise
     */
    public boolean createChildScope(String name) {
        if (!scope.hasChildScope(name)) {
            return scope.createChildScope(name);
        }
        return false;
    }

    /**
     * Return child scope
     *
     * @param name
     *            Child scope name
     * @return Child scope with given name
     */
    public IScope getChildScope(String name) {
        return scope.getScope(name);
    }

    /**
     * Iterator for child scope names
     *
     * @return collection of child scope names
     */
    public Set<String> getChildScopeNames() {
        return scope.getScopeNames();
    }

    /**
     * Getter for set of clients
     *
     * @return Set of clients
     */
    public Set<IClient> getClients() {
        return scope.getClients();
    }

    /**
     * Returns all connections in the scope
     *
     * @return collection of a set of connections
     */
    public Collection<Set<IConnection>> getConnections() {
        return scope.getConnections();
    }

    /**
     * Returns all connections for a given client
     *
     * @param client
     *            client
     * @return set of connections
     */
    @SuppressWarnings("deprecation")
    public Set<IConnection> lookupConnections(IClient client) {
        return scope.lookupConnections(client);
    }

    /**
     * Getter for context
     *
     * @return Value for context
     */
    public IContext getContext() {
        return scope.getContext();
    }

    /**
     * Getter for depth
     *
     * @return Value for depth
     */
    public int getDepth() {
        return scope.getDepth();
    }

    /**
     * Getter for name
     *
     * @return Value for name
     */
    public String getName() {
        return scope.getName();
    }

    /**
     * Return parent scope
     *
     * @return Parent scope
     */
    public IScope getParent() {
        return scope.getParent();
    }

    /**
     * Getter for stateful scope path
     *
     * @return Value for path
     */
    public String getPath() {
        return scope.getPath();
    }

    /**
     * Whether this scope has a child scope with given name
     *
     * @param name
     *            Child scope name
     * @return true if it does have it, false otherwise
     */
    public boolean hasChildScope(String name) {
        return scope.hasChildScope(name);
    }

    /**
     * If this scope has a parent
     *
     * @return true if this scope has a parent scope, false otherwise
     */
    public boolean hasParent() {
        return scope.hasParent();
    }

    /**
     * Returns array of resources (as Spring core Resource class instances)
     *
     * @param pattern
     *            Resource pattern
     * @return Returns array of resources
     * @throws java.io.IOException
     *             I/O exception
     */
    @SuppressWarnings("null")
    public Resource[] getResources(String pattern) throws IOException {
        return scope.getResources(pattern);
    }

    /**
     * Return resource by name
     *
     * @param path
     *            Resource name
     * @return Resource with given name
     */
    @SuppressWarnings("null")
    public Resource getResource(String path) {
        return scope.getResource(path);
    }

}
