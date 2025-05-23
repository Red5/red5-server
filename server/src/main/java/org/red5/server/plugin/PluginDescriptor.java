/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.plugin;

import java.util.Map;

/**
 * Simple descriptor for plug-ins.
 *
 * @author Paul Gregoire
 */
public final class PluginDescriptor {

    private String pluginName;

    private String pluginType;

    private String method;

    private String methodReturnType;

    private Map<String, Object> properties;

    /**
     * <p>Getter for the field <code>pluginName</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getPluginName() {
        return pluginName;
    }

    /**
     * <p>Setter for the field <code>pluginName</code>.</p>
     *
     * @param pluginName a {@link java.lang.String} object
     */
    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
    }

    /**
     * <p>Getter for the field <code>pluginType</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getPluginType() {
        return pluginType;
    }

    /**
     * <p>Setter for the field <code>pluginType</code>.</p>
     *
     * @param pluginType a {@link java.lang.String} object
     */
    public void setPluginType(String pluginType) {
        this.pluginType = pluginType;
    }

    /**
     * <p>Getter for the field <code>method</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getMethod() {
        return method;
    }

    /**
     * <p>Setter for the field <code>method</code>.</p>
     *
     * @param method a {@link java.lang.String} object
     */
    public void setMethod(String method) {
        this.method = method;
    }

    /**
     * <p>Getter for the field <code>methodReturnType</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getMethodReturnType() {
        return methodReturnType;
    }

    /**
     * <p>Setter for the field <code>methodReturnType</code>.</p>
     *
     * @param methodReturnType a {@link java.lang.String} object
     */
    public void setMethodReturnType(String methodReturnType) {
        this.methodReturnType = methodReturnType;
    }

    /**
     * <p>Getter for the field <code>properties</code>.</p>
     *
     * @return a {@link java.util.Map} object
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * <p>Setter for the field <code>properties</code>.</p>
     *
     * @param properties a {@link java.util.Map} object
     */
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

}
