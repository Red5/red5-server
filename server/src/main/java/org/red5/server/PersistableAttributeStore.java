/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.red5.io.object.Deserializer;
import org.red5.io.object.Input;
import org.red5.io.object.Output;
import org.red5.io.object.Serializer;
import org.red5.server.api.IAttributeStore;
import org.red5.server.api.persistence.IPersistable;
import org.red5.server.api.persistence.IPersistenceStore;

/**
 * Persistable attributes store. Server-side SharedObjects feature based on this class.
 *
 * @author mondain
 */
public class PersistableAttributeStore extends AttributeStore implements IPersistable {

    /**
     * Persistence flag
     */
    protected boolean persistent = true;

    /**
     * Attribute store name
     */
    protected String name;

    /**
     * Attribute store type
     */
    protected String type;

    /**
     * Attribute store path (on local hard drive)
     */
    protected String path;

    /**
     * Last modified Timestamp
     */
    protected long lastModified = -1;

    /**
     * Store object that deals with save/load routines
     */
    protected IPersistenceStore store;

    /**
     * Creates persistable attribute store
     *
     * @param type
     *            Attribute store type
     * @param name
     *            Attribute store name
     * @param path
     *            Attribute store path
     * @param persistent
     *            Whether store is persistent or not
     */
    public PersistableAttributeStore(String type, String name, String path, boolean persistent) {
        super();
        this.type = type;
        this.name = name;
        this.path = path;
        this.persistent = persistent;
    }

    /**
     * Set last modified flag to current system time
     */
    protected void modified() {
        lastModified = System.currentTimeMillis();
        if (store != null) {
            store.save(this);
        }
    }

    /**
     * Check whether object is persistent or not
     *
     * @return true if object is persistent, false otherwise
     */
    public boolean isPersistent() {
        return persistent;
    }

    /**
     * {@inheritDoc}
     *
     * Set for persistence
     */
    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    /**
     * Returns last modification time as timestamp
     *
     * @return Timestamp of last attribute modification
     */
    public long getLastModified() {
        return lastModified;
    }

    /**
     * Return store name
     *
     * @return Store name
     */
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     *
     * Setter for name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Ruturn scope path
     *
     * @return Path
     */
    public String getPath() {
        return path;
    }

    /**
     * {@inheritDoc}
     *
     * Setter for scope path
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Return scope type
     *
     * @return Scope type
     */
    public String getType() {
        return type;
    }

    /**
     * {@inheritDoc}
     *
     * Serializes byte buffer output, storing them to attributes
     */
    public void serialize(Output output) throws IOException {
        Map<String, Object> persistentAttributes = new HashMap<String, Object>();
        for (Map.Entry<String, Object> entry : getAttributes().entrySet()) {
            final String name = entry.getKey();
            if (name.startsWith(IPersistable.TRANSIENT_PREFIX)) {
                continue;
            }

            persistentAttributes.put(name, entry.getValue());
        }
        Serializer.serialize(output, persistentAttributes);
    }

    /**
     * {@inheritDoc}
     *
     * Deserializes data from input to attributes
     */
    public void deserialize(Input input) throws IOException {
        setAttributes(Deserializer.<Map<String, Object>> deserialize(input, Map.class));
    }

    /**
     * {@inheritDoc}
     *
     * Load data from another persistent store
     */
    public void setStore(IPersistenceStore store) {
        if (this.store != null) {
            this.store.notifyClose();
        }
        this.store = store;
        if (store != null) {
            store.load(this);
        }
    }

    /**
     * Return persistent store
     *
     * @return Persistence store
     */
    public IPersistenceStore getStore() {
        return store;
    }

    /** {@inheritDoc} */
    @Override
    public Object getAttribute(String name, Object defaultValue) {
        if (name == null) {
            return null;
        }
        if (defaultValue == null) {
            throw new NullPointerException("the default value may not be null");
        }
        Object result = attributes.putIfAbsent(name, defaultValue);
        if (result == null) {
            // The default value has been set
            modified();
            result = defaultValue;
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean setAttribute(String name, Object value) {
        boolean result = super.setAttribute(name, value);
        if (result && name != null && !name.startsWith(IPersistable.TRANSIENT_PREFIX)) {
            modified();
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean setAttributes(Map<String, Object> values) {
        boolean success = super.setAttributes(values);
        modified();
        return success;
    }

    /** {@inheritDoc} */
    @Override
    public boolean setAttributes(IAttributeStore values) {
        boolean success = super.setAttributes(values);
        modified();
        return success;
    }

    /**
     * {@inheritDoc}
     *
     * Removes attribute
     */
    @Override
    public boolean removeAttribute(String name) {
        boolean result = super.removeAttribute(name);
        if (result && name != null && !name.startsWith(IPersistable.TRANSIENT_PREFIX)) {
            modified();
        }
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * Removes all attributes and sets modified flag
     */
    @Override
    public void removeAttributes() {
        super.removeAttributes();
        modified();
    }
}
