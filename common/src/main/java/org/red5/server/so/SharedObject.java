/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2016 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.so;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import org.red5.io.object.Deserializer;
import org.red5.io.object.Input;
import org.red5.io.object.Output;
import org.red5.io.object.Serializer;
import org.red5.server.AttributeStore;
import org.red5.server.api.IAttributeStore;
import org.red5.server.api.Red5;
import org.red5.server.api.event.IEventListener;
import org.red5.server.api.persistence.IPersistable;
import org.red5.server.api.persistence.IPersistenceStore;
import org.red5.server.api.scope.ScopeType;
import org.red5.server.api.statistics.ISharedObjectStatistics;
import org.red5.server.api.statistics.support.StatisticsCounter;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.codec.RTMP;
import org.red5.server.net.rtmp.message.Constants;
import org.red5.server.so.ISharedObjectEvent.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a remote shared object on server-side. RSO's are shared by multiple clients and synchronized on each data change. A shared
 * object can be persistent or transient; the difference being saved to the disk for later access vs transient objects that are not
 * persisted to storage. Shared objects have name identifiers and a path. In this implementation we use IPersistenceStore to delegate all
 * (de)serialization work. SOs store data as a "name-value" store. Each value in can be a complex object or map. All access to methods that
 * change properties in the SO must be properly synchronized for multithreaded access.
 */
public class SharedObject extends AttributeStore implements ISharedObjectStatistics, IPersistable, Constants {

    protected static Logger log = LoggerFactory.getLogger(SharedObject.class);

    /**
     * Reentrant lock with fairness enabled; used for writes.
     */
    private transient ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    private transient WriteLock writeLock = lock.writeLock();

    /**
     * Timestamp the scope was created.
     */
    private final long creationTime = System.nanoTime();

    /**
     * Shared Object name (identifier)
     */
    protected String name = "";

    /**
     * SO path
     */
    protected String path = "";

    /**
     * true if the SharedObject was stored by the persistence framework and can be used later on reconnection
     */
    protected boolean persistent;

    /**
     * Object that is delegated with all storage work for persistent SOs
     */
    protected IPersistenceStore storage;

    /**
     * Version. Used on synchronization purposes.
     */
    protected volatile AtomicInteger version = new AtomicInteger(1);

    /**
     * Number of pending update operations (beginUpdate / endUpdate)
     */
    protected volatile AtomicInteger updateCounter = new AtomicInteger();

    /**
     * Last modified timestamp
     */
    protected volatile long lastModified = -1;

    /**
     * Owner event
     */
    protected SharedObjectMessage ownerMessage;

    /**
     * Synchronization events
     */
    protected transient volatile ConcurrentSkipListSet<ISharedObjectEvent> syncEvents = new ConcurrentSkipListSet<>();

    /**
     * Listeners
     */
    protected transient volatile CopyOnWriteArraySet<IEventListener> listeners = new CopyOnWriteArraySet<>();

    /**
     * Event listener, actually RTMP connection
     */
    protected IEventListener source;

    /**
     * Number of times the SO has been acquired
     */
    protected volatile AtomicInteger acquireCount = new AtomicInteger();

    /**
     * Manages listener statistics.
     */
    protected transient StatisticsCounter listenerStats = new StatisticsCounter();

    /**
     * Counts number of "change" events.
     */
    protected AtomicInteger changeStats = new AtomicInteger();

    /**
     * Counts number of "delete" events.
     */
    protected AtomicInteger deleteStats = new AtomicInteger();

    /**
     * Counts number of "send message" events.
     */
    protected AtomicInteger sendStats = new AtomicInteger();

    /**
     * Whether or not this shared object is closed
     */
    protected volatile AtomicBoolean closed = new AtomicBoolean(false);

    /** Constructs a new SharedObject. */
    public SharedObject() {
        // This is used by the persistence framework
        super();
        attributes = new HashMap<>();
        ownerMessage = new SharedObjectMessage(null, null, -1, false);
    }

    /**
     * Constructs new SO from Input object
     * 
     * @param input
     *            Input source
     * @throws IOException
     *             I/O exception
     *
     * @see org.red5.io.object.Input
     */
    public SharedObject(Input input) throws IOException {
        this();
        deserialize(input);
    }

    /**
     * Creates new SO from given data map, name, path and persistence option
     *
     * @param name
     *            SO name
     * @param path
     *            SO path
     * @param persistent
     *            SO persistence
     */
    public SharedObject(String name, String path, boolean persistent) {
        super();
        this.name = name;
        this.path = path;
        this.persistent = persistent;
        attributes = new HashMap<>();
        ownerMessage = new SharedObjectMessage(null, name, 0, persistent);
    }

    /**
     * Creates new SO from given data map, name, path, storage object and persistence option
     * 
     * @param name
     *            SO name
     * @param path
     *            SO path
     * @param persistent
     *            SO persistence
     * @param storage
     *            Persistence storage
     */
    public SharedObject(String name, String path, boolean persistent, IPersistenceStore storage) {
        this(name, path, persistent);
        setStore(storage);
    }

    /**
     * Creates new SO from given data map, name, path and persistence option
     *
     * @param data
     *            Data
     * @param name
     *            SO name
     * @param path
     *            SO path
     * @param persistent
     *            SO persistence
     */
    public SharedObject(Map<String, Object> data, String name, String path, boolean persistent) {
        this(name, path, persistent);
        attributes.putAll(data);
    }

    /**
     * Creates new SO from given data map, name, path, storage object and persistence option
     * 
     * @param data
     *            Data
     * @param name
     *            SO name
     * @param path
     *            SO path
     * @param persistent
     *            SO persistence
     * @param storage
     *            Persistence storage
     */
    public SharedObject(Map<String, Object> data, String name, String path, boolean persistent, IPersistenceStore storage) {
        this(data, name, path, persistent);
        setStore(storage);
    }

    /** {@inheritDoc} */
    public String getName() {
        return name;
    }

    /** {@inheritDoc} */
    public void setName(String name) {
        throw new UnsupportedOperationException(String.format("Name change not supported; current name: %s", getName()));
    }

    /** {@inheritDoc} */
    public String getPath() {
        return path;
    }

    /** {@inheritDoc} */
    public void setPath(String path) {
        this.path = path;
    }

    /** {@inheritDoc} */
    public String getType() {
        return ScopeType.SHARED_OBJECT.toString();
    }

    /** {@inheritDoc} */
    public long getLastModified() {
        return lastModified;
    }

    /** {@inheritDoc} */
    public boolean isPersistent() {
        return persistent;
    }

    /** {@inheritDoc} */
    public void setPersistent(boolean persistent) {
        log.debug("setPersistent: {}", persistent);
        this.persistent = persistent;
    }

    /**
     * Send update notification over data channel of RTMP connection
     */
    protected synchronized void sendUpdates() {
        log.debug("sendUpdates");
        // get the current version
        final int currentVersion = version.get();
        log.debug("Current version: {}", currentVersion);
        // get the name
        final String name = getName();
        //get owner events
        Set<ISharedObjectEvent> ownerEvents = ownerMessage.getEvents();
        if (!ownerEvents.isEmpty()) {
            // get all current owner events - single ordered set going to the event owner
            final TreeSet<ISharedObjectEvent> events = new TreeSet<>(ownerEvents);
            ownerEvents.removeAll(events);
            // send update to "owner" of this update request
            if (source != null) {
                final RTMPConnection con = (RTMPConnection) source;
                // create a worker
                SharedObjectService.submitTask(() -> {
                    Red5.setConnectionLocal(con);
                    con.sendSharedObjectMessage(name, currentVersion, persistent, events);
                    Red5.setConnectionLocal(null);
                });
            } else {
                log.debug("No source connection, owner events not sent");
            }
        } else {
            if (log.isTraceEnabled()) {
                log.trace("No owner events to send");
            }
        }
        // tell all the listeners
        if (!syncEvents.isEmpty()) {
            // get the listeners
            Set<IEventListener> listeners = getListeners();
            // if there are no listeners, clear the events
            if (listeners.isEmpty()) {
                log.debug("No listeners for {} sync events, clearing", syncEvents.size());
                syncEvents.clear();
            } else {
                if (log.isTraceEnabled()) {
                    log.trace("Listeners: {}", listeners);
                }
                // get all current sync events 
                final TreeSet<ISharedObjectEvent> events = new TreeSet<>(syncEvents);
                syncEvents.removeAll(events);
                // updates all registered clients of this shared object
                listeners.stream().filter(listener -> listener != source).forEach(listener -> {
                    final RTMPConnection con = (RTMPConnection) listener;
                    // create a worker
                    SharedObjectService.submitTask(() -> {
                        if (con.isConnected()) {
                            Red5.setConnectionLocal(con);
                            con.sendSharedObjectMessage(name, currentVersion, persistent, events);
                            Red5.setConnectionLocal(null);
                        } else {
                            log.trace("Skipping {} connection: {}", RTMP.states[con.getStateCode()], con.getId());
                            // if the connection is 'disconnected' remove it
                            if (con.isDisconnected()) {
                                unregister(con);
                            }
                        }
                    });
                });
            }
        } else if (log.isTraceEnabled()) {
            log.trace("No sync events to send");
        }
    }

    /**
     * Send notification about modification of SO
     */
    protected void notifyModified() {
        log.debug("notifyModified - updaters: {}", updateCounter.get());
        if (updateCounter.get() == 0) {
            // modification made -> increase version of SO
            version.incrementAndGet();
            // update last mod time
            lastModified = System.currentTimeMillis();
            if (storage == null || !storage.save(this)) {
                log.warn("Could not store shared object");
            }
            sendUpdates();
        }
    }

    /**
     * Return an error message to the client.
     * 
     * @param message
     *            message
     */
    protected void returnError(String message) {
        ownerMessage.addEvent(Type.CLIENT_STATUS, "error", message);
    }

    /** {@inheritDoc} */
    @Override
    public Object getAttribute(String name) {
        Object result = null;
        if (hasAttribute(name)) {
            boolean locked = false;
            try {
                if (locked = lock.readLock().tryLock(10L, TimeUnit.MILLISECONDS)) {
                    result = attributes.get(name);
                } else {
                    log.trace("Failed to get read lock");
                }
            } catch (Exception e) {
                log.warn("Exception in setAttribute", e);
            } finally {
                if (locked) {
                    lock.readLock().unlock();
                }
            }
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public Object getAttribute(String name, Object def) {
        log.debug("getAttribute - name: {} default: {}", name, def);
        Object result = null;
        if (name != null) {
            try {
                Object value = getAttribute(name);
                if (value == null) {
                    // if the current value is null do a setAttribute
                    if (setAttribute(name, def)) {
                        result = def;
                    } else {
                        log.warn("getAttribute: {} with a default value: {} failed", name, def);
                    }
                } else {
                    result = value;
                }
            } catch (Exception e) {
                log.warn("Exception in getAttribute", e);
            }
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean setAttribute(String name, Object value) {
        log.debug("setAttribute - name: {} value: {}", name, value);
        boolean result = false;
        boolean locked = false;
        try {
            beginUpdate();
            if (locked = writeLock.tryLock(100L, TimeUnit.MILLISECONDS)) {
                if (ownerMessage.addEvent(Type.CLIENT_UPDATE_ATTRIBUTE, name, null)) {
                    // Setting a null value removes the attribute
                    if (value == null) {
                        boolean removed = super.removeAttribute(name);
                        if (removed) {
                            syncEvents.add(new SharedObjectEvent(Type.CLIENT_DELETE_DATA, name, null));
                            deleteStats.incrementAndGet();
                            result = true;
                        }
                    } else {
                        boolean set = super.setAttribute(name, value);
                        log.debug("Set attribute?: {}", set);
                        if (set) {
                            // only sync if the attribute changed
                            syncEvents.add(new SharedObjectEvent(Type.CLIENT_UPDATE_DATA, name, value));
                            changeStats.incrementAndGet();
                            result = true;
                        }
                    }
                }
            } else {
                log.trace("Failed to get write lock");
            }
        } catch (Exception e) {
            log.warn("Exception in setAttribute", e);
        } finally {
            if (locked) {
                writeLock.unlock();
            }
            endUpdate();
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean setAttributes(Map<String, Object> values) {
        if (values != null) {
            beginUpdate();
            int valuesCount = values.size();
            try {
                writeLock.lockInterruptibly();
                for (Map.Entry<String, Object> entry : values.entrySet()) {
                    if (super.setAttribute(entry.getKey(), entry.getValue())) {
                        --valuesCount;
                    }
                }
                return (valuesCount == 0);
            } catch (Exception e) {
                log.warn("Exception in setAttributes", e);
            } finally {
                writeLock.unlock();
                endUpdate();
            }
        }
        // expect every value to have been added
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean setAttributes(IAttributeStore values) {
        if (values != null) {
            return setAttributes(values.getAttributes());
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean removeAttribute(String name) {
        boolean result = false;
        try {
            writeLock.lockInterruptibly();
            // Send confirmation to client
            final SharedObjectEvent event = new SharedObjectEvent(Type.CLIENT_DELETE_DATA, name, null);
            if (ownerMessage.addEvent(event)) {
                if (super.removeAttribute(name)) {
                    syncEvents.add(event);
                    deleteStats.incrementAndGet();
                    result = true;
                }
                notifyModified();
            }
        } catch (Exception e) {
            log.warn("Exception in removeAttribute", e);
        } finally {
            writeLock.unlock();
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public void removeAttributes() {
        try {
            beginUpdate();
            final Set<String> keys = new HashSet<>(attributes.keySet());
            keys.forEach(name -> removeAttribute(name));
        } finally {
            endUpdate();
        }
    }

    /**
     * Broadcast event to event handler
     * 
     * @param handler
     *            Event handler
     * @param arguments
     *            Arguments
     */
    protected void sendMessage(String handler, List<?> arguments) {
        final SharedObjectEvent event = new SharedObjectEvent(Type.CLIENT_SEND_MESSAGE, handler, arguments);
        if (ownerMessage.addEvent(event)) {
            syncEvents.add(event);
            sendStats.incrementAndGet();
            if (log.isTraceEnabled()) {
                log.trace("Send message: {}", arguments);
            }
        }
    }

    /**
     * Getter for data.
     *
     * @return SO data as unmodifiable map
     */
    public Map<String, Object> getData() {
        return getAttributes();
    }

    /**
     * Getter for version.
     *
     * @return SO version.
     */
    public int getVersion() {
        return version.get();
    }

    /**
     * Register event listener
     * 
     * @param listener
     *            Event listener
     * @return true if listener was added
     */
    protected boolean register(IEventListener listener) {
        log.debug("register - listener: {}", listener);
        boolean registered = listeners.add(listener);
        if (registered) {
            listenerStats.increment();
            // prepare response for new client
            ownerMessage.addEvent(Type.CLIENT_INITIAL_DATA, null, null);
            if (!isPersistent()) {
                ownerMessage.addEvent(Type.CLIENT_CLEAR_DATA, null, null);
            }
            if (!attributes.isEmpty()) {
                ownerMessage.addEvent(new SharedObjectEvent(Type.CLIENT_UPDATE_DATA, null, getAttributes()));
            }
            // we call notifyModified here to send response if we're not in a beginUpdate block
            notifyModified();
        }
        return registered;
    }

    /**
     * Unregister event listener
     * 
     * @param listener
     *            Event listener
     */
    protected void unregister(IEventListener listener) {
        log.debug("unregister - listener: {}", listener);
        if (listeners.remove(listener)) {
            listenerStats.decrement();
        }
    }

    /**
     * Check if shared object must be released.
     */
    protected void checkRelease() {
        if (!isPersistent() && listeners.isEmpty() && !isAcquired()) {
            log.info("Deleting shared object {} because all clients disconnected and it is no longer acquired", name);
            if (storage != null) {
                if (!storage.remove(this)) {
                    log.error("Could not remove shared object");
                }
            }
            close();
        }
    }

    /**
     * Get event listeners.
     *
     * @return Value for property 'listeners'.
     */
    public Set<IEventListener> getListeners() {
        return Collections.unmodifiableSet(listeners);
    }

    /**
     * Begin update of this Shared Object. Increases number of pending update operations
     */
    public void beginUpdate() {
        log.debug("beginUpdate");
        beginUpdate(source);
    }

    /**
     * Begin update of this Shared Object and setting listener
     * 
     * @param listener
     *            Update with listener
     */
    public void beginUpdate(IEventListener listener) {
        log.debug("beginUpdate - listener: {}", listener);
        source = listener;
        // increase number of pending updates
        updateCounter.incrementAndGet();
    }

    /**
     * End update of this Shared Object. Decreases number of pending update operations and broadcasts modified event if it is equal to zero
     * (i.e. no more pending update operations).
     */
    public void endUpdate() {
        log.debug("endUpdate");
        // decrease number of pending updates
        if (updateCounter.decrementAndGet() == 0) {
            notifyModified();
            source = null;
        }
    }

    /** {@inheritDoc} */
    public void serialize(Output output) throws IOException {
        log.debug("serialize - name: {}", name);
        Serializer.serialize(output, getName());
        Map<String, Object> map = getAttributes();
        if (log.isTraceEnabled()) {
            log.trace("Attributes: {}", map);
        }
        Serializer.serialize(output, map);
    }

    /** {@inheritDoc} */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void deserialize(Input input) throws IOException {
        log.debug("deserialize");
        name = Deserializer.deserialize(input, String.class);
        log.trace("Name: {}", name);
        persistent = true;
        Map<String, Object> map = Deserializer.<Map> deserialize(input, Map.class);
        if (log.isTraceEnabled()) {
            log.trace("Attributes: {}", map);
        }
        super.setAttributes(map);
        ownerMessage.setName(name);
        ownerMessage.setPersistent(persistent);
    }

    /** {@inheritDoc} */
    public void setStore(IPersistenceStore store) {
        this.storage = store;
    }

    /** {@inheritDoc} */
    public IPersistenceStore getStore() {
        return storage;
    }

    /**
     * Deletes all the attributes and sends a clear event to all listeners. The persistent data object is also removed from a persistent
     * shared object.
     * 
     * @return true on success, false otherwise
     */
    protected boolean clear() {
        log.debug("clear");
        removeAttributes();
        // send confirmation to client
        ownerMessage.addEvent(Type.CLIENT_CLEAR_DATA, name, null);
        notifyModified();
        changeStats.incrementAndGet();
        return true;
    }

    /**
     * Detaches a reference from this shared object, reset it's state, this will destroy the reference immediately. This is useful when you
     * don't want to proxy a shared object any longer.
     */
    protected void close() {
        log.debug("close");
        closed.compareAndSet(false, true);
        // clear collections
        removeAttributes();
        listeners.clear();
        syncEvents.clear();
        ownerMessage.getEvents().clear();
    }

    /**
     * Prevent shared object from being released. Each call to acquire must be paired with a call to release so the SO isn't held forever.
     * This is only valid for non-persistent SOs.
     */
    public void acquire() {
        log.debug("acquire");
        acquireCount.incrementAndGet();
    }

    /**
     * Check if shared object currently is acquired.
     * 
     * @return true if the SO is acquired, false otherwise
     */
    public boolean isAcquired() {
        return acquireCount.get() > 0;
    }

    /**
     * Release previously acquired shared object. If the SO is non-persistent, no more clients are connected the SO isn't acquired any more,
     * the data is released.
     */
    public void release() {
        log.debug("release");
        if (acquireCount.get() == 0) {
            throw new RuntimeException("The shared object was not acquired before.");
        }
        if (acquireCount.decrementAndGet() == 0) {
            checkRelease();
        }
    }

    public boolean isClosed() {
        return closed.get();
    }

    /** {@inheritDoc} */
    public long getCreationTime() {
        return creationTime;
    }

    /** {@inheritDoc} */
    public int getTotalListeners() {
        return listenerStats.getTotal();
    }

    /** {@inheritDoc} */
    @Deprecated
    public int getMaxListeners() {
        return listenerStats.getTotal();
    }

    /** {@inheritDoc} */
    public int getActiveListeners() {
        return listenerStats.getCurrent();
    }

    /** {@inheritDoc} */
    public int getTotalChanges() {
        return changeStats.intValue();
    }

    /** {@inheritDoc} */
    public int getTotalDeletes() {
        return deleteStats.intValue();
    }

    /** {@inheritDoc} */
    public int getTotalSends() {
        return sendStats.intValue();
    }

    /** {@inheritDoc} */
    public void setDirty(boolean dirty) {
        log.trace("setDirty: {}", dirty);
        notifyModified();
    }

    /** {@inheritDoc} */
    public void setDirty(String name) {
        log.trace("setDirty: {}", name);
        // get uses read lock, no need to do locking here
        Object value = getAttribute(name);
        if (ownerMessage.addEvent(Type.CLIENT_UPDATE_ATTRIBUTE, name, null)) {
            // a null value means a removal the attribute
            if (value == null) {
                syncEvents.add(new SharedObjectEvent(Type.CLIENT_DELETE_DATA, name, null));
                deleteStats.incrementAndGet();
            } else {
                syncEvents.add(new SharedObjectEvent(Type.CLIENT_UPDATE_DATA, name, value));
                changeStats.incrementAndGet();
            }
            notifyModified();
        }
    }

}
