/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.plugin;

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.red5.logging.Red5LoggerFactory;
import org.red5.server.api.plugin.IRed5Plugin;
import org.slf4j.Logger;

/**
 * Central registry for Red5 plug-ins.
 *
 * @author Paul Gregoire
 */
public class PluginRegistry {

    private static Logger log = Red5LoggerFactory.getLogger(PluginRegistry.class, "plugins");

    // keeps track of plug-ins, keyed by plug-in name
    private static volatile ConcurrentMap<String, IRed5Plugin> plugins = new ConcurrentHashMap<>(3, 0.9f, 1);

    /**
     * Returns true if the plug-in is registered.
     *
     * @param plugin
     *            plugin
     * @return true if the plug-in is registered
     */
    public static boolean isRegistered(IRed5Plugin plugin) {
        String pluginName = plugin.getName();
        return plugins.containsKey(pluginName);
    }

    /**
     * Returns true if the plug-in is registered.
     *
     * @param pluginName
     *            plugin name
     * @return true if the plug-in is registered
     */
    public static boolean isRegistered(String pluginName) {
        return plugins.containsKey(pluginName);
    }

    /**
     * Registers a plug-in.
     *
     * @param plugin
     *            plugin
     */
    public static void register(IRed5Plugin plugin) {
        log.debug("Register plugin: {}", plugin);
        String pluginName = plugin.getName();
        if (plugins.containsKey(pluginName)) {
            //get old plugin
            IRed5Plugin oldPlugin = plugins.get(pluginName);
            //if they are not the same shutdown the older one
            if (!plugin.equals(oldPlugin)) {
                try {
                    oldPlugin.doStop();
                } catch (Exception e) {
                    log.warn("Exception caused when stopping old plugin", e);
                }
                //replace old one
                plugins.replace(pluginName, plugin);
            }
        } else {
            plugins.put(pluginName, plugin);
        }
    }

    /**
     * Unregisters a plug-in.
     *
     * @param plugin
     *            plugin
     */
    public static void unregister(IRed5Plugin plugin) {
        log.debug("Unregister plugin: {}", plugin);
        if (plugins.containsValue(plugin)) {
            boolean removed = false;
            for (Entry<String, IRed5Plugin> f : plugins.entrySet()) {
                if (plugin.equals(f.getValue())) {
                    log.debug("Removing {}", plugin);
                    plugins.remove(f.getKey());
                    removed = true;
                    break;
                } else {
                    log.debug("Not equal - {} {}", plugin, f.getValue());
                }
            }
            if (!removed) {
                log.debug("Last try to remove the plugin");
                plugins.remove(plugin.getName());
            }
        } else {
            log.warn("Plugin is not registered {}", plugin);
        }
    }

    /**
     * Returns a plug-in.
     *
     * @param pluginName
     *            plugin name
     * @return requested plug-in matching the name given or null if not found
     */
    public static IRed5Plugin getPlugin(String pluginName) {
        IRed5Plugin plugin = plugins.get(pluginName);
        return plugin;
    }

    /**
     * Shuts down the registry and stops any plug-ins that are found.
     *
     * @throws java.lang.Exception
     *             on exception
     */
    public static void shutdown() throws Exception {
        log.info("Destroying and cleaning up {} plugins", plugins.size());
        // loop through the plugins and stop them
        for (Entry<String, IRed5Plugin> pluginEntry : plugins.entrySet()) {
            IRed5Plugin plugin = pluginEntry.getValue();
            try {
                plugin.doStop();
            } catch (Exception ex) {
                if (plugin != null) {
                    log.warn("Plugin stop failed for: {}", plugin.getName(), ex);
                } else {
                    log.warn("Plugin stop failed", ex);
                }
            }
        }
        plugins.clear();
    }

}
