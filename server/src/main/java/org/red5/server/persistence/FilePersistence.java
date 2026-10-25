/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.persistence;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.io.amf.Input;
import org.red5.io.amf.Output;
import org.red5.io.object.Deserializer;
import org.red5.server.api.IContext;
import org.red5.server.api.persistence.IPersistable;
import org.red5.server.api.scheduling.IScheduledJob;
import org.red5.server.api.scheduling.ISchedulingService;
import org.red5.server.api.scope.IScope;
import org.red5.server.net.servlet.ServletUtils;
import org.red5.server.so.SharedObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.web.context.support.ServletContextResource;

/**
 * Simple file-based persistence for objects. Lowers memory usage if used instead of RAM memory storage.
 *
 * @author The Red5 Project
 * @author Joachim Bauch (jojo@struktur.de)
 */
public class FilePersistence extends RamPersistence {

    private Logger log = LoggerFactory.getLogger(FilePersistence.class);

    /**
     * Scheduler for persistence job.
     */
    private ISchedulingService schedulingService;

    /**
     * Modified objects.
     */
    private ConcurrentLinkedQueue<IPersistable> queue = new ConcurrentLinkedQueue<IPersistable>();

    // objects currently waiting in the queue, so a frequently modified object is only queued once per flush
    private final Set<IPersistable> pending = ConcurrentHashMap.newKeySet();

    /**
     * Files path
     */
    private String path = "persistence";

    /**
     * Root directory under file storage path
     */
    private String rootDir = "";

    /**
     * File extension for persistent objects
     */
    private String extension = ".red5";

    /**
     * Whether there's need to check for empty directories
     */
    private boolean checkForEmptyDirectories = true;

    /**
     * Interval to serialize modified objects in milliseconds.
     */
    private int persistenceInterval = 10000;

    /**
     * Name of the job for serializing persistent objects.
     */
    private String storeJobName;

    /**
     * Minimum usable bytes applied to new instances; 0 disables the check.
     */
    private static volatile long defaultMinFreeBytes;

    /**
     * Minimum usable bytes on the persistence volume for a write to proceed; 0 disables the check.
     */
    private volatile long minFreeBytes = defaultMinFreeBytes;

    /**
     * Whether the persistence volume is currently below the free space minimum, so the condition is only logged on change.
     */
    private final AtomicBoolean lowSpace = new AtomicBoolean();

    /**
     * Budget of persisted bytes applied to new instances; 0 disables the budget.
     */
    private static volatile long defaultMaxBytes;

    /**
     * Budget of the bytes of the files under this store's persistence root; 0 disables the budget.
     */
    private volatile long maxBytes = defaultMaxBytes;

    /**
     * Whether a write was skipped for the budget, so the condition is only logged on change.
     */
    private final AtomicBoolean overBudget = new AtomicBoolean();

    /**
     * Whether a write was skipped for the budget during the current flush.
     */
    private boolean budgetSkipped;

    /**
     * Persisted bytes by canonical persistence root. Shared objects of room scopes get stores of their own on the root of their
     * application, so the budget is accounted per root rather than per store.
     */
    private static final ConcurrentMap<String, DiskUsage> diskUsage = new ConcurrentHashMap<>();

    /**
     * Persisted bytes of this store's persistence root, resolved on first use. Guarded by this store.
     */
    private DiskUsage usage;

    /**
     * Outcome of a write
     */
    private enum Write {
        /** written, or nothing to write */
        WRITTEN,
        /** skipped for low space or the budget, retried on the next flush */
        SKIPPED,
        /** failed */
        FAILED
    }

    /**
     * Create file persistence object from given resource pattern resolver
     *
     * @param resolver
     *            Resource pattern resolver and loader
     */
    public FilePersistence(ResourcePatternResolver resolver) {
        super(resolver);
        setPath(path);
    }

    /**
     * Create file persistence object for given scope
     *
     * @param scope
     *            Scope
     */
    public FilePersistence(IScope scope) {
        super(scope);
        setPath(path);
        IContext ctx = scope.getContext();
        if (ctx.hasBean(ISchedulingService.BEAN_NAME)) {
            schedulingService = (ISchedulingService) ctx.getBean(ISchedulingService.BEAN_NAME);
        } else {
            // try the parent
            schedulingService = (ISchedulingService) scope.getParent().getContext().getBean(ISchedulingService.BEAN_NAME);
        }
        // add the job
        storeJobName = schedulingService.addScheduledJob(persistenceInterval, new FilePersistenceJob());
    }

    /**
     * Returns the context path.
     *
     * @param rootFile
     * @return context path
     */
    private String getContextPath(Resource rootFile) {
        String contextPath = null;
        if (rootFile instanceof ServletContextResource) {
            ServletContextResource servletResource = (ServletContextResource) rootFile;
            contextPath = servletResource.getServletContext().getContextPath();
            if ("/".equals(contextPath)) {
                contextPath = "/root";
            }
        } else if (resources instanceof IScope) {
            contextPath = ((IScope) resources).getContextPath();
            if (contextPath == null) {
                contextPath = "/root";
            }
        }
        log.debug("Persistence context path: {}", contextPath);
        return contextPath;
    }

    /**
     * Initializes the root directory and creates it if it doesn't already exist.
     *
     * @param rootFile
     * @param contextPath
     * @throws IOException
     */
    private void initRootDir(Resource rootFile, String contextPath) throws IOException {
        if (rootFile instanceof ServletContextResource) {
            rootDir = String.format("%s/webapps%s", System.getProperty("red5.root"), contextPath);
        } else if (resources instanceof IScope) {
            rootDir = String.format("%s%s", resources.getResource("/").getFile().getAbsolutePath(), contextPath);
        }
        log.debug("Persistence directory path: {}", rootDir);
        File persistDir = new File(rootDir, path);
        if (!persistDir.exists()) {
            if (!persistDir.mkdirs()) {
                log.warn("Persistence directory creation failed");
            } else {
                log.debug("Persistence directory access - read: {} write: {}", persistDir.canRead(), persistDir.canWrite());
            }
        } else {
            log.debug("Persistence directory access - read: {} write: {}", persistDir.canRead(), persistDir.canWrite());
        }
        persistDir = null;
    }

    /**
     * Setter for file path.
     *
     * @param path
     *            New path
     */
    @SuppressWarnings("null")
    public void setPath(String path) {
        log.debug("Set path: {}", path);
        Resource rootFile = resources.getResource(path);
        try {
            log.debug("Absolute path: {}", resources.getResource("/").getFile().getAbsolutePath());
            // check for existence
            if (!rootFile.exists()) {
                log.debug("Persistence directory does not exist");
                // get context path
                String contextPath = getContextPath(rootFile);
                // int root dir
                initRootDir(rootFile, contextPath);
            } else {
                rootDir = rootFile.getFile().getAbsolutePath();
            }
            log.debug("Root dir: {} path: {}", rootDir, path);
            // set the path
            this.path = path;
            synchronized (this) {
                // the root may have changed
                usage = null;
            }
        } catch (IOException err) {
            log.error("I/O exception thrown when setting file path to {}", path, err);
            throw new RuntimeException(err);
        }
    }

    /**
     * Setter for extension.
     *
     * @param extension
     *            New extension.
     */
    public void setExtension(String extension) {
        this.extension = extension;
    }

    /**
     * <p>Setter for the field <code>checkForEmptyDirectories</code>.</p>
     *
     * @param checkForEmptyDirectories
     *            the checkForEmptyDirectories to set
     */
    public void setCheckForEmptyDirectories(boolean checkForEmptyDirectories) {
        this.checkForEmptyDirectories = checkForEmptyDirectories;
    }

    /**
     * <p>Getter for the field <code>persistenceInterval</code>.</p>
     *
     * @return the persistenceInterval
     */
    public int getPersistenceInterval() {
        return persistenceInterval;
    }

    /**
     * <p>Setter for the field <code>persistenceInterval</code>.</p>
     *
     * @param persistenceInterval
     *            the persistenceInterval to set
     */
    public void setPersistenceInterval(int persistenceInterval) {
        this.persistenceInterval = persistenceInterval;
    }

    /**
     * Sets the minimum usable bytes on the persistence volume used by instances created after this call; 0 disables the check. Stores
     * are created reflectively per scope, so this is the configuration hook (so.persistence.min.free.bytes).
     *
     * @param minFreeBytes
     *            minimum usable bytes
     */
    public static void setDefaultMinFreeBytes(long minFreeBytes) {
        defaultMinFreeBytes = minFreeBytes;
    }

    /**
     * Sets the minimum usable bytes on the persistence volume; while usable space is below it, writes are skipped and the objects stay
     * queued for the next flush. 0 disables the check.
     *
     * @param minFreeBytes
     *            minimum usable bytes
     */
    public void setMinFreeBytes(long minFreeBytes) {
        this.minFreeBytes = minFreeBytes;
    }

    /**
     * Sets the budget of persisted bytes used by instances created after this call; 0 disables the budget. Stores are created
     * reflectively per scope, so this is the configuration hook (so.persistence.max.bytes).
     *
     * @param maxBytes
     *            budget in bytes
     */
    public static void setDefaultMaxBytes(long maxBytes) {
        defaultMaxBytes = maxBytes;
    }

    /**
     * Sets the budget of the bytes of the files under this store's persistence root, which is per application; 0 disables the budget. The
     * files already present are counted the first time the budget is checked. A write that would take the total beyond the budget is
     * skipped and the object stays queued for the next flush, so it is written once removed or smaller objects make room.
     *
     * @param maxBytes
     *            budget in bytes
     */
    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    /**
     * Returns the total size of the persisted files under this store's persistence root counted against the budget; 0 until the budget
     * has been checked once.
     *
     * @return persisted bytes
     */
    public synchronized long getPersistedBytes() {
        return getUsage().total();
    }

    /**
     * Returns the usage of this store's persistence root, shared with the other stores on that root. Called while holding this store's
     * lock.
     *
     * @return disk usage
     */
    private DiskUsage getUsage() {
        if (usage == null) {
            File root;
            try {
                root = getPersistenceRoot();
            } catch (IOException e) {
                root = new File(rootDir, path);
            }
            usage = diskUsage.computeIfAbsent(canonicalPath(root), key -> new DiskUsage(new File(key)));
        }
        return usage;
    }

    /**
     * Return file path for persistable object
     *
     * @param object
     *            Object to obtain file path for
     * @return Path on disk
     */
    private String getObjectFilepath(IPersistable object) {
        return getObjectFilepath(object, false);
    }

    /**
     * Return file path for persistable object
     *
     * @param object
     *            Object to obtain file path for
     * @param completePath
     *            Whether full path should be returned
     * @return Path on disk
     */
    private String getObjectFilepath(IPersistable object, boolean completePath) {
        StringBuilder result = new StringBuilder(path);
        result.append('/');
        result.append(object.getType());
        result.append('/');

        String objectPath = object.getPath();
        log.debug("Object path: {}", objectPath);
        result.append(objectPath);
        if (!objectPath.endsWith("/")) {
            result.append('/');
        }
        if (completePath) {
            String name = object.getName();
            log.debug("Object name: {}", name);
            int pos = name.lastIndexOf('/');
            if (pos >= 0) {
                result.append(name.substring(0, pos));
            }
        }
        //fix up path
        int idx = -1;
        if (File.separatorChar != '/') {
            while ((idx = result.indexOf(File.separator)) != -1) {
                result.deleteCharAt(idx);
                result.insert(idx, '/');
            }
        }
        //remove any './'
        if ((idx = result.indexOf("./")) != -1) {
            result.delete(idx, idx + 2);
        }
        //remove any '//'
        while ((idx = result.indexOf("//")) != -1) {
            result.deleteCharAt(idx);
        }
        if (log.isDebugEnabled()) {
            log.debug("Adjusted object path: {}", result.toString());
        }
        return result.toString();
    }

    /** {@inheritDoc} */
    @Override
    protected String getObjectPath(String id, String name) {
        if (id.startsWith(path)) {
            id = id.substring(path.length() + 1);
        }
        return super.getObjectPath(id, name);
    }

    /**
     * Get filename for persistable object
     *
     * @param object
     *            Persistable object
     * @return Name of file where given object is persisted to
     */
    private String getObjectFilename(IPersistable object) {
        String path = getObjectFilepath(object);
        String name = object.getName();
        if (name == null) {
            name = PERSISTENCE_NO_NAME;
        }
        return path + name + extension;
    }

    /**
     * Load resource with given name
     *
     * @param name
     *            Resource name
     * @return Persistable object
     */
    private IPersistable doLoad(String name) {
        return doLoad(name, null);
    }

    /**
     * Load resource with given name and attaches to persistable object
     *
     * @param name
     *            Resource name
     * @param object
     *            Object to attach to
     * @return Persistable object
     */
    @SuppressWarnings({ "deprecation", "null" })
    private IPersistable doLoad(String name, IPersistable object) {
        log.debug("doLoad - name: {} object: {}", name, object);
        IPersistable result = object;
        //		if (log.isTraceEnabled()) {
        //			try {
        //				log.trace("Relative #1: {}", (resources.getResource(name) != null ? resources.getResource(name).getFile().getAbsolutePath() : "Not found"));
        //				log.trace("Absolute #2: {}", (resources.getResource("file://" + rootDir + '/' + name) != null ? resources.getResource("file://" + rootDir + '/' + name).getFile()
        //						.getAbsolutePath() : "Not found"));
        //			} catch (IOException e) {
        //				log.warn("", e);
        //			}
        //		}
        Resource data = resources.getResource(name);
        if (data == null || !data.exists()) {
            // no such file
            log.debug("Resource / data was not found");
            // try again with full path
            data = resources.getResource("file://" + rootDir + '/' + name);
            if (data == null || !data.exists()) {
                log.debug("Resource / data was not found (full path)");
                return null;
            }
        }
        FileInputStream input;
        String filename;
        try {
            File fp = data.getFile();
            if (fp.length() == 0) {
                // File is empty
                log.error("The file at {} is empty", data.getFilename());
                return null;
            }
            filename = fp.getAbsolutePath();
            input = new FileInputStream(filename);
        } catch (FileNotFoundException e) {
            log.error("The file at {} does not exist", data.getFilename());
            return null;
        } catch (IOException e) {
            log.error("Could not load file from {}", data.getFilename(), e);
            return null;
        }

        try {
            IoBuffer buf = IoBuffer.allocate(input.available());
            try {
                ServletUtils.copy(input, buf.asOutputStream());
                buf.flip();
                Input in = new Input(buf);
                String className = Deserializer.deserialize(in, String.class);
                if (result == null) {
                    // we need to create the object first
                    try {
                        Class<?> theClass = Class.forName(className);
                        Constructor<?> constructor = null;
                        try {
                            // try to create object by calling constructor with Input stream as parameter
                            for (Class<?> interfaceClass : in.getClass().getInterfaces()) {
                                constructor = theClass.getConstructor(new Class[] { interfaceClass });
                                if (constructor != null) {
                                    break;
                                }
                            }
                            if (constructor == null) {
                                throw new NoSuchMethodException();
                            }
                            result = (IPersistable) constructor.newInstance(in);
                        } catch (Exception err) {
                            // error while invoking found constructor, use empty constructor
                            result = (IPersistable) theClass.newInstance();
                            result.deserialize(in);
                        }
                    } catch (ClassNotFoundException cnfe) {
                        log.error("Unknown class {}", className);
                        return null;
                    } catch (IllegalAccessException iae) {
                        log.error("Illegal access", iae);
                        return null;
                    } catch (InstantiationException ie) {
                        log.error("Could not instantiate class {}", className);
                        return null;
                    }
                    // set object's properties
                    log.debug("Name (after load): {}", result.getName());
                    result.setPath(getObjectPath(name, result.getName()));
                } else {
                    // Initialize existing object
                    String resultClass = result.getClass().getName();
                    if (!resultClass.equals(className)) {
                        log.error("The classes differ: {} != {}", resultClass, className);
                        return null;
                    }
                    result.deserialize(in);
                }
            } finally {
                buf.free();
                buf = null;
            }
            if (result.getStore() != this) {
                result.setStore(this);
            }
            super.save(result);
            log.debug("Loaded persistent object {} from {}", result, filename);
        } catch (IOException e) {
            log.error("Could not load file at {}", filename);
            return null;
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public IPersistable load(String name) {
        log.debug("load - name: {}", name);
        if (!isValidObjectName(name)) {
            log.warn("Refusing to load object with invalid name: {}", name);
            return null;
        }
        IPersistable result = super.load(name);
        if (result != null) {
            // Object has already been loaded
            return result;
        }
        return doLoad(path + '/' + name + extension);
    }

    /** {@inheritDoc} */
    @Override
    public boolean load(IPersistable object) {
        log.debug("load - name: {}", object);
        if (object.isPersistent()) {
            // already loaded
            return true;
        }
        return (doLoad(getObjectFilename(object), object) != null);
    }

    /**
     * Save persistable object
     *
     * @param object
     *            Persistable object
     * @return a boolean
     */
    protected synchronized boolean saveObject(IPersistable object) {
        return write(object) == Write.WRITTEN;
    }

    /**
     * Writes a persistable object to its file. Called while holding this store's lock.
     *
     * @param object
     *            Persistable object
     * @return whether the object was written, skipped for space or budget, or failed
     */
    @SuppressWarnings("null")
    private Write write(IPersistable object) {
        log.debug("saveObject - object: {}", object);
        if (!hasValidNameAndPath(object)) {
            log.warn("Refusing to persist object with invalid name or path: {} / {}", object.getName(), object.getPath());
            return Write.FAILED;
        }
        boolean empty = false;
        if (object instanceof SharedObject) {
            SharedObject soRef = (SharedObject) object;
            if (soRef.isClosed()) {
                // a closed shared object has dropped its attributes, the stored state is kept as it is
                return Write.WRITTEN;
            }
            // an empty shared object is not written to disk. APPSERVER-364
            empty = soRef.getAttributes().isEmpty();
        }
        String path = getObjectFilepath(object, true);
        log.trace("Path: {}", path);
        Resource resPath = resources.getResource(path);
        boolean exists = resPath.exists();
        log.debug("Resource (relative dir) exists: {}", exists);
        File dir = null;
        try {
            if (!exists) {
                resPath = resources.getResource("classpath:" + path);
                exists = resPath.exists();
                log.debug("Resource (classpath dir) exists: {}", exists);
                if (!exists) {
                    StringBuilder root = new StringBuilder(rootDir);
                    //fix up path
                    int idx = -1;
                    if (File.separatorChar != '/') {
                        while ((idx = root.indexOf(File.separator)) != -1) {
                            root.deleteCharAt(idx);
                            root.insert(idx, '/');
                        }
                    }
                    resPath = resources.getResource("file://" + root.toString() + File.separatorChar + path);
                    exists = resPath.exists();
                    log.debug("Resource (absolute dir) exists: {}", exists);
                }
            }
            dir = resPath.getFile();
            log.debug("Resulting absolute path: {}", dir.getAbsolutePath());
        } catch (IOException err) {
            log.error("Could not create resource file for path {}", path, err);
            return Write.FAILED;
        }
        String filename = getObjectFilename(object);
        log.debug("File name: {}", filename);
        //strip path
        if (filename.indexOf('/') != -1) {
            filename = filename.substring(filename.lastIndexOf('/'));
            log.debug("New file name: {}", filename);
        }
        File file = new File(dir, filename);
        // containment, free space and the budget are checked before any directory is created
        if (!isWithinPersistenceRoot(file)) {
            log.warn("Refusing to persist object {} outside the persistence root: {}", object.getName(), file);
            return Write.FAILED;
        }
        if (empty) {
            // the file of an earlier state is removed, otherwise cleared attributes would come back on the next load
            if (file.isFile()) {
                String key = canonicalPath(file);
                if (!file.delete()) {
                    log.warn("Could not remove the file of empty object {}: {}", object.getName(), file);
                    return Write.FAILED;
                }
                releaseUsage(key);
                checkRemoveEmptyDirectories(getObjectFilename(object));
                log.debug("Removed the file of empty object {}", object.getName());
            }
            return Write.WRITTEN;
        }
        if (!hasFreeSpace(dir)) {
            return Write.SKIPPED;
        }
        Write result = Write.WRITTEN;
        IoBuffer buf = null;
        try {
            int initialSize = 8192;
            if (file.exists()) {
                // We likely also need the original file size when writing object
                initialSize += (int) file.length();
            }
            buf = IoBuffer.allocate(initialSize);
            buf.setAutoExpand(true);
            Output out = new Output(buf);
            out.writeString(object.getClass().getName());
            object.serialize(out);
            buf.flip();
            // the encoded size is what the file will take; it is reserved against the budget before anything is written
            final long size = buf.remaining();
            if (!reserve(file, size)) {
                return Write.SKIPPED;
            }
            if (!dir.isDirectory() && !dir.mkdirs()) {
                log.error("Could not create directory {}", dir.getAbsolutePath());
                getUsage().update(canonicalPath(file), file.isFile() ? file.length() : 0L);
                return Write.FAILED;
            }
            try (FileOutputStream output = new FileOutputStream(file.getAbsolutePath())) {
                ServletUtils.copy(buf.asInputStream(), output);
            }
            log.debug("Stored persistent object {} at {}", object, filename);
        } catch (IOException e) {
            log.error("Could not create / write file {}", filename, e);
            log.warn("Exception {}", e);
            // the file may have been left as it was, or truncated
            getUsage().update(canonicalPath(file), file.isFile() ? file.length() : 0L);
            result = Write.FAILED;
        } finally {
            if (buf != null) {
                buf.free();
                buf = null;
            }
        }
        return result;
    }

    /**
     * Reserves the size a file is about to take against the budget of its persistence root. Skips are logged once when the condition
     * starts; it ends when a flush has written everything it could without a budget skip. Called while holding this store's lock.
     *
     * @param file
     *            target file, which may exist already
     * @param size
     *            bytes to write
     * @return true if the write fits or no budget is configured
     */
    private boolean reserve(File file, long size) {
        final long max = maxBytes;
        final DiskUsage disk = getUsage();
        if (disk.reserve(canonicalPath(file), size, max, extension)) {
            return true;
        }
        budgetSkipped = true;
        if (overBudget.compareAndSet(false, true)) {
            log.error("Persistent objects under {} take {} bytes, writing {} more would exceed the budget of {} bytes; objects are not written until others are removed or shrink", disk.root, disk.total(), size, max);
        }
        return false;
    }

    /**
     * Removes a deleted file from the usage of its persistence root.
     *
     * @param key
     *            canonical path of the file, resolved before it was deleted
     */
    private synchronized void releaseUsage(String key) {
        getUsage().release(key);
    }

    /**
     * Sizes of the persisted files under one persistence root, shared by the stores on that root. Files are only tracked once a budget has
     * been checked, which first counts the files already present.
     */
    private static final class DiskUsage {

        private final File root;

        private final Map<String, Long> files = new HashMap<>();

        private long total;

        private boolean scanned;

        DiskUsage(File root) {
            this.root = root;
        }

        /**
         * Records the size a file is about to take, unless that would take the total beyond the maximum.
         *
         * @param key
         *            canonical path of the file
         * @param size
         *            its new size
         * @param max
         *            maximum total, 0 for none
         * @param extension
         *            extension of persisted files, for the first count
         * @return true if recorded, false if the maximum would be exceeded
         */
        synchronized boolean reserve(String key, long size, long max, String extension) {
            if (max > 0 && !scanned) {
                scan(extension);
            }
            if (!scanned) {
                return true;
            }
            Long previous = files.get(key);
            long next = total - (previous != null ? previous : 0L) + size;
            if (max > 0 && next > max) {
                return false;
            }
            files.put(key, size);
            total = next;
            return true;
        }

        /**
         * Replaces the recorded size of a file, as after a failed write.
         *
         * @param key
         *            canonical path of the file
         * @param size
         *            its actual size
         */
        synchronized void update(String key, long size) {
            if (scanned) {
                Long previous = files.put(key, size);
                total += size - (previous != null ? previous : 0L);
            }
        }

        /**
         * Removes a deleted file.
         *
         * @param key
         *            canonical path of the file
         */
        synchronized void release(String key) {
            if (scanned) {
                Long previous = files.remove(key);
                if (previous != null) {
                    total -= previous;
                }
            }
        }

        synchronized long total() {
            return total;
        }

        /**
         * Counts the files already under the root.
         *
         * @param extension
         *            extension of persisted files
         */
        private void scan(String extension) {
            scanned = true;
            if (!root.isDirectory()) {
                return;
            }
            // links are not followed, files outside the root are not counted
            try (Stream<Path> paths = Files.walk(root.toPath())) {
                paths.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(extension)).forEach(p -> {
                    File existing = p.toFile();
                    long length = existing.length();
                    if (files.putIfAbsent(canonicalPath(existing), length) == null) {
                        total += length;
                    }
                });
                LoggerFactory.getLogger(FilePersistence.class).debug("Persisted objects under {} take {} bytes", root, total);
            } catch (IOException | UncheckedIOException e) {
                LoggerFactory.getLogger(FilePersistence.class).warn("Could not count the persisted objects under {}", root, e);
            }
        }

    }

    /**
     * Returns the canonical path of a file, or its absolute path if it cannot be resolved.
     *
     * @param file
     *            file
     * @return path used as tracking key
     */
    private static String canonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }

    /**
     * Checks the object name and path against the identifier grammar used for file persistence.
     *
     * @param object
     *            Persistable object
     * @return true if the name and path may be mapped onto the filesystem
     */
    private boolean hasValidNameAndPath(IPersistable object) {
        // object paths are scope context paths such as /live, so one leading slash is expected
        String objectPath = object.getPath();
        if (objectPath != null && objectPath.startsWith("/")) {
            objectPath = objectPath.substring(1);
        }
        return isValidObjectName(object.getName()) && (objectPath == null || objectPath.isEmpty() || isValidObjectName(objectPath));
    }

    /**
     * Checks that the volume holding the given directory has at least the configured minimum of usable space. The low space condition is
     * logged once when it is detected and once when it clears, not once per object.
     *
     * @param dir
     *            target directory, which may not exist yet
     * @return true if there is enough usable space or no minimum is configured
     */
    private boolean hasFreeSpace(File dir) {
        if (minFreeBytes <= 0) {
            lowSpace.set(false);
            return true;
        }
        // usable space is only reported for existing paths
        File existing = dir;
        while (existing != null && !existing.exists()) {
            existing = existing.getParentFile();
        }
        if (existing == null) {
            log.warn("Could not check free space for {}", dir);
            return true;
        }
        long usable = existing.getUsableSpace();
        if (usable < minFreeBytes) {
            if (lowSpace.compareAndSet(false, true)) {
                log.error("Persistence volume at {} has {} usable bytes, below the {} byte minimum; persistent objects are not written until space is available", existing, usable, minFreeBytes);
            }
            return false;
        }
        if (lowSpace.compareAndSet(true, false)) {
            log.info("Persistence volume at {} has {} usable bytes again, resuming writes", existing, usable);
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean save(IPersistable object) {
        if (super.save(object)) {
            if (pending.add(object)) {
                queue.add(object);
            }
            return true;
        }
        return false;
    }

    /**
     * Returns the directory every persisted file must live beneath.
     *
     * @return persistence root directory
     * @throws IOException if the root cannot be resolved
     */
    private File getPersistenceRoot() throws IOException {
        Resource res = resources.getResource(path);
        if (res.exists()) {
            return res.getFile();
        }
        return new File(rootDir, path);
    }

    /**
     * Checks that a file resolves beneath the persistence root, after normalizing any traversal components.
     *
     * @param file candidate file
     * @return true if the file is contained in the persistence root
     */
    private boolean isWithinPersistenceRoot(File file) {
        try {
            String root = getPersistenceRoot().getCanonicalPath();
            String candidate = file.getCanonicalPath();
            return candidate.startsWith(root + File.separator);
        } catch (IOException e) {
            log.warn("Unable to resolve persistence root", e);
            return false;
        }
    }

    /**
     * Remove empty dirs
     *
     * @param base
     *            Base directory
     */
    @SuppressWarnings("null")
    protected void checkRemoveEmptyDirectories(String base) {
        if (checkForEmptyDirectories) {
            String dir;
            Resource resFile = resources.getResource(base.substring(0, base.lastIndexOf('/')));
            try {
                dir = resFile.getFile().getAbsolutePath();
            } catch (IOException err) {
                return;
            }
            while (!dir.equals(rootDir)) {
                File fp = new File(dir);
                if (!fp.isDirectory()) {
                    // This should never happen
                    break;
                }
                if (fp.list().length != 0) {
                    // Directory is not empty
                    break;
                }
                if (!fp.delete()) {
                    // Could not remove directory
                    break;
                }
                // Move up one directory
                dir = fp.getParent();
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized boolean remove(String name) {
        if (!isValidObjectName(name)) {
            log.warn("Refusing to remove object with invalid name: {}", name);
            return false;
        }
        IPersistable removed = objects.get(name);
        super.remove(name);
        if (removed != null && pending.remove(removed)) {
            // a queued write would bring the file back
            queue.remove(removed);
        }
        boolean result = true;
        String filename = path + '/' + name + extension;
        Resource resFile = resources.getResource(filename);
        if (!resFile.exists()) {
            // resolved against the root directory, as when the object was written or loaded
            resFile = resources.getResource("file://" + rootDir + '/' + filename);
        }
        if (resFile.exists()) {
            try {
                File target = resFile.getFile();
                if (!isWithinPersistenceRoot(target)) {
                    log.warn("Refusing to remove file outside the persistence root: {}", target);
                    return false;
                }
                String key = canonicalPath(target);
                result = target.delete();
                if (result) {
                    releaseUsage(key);
                    checkRemoveEmptyDirectories(filename);
                }
            } catch (IOException err) {
                result = false;
            }
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean remove(IPersistable object) {
        return remove(getObjectId(object));
    }

    /** {@inheritDoc} */
    @Override
    public void notifyClose() {
        // stop the job
        if (storeJobName != null) {
            schedulingService.removeScheduledJob(storeJobName);
            storeJobName = null;
        }
        // write any pending objects
        persist();
        //
        super.notifyClose();
    }

    /**
     * Writes an object's pending changes, then drops it from memory so the next {@link #load(String)} reads it from its file. An object
     * whose changes are skipped for low space or the budget, or fail, stays loaded and queued.
     *
     * @param object
     *            the object to evict
     * @return true if the object was dropped from memory
     */
    @Override
    public synchronized boolean evict(IPersistable object) {
        // release before writing so a change made during the write queues the object again
        if (pending.remove(object)) {
            queue.remove(object);
            if (write(object) != Write.WRITTEN) {
                if (pending.add(object)) {
                    queue.add(object);
                }
                return false;
            }
        }
        objects.remove(getObjectId(object), object);
        log.debug("Evicted persistent object {}", object);
        return true;
    }

    private synchronized void persist() {
        budgetSkipped = false;
        IPersistable persistable = null;
        List<IPersistable> skipped = null;
        while (!queue.isEmpty()) {
            try {
                persistable = queue.poll();
                if (persistable == null) {
                    break;
                }
                // release before writing so a change made during the write queues the object again
                pending.remove(persistable);
                Write result = write(persistable);
                if (result == Write.SKIPPED) {
                    // a write skipped for low space or the budget is retried on the next flush, not within this one
                    log.debug("Object persist skipped for: {}", persistable);
                    if (skipped == null) {
                        skipped = new ArrayList<>();
                    }
                    skipped.add(persistable);
                } else if (result == Write.FAILED) {
                    // other failures are not retried until the object changes again, so a permanent failure is not logged every flush
                    log.warn("Object persist failed for: {}", persistable);
                }
            } catch (Throwable e) {
                log.error("Error while saving {} in {}. {}", new Object[] { persistable, this, e });
            }
        }
        if (skipped != null) {
            for (IPersistable object : skipped) {
                // objects removed from the store meanwhile are not written again
                if (objects.get(getObjectId(object)) == object && pending.add(object)) {
                    queue.add(object);
                }
            }
        }
        if (!budgetSkipped && overBudget.compareAndSet(true, false)) {
            log.info("Persistent objects under {} fit the budget of {} bytes again, resuming writes", rootDir, maxBytes);
        }
    }

    private final class FilePersistenceJob implements IScheduledJob {

        public void execute(ISchedulingService svc) {
            persist();
        }

    }

}
