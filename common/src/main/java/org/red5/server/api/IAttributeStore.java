/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.api;

import java.util.Map;
import java.util.Set;

import org.red5.server.jmx.mxbeans.AttributeStoreMXBean;

/**
 * Base interface for all API objects with attributes
 *
 * @author The Red5 Project
 * @author Luke Hubbard (luke@codegent.com)
 */
public interface IAttributeStore extends AttributeStoreMXBean {

    /**
     * Get the attribute names. The resulting set will be read-only.
     *
     * @return set containing all attribute names
     */
    Set<String> getAttributeNames();

    /**
     * Get the attributes. The resulting map will be read-only.
     *
     * @return map containing all attributes
     */
    Map<String, Object> getAttributes();

    /**
     * Set an attribute on this object.
     *
     * @param name
     *            the name of the attribute to change
     * @param value
     *            the new value of the attribute
     * @return true if the attribute value changed otherwise false
     */
    boolean setAttribute(String name, Object value);

    /**
     * Set an attribute on this object.
     *
     * @param enm
     *            the enum of the attribute to change
     * @param value
     *            the new value of the attribute
     * @return true if the attribute value was added or changed, otherwise false
     */
    boolean setAttribute(final Enum<?> enm, final Object value);

    /**
     * Set an attribute on this object if it is not already set.
     *
     * @param name
     *            the name of the attribute
     * @param value
     *            the value of the attribute
     * @return previous value or null if it was absent
     */
    Object setAttributeIfAbsent(final String name, final Object value);

    /**
     * Set multiple attributes on this object.
     *
     * @param values
     *            the attributes to set
     * @return true if the attribute values changed otherwise false
     */
    boolean setAttributes(Map<String, Object> values);

    /**
     * Set multiple attributes on this object.
     *
     * @param values
     *            the attributes to set
     * @return true if the attribute values changed otherwise false
     */
    boolean setAttributes(IAttributeStore values);

    /**
     * Return the value for a given attribute.
     *
     * @param name
     *            the name of the attribute to get
     * @return the attribute value or null if the attribute doesn't exist
     */
    Object getAttribute(String name);

    /**
     * Return the value for a given attribute.
     *
     * @param enm
     *            the enum for the attribute to get
     * @return the attribute value or null if the attribute doesn't exist
     */
    Object getAttribute(Enum<?> enm);

    /**
     * Return the value for a given attribute and set it if it doesn't exist.
     *
     * This is a utility function that internally performs the following code: <br>
     *
     * <pre>
     * if (!hasAttribute(name))
     *     setAttribute(name, defaultValue);
     * return getAttribute(name);
     * </pre>
     *
     * @param name
     *            the name of the attribute to get
     * @param defaultValue
     *            the value of the attribute to set if the attribute doesn't exist
     * @return the attribute value
     */
    Object getAttribute(String name, Object defaultValue);

    /**
     * {@inheritDoc}
     *
     * Check the object has an attribute.
     */
    boolean hasAttribute(String name);

    /**
     * Check the object has an attribute.
     *
     * @param enm
     *            the enum for the attribute to check
     * @return true if the attribute exists otherwise false
     */
    boolean hasAttribute(Enum<?> enm);

    /**
     * {@inheritDoc}
     *
     * Remove an attribute.
     */
    boolean removeAttribute(String name);

    /**
     * Remove an attribute.
     *
     * @param enm
     *            the enum of the attribute to remove
     * @return true if the attribute was found and removed otherwise false
     */
    boolean removeAttribute(Enum<?> enm);

    /**
     * Remove all attributes.
     */
    void removeAttributes();

    /**
     * Size of the attribute store.
     *
     * @return count of attributes
     */
    int size();

}
