/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.so;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.red5.server.api.IConnection;
import org.red5.server.api.persistence.IPersistable;
import org.red5.server.api.persistence.IPersistenceStore;
import org.red5.server.api.persistence.PersistenceUtils;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.ScopeType;
import org.red5.server.api.so.ISharedObject;
import org.red5.server.api.so.ISharedObjectService;
import org.red5.server.persistence.RamPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Shared object service
 *
 * @author mondain
 */
public class SharedObjectService implements ISharedObjectService {

    private Logger log = LoggerFactory.getLogger(SharedObjectService.class);

    /**
     * Persistence store prefix
     */
    private static final String SO_PERSISTENCE_STORE = IPersistable.TRANSIENT_PREFIX + "_SO_PERSISTENCE_STORE_";

    /**
     * Transient store prefix
     */
    private static final String SO_TRANSIENT_STORE = IPersistable.TRANSIENT_PREFIX + "_SO_TRANSIENT_STORE_";

    /**
     * Connection attribute holding the number of shared objects created by that connection
     */
    private static final String SO_CREATED_COUNT = IPersistable.TRANSIENT_PREFIX + "_SO_CREATED_COUNT_";

    /**
     * Default maximum number of name segments; persistent names map onto nested directories.
     */
    public static final int DEFAULT_MAX_NAME_DEPTH = 16;

    /**
     * Service used to provide updates / notifications.
     */
    private static ThreadPoolTaskScheduler scheduler;

    /**
     * Maximum number of shared objects in one scope; 0 means unlimited.
     */
    private int maxSharedObjectsPerScope;

    /**
     * Maximum number of attributes a client may create in one shared object; 0 means unlimited.
     */
    private int maxAttributesPerSharedObject;

    /**
     * Maximum number of '/' separated segments in a shared object name; 0 means unlimited.
     */
    private int maxNameDepth = DEFAULT_MAX_NAME_DEPTH;

    /**
     * Whether clients may create persistent shared objects.
     */
    private boolean allowClientPersistent = true;

    /**
     * Maximum number of shared objects a single connection may create; 0 means unlimited.
     */
    private int maxSharedObjectsPerConnection;

    /**
     * Outcome of a creation attempt
     */
    private enum Creation {
        CREATED, EXISTING, FAILED
    }

    /**
     * Persistence class name
     */
    private String persistenceClassName = "org.red5.server.persistence.RamPersistence";

    /**
     * Pushes a task to the scheduler for single execution.
     *
     * @param task
     *            runnable
     */
    public static void submitTask(Runnable task) {
        scheduler.execute(task);
    }

    /**
     * Sets the maximum number of shared objects in one scope; 0 means unlimited.
     *
     * @param maxSharedObjectsPerScope maximum shared objects per scope
     */
    public void setMaxSharedObjectsPerScope(int maxSharedObjectsPerScope) {
        this.maxSharedObjectsPerScope = maxSharedObjectsPerScope;
    }

    /**
     * Sets the maximum number of attributes a client may create in one shared object; 0 means unlimited. Server-side updates are not
     * limited.
     *
     * @param maxAttributesPerSharedObject maximum attributes per shared object
     */
    public void setMaxAttributesPerSharedObject(int maxAttributesPerSharedObject) {
        this.maxAttributesPerSharedObject = maxAttributesPerSharedObject;
    }

    /**
     * Sets the maximum number of '/' separated segments in a shared object name; 0 means unlimited. Persistent names map onto nested
     * directories, so this bounds the directory depth one name can create. Applies to every creation, client or server-side.
     *
     * @param maxNameDepth maximum name segments
     */
    public void setMaxNameDepth(int maxNameDepth) {
        this.maxNameDepth = maxNameDepth;
    }

    /**
     * Sets whether clients may create persistent shared objects. When false, a client request that would create a persistent shared object
     * is refused; clients may still use persistent shared objects created by server-side code, and server-side creation is not affected.
     *
     * @param allowClientPersistent true to allow clients to create persistent shared objects
     */
    public void setAllowClientPersistent(boolean allowClientPersistent) {
        this.allowClientPersistent = allowClientPersistent;
    }

    /**
     * Sets the maximum number of shared objects a single connection may create; 0 means unlimited. A creation is counted when a request
     * from the connection adds a new shared object to a scope, in any scope, for the lifetime of the connection. Joining an existing
     * shared object is not counted, and the count is not reduced when a shared object is released. Server-side creation is not counted.
     *
     * @param maxSharedObjectsPerConnection maximum shared objects created per connection
     */
    public void setMaxSharedObjectsPerConnection(int maxSharedObjectsPerConnection) {
        this.maxSharedObjectsPerConnection = maxSharedObjectsPerConnection;
    }

    /**
     * <p>setMaximumEventsPerUpdate.</p>
     *
     * @param maximumEventsPerUpdate
     *            the maximumEventsPerUpdate to set
     */
    @Deprecated
    public void setMaximumEventsPerUpdate(int maximumEventsPerUpdate) {
    }

    /**
     * Setter for persistence class name.
     *
     * @param name
     *            Setter for persistence class name
     */
    public void setPersistenceClassName(String name) {
        persistenceClassName = name;
    }

    /**
     * <p>Setter for the field <code>scheduler</code>.</p>
     *
     * @param scheduler
     *            the scheduler to set
     */
    public static void setScheduler(ThreadPoolTaskScheduler scheduler) {
        SharedObjectService.scheduler = scheduler;
    }

    /**
     * Return scope store
     *
     * @param scope
     *            Scope
     * @param persistent
     *            Persistent store or not?
     * @return Scope's store
     */
    private IPersistenceStore getStore(IScope scope, boolean persistent) {
        IPersistenceStore store;
        if (!persistent) {
            // Use special store for non-persistent shared objects
            if (!scope.hasAttribute(SO_TRANSIENT_STORE)) {
                store = new RamPersistence(scope);
                scope.setAttribute(SO_TRANSIENT_STORE, store);
                return store;
            }
            return (IPersistenceStore) scope.getAttribute(SO_TRANSIENT_STORE);
        }
        // Evaluate configuration for persistent shared objects
        if (!scope.hasAttribute(SO_PERSISTENCE_STORE)) {
            try {
                store = PersistenceUtils.getPersistenceStore(scope, persistenceClassName);
                log.info("Created persistence store {} for shared objects", store);
            } catch (Exception err) {
                log.warn("Could not create persistence store ({}) for shared objects, falling back to Ram persistence", persistenceClassName, err);
                store = new RamPersistence(scope);
            }
            scope.setAttribute(SO_PERSISTENCE_STORE, store);
            return store;
        }
        return (IPersistenceStore) scope.getAttribute(SO_PERSISTENCE_STORE);
    }

    /** {@inheritDoc} */
    public boolean createSharedObject(IScope scope, String name, boolean persistent) {
        // added or already existing will be true
        return create(scope, name, persistent) != Creation.FAILED;
    }

    /** {@inheritDoc} */
    @Override
    public boolean createClientSharedObject(IConnection conn, IScope scope, String name, boolean persistent) {
        if (hasSharedObject(scope, name)) {
            // joining an existing shared object is not a creation
            return true;
        }
        if (persistent && !allowClientPersistent) {
            log.warn("Refusing client request to create persistent shared object {} in {}", name, scope.getName());
            return false;
        }
        if (conn == null || maxSharedObjectsPerConnection <= 0) {
            return createSharedObject(scope, name, persistent);
        }
        AtomicInteger created = getCreationCount(conn);
        // check and count under the connection's counter so concurrent requests from one connection cannot exceed the limit
        synchronized (created) {
            if (created.get() >= maxSharedObjectsPerConnection) {
                log.warn("Refusing to create shared object {} in {} for {}, limit of {} per connection reached", name, scope.getName(), conn.getSessionId(), maxSharedObjectsPerConnection);
                return false;
            }
            Creation result = create(scope, name, persistent);
            if (result == Creation.CREATED) {
                created.incrementAndGet();
            }
            return result != Creation.FAILED;
        }
    }

    /**
     * Returns the number of shared objects created by the given connection, creating the counter on first use.
     *
     * @param conn
     *            connection
     * @return creation counter
     */
    private AtomicInteger getCreationCount(IConnection conn) {
        synchronized (conn) {
            Object count = conn.getAttribute(SO_CREATED_COUNT);
            if (count == null) {
                count = new AtomicInteger();
                conn.setAttribute(SO_CREATED_COUNT, count);
            }
            return (AtomicInteger) count;
        }
    }

    /**
     * Creates the shared object if it does not exist yet.
     *
     * @param scope
     *            parent scope
     * @param name
     *            shared object name
     * @param persistent
     *            whether the shared object is persistent
     * @return whether the shared object was created, already existed or could not be created
     */
    private Creation create(IScope scope, String name, boolean persistent) {
        if (!RamPersistence.isValidObjectName(name)) {
            log.warn("Refusing to create shared object with invalid name: {}", name);
            return Creation.FAILED;
        }
        if (maxNameDepth > 0 && name.split("/", -1).length > maxNameDepth) {
            log.warn("Refusing to create shared object {}, name has more than {} segments", name, maxNameDepth);
            return Creation.FAILED;
        }
        if (hasSharedObject(scope, name)) {
            // the shared object already exists
            log.trace("Shared object ({}) already exists. Persistent: {}", name, persistent);
            return Creation.EXISTING;
        }
        if (maxSharedObjectsPerScope > 0 && getSharedObjectNames(scope).size() >= maxSharedObjectsPerScope) {
            log.warn("Refusing to create shared object {} in {}, limit of {} reached", name, scope.getName(), maxSharedObjectsPerScope);
            return Creation.FAILED;
        }
        log.debug("Attempting to add shared object: {} to {}", name, scope.getName());
        SharedObjectScope so = new SharedObjectScope(scope, name, persistent, getStore(scope, persistent));
        so.setMaxClientAttributes(maxAttributesPerSharedObject);
        if (scope.addChildScope(so)) {
            return Creation.CREATED;
        }
        boolean exists = hasSharedObject(scope, name);
        log.debug("Add failed on create, shared object already exists: {}", exists);
        return exists ? Creation.EXISTING : Creation.FAILED;
    }

    /** {@inheritDoc} */
    public ISharedObject getSharedObject(IScope scope, String name) {
        return (ISharedObject) scope.getBasicScope(ScopeType.SHARED_OBJECT, name);
    }

    /** {@inheritDoc} */
    public ISharedObject getSharedObject(IScope scope, String name, boolean persistent) {
        if (!hasSharedObject(scope, name)) {
            createSharedObject(scope, name, persistent);
        }
        return getSharedObject(scope, name);
    }

    /** {@inheritDoc} */
    public Set<String> getSharedObjectNames(IScope scope) {
        return scope.getBasicScopeNames(ScopeType.SHARED_OBJECT);
    }

    /** {@inheritDoc} */
    public boolean hasSharedObject(IScope scope, String name) {
        return scope.hasChildScope(ScopeType.SHARED_OBJECT, name);
    }

    /** {@inheritDoc} */
    public boolean clearSharedObjects(IScope scope, String name) {
        boolean result = false;
        if (hasSharedObject(scope, name)) {
            // '/' clears all local and persistent shared objects associated with the instance
            // /foo/bar clears the shared object /foo/bar; if bar is a directory name, no shared objects are deleted.
            // /foo/bar/* clears all shared objects stored under the instance directory /foo/bar.
            // The bar directory is also deleted if no persistent shared objects are in use within this namespace.
            // /foo/bar/XX?? clears all shared objects that begin with XX, followed by any two characters. If a directory name matches
            // this specification, all the shared objects within this directory are cleared.
            result = ((ISharedObject) scope.getBasicScope(ScopeType.SHARED_OBJECT, name)).clear();
        }
        return result;
    }

}
