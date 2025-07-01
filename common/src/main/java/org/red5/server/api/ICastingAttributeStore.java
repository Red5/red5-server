/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.api;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Attribute storage with automatic object casting support.
 *
 * @author The Red5 Project
 * @author Joachim Bauch (jojo@struktur.de)
 */
public interface ICastingAttributeStore extends IAttributeStore {

    /**
     * Get Boolean attribute by name
     *
     * @param name
     *            Attribute name
     * @return Attribute
     */
    Boolean getBoolAttribute(String name);

    /**
     * Get Byte attribute by name
     *
     * @param name
     *            Attribute name
     * @return Attribute
     */
    Byte getByteAttribute(String name);

    /**
     * Get Double attribute by name
     *
     * @param name
     *            Attribute name
     * @return Attribute
     */
    Double getDoubleAttribute(String name);

    /**
     * Get Integer attribute by name
     *
     * @param name
     *            Attribute name
     * @return Attribute
     */
    Integer getIntAttribute(String name);

    /**
     * Get List attribute by name
     *
     * @param name
     *            Attribute name
     * @return Attribute
     */
    List<?> getListAttribute(String name);

    /**
     * Get boolean attribute by name
     *
     * @param name
     *            Attribute name
     * @return Attribute
     */
    Long getLongAttribute(String name);

    /**
     * Get Long attribute by name
     *
     * @param name
     *            Attribute name
     * @return Attribute
     */
    Map<?, ?> getMapAttribute(String name);

    /**
     * Get Set attribute by name
     *
     * @param name
     *            Attribute name
     * @return Attribute
     */
    Set<?> getSetAttribute(String name);

    /**
     * Get Short attribute by name
     *
     * @param name
     *            Attribute name
     * @return Attribute
     */
    Short getShortAttribute(String name);

    /**
     * Get String attribute by name
     *
     * @param name
     *            Attribute name
     * @return Attribute
     */
    String getStringAttribute(String name);

}
