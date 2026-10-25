/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.so;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.red5.server.BaseConnection;
import org.red5.server.api.IAttributeStore;
import org.red5.server.api.IContext;
import org.red5.server.api.event.IEvent;
import org.red5.server.api.event.IEventListener;
import org.red5.server.api.persistence.IPersistable;
import org.red5.server.api.persistence.IPersistenceStore;
import org.red5.server.api.scheduling.IScheduledJob;
import org.red5.server.api.scheduling.ISchedulingService;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.ScopeType;
import org.red5.server.api.so.ISharedObject;
import org.red5.server.api.so.ISharedObjectListener;
import org.red5.server.api.so.ISharedObjectSecurity;
import org.red5.server.api.so.ISharedObjectSecurityService;
import org.red5.server.api.so.ISharedObjectService;
import org.red5.server.api.statistics.ISharedObjectStatistics;
import org.red5.server.net.rtmp.status.StatusCodes;
import org.red5.server.scope.BasicScope;
import org.red5.server.service.ReflectionUtils;
import org.red5.server.util.ScopeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Special scope for shared objects
 *
 * @author mondain
 */
public class SharedObjectScope extends BasicScope implements ISharedObject, StatusCodes {

    private Logger log = LoggerFactory.getLogger(SharedObjectScope.class);

    /**
     * Parent scope attribute holding the accounted size of the scope's shared objects
     */
    private static final String SO_SCOPE_BYTES = IPersistable.TRANSIENT_PREFIX + "_SO_SCOPE_BYTES_";

    /**
     * Parent scope attribute holding the versions of persistent shared objects evicted from memory, by name
     */
    private static final String SO_EVICTED_VERSIONS = IPersistable.TRANSIENT_PREFIX + "_SO_EVICTED_VERSIONS_";

    /**
     * Server-side listeners
     */
    private transient CopyOnWriteArraySet<ISharedObjectListener> serverListeners = new CopyOnWriteArraySet<>();

    /**
     * Event handlers
     */
    private transient ConcurrentMap<String, Object> handlers = new ConcurrentHashMap<>(1, 0.9f, 1);

    /**
     * Security handlers
     */
    private transient CopyOnWriteArraySet<ISharedObjectSecurity> securityHandlers = new CopyOnWriteArraySet<>();

    /**
     * Scoped shared object
     */
    protected AtomicReference<SharedObject> so = new AtomicReference<>(null);

    /**
     * Time to linger before checking for disposal
     */
    private long lingerPeriod = 5000L;

    /**
     * Linger job name
     */
    private String lingerJobName;

    /**
     * Maximum AMF encoded size of a value written by a client; 0 means unlimited.
     */
    private int maxValueBytes;

    /**
     * Maximum accounted size of this shared object's attributes after a client write; 0 means unlimited.
     */
    private long maxBytesPerObject;

    /**
     * Maximum accounted size of the attributes of all shared objects in the parent scope after a client write; 0 means unlimited.
     */
    private long maxBytesPerScope;

    /**
     * Maximum number of shared objects one connection may be connected to at the same time; 0 means unlimited.
     */
    private int maxConnectedPerConnection;

    /**
     * Time without listeners after which a persistent shared object is evicted from memory; 0 disables eviction.
     */
    private volatile long idleEvictMillis;

    /**
     * Time the last listener left, or the idle period started
     */
    private volatile long idleSince;

    /**
     * Whether an idle check is scheduled
     */
    private final AtomicBoolean evictionCheckScheduled = new AtomicBoolean();

    /**
     * Messages are handled under the read lock; eviction takes the write lock, so it never runs while a message is handled.
     */
    private final ReentrantReadWriteLock evictionLock = new ReentrantReadWriteLock();

    /**
     * Whether this persistent shared object was evicted from memory; an evicted scope has been removed from its parent.
     */
    private volatile boolean evicted;

    /**
     * Service that created this shared object, used to reload it after eviction
     */
    private ISharedObjectService service;

    /**
     * Creates shared object with given parent scope, name, persistence flag state and store object
     *
     * @param parent
     *            Parent scope
     * @param name
     *            Name
     * @param persistent
     *            Persistence flag state
     * @param store
     *            Persistence store
     */
    public SharedObjectScope(IScope parent, String name, boolean persistent, IPersistenceStore store) {
        super(parent, ScopeType.SHARED_OBJECT, name, persistent);
        // create shared object wrapper around the attributes
        String path = parent.getContextPath();
        if ("".equals(path) || path.charAt(0) != '/') {
            path = '/' + path;
        }
        log.trace("Path+name: {}/{}", path, name);
        SharedObject tmp = null;
        // Load SO
        Optional<SharedObject> opt = Optional.ofNullable((SharedObject) store.load(ScopeType.SHARED_OBJECT + path + '/' + name));
        if (opt.isPresent()) {
            tmp = opt.get();
            // set path
            tmp.setPath(path);
        } else {
            // Create if it doesn't exist
            tmp = new SharedObject(name, path, persistent, store);
            // Save
            store.save(tmp);
        }
        if (persistent) {
            // an object reloaded after eviction continues with the version it had in memory
            ConcurrentMap<String, Integer> evictedVersions = getEvictedVersions(parent, false);
            Integer evictedVersion = evictedVersions != null ? evictedVersions.remove(name) : null;
            if (evictedVersion != null) {
                tmp.restoreVersion(evictedVersion);
            }
        }
        so.set(tmp);
    }

    /**
     * Returns the versions of the persistent shared objects of a scope that were evicted from memory, by name.
     *
     * @param parent
     *            parent scope
     * @param create
     *            whether to create the map if the scope has none
     * @return versions by name, or null if there is none and create is false
     */
    @SuppressWarnings("unchecked")
    private static ConcurrentMap<String, Integer> getEvictedVersions(IScope parent, boolean create) {
        synchronized (parent) {
            Object versions = parent.getAttribute(SO_EVICTED_VERSIONS);
            if (versions == null && create) {
                versions = new ConcurrentHashMap<String, Integer>();
                parent.setAttribute(SO_EVICTED_VERSIONS, versions);
            }
            return (ConcurrentMap<String, Integer>) versions;
        }
    }

    /**
     * Returns whether the named persistent shared object of a scope was evicted from memory and has not been loaded again since.
     *
     * @param parent
     *            parent scope
     * @param name
     *            shared object name
     * @return true if the object was evicted and can be reloaded from its store
     */
    static boolean isEvicted(IScope parent, String name) {
        ConcurrentMap<String, Integer> evictedVersions = getEvictedVersions(parent, false);
        return evictedVersions != null && evictedVersions.containsKey(name);
    }

    /**
     * Returns the counter of the accounted size of the shared objects of a scope.
     *
     * @param parent
     *            parent scope
     * @return counter shared by the scope's shared objects
     */
    private static AtomicLong getScopeBytes(IScope parent) {
        synchronized (parent) {
            Object bytes = parent.getAttribute(SO_SCOPE_BYTES);
            if (bytes == null) {
                bytes = new AtomicLong();
                parent.setAttribute(SO_SCOPE_BYTES, bytes);
            }
            return (AtomicLong) bytes;
        }
    }

    /**
     * Returns the accounted size of the attributes of all shared objects in the given scope that are subject to a memory budget.
     *
     * @param parent
     *            parent scope
     * @return accounted size in bytes
     */
    public static long getScopeAttributeBytes(IScope parent) {
        Object bytes = parent.getAttribute(SO_SCOPE_BYTES);
        return bytes instanceof AtomicLong ? ((AtomicLong) bytes).get() : 0L;
    }

    /** {@inheritDoc} */
    @Override
    public void acquire() {
        so.get().acquire();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isAcquired() {
        return so.get().isAcquired();
    }

    /** {@inheritDoc} */
    @Override
    public void release() {
        so.get().release();
    }

    /**
     * This is no longer supported and is only here to prevent issues at runtime for those
     * implementing the call.
     *
     * @return false
     */
    @Deprecated
    public boolean isLocked() {
        return false;
    }

    /** {@inheritDoc} */
    @Deprecated
    @Override
    public void setDirty(boolean dirty) {
        // prefer that the use of the simplistic dirty be discontinued
        so.get().setDirty(dirty);
    }

    /** {@inheritDoc} */
    @Override
    public void setDirty(String name) {
        final SharedObject sharedObject = so.get();
        sharedObject.setDirty(name);
        Object value = sharedObject.getAttribute(name);
        for (ISharedObjectListener listener : serverListeners) {
            listener.onSharedObjectUpdate(this, name, value);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void beginUpdate() {
        so.get().beginUpdate();
    }

    /** {@inheritDoc} */
    @Override
    public void beginUpdate(IEventListener listener) {
        // start updates
        so.get().beginUpdate(listener);
    }

    /** {@inheritDoc} */
    @Override
    public void endUpdate() {
        // end update of SO
        so.get().endUpdate();
    }

    /** {@inheritDoc} */
    public void registerSharedObjectSecurity(ISharedObjectSecurity handler) {
        securityHandlers.add(handler);
    }

    /** {@inheritDoc} */
    public void unregisterSharedObjectSecurity(ISharedObjectSecurity handler) {
        securityHandlers.remove(handler);
    }

    /**
     * {@inheritDoc}
     *
     * @return a {@link java.util.Set} object
     */
    public Set<ISharedObjectSecurity> getSharedObjectSecurity() {
        return Collections.unmodifiableSet(securityHandlers);
    }

    /** {@inheritDoc} */
    @Override
    public IPersistenceStore getStore() {
        return so.get().getStore();
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return so.get().getName();
    }

    /** {@inheritDoc} */
    @Override
    public String getPath() {
        return so.get().getPath();
    }

    /**
     * <p>setPath.</p>
     *
     * @param path a {@link java.lang.String} object
     */
    public void setPath(String path) {
        so.get().setPath(path);
    }

    /**
     * {@inheritDoc}
     *
     * @return a boolean
     */
    public boolean isPersistent() {
        return so.get().isPersistent();
    }

    /**
     * {@inheritDoc}
     *
     * @return a int
     */
    public int getVersion() {
        return so.get().getVersion();
    }

    /** {@inheritDoc} */
    public void sendMessage(String handler, List<?> arguments) {
        if (so.get() != null) {
            so.get().sendMessage(handler, arguments);
            // Invoke method on registered handler
            String serviceName, serviceMethod;
            // Find out last dot position
            int dotPos = handler.lastIndexOf('.');
            // If any, split service name and service method name
            if (dotPos != -1) {
                serviceName = handler.substring(0, dotPos);
                serviceMethod = handler.substring(dotPos + 1);
            } else {
                // Otherwise only service method name is available
                serviceName = "";
                serviceMethod = handler;
            }
            // Get previously registered handler for service
            Object soHandler = getServiceHandler(serviceName);
            if (soHandler == null && hasParent()) {
                // No custom handler, check for service defined in the scope's context
                IContext context = getParent().getContext();
                String serviceId = null;
                try {
                    // The bean must have a name of
                    // "<SharedObjectName>.<DottedServiceName>.soservice"
                    serviceId = String.format("%s.%s.soservice", getName(), serviceName);
                    if (context.hasBean(serviceId)) {
                        soHandler = context.getBean(serviceId);
                    }
                } catch (Exception err) {
                    log.debug("No such bean: {}", serviceId);
                }
            }
            // Once handler is found, find matching method
            if (soHandler != null) {
                // With exact params...
                Object[] methodResult = ReflectionUtils.findMethod(soHandler, serviceMethod, arguments);
                // If method is found...
                if (methodResult.length > 0 && methodResult[0] != null) {
                    Method method = (Method) methodResult[0];
                    Object[] params = (Object[]) methodResult[1];
                    // ...try to invoke it and handle exceptions
                    try {
                        method.invoke(soHandler, params);
                    } catch (Exception err) {
                        log.error("Error while invoking method {} on shared object handler {}", new Object[] { serviceMethod, handler }, err);
                    }
                }
            }
            // notify server listeners
            for (ISharedObjectListener listener : serverListeners) {
                listener.onSharedObjectSend(this, handler, arguments);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean removeAttribute(String name) {
        boolean success = so.get().removeAttribute(name);
        // notify listeners on success and return true
        if (success) {
            for (ISharedObjectListener listener : serverListeners) {
                listener.onSharedObjectDelete(this, name);
            }
        }
        return success;
    }

    /** {@inheritDoc} */
    @Override
    public boolean removeAttribute(Enum<?> enm) {
        return removeAttribute(enm.name());
    }

    /** {@inheritDoc} */
    @Override
    public void removeAttributes() {
        // remove all attributes
        so.get().removeAttributes();
        // notify listeners on attributes clear
        for (ISharedObjectListener listener : serverListeners) {
            listener.onSharedObjectClear(this);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @return a int
     */
    public int size() {
        return so.get() != null ? so.get().getAttributeNames().size() : 0;
    }

    /** {@inheritDoc} */
    @Override
    public boolean addEventListener(IEventListener listener) {
        boolean result = super.addEventListener(listener) && so.get().register(listener);
        for (ISharedObjectListener soListener : serverListeners) {
            soListener.onSharedObjectConnect(this);
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean removeEventListener(IEventListener listener) {
        // remove the listener from the so
        so.get().unregister(listener);
        // if we have not been released by all that acquired then keep on
        // disconnection of the last listener
        if (so.get().isAcquired()) {
            log.debug("Shared object has been aquired so setting keep on disconnect");
            keepOnDisconnect = true;
        }
        // remove the listener
        boolean result = super.removeEventListener(listener);
        // notify other listeners that someone has stopped listening
        serverListeners.forEach(soListener -> {
            soListener.onSharedObjectDisconnect(this);
        });
        // check that linger job has be set
        if (lingerJobName == null) {
            // start a job to allow the so to linger for just a few ticks
            ISchedulingService scheduler = (ISchedulingService) getParent().getContext().getBean(ISchedulingService.BEAN_NAME);
            lingerJobName = scheduler.addScheduledOnceJob(lingerPeriod, new IScheduledJob() {
                public void execute(ISchedulingService service) {
                    if (so.get() != null && !so.get().isClosed()) {
                        so.get().checkRelease();
                    }
                }
            });
        }
        // check acquire
        if (so.get().isClosed()) {
            log.debug("Removing scope: {}", this);
            getParent().removeChildScope(this);
        } else if (idleEvictMillis > 0 && listeners.isEmpty() && isPersistent()) {
            // the last listener left, a persistent object starts its idle period
            idleSince = System.currentTimeMillis();
            scheduleEvictionCheck(idleEvictMillis);
        }
        return result;
    }

    /**
     * Sets the time without listeners after which this shared object, if persistent, is written to its store and evicted from memory; 0
     * disables eviction. The object is not evicted while it is acquired, has server-side listeners, security or service handlers
     * registered, or has changes its store cannot write. An evicted object is removed from its parent scope and reloaded from the store,
     * with its version, the next time it is requested. Server-side code that keeps a reference to a persistent shared object should
     * acquire it, or look it up again when it needs it.
     *
     * @param idleEvictMillis
     *            idle time in milliseconds, 0 to disable
     */
    public void setIdleEvictMillis(long idleEvictMillis) {
        this.idleEvictMillis = idleEvictMillis;
        if (idleEvictMillis > 0 && listeners.isEmpty() && isPersistent()) {
            // an object created without listeners is idle from the start
            idleSince = System.currentTimeMillis();
            scheduleEvictionCheck(idleEvictMillis);
        }
    }

    /**
     * Sets the service used to reload this shared object after it was evicted.
     *
     * @param service
     *            shared object service
     */
    void setService(ISharedObjectService service) {
        this.service = service;
    }

    /**
     * Schedules a check for eviction of this idle shared object, unless one is already scheduled.
     *
     * @param delay
     *            delay in milliseconds
     */
    private void scheduleEvictionCheck(long delay) {
        if (evictionCheckScheduled.compareAndSet(false, true)) {
            try {
                ISchedulingService scheduler = (ISchedulingService) getParent().getContext().getBean(ISchedulingService.BEAN_NAME);
                scheduler.addScheduledOnceJob(delay, new IScheduledJob() {
                    public void execute(ISchedulingService service) {
                        evictionCheckScheduled.set(false);
                        long next = checkEviction();
                        if (next > 0) {
                            scheduleEvictionCheck(next);
                        }
                    }
                });
            } catch (Exception e) {
                evictionCheckScheduled.set(false);
                log.warn("Could not schedule the idle check of shared object {}", getName(), e);
            }
        }
    }

    /**
     * Evicts this persistent shared object from memory if it has had no listeners for the idle period and is not in use: pending changes
     * are written by the store, the store drops the object, its version is kept for the reload and the scope is removed from its parent.
     *
     * @return delay in milliseconds until the object should be checked again, or -1 if no check is needed: it was evicted, eviction is
     *         disabled, or it has listeners and the last one to leave will start a new idle period
     */
    long checkEviction() {
        final SharedObject sharedObject = so.get();
        if (idleEvictMillis <= 0 || sharedObject == null || sharedObject.isClosed() || !sharedObject.isPersistent()) {
            return -1L;
        }
        final Lock writeLock = evictionLock.writeLock();
        if (!writeLock.tryLock()) {
            // a message is being handled
            return idleEvictMillis;
        }
        try {
            if (evicted) {
                return -1L;
            }
            if (!listeners.isEmpty() || !sharedObject.getListeners().isEmpty()) {
                return -1L;
            }
            long idle = System.currentTimeMillis() - idleSince;
            if (idle < idleEvictMillis) {
                return idleEvictMillis - idle;
            }
            if (sharedObject.isAcquired() || !serverListeners.isEmpty() || !securityHandlers.isEmpty() || !handlers.isEmpty()) {
                // in use by the application
                return idleEvictMillis;
            }
            final int version = sharedObject.getVersion();
            final IPersistenceStore store = sharedObject.getStore();
            if (store == null || !store.evict(sharedObject)) {
                // changes that could not be written yet, or a store that only keeps objects in memory
                log.debug("Shared object {} could not be evicted from its store, checking again later", getName());
                return idleEvictMillis;
            }
            evicted = true;
            sharedObject.disableSizeAccounting();
            getEvictedVersions(getParent(), true).put(getName(), version);
            // removed while holding the lock so a lookup cannot find this scope once a message waiting for it sees the eviction
            getParent().removeChildScope(this);
            log.debug("Evicted idle persistent shared object {} version {} from memory", getName(), version);
            return -1L;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Returns whether this persistent shared object was evicted from memory. An evicted scope is no longer part of its parent; the shared
     * object is reloaded from its store by the next lookup.
     *
     * @return true if evicted
     */
    public boolean isEvicted() {
        return evicted;
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasAttribute(String name) {
        return so.get().hasAttribute(name);
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasAttribute(Enum<?> enm) {
        return hasAttribute(enm.name());
    }

    /** {@inheritDoc} */
    @Override
    public Object getAttribute(String name) {
        return so.get().getAttribute(name);
    }

    /** {@inheritDoc} */
    @Override
    public Object getAttribute(Enum<?> enm) {
        return getAttribute(enm.name());
    }

    /** {@inheritDoc} */
    @Override
    public Object getAttribute(String name, Object value) {
        return so.get().getAttribute(name, value);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Object> getAttributes() {
        return so.get().getAttributes();
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> getAttributeNames() {
        return so.get().getAttributeNames();
    }

    /** {@inheritDoc} */
    @Override
    public Boolean getBoolAttribute(String name) {
        return so.get().getBoolAttribute(name);
    }

    /** {@inheritDoc} */
    @Override
    public Byte getByteAttribute(String name) {
        return so.get().getByteAttribute(name);
    }

    /** {@inheritDoc} */
    @Override
    public Double getDoubleAttribute(String name) {
        return so.get().getDoubleAttribute(name);
    }

    /** {@inheritDoc} */
    @Override
    public Integer getIntAttribute(String name) {
        return so.get().getIntAttribute(name);
    }

    /** {@inheritDoc} */
    @Override
    public List<?> getListAttribute(String name) {
        return so.get().getListAttribute(name);
    }

    /** {@inheritDoc} */
    @Override
    public Long getLongAttribute(String name) {
        return so.get().getLongAttribute(name);
    }

    /** {@inheritDoc} */
    @Override
    public Map<?, ?> getMapAttribute(String name) {
        return so.get().getMapAttribute(name);
    }

    /** {@inheritDoc} */
    @Override
    public Set<?> getSetAttribute(String name) {
        return so.get().getSetAttribute(name);
    }

    /** {@inheritDoc} */
    @Override
    public Short getShortAttribute(String name) {
        return so.get().getShortAttribute(name);
    }

    /** {@inheritDoc} */
    @Override
    public String getStringAttribute(String name) {
        return so.get().getStringAttribute(name);
    }

    /**
     * {@inheritDoc}
     *
     * @return a {@link java.util.Map} object
     */
    public Map<String, Object> getData() {
        return so.get().getData();
    }

    /**
     * Return security handlers for this shared object or
     *
     * <pre>
     * null
     * </pre>
     *
     * if none are found.
     *
     * @return set of security handlers
     */
    private Set<ISharedObjectSecurity> getSecurityHandlers() {
        ISharedObjectSecurityService security = (ISharedObjectSecurityService) ScopeUtils.getScopeService(getParent(), ISharedObjectSecurityService.class);
        if (security == null) {
            return null;
        }
        return security.getSharedObjectSecurity();
    }

    /**
     * Call handlers and check if connection to the existing SO is allowed.
     *
     * @return is connection allowed
     */
    protected boolean isConnectionAllowed() {
        // Check internal handlers first
        for (ISharedObjectSecurity handler : securityHandlers) {
            if (!handler.isConnectionAllowed(this)) {
                return false;
            }
        }
        // Check global SO handlers next
        final Set<ISharedObjectSecurity> handlers = getSecurityHandlers();
        if (handlers == null) {
            return true;
        }
        for (ISharedObjectSecurity handler : handlers) {
            if (!handler.isConnectionAllowed(this)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Call handlers and check if writing to the SO is allowed.
     *
     * @param key
     *            key
     * @param value
     *            value
     * @return is write allowed
     */
    protected boolean isWriteAllowed(String key, Object value) {
        // check internal handlers first
        for (ISharedObjectSecurity handler : securityHandlers) {
            if (!handler.isWriteAllowed(this, key, value)) {
                return false;
            }
        }
        // check global SO handlers next
        final Set<ISharedObjectSecurity> handlers = getSecurityHandlers();
        if (handlers == null) {
            return true;
        }
        for (ISharedObjectSecurity handler : handlers) {
            if (!handler.isWriteAllowed(this, key, value)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Call handlers and check if deleting a property from the SO is allowed.
     *
     * @param key
     *            key
     * @return is delete allowed
     */
    protected boolean isDeleteAllowed(String key) {
        // check internal handlers first
        for (ISharedObjectSecurity handler : securityHandlers) {
            if (!handler.isDeleteAllowed(this, key)) {
                return false;
            }
        }
        // check global SO handlers next
        final Set<ISharedObjectSecurity> handlers = getSecurityHandlers();
        if (handlers == null) {
            return true;
        }
        for (ISharedObjectSecurity handler : handlers) {
            if (!handler.isDeleteAllowed(this, key)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Call handlers and check if sending a message to the clients connected to the SO is allowed.
     *
     * @param message
     *            message
     * @param arguments
     *            arguments
     * @return is send allowed
     */
    protected boolean isSendAllowed(String message, List<?> arguments) {
        // check internal handlers first
        for (ISharedObjectSecurity handler : securityHandlers) {
            if (!handler.isSendAllowed(this, message, arguments)) {
                return false;
            }
        }
        // check global SO handlers next
        final Set<ISharedObjectSecurity> handlers = getSecurityHandlers();
        if (handlers == null) {
            return true;
        }
        for (ISharedObjectSecurity handler : handlers) {
            if (!handler.isSendAllowed(this, message, arguments)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Maximum number of attributes clients may create; 0 means unlimited.
     */
    private int maxClientAttributes;

    /**
     * Sets the maximum number of attributes clients may create in this shared object; 0 means unlimited. Server-side updates are not
     * limited.
     *
     * @param maxClientAttributes maximum attributes
     */
    public void setMaxClientAttributes(int maxClientAttributes) {
        this.maxClientAttributes = maxClientAttributes;
    }

    /**
     * Sets the maximum AMF encoded size of a value written by a client; 0 means unlimited. Server-side updates are not limited.
     *
     * @param maxValueBytes maximum value size in bytes
     */
    public void setMaxValueBytes(int maxValueBytes) {
        this.maxValueBytes = maxValueBytes;
    }

    /**
     * Sets the budget of this shared object: a client write that would grow the accounted size of its attributes beyond it is rejected; 0
     * means unlimited. The accounted size of an attribute is the UTF-8 length of its name plus the AMF encoded length of its value, and
     * server-side updates count toward it but are not rejected.
     *
     * @param maxBytesPerObject maximum accounted size in bytes
     */
    public void setMaxBytesPerObject(long maxBytesPerObject) {
        this.maxBytesPerObject = maxBytesPerObject;
        enableSizeAccounting();
    }

    /**
     * Sets the budget of the shared objects of the parent scope: a client write that would grow the accounted size of all their
     * attributes beyond it is rejected; 0 means unlimited. Only shared objects in memory are counted, and server-side updates count toward
     * it but are not rejected.
     *
     * @param maxBytesPerScope maximum accounted size in bytes
     */
    public void setMaxBytesPerScope(long maxBytesPerScope) {
        this.maxBytesPerScope = maxBytesPerScope;
        enableSizeAccounting();
    }

    /**
     * Starts accounting the size of the attributes once a memory budget is set.
     */
    private void enableSizeAccounting() {
        if (hasMemoryBudget()) {
            so.get().enableSizeAccounting(getScopeBytes(getParent()));
        }
    }

    /**
     * Returns whether a memory budget applies to this shared object.
     *
     * @return true if a per object or per scope budget is set
     */
    private boolean hasMemoryBudget() {
        return maxBytesPerObject > 0 || maxBytesPerScope > 0;
    }

    /**
     * Sets the maximum number of shared objects a connection may be connected to at the same time, checked when a connection connects to
     * this shared object; 0 means unlimited. Leaving a shared object or disconnecting frees the connection's slot.
     *
     * @param maxConnectedPerConnection maximum connected shared objects per connection
     */
    public void setMaxConnectedPerConnection(int maxConnectedPerConnection) {
        this.maxConnectedPerConnection = maxConnectedPerConnection;
    }

    /**
     * Returns whether the given listener is a current member of this shared object, meaning a connect was admitted and it has not since
     * disconnected.
     *
     * @param listener event listener, usually a connection
     * @return true if the listener is a member
     */
    protected boolean isMember(IEventListener listener) {
        return listeners.contains(listener);
    }

    /** {@inheritDoc} */
    @Override
    public void dispatchEvent(IEvent e) {
        if (e instanceof ISharedObjectMessage || e.getType() == IEvent.Type.SHARED_OBJECT) {
            final Lock readLock = evictionLock.readLock();
            readLock.lock();
            try {
                if (!evicted) {
                    handleMessage((ISharedObjectMessage) e);
                    return;
                }
            } finally {
                readLock.unlock();
            }
            // evicted after the message was routed here, hand it to the object reloaded from the store
            ISharedObject reloaded = reload();
            if (reloaded != null && reloaded != this) {
                reloaded.dispatchEvent(e);
            } else {
                log.warn("Shared object {} was evicted and could not be reloaded", getName());
            }
        } else {
            // don't know how to handle this event
            super.dispatchEvent(e);
        }
    }

    /**
     * Looks up this shared object in its parent scope, which reloads it from its store after eviction.
     *
     * @return the shared object in memory, or null if it could not be loaded
     */
    private ISharedObject reload() {
        ISharedObjectService soService = service;
        if (soService == null) {
            soService = (ISharedObjectService) ScopeUtils.getScopeService(getParent(), ISharedObjectService.class, SharedObjectService.class, false);
        }
        return soService != null ? soService.getSharedObject(getParent(), getName(), true) : null;
    }

    /**
     * Applies the events of a shared object message.
     *
     * @param msg
     *            shared object message
     */
    private void handleMessage(ISharedObjectMessage msg) {
        try {
            IEventListener source = null;
            if (msg.hasSource()) {
                source = msg.getSource();
                beginUpdate(source);
            } else {
                beginUpdate();
            }
            events: for (ISharedObjectEvent event : msg.getEvents()) {
                final String key = event.getKey();
                final ISharedObjectEvent.Type type = event.getType();
                // client-originated mutations and sends require current membership, which only an admitted connect grants
                if (source != null && (type == ISharedObjectEvent.Type.SERVER_SET_ATTRIBUTE || type == ISharedObjectEvent.Type.SERVER_DELETE_ATTRIBUTE || type == ISharedObjectEvent.Type.SERVER_SEND_MESSAGE) && !isMember(source)) {
                    log.debug("Rejecting {} on {} from non-member {}", type, getName(), source);
                    if (type != ISharedObjectEvent.Type.SERVER_SEND_MESSAGE) {
                        so.get().returnError(SO_NO_WRITE_ACCESS);
                    }
                    continue;
                }
                switch (type) {
                    case SERVER_CONNECT:
                        if (!isConnectionAllowed()) {
                            so.get().returnError(SO_NO_READ_ACCESS);
                            // a denied connect ends the rest of this message
                            break events;
                        } else if (source != null) {
                            if (source instanceof BaseConnection) {
                                if (!((BaseConnection) source).registerBasicScope(this, maxConnectedPerConnection)) {
                                    log.warn("Refusing connection to {} for {}, limit of {} connected shared objects per connection reached", getName(), source, maxConnectedPerConnection);
                                    so.get().returnError(SO_NO_READ_ACCESS);
                                    break events;
                                }
                            } else {
                                addEventListener(source);
                            }
                        }
                        break;
                    case SERVER_DISCONNECT:
                        if (source != null) {
                            if (source instanceof BaseConnection) {
                                ((BaseConnection) source).unregisterBasicScope(this);
                            } else {
                                removeEventListener(source);
                            }
                        }
                        break;
                    case SERVER_SET_ATTRIBUTE:
                        final Object value = event.getValue();
                        if (source != null && maxClientAttributes > 0 && !hasAttribute(key) && getAttributeNames().size() >= maxClientAttributes) {
                            log.warn("Rejecting new attribute {} on {}, limit of {} reached", key, getName(), maxClientAttributes);
                            so.get().returnError(SO_NO_WRITE_ACCESS);
                            break;
                        }
                        // client values are only measured when a size limit applies
                        final boolean measured = source != null && (maxValueBytes > 0 || hasMemoryBudget());
                        final int valueSize = measured ? AttributeSize.ofValue(value) : 0;
                        if (measured && maxValueBytes > 0 && (valueSize < 0 || valueSize > maxValueBytes)) {
                            log.warn("Rejecting attribute {} on {}, value of {} bytes exceeds the limit of {}", key, getName(), valueSize, maxValueBytes);
                            so.get().returnError(SO_NO_WRITE_ACCESS);
                        } else if (!isWriteAllowed(key, value)) {
                            // adds an owner event
                            //so.get().returnAttributeValue(key);
                            so.get().returnError(SO_NO_WRITE_ACCESS);
                        } else if (measured && hasMemoryBudget()) {
                            SharedObject.WriteResult result = so.get().setAttributeWithinBudget(key, value, AttributeSize.of(key, valueSize), maxBytesPerObject, maxBytesPerScope);
                            if (result == SharedObject.WriteResult.REJECTED) {
                                so.get().returnError(SO_NO_WRITE_ACCESS);
                            } else if (result == SharedObject.WriteResult.SET) {
                                notifyUpdate(key, value);
                            }
                        } else {
                            setAttribute(key, value);
                        }
                        break;
                    case SERVER_DELETE_ATTRIBUTE:
                        if (!isDeleteAllowed(key)) {
                            //so.get().returnAttributeValue(key);
                            so.get().returnError(SO_NO_WRITE_ACCESS);
                        } else {
                            removeAttribute(key);
                        }
                        break;
                    case SERVER_SEND_MESSAGE:
                        final List<?> arguments = (List<?>) event.getValue();
                        // Ignore request silently if not allowed
                        if (isSendAllowed(key, arguments)) {
                            sendMessage(key, arguments);
                        } else {
                            log.debug("Send is not allowed for {}", key);
                        }
                        break;
                    default:
                        log.warn("Unknown SO event: {}", event.getType());
                }
            }
        } finally {
            endUpdate();
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean setAttribute(String name, Object value) {
        boolean success = so.get().setAttribute(name, value);
        if (success) {
            notifyUpdate(name, value);
        }
        return success;
    }

    /**
     * Notifies the server-side listeners of an updated attribute.
     *
     * @param name
     *            attribute name
     * @param value
     *            new value
     */
    private void notifyUpdate(String name, Object value) {
        for (ISharedObjectListener listener : serverListeners) {
            listener.onSharedObjectUpdate(this, name, value);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean setAttribute(Enum<?> enm, Object value) {
        return setAttribute(enm.name(), value);
    }

    /** {@inheritDoc} */
    @Override
    public boolean setAttributes(IAttributeStore values) {
        boolean success = so.get().setAttributes(values);
        if (success) {
            for (ISharedObjectListener listener : serverListeners) {
                listener.onSharedObjectUpdate(this, values);
            }
        }
        return success;
    }

    /** {@inheritDoc} */
    @Override
    public boolean setAttributes(Map<String, Object> values) {
        boolean success = so.get().setAttributes(values);
        if (success) {
            for (ISharedObjectListener listener : serverListeners) {
                listener.onSharedObjectUpdate(this, values);
            }
        }
        return success;
    }

    /** {@inheritDoc} */
    public void addSharedObjectListener(ISharedObjectListener listener) {
        serverListeners.add(listener);
    }

    /** {@inheritDoc} */
    public void removeSharedObjectListener(ISharedObjectListener listener) {
        serverListeners.remove(listener);
    }

    /** {@inheritDoc} */
    public void registerServiceHandler(Object handler) {
        registerServiceHandler("", handler);
    }

    /** {@inheritDoc} */
    public void registerServiceHandler(String name, Object handler) {
        if (name == null) {
            name = "";
        }
        handlers.put(name, handler);
    }

    /**
     * <p>unregisterServiceHandler.</p>
     */
    public void unregisterServiceHandler() {
        unregisterServiceHandler("");
    }

    /** {@inheritDoc} */
    public void unregisterServiceHandler(String name) {
        if (name == null) {
            name = "";
        }
        handlers.remove(name);
    }

    /** {@inheritDoc} */
    public Object getServiceHandler(String name) {
        if (name == null) {
            name = "";
        }
        return handlers.get(name);
    }

    /**
     * {@inheritDoc}
     *
     * @return a {@link java.util.Set} object
     */
    public Set<String> getServiceHandlerNames() {
        return Collections.unmodifiableSet(handlers.keySet());
    }

    /**
     * {@inheritDoc}
     *
     * @return a boolean
     */
    public boolean clear() {
        boolean success = so.get().clear();
        if (success) {
            for (ISharedObjectListener listener : serverListeners) {
                listener.onSharedObjectClear(this);
            }
        }
        return success;
    }

    /**
     * {@inheritDoc}
     */
    public void close() {
        // close the internal SO
        so.get().close();
        // remove from the parent
        parent.removeChildScope(this);
        // clear the reference
        so.set(null);
    }

    /**
     * {@inheritDoc}
     *
     * @return a {@link org.red5.server.api.statistics.ISharedObjectStatistics} object
     */
    public ISharedObjectStatistics getStatistics() {
        return so.get();
    }

    /**
     * Returns true if the scope has a reference to a shared object.
     *
     * @return true if the scope has a reference to a shared object
     */
    public boolean hasRef() {
        return so.get() != null;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        final SharedObject sharedObject = so.get();
        if (sharedObject != null) {
            return "SharedObjectScope: " + sharedObject.getName() + " version: " + sharedObject.getVersion() + " created: " + sharedObject.getCreationTime();
        }
        return "SharedObjectScope";
    }

}
