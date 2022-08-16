/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2016 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.openmbean.CompositeData;

import org.red5.io.utils.ConversionUtils;
import org.red5.server.api.IAttributeStore;
import org.red5.server.api.ICastingAttributeStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AttributeStore implements ICastingAttributeStore {

    protected static Logger log = LoggerFactory.getLogger(AttributeStore.class);

    /**
     * Map for attributes with initialCapacity = 1, loadFactor = .9, concurrencyLevel = (# of processors)
     */
    protected Map<String, Object> attributes = new ConcurrentHashMap<>(1, 0.5f, Runtime.getRuntime().availableProcessors());

    /**
     * Creates empty attribute store. Object is not associated with a persistence storage.
     */
    public AttributeStore() {
    }

    /**
     * Creates attribute store with initial values. Object is not associated with a persistence storage.
     *
     * @param values
     *            map
     */
    public AttributeStore(Map<String, Object> values) {
        setAttributes(values);
    }

    /**
     * Creates attribute store with initial values. Object is not associated with a persistence storage.
     *
     * @param values
     *            map
     */
    public AttributeStore(IAttributeStore values) {
        setAttributes(values);
    }

    /**
     * Filter
     *
     * <pre>
     * null
     * </pre>
     *
     * keys and values from given map.
     *
     * @param values
     *            the map to filter
     * @return filtered map
     */
    protected Map<String, Object> filterNull(Map<String, Object> values) {
        Map<String, Object> result = new HashMap<>();
        values.forEach((key, value) -> {
            if (key != null && value != null) {
                result.put(key, value);
            }
        });
        return result;
    }

    /** {@inheritDoc} */
    public boolean hasAttribute(String name) {
        if (name == null) {
            return false;
        }
        return attributes.containsKey(name);
    }

    /** {@inheritDoc} */
    public boolean hasAttribute(Enum<?> enm) {
        return hasAttribute(enm.name());
    }

    /** {@inheritDoc} */
    public Set<String> getAttributeNames() {
        return Collections.unmodifiableSet(attributes.keySet());
    }

    /** {@inheritDoc} */
    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    /** {@inheritDoc} */
    public Object getAttribute(String name) {
        if (name == null) {
            return null;
        }
        return attributes.get(name);
    }

    /** {@inheritDoc} */
    public Object getAttribute(Enum<?> enm) {
        return getAttribute(enm.name());
    }

    /** {@inheritDoc} */
    public Object getAttribute(String name, Object defaultValue) {
        if (name == null) {
            return null;
        }
        if (defaultValue == null) {
            throw new NullPointerException("the default value may not be null");
        }
        Object result = attributes.put(name, defaultValue);
        // if no previous value result will be null
        if (result == null) {
            // use the default value
            result = defaultValue;
        }
        return result;
    }

    /**
     * Set an attribute on this object.
     *
     * @param name
     *            the name of the attribute to change
     * @param value
     *            the new value of the attribute
     * @return true if the attribute value was added or changed, otherwise false
     */
    public boolean setAttribute(final String name, final Object value) {
        log.trace("setAttribute({}, {})", name, value);
        boolean result = false;
        if (name != null && value != null) {
            // get previous value
            final Object previous = attributes.putIfAbsent(name, value);
            // previous will be null if the attribute didn't exist and if it does it will equal the previous value
            if (previous != null) {
                // if the value is a collection, check the elements for modification
                if (value instanceof Collection) {
                    Collection<?> prevCollection = (Collection<?>) previous;
                    Collection<?> newCollection = (Collection<?>) value;
                    for (Object newCollectionEntry : newCollection) {
                        int freq = Collections.frequency(prevCollection, newCollectionEntry);
                        // first element that does not exist in the previous collection will trigger the modified result
                        if (freq == 0) {
                            result = true;
                            break;
                        }
                    }
                } else if (value instanceof Map) {
                    Map<?, ?> prevMap = (Map<?, ?>) previous;
                    Map<?, ?> newMap = (Map<?, ?>) value;
                    // check key differences first
                    if (!prevMap.keySet().containsAll(newMap.keySet())) {
                        result = true;
                    } else {
                        // check entries
                        for (Entry<?, ?> newMapEntry : newMap.entrySet()) {
                            Object prevValue = prevMap.get(newMapEntry.getKey());
                            if (prevValue == null) {
                                result = true;
                                break;
                            } else {
                                if (!prevValue.equals(newMapEntry.getValue())) {
                                    result = true;
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    // whether or not the new incoming value is "equal" to the previous value; not equal equates to "modified"
                    result = !value.equals(previous);
                    if (log.isTraceEnabled()) {
                        log.trace("Equality check - modified: {} previous: {} new: {}", result, previous, value);
                        log.trace("Class: {} {}", previous.getClass(), value.getClass());
                    }
                    // if the new value is not equal to the current / previous value and its a base-type or array, replace it
                    if (result && ConversionUtils.isBaseTypeOrArray(previous)) {
                        if (attributes.replace(name, previous, value)) {
                            log.trace("Value replaced");
                        } else {
                            log.trace("Value replacement failed");
                        }
                    }
                }
            } else {
                result = true;
            }
            if (log.isTraceEnabled()) {
                log.trace("{}", attributes);
            }
        }
        return result;
    }

    /** {@inheritDoc} */
    public boolean setAttribute(final Enum<?> enm, final Object value) {
        return setAttribute(enm.name(), value);
    }

    /** {@inheritDoc} */
    public boolean setAttributes(Map<String, Object> values) {
        attributes.putAll(filterNull(values));
        // put all doesn't return a boolean so we assume all were added
        return true;
    }

    /** {@inheritDoc} */
    public boolean setAttributes(IAttributeStore values) {
        return setAttributes(values.getAttributes());
    }

    /** {@inheritDoc} */
    public boolean removeAttribute(String name) {
        if (name != null) {
            return (attributes.remove(name) != null);
        }
        return false;
    }

    /** {@inheritDoc} */
    public boolean removeAttribute(Enum<?> enm) {
        return removeAttribute(enm.name());
    }

    /**
     * Remove all attributes.
     */
    public void removeAttributes() {
        attributes.clear();
    }

    /** {@inheritDoc} */
    public int size() {
        return attributes != null ? attributes.size() : 0;
    }

    /**
     * Get Boolean attribute by name
     *
     * @param name
     *            Attribute name
     * @return Attribute
     */
    public Boolean getBoolAttribute(String name) {
        return (Boolean) getAttribute(name);
    }

    /**
     * Get Byte attribute by name
     *
     * @param name
     *            Attribute name
     * @return Attribute
     */
    public Byte getByteAttribute(String name) {
        return (Byte) getAttribute(name);
    }

    /**
     * Get Double attribute by name
     *
     * @param name
     *            Attribute name
     * @return Attribute
     */
    public Double getDoubleAttribute(String name) {
        return (Double) getAttribute(name);
    }

    /**
     * Get Integer attribute by name
     *
     * @param name
     *            Attribute name
     * @return Attribute
     */
    public Integer getIntAttribute(String name) {
        return (Integer) getAttribute(name);
    }

    /**
     * Get List attribute by name
     *
     * @param name
     *            Attribute name
     * @return Attribute
     */
    public List<?> getListAttribute(String name) {
        return (List<?>) getAttribute(name);
    }

    /**
     * Get boolean attribute by name
     *
     * @param name
     *            Attribute name
     * @return Attribute
     */
    public Long getLongAttribute(String name) {
        return (Long) getAttribute(name);
    }

    /**
     * Get Long attribute by name
     *
     * @param name
     *            Attribute name
     * @return Attribute
     */
    public Map<?, ?> getMapAttribute(String name) {
        return (Map<?, ?>) getAttribute(name);
    }

    /**
     * Get Set attribute by name
     *
     * @param name
     *            Attribute name
     * @return Attribute
     */
    public Set<?> getSetAttribute(String name) {
        return (Set<?>) getAttribute(name);
    }

    /**
     * Get Short attribute by name
     *
     * @param name
     *            Attribute name
     * @return Attribute
     */
    public Short getShortAttribute(String name) {
        return (Short) getAttribute(name);
    }

    /**
     * Get String attribute by name
     *
     * @param name
     *            Attribute name
     * @return Attribute
     */
    public String getStringAttribute(String name) {
        return (String) getAttribute(name);
    }

    /**
     * Allows for reconstruction via CompositeData.
     *
     * @param cd
     *            composite data
     * @return AttributeStore class instance
     */
    @SuppressWarnings("unchecked")
    public static AttributeStore from(CompositeData cd) {
        AttributeStore instance = null;
        if (cd.containsKey("attributes")) {
            Object cn = cd.get("attributes");
            if (cn != null) {
                if (cn instanceof IAttributeStore) {
                    instance = new AttributeStore((IAttributeStore) cn);
                } else if (cn instanceof Map) {
                    instance = new AttributeStore((Map<String, Object>) cn);
                }
            } else {
                instance = new AttributeStore();
            }
        } else {
            instance = new AttributeStore();
        }
        return instance;
    }

    /*
     * @SuppressWarnings("serial") private final class ConcurrentAttributesMap<K, V> extends ConcurrentHashMap<K, V> { ConcurrentAttributesMap(int size) { super(size, 0.75f, 1); }
     * @Override public V get(Object key) { if (log.isTraceEnabled()) { log.trace("get key: {}", key); } return super.get(key); }
     * @Override public V put(K key, V value) { if (log.isTraceEnabled()) { log.trace("put key: {} value: {}", key, value); } return super.put(key, value); }
     * @Override public V putIfAbsent(K key, V value) { if (log.isTraceEnabled()) { log.trace("putIfAbsent key: {} value: {}", key, value); } return super.putIfAbsent(key, value); }
     * @Override public void putAll(Map<? extends K, ? extends V> m) { if (log.isTraceEnabled()) { log.trace("putAll map: {}", m); } super.putAll(m); }
     * @Override public boolean replace(K key, V oldValue, V newValue) { if (log.isTraceEnabled()) { log.trace("replace key: {} old value: {} new value: {}", new Object[] { key,
     * oldValue, newValue }); } return super.replace(key, oldValue, newValue); }
     * @Override public V replace(K key, V value) { if (log.isTraceEnabled()) { log.trace("replace key: {} value: {}", key, value); } return super.replace(key, value); } }
     */

}
