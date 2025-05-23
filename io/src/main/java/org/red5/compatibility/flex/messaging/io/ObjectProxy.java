/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.compatibility.flex.messaging.io;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.red5.io.amf3.IDataInput;
import org.red5.io.amf3.IDataOutput;
import org.red5.io.amf3.IExternalizable;

/**
 * Flex <code>ObjectProxy</code> compatibility class.
 *
 * @see <a href="http://help.adobe.com/en_US/FlashPlatform/reference/actionscript/3/mx/utils/ObjectProxy.html">ObjectProxy</a>
 * @author The Red5 Project
 * @author Joachim Bauch (jojo@struktur.de)
 * @param <T>
 *            type
 * @param <V>
 *            value
 */
public class ObjectProxy<T, V> implements Map<T, V>, IExternalizable {

    private String uid;

    private Object type;

    /** The proxied object. */
    private Map<T, V> item;

    /**
     * Create new empty proxy.
     */
    public ObjectProxy() {
        this(new HashMap<T, V>());
    }

    /**
     * Create proxy for given object.
     *
     * @param item
     *            object to proxy
     */
    public ObjectProxy(Map<T, V> item) {
        this.item = new HashMap<T, V>(item);
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("unchecked")
    public void readExternal(IDataInput input) {
        item = (Map<T, V>) input.readObject();
    }

    /** {@inheritDoc} */
    @Override
    public void writeExternal(IDataOutput output) {
        output.writeObject(item);
    }

    /**
     * {@inheritDoc}
     *
     * Return string representation of the proxied object.
     */
    @Override
    public String toString() {
        return item.toString();
    }

    /** {@inheritDoc} */
    @Override
    public void clear() {
        item.clear();
    }

    /**
     * {@inheritDoc}
     *
     * Check if proxied object has a given property.
     */
    @Override
    public boolean containsKey(Object name) {
        return item.containsKey(name);
    }

    /** {@inheritDoc} */
    @Override
    public boolean containsValue(Object value) {
        return item.containsValue(value);
    }

    /** {@inheritDoc} */
    @Override
    public Set<Entry<T, V>> entrySet() {
        return Collections.unmodifiableSet(item.entrySet());
    }

    /**
     * {@inheritDoc}
     *
     * Return the value of a property.
     */
    @Override
    public V get(Object name) {
        return item.get(name);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEmpty() {
        return item.isEmpty();
    }

    /** {@inheritDoc} */
    @Override
    public Set<T> keySet() {
        return item.keySet();
    }

    /**
     * {@inheritDoc}
     *
     * Change a property of the proxied object.
     */
    @Override
    public V put(T name, V value) {
        return item.put(name, value);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void putAll(Map values) {
        item.putAll(values);
    }

    /**
     * {@inheritDoc}
     *
     * Remove a property from the proxied object.
     */
    @Override
    public V remove(Object name) {
        return item.remove(name);
    }

    /** {@inheritDoc} */
    @Override
    public int size() {
        return item.size();
    }

    /** {@inheritDoc} */
    @Override
    public Collection<V> values() {
        return Collections.unmodifiableCollection(item.values());
    }

    /**
     * <p>Getter for the field <code>uid</code>.</p>
     *
     * @return the uid
     */
    public String getUid() {
        return uid;
    }

    /**
     * <p>Setter for the field <code>uid</code>.</p>
     *
     * @param uid
     *            the uid to set
     */
    public void setUid(String uid) {
        this.uid = uid;
    }

    /**
     * <p>Getter for the field <code>type</code>.</p>
     *
     * @return the type
     */
    public Object getType() {
        return type;
    }

    /**
     * <p>Setter for the field <code>type</code>.</p>
     *
     * @param type
     *            the type to set
     */
    public void setType(Object type) {
        this.type = type;
    }

    // TODO: implement other ObjectProxy methods

}
