/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.classloading;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * An almost trivial no-fuss implementation of a class loader following the child-first delegation model. <i>Based on code from Ceki Gulcu</i>
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public final class ChildFirstClassLoader extends URLClassLoader {

    private ClassLoader parent;

    private ClassLoader parentParent;

    private ClassLoader system;

    /**
     * <p>Constructor for ChildFirstClassLoader.</p>
     *
     * @param urls an array of {@link java.net.URL} objects
     */
    public ChildFirstClassLoader(URL[] urls) {
        super(urls);
        this.parent = super.getParent();
        system = getSystemClassLoader();
        // if we have a parent of the parent and its not the system classloader
        parentParent = this.parent.getParent() != system ? this.parent.getParent() : null;
        dumpClassLoaderNames();
    }

    /**
     * <p>Constructor for ChildFirstClassLoader.</p>
     *
     * @param urls an array of {@link java.net.URL} objects
     * @param parent a {@link java.lang.ClassLoader} object
     */
    public ChildFirstClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
        this.parent = parent;
        system = getSystemClassLoader();
        if (parent != null) {
            // if we have a parent of the parent and its not the system
            // classloader
            parentParent = this.parent.getParent() != system ? this.parent.getParent() : null;
        }
        dumpClassLoaderNames();
    }

    private void dumpClassLoaderNames() {
        System.out.printf("[ChildFirstClassLoader] Classloaders:\nSystem %s\nParents Parent %s\nParent %s\nThis class %s\nTCL %s\n\n", system, parentParent, this.parent, ChildFirstClassLoader.class.getClassLoader(), Thread.currentThread().getContextClassLoader());
    }

    /** {@inheritDoc} */
    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return loadClass(name, false);
    }

    /**
     * {@inheritDoc}
     *
     * We override the parent-first behavior established by java.lang.Classloader.
     * <p>
     * The implementation is surprisingly straightforward.
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {

        // First, check if the class has already been loaded
        Class<?> c = findLoadedClass(name);

        // if not loaded, search the local (child) resources
        if (c == null) {
            try {
                c = findClass(name);
            } catch (ClassNotFoundException cnfe) {
                // ignore
            }
        }

        // If we could not find it, delegate to parent
        // Note that we do not attempt to catch any ClassNotFoundException
        if (c == null) {
            try {
                c = this.parent.loadClass(name);
            } catch (Exception e) {
                // ignore the Spring "BeanInfo" class lookup errors
                // if (e.getMessage().indexOf("BeanInfo") == -1) {
                // log.warn("Exception {}", e);
                // }
            }
            if (c == null && parentParent != null) {
                try {
                    c = parentParent.loadClass(name);
                } catch (Exception e) {
                    // if (e.getMessage().indexOf("BeanInfo") == -1) {
                    // log.warn("Exception {}", e);
                    // }
                }
            }
            if (c == null) {
                try {
                    c = system.loadClass(name);
                } catch (Exception e) {
                    // if (e.getMessage().indexOf("BeanInfo") == -1) {
                    // log.warn("Exception {}", e);
                    // }
                }
            }
        }

        // resolve if requested
        if (resolve) {
            resolveClass(c);
        }

        return c;
    }

    /**
     * {@inheritDoc}
     *
     * Override the parent-first resource loading model established by java.lang.Classloader with child-first behavior.
     */
    @Override
    public URL getResource(String name) {
        URL url = findResource(name);
        // If local search failed, delegate to parent
        if (url == null) {
            url = this.parent.getResource(name);
        }
        return url;
    }
}
