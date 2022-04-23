/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2016 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.stream;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;

import org.red5.codec.IStreamCodecInfo;
import org.red5.codec.StreamCodecInfo;
import org.red5.server.api.scope.IScope;
import org.red5.server.api.scope.IScopeHandler;
import org.red5.server.api.stream.IStream;
import org.red5.server.api.stream.IStreamAwareScopeHandler;
import org.red5.server.api.stream.StreamState;
import org.red5.server.net.rtmp.event.Notify;

/**
 * Abstract base implementation of IStream. Contains codec information, stream name, scope, event handling, and provides stream start and
 * stop operations.
 *
 * @see org.red5.server.api.stream.IStream
 */
public abstract class AbstractStream implements IStream {

    /**
     * Stream name
     */
    private String name;

    /**
     * Stream scope
     */
    private IScope scope;

    /**
     * Stream audio and video codec information
     */
    protected IStreamCodecInfo codecInfo = new StreamCodecInfo();

    /**
     * Stores the streams metadata
     */
    protected transient AtomicReference<Notify> metaData = new AtomicReference<>();

    /**
     * Contains {@link PropertyChangeListener}s registered with this stream and following its changes of state.
     */
    protected transient CopyOnWriteArraySet<PropertyChangeListener> stateListeners = new CopyOnWriteArraySet<>();

    /**
     * Timestamp the stream was created.
     */
    protected long creationTime = System.currentTimeMillis();

    /**
     * Timestamp the stream was started.
     */
    protected long startTime;

    /**
     * Current state
     */
    protected final transient AtomicReference<StreamState> state = new AtomicReference<>(StreamState.UNINIT);

    /**
     * Creates a new {@link PropertyChangeEvent} and delivers it to all currently registered state listeners.
     *
     * @param oldState
     *            the {@link StreamState} we had before the change
     * @param newState
     *            the {@link StreamState} we had after the change
     */
    protected void fireStateChange(StreamState oldState, StreamState newState) {
        final PropertyChangeEvent evt = new PropertyChangeEvent(this, "StreamState", oldState, newState);
        for (PropertyChangeListener listener : stateListeners) {
            listener.propertyChange(evt);
        }
    }

    /**
     * Adds to the list of listeners tracking changes of the {@link StreamState} of this stream.
     *
     * @param listener
     *            the listener to register
     */
    public void addStateChangeListener(PropertyChangeListener listener) {
        stateListeners.add(listener);
    }

    /**
     * Removes from the list of listeners tracking changes of the {@link StreamState} of this stream.
     *
     * @param listener
     *            the listener to remove
     */
    public void removeStateChangeListener(PropertyChangeListener listener) {
        stateListeners.remove(listener);
    }

    /**
     * Return stream name.
     * 
     * @return Stream name
     */
    public String getName() {
        return name;
    }

    /**
     * Return codec information.
     * 
     * @return Stream codec information
     */
    public IStreamCodecInfo getCodecInfo() {
        return codecInfo;
    }

    /**
     * Returns a copy of the metadata for the associated stream, if it exists.
     * 
     * @return stream meta data
     */
    public Notify getMetaData() {
        Notify md = metaData.get();
        if (md != null) {
            try {
                return md.duplicate();
            } catch (Exception e) {
            }
        }
        return md;
    }

    /**
     * Set the metadata.
     * 
     * @param metaData
     *            stream meta data
     */
    public void setMetaData(Notify metaData) {
        this.metaData.set(metaData);
    }

    /**
     * Return scope.
     * 
     * @return Scope
     */
    public IScope getScope() {
        return scope;
    }

    /**
     * Returns timestamp at which the stream was created.
     * 
     * @return creation timestamp
     */
    public long getCreationTime() {
        return creationTime;
    }

    /**
     * Returns timestamp at which the stream was started.
     * 
     * @return started timestamp
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Setter for name.
     * 
     * @param name
     *            Stream name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Setter for codec info.
     * 
     * @param codecInfo
     *            Codec info
     */
    public void setCodecInfo(IStreamCodecInfo codecInfo) {
        this.codecInfo = codecInfo;
    }

    /**
     * Setter for scope.
     * 
     * @param scope
     *            Scope
     */
    public void setScope(IScope scope) {
        this.scope = scope;
    }

    /**
     * Return stream state.
     * 
     * @return StreamState
     */
    public StreamState getState() {
        return state.get();
    }

    /**
     * Sets the stream state.
     * 
     * @param newState stream state
     */
    public void setState(StreamState newState) {
        StreamState oldState = state.get();
        if (!oldState.equals(newState) && state.compareAndSet(oldState, newState)) {
            fireStateChange(oldState, newState);
        }
    }

    /**
     * Return stream aware scope handler or null if scope is null.
     * 
     * @return IStreamAwareScopeHandler implementation
     */
    protected IStreamAwareScopeHandler getStreamAwareHandler() {
        if (scope != null) {
            IScopeHandler handler = scope.getHandler();
            if (handler instanceof IStreamAwareScopeHandler) {
                return (IStreamAwareScopeHandler) handler;
            }
        }
        return null;
    }
}
