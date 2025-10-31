/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.amf;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.beanutils.BeanMap;
import org.apache.mina.core.buffer.IoBuffer;
import org.red5.annotations.Anonymous;
import org.red5.io.amf3.ByteArray;
import org.red5.io.object.BaseOutput;
import org.red5.io.object.RecordSet;
import org.red5.io.object.Serializer;
import org.red5.io.utils.XMLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;

/**
 * <p>Output class.</p>
 *
 * @author The Red5 Project
 * @author Luke Hubbard, Codegent Ltd (luke@codegent.com)
 * @author Paul Gregoire (mondain@gmail.com)
 * @author Harald Radi (harald.radi@nme.at)
 */
public class Output extends BaseOutput implements org.red5.io.object.Output {

    /** Constant <code>log</code> */
    protected static Logger log = LoggerFactory.getLogger(Output.class);

    private static ReentrantLock lookupLock = new ReentrantLock();

    private static Cache<String, byte[]> stringCache;

    private static Cache<Class<?>, Map<String, Boolean>> serializeCache;

    private static Cache<Class<?>, Map<String, Field>> fieldCache;

    private static Cache<Class<?>, Map<String, Method>> getterCache;

    private static volatile boolean cacheInitialized = false;

    protected static void initializeCaches() {
        if (!cacheInitialized) {
            lookupLock.lock();
            try {
                if (!cacheInitialized) {
                    log.debug("Initializing Caffeine caches for AMF serialization");

                    // String cache - stores encoded UTF-8 byte arrays for strings
                    // Configured for: max 1000 entries, 20 minute idle timeout
                    stringCache = Caffeine.newBuilder().maximumSize(1000).expireAfterAccess(20, TimeUnit.MINUTES).recordStats().build();

                    // Serialize cache - stores serialization decisions for class fields
                    // Configured for: max 200 entries, 20 minute idle timeout
                    serializeCache = Caffeine.newBuilder().maximumSize(200).expireAfterAccess(20, TimeUnit.MINUTES).recordStats().build();

                    // Field cache - stores reflection field lookups
                    // Configured for: max 200 entries, 20 minute idle timeout
                    fieldCache = Caffeine.newBuilder().maximumSize(200).expireAfterAccess(20, TimeUnit.MINUTES).recordStats().build();

                    // Getter cache - stores getter method references
                    // Configured for: max 200 entries, 20 minute idle timeout
                    getterCache = Caffeine.newBuilder().maximumSize(200).expireAfterAccess(20, TimeUnit.MINUTES).recordStats().build();

                    cacheInitialized = true;
                    log.info("Caffeine caches initialized for AMF serialization");
                }
            } finally {
                lookupLock.unlock();
            }
        }
    }

    /**
     * Output buffer
     */
    protected IoBuffer buf;

    /**
     * Creates output with given byte buffer
     *
     * @param buf
     *            Byte buffer
     */
    public Output(IoBuffer buf) {
        super();
        this.buf = buf;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isCustom(Object custom) {
        return false;
    }

    /**
     * <p>checkWriteReference.</p>
     *
     * @param obj a {@link java.lang.Object} object
     * @return a boolean
     */
    protected boolean checkWriteReference(Object obj) {
        if (hasReference(obj)) {
            writeReference(obj);
            return true;
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public void writeArray(Collection<?> array) {
        log.debug("writeArray - (collection source) array: {}", array);
        if (!checkWriteReference(array)) {
            storeReference(array);
            buf.put(AMF.TYPE_ARRAY);
            buf.putInt(array.size());
            for (Object item : array) {
                Serializer.serialize(this, item);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public void writeArray(Object[] array) {
        log.debug("writeArray - (array source) array: {}", Arrays.asList(array));
        if (array != null) {
            if (!checkWriteReference(array)) {
                storeReference(array);
                buf.put(AMF.TYPE_ARRAY);
                buf.putInt(array.length);
                for (Object item : array) {
                    Serializer.serialize(this, item);
                }
            }
        } else {
            writeNull();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void writeArray(Object array) {
        log.debug("writeArray - (object source) array: {}", array);
        if (array != null) {
            if (!checkWriteReference(array)) {
                storeReference(array);
                buf.put(AMF.TYPE_ARRAY);
                final int length = Array.getLength(array);
                buf.putInt(length);
                for (int i = 0; i < length; i++) {
                    Serializer.serialize(this, Array.get(array, i));
                }
            }
        } else {
            writeNull();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void writeMap(Map<Object, Object> map) {
        //log.info("writeMap: {}", map);
        if (!checkWriteReference(map)) {
            storeReference(map);
            buf.put(AMF.TYPE_MIXED_ARRAY);
            int maxInt = -1;
            for (int i = 0; i < map.size(); i++) {
                try {
                    if (!map.containsKey(i)) {
                        break;
                    }
                } catch (ClassCastException err) {
                    // map has non-number keys
                    break;
                }
                maxInt = i;
            }
            buf.putInt(maxInt + 1);
            // TODO: Need to support an incoming key named length
            for (Map.Entry<Object, Object> entry : map.entrySet()) {
                final String key = entry.getKey().toString();
                //log.info("key: {} item: {}", key, entry.getValue());
                if ("length".equals(key)) {
                    continue;
                }
                putString(key);
                Serializer.serialize(this, entry.getValue());
            }
            if (maxInt >= 0) {
                putString("length");
                Serializer.serialize(this, maxInt + 1);
            }
            buf.put(AMF.END_OF_OBJECT_SEQUENCE);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void writeMap(Collection<?> array) {
        if (!checkWriteReference(array)) {
            storeReference(array);
            buf.put(AMF.TYPE_MIXED_ARRAY);
            buf.putInt(array.size() + 1);
            int idx = 0;
            for (Object item : array) {
                if (item != null) {
                    putString(String.valueOf(idx++));
                    Serializer.serialize(this, item);
                } else {
                    idx++;
                }
            }
            putString("length");
            Serializer.serialize(this, array.size() + 1);
            buf.put(AMF.END_OF_OBJECT_SEQUENCE);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void writeRecordSet(RecordSet recordset) {
        if (!checkWriteReference(recordset)) {
            storeReference(recordset);
            // Write out start of object marker
            buf.put(AMF.TYPE_CLASS_OBJECT);
            putString("RecordSet");
            // Serialize
            Map<String, Object> info = recordset.serialize();
            // Write out serverInfo key
            putString("serverInfo");
            // Serialize
            Serializer.serialize(this, info);
            // Write out end of object marker
            buf.put(AMF.END_OF_OBJECT_SEQUENCE);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void writeBoolean(Boolean bol) {
        buf.put(AMF.TYPE_BOOLEAN);
        buf.put(bol ? AMF.VALUE_TRUE : AMF.VALUE_FALSE);
    }

    /** {@inheritDoc} */
    @Override
    public void writeCustom(Object custom) {
    }

    /** {@inheritDoc} */
    @Override
    public void writeDate(Date date) {
        buf.put(AMF.TYPE_DATE);
        buf.putDouble(date.getTime());
        buf.putShort((short) (TimeZone.getDefault().getRawOffset() / 60 / 1000));
    }

    /** {@inheritDoc} */
    @Override
    public void writeNull() {
        // System.err.println("Write null");
        buf.put(AMF.TYPE_NULL);
    }

    /** {@inheritDoc} */
    @Override
    public void writeNumber(Number num) {
        buf.put(AMF.TYPE_NUMBER);
        buf.putDouble(num.doubleValue());
    }

    /** {@inheritDoc} */
    @Override
    public void writeReference(Object obj) {
        log.debug("Write reference");
        buf.put(AMF.TYPE_REFERENCE);
        buf.putShort(getReferenceId(obj));
    }

    /** {@inheritDoc} */
    @SuppressWarnings({ "rawtypes" })
    @Override
    public void writeObject(Object object) {
        if (!checkWriteReference(object)) {
            storeReference(object);
            // create new map out of bean properties
            BeanMap beanMap = new BeanMap(object);
            // set of bean attributes
            Set attrs = beanMap.keySet();
            log.trace("Bean map keys: {}", attrs);
            if (attrs.size() == 0 || (attrs.size() == 1 && beanMap.containsKey("class"))) {
                // beanMap is empty or can only access "class" attribute, skip it
                writeArbitraryObject(object);
                return;
            }
            // write out either start of object marker for class name or "empty" start of object marker
            Class<?> objectClass = object.getClass();
            if (!objectClass.isAnnotationPresent(Anonymous.class)) {
                buf.put(AMF.TYPE_CLASS_OBJECT);
                putString(buf, Serializer.getClassName(objectClass));
            } else {
                buf.put(AMF.TYPE_OBJECT);
            }
            // if (object instanceof ICustomSerializable) {
            //     ((ICustomSerializable) object).serialize(this);
            //     buf.put(AMF.END_OF_OBJECT_SEQUENCE);
            //     return;
            // }
            // Iterate thru entries and write out property names with separators
            for (Object key : attrs) {
                String fieldName = key.toString();
                log.debug("Field name: {} class: {}", fieldName, objectClass);
                Field field = getField(objectClass, fieldName);
                Method getter = getGetter(objectClass, beanMap, fieldName);
                // Check if the Field corresponding to the getter/setter pair is transient
                if (!serializeField(objectClass, fieldName, field, getter)) {
                    continue;
                }
                putString(buf, fieldName);
                Serializer.serialize(this, field, getter, object, beanMap.get(key));
            }
            // write out end of object mark
            buf.put(AMF.END_OF_OBJECT_SEQUENCE);
        }
    }

    /**
     * <p>serializeField.</p>
     *
     * @param objectClass a {@link java.lang.Class} object
     * @param keyName a {@link java.lang.String} object
     * @param field a {@link java.lang.reflect.Field} object
     * @param getter a {@link java.lang.reflect.Method} object
     * @return a boolean
     */
    protected boolean serializeField(Class<?> objectClass, String keyName, Field field, Method getter) {
        initializeCaches();
        Map<String, Boolean> serializeMap = getSerializeCache().get(objectClass, k -> new HashMap<>());
        return serializeMap.computeIfAbsent(keyName, k -> Serializer.serializeField(keyName, field, getter));
    }

    /**
     * <p>getField.</p>
     *
     * @param objectClass a {@link java.lang.Class} object
     * @param keyName a {@link java.lang.String} object
     * @return a {@link java.lang.reflect.Field} object
     */
    protected Field getField(Class<?> objectClass, String keyName) {
        initializeCaches();
        Map<String, Field> fieldMap = getFieldCache().get(objectClass, k -> new HashMap<>());
        return fieldMap.computeIfAbsent(keyName, k -> {
            for (Class<?> clazz = objectClass; !clazz.equals(Object.class); clazz = clazz.getSuperclass()) {
                Field[] fields = clazz.getDeclaredFields();
                if (fields.length > 0) {
                    for (Field fld : fields) {
                        if (fld.getName().equals(keyName)) {
                            return fld;
                        }
                    }
                }
            }
            return null;
        });
    }

    /**
     * <p>getGetter.</p>
     *
     * @param objectClass a {@link java.lang.Class} object
     * @param beanMap a {@link org.apache.commons.beanutils.BeanMap} object
     * @param keyName a {@link java.lang.String} object
     * @return a {@link java.lang.reflect.Method} object
     */
    protected Method getGetter(Class<?> objectClass, BeanMap beanMap, String keyName) {
        initializeCaches();
        Map<String, Method> getterMap = getGetterCache().get(objectClass, k -> new HashMap<>());
        return getterMap.computeIfAbsent(keyName, k -> beanMap.getReadMethod(keyName));
    }

    /** {@inheritDoc} */
    @Override
    public void writeObject(Map<Object, Object> map) {
        if (!checkWriteReference(map)) {
            storeReference(map);
            buf.put(AMF.TYPE_OBJECT);
            boolean isBeanMap = (map instanceof BeanMap);
            for (Map.Entry<Object, Object> entry : map.entrySet()) {
                log.debug("Key: {} item: {}", entry.getKey(), entry.getValue());
                if (isBeanMap && "class".equals(entry.getKey())) {
                    continue;
                }
                putString(entry.getKey().toString());
                Serializer.serialize(this, entry.getValue());
            }
            buf.put(AMF.END_OF_OBJECT_SEQUENCE);
        }
    }

    /**
     * Writes an arbitrary object to the output.
     *
     * @param object
     *            Object to write
     */
    protected void writeArbitraryObject(Object object) {
        log.debug("writeObject");
        // If we need to serialize class information...
        Class<?> objectClass = object.getClass();
        if (!objectClass.isAnnotationPresent(Anonymous.class)) {
            // Write out start object marker for class name
            buf.put(AMF.TYPE_CLASS_OBJECT);
            putString(buf, Serializer.getClassName(objectClass));
        } else {
            // Write out start object marker without class name
            buf.put(AMF.TYPE_OBJECT);
        }
        // Iterate thru fields of an object to build "name-value" map from it
        for (Field field : objectClass.getFields()) {
            String fieldName = field.getName();
            log.debug("Field: {} class: {}", field, objectClass);
            // Check if the Field corresponding to the getter/setter pair is transient
            if (!serializeField(objectClass, fieldName, field, null)) {
                continue;
            }
            Object value;
            try {
                // Get field value
                value = field.get(object);
            } catch (IllegalAccessException err) {
                // Swallow on private and protected properties access exception
                continue;
            }
            // Write out prop name
            putString(buf, fieldName);
            // Write out
            Serializer.serialize(this, field, null, object, value);
        }
        // write out end of object marker
        buf.put(AMF.END_OF_OBJECT_SEQUENCE);
    }

    /** {@inheritDoc} */
    @Override
    public void writeString(String string) {
        final byte[] encoded = encodeString(string);
        final int len = encoded.length;
        if (len < AMF.LONG_STRING_LENGTH) {
            buf.put(AMF.TYPE_STRING);
            // write unsigned short
            buf.put((byte) ((len >> 8) & 0xff));
            buf.put((byte) (len & 0xff));
        } else {
            buf.put(AMF.TYPE_LONG_STRING);
            buf.putInt(len);
        }
        buf.put(encoded);
    }

    /** {@inheritDoc} */
    @Override
    public void writeByteArray(ByteArray array) {
        throw new RuntimeException("ByteArray objects not supported with AMF0");
    }

    /** {@inheritDoc} */
    @Override
    public void writeVectorInt(Vector<Integer> vector) {
        throw new RuntimeException("Vector objects not supported with AMF0");
    }

    /** {@inheritDoc} */
    @Override
    public void writeVectorUInt(Vector<Long> vector) {
        throw new RuntimeException("Vector objects not supported with AMF0");
    }

    /** {@inheritDoc} */
    @Override
    public void writeVectorNumber(Vector<Double> vector) {
        throw new RuntimeException("Vector objects not supported with AMF0");
    }

    /** {@inheritDoc} */
    @Override
    public void writeVectorObject(Vector<Object> vector) {
        throw new RuntimeException("Vector objects not supported with AMF0");
    }

    /**
     * Encode string.
     *
     * @param string
     *            string to encode
     * @return encoded string
     */
    protected static byte[] encodeString(String string) {
        initializeCaches();
        return getStringCache().get(string, k -> {
            ByteBuffer buf = AMF.CHARSET.encode(k);
            byte[] encoded = new byte[buf.remaining()];
            buf.get(encoded);
            return encoded;
        });
    }

    /**
     * Write out string
     *
     * @param buf
     *            Byte buffer to write to
     * @param string
     *            String to write
     */
    public static void putString(IoBuffer buf, String string) {
        final byte[] encoded = encodeString(string);
        if (encoded.length < AMF.LONG_STRING_LENGTH) {
            // write unsigned short
            buf.put((byte) ((encoded.length >> 8) & 0xff));
            buf.put((byte) (encoded.length & 0xff));
        } else {
            buf.putInt(encoded.length);
        }
        buf.put(encoded);
    }

    /** {@inheritDoc} */
    @Override
    public void putString(String string) {
        putString(buf, string);
    }

    /** {@inheritDoc} */
    @Override
    public void writeXML(Document xml) {
        buf.put(AMF.TYPE_XML);
        putString(XMLUtils.docToString(xml));
    }

    /**
     * Convenience method to allow XML text to be used, instead of requiring an XML Document.
     *
     * @param xml
     *            xml to write
     */
    public void writeXML(String xml) {
        buf.put(AMF.TYPE_XML);
        putString(xml);
    }

    /**
     * Return buffer of this Output object
     *
     * @return Byte buffer of this Output object
     */
    public IoBuffer buf() {
        return this.buf;
    }

    /**
     * <p>reset.</p>
     */
    public void reset() {
        clearReferences();
    }

    /**
     * <p>Getter for the field <code>stringCache</code>.</p>
     *
     * @return a {@link com.github.benmanes.caffeine.cache.Cache} object
     */
    protected static Cache<String, byte[]> getStringCache() {
        initializeCaches();
        return stringCache;
    }

    /**
     * <p>Getter for the field <code>serializeCache</code>.</p>
     *
     * @return a {@link com.github.benmanes.caffeine.cache.Cache} object
     */
    protected static Cache<Class<?>, Map<String, Boolean>> getSerializeCache() {
        initializeCaches();
        return serializeCache;
    }

    /**
     * <p>Getter for the field <code>fieldCache</code>.</p>
     *
     * @return a {@link com.github.benmanes.caffeine.cache.Cache} object
     */
    protected static Cache<Class<?>, Map<String, Field>> getFieldCache() {
        initializeCaches();
        return fieldCache;
    }

    /**
     * <p>Getter for the field <code>getterCache</code>.</p>
     *
     * @return a {@link com.github.benmanes.caffeine.cache.Cache} object
     */
    protected static Cache<Class<?>, Map<String, Method>> getGetterCache() {
        initializeCaches();
        return getterCache;
    }

    /**
     * <p>destroyCache.</p>
     */
    public static void destroyCache() {
        if (cacheInitialized) {
            lookupLock.lock();
            try {
                if (stringCache != null) {
                    stringCache.invalidateAll();
                }
                if (serializeCache != null) {
                    serializeCache.invalidateAll();
                }
                if (fieldCache != null) {
                    fieldCache.invalidateAll();
                }
                if (getterCache != null) {
                    getterCache.invalidateAll();
                }
                fieldCache = null;
                getterCache = null;
                serializeCache = null;
                stringCache = null;
                cacheInitialized = false;
                log.info("Caffeine caches destroyed for AMF serialization");
            } finally {
                lookupLock.unlock();
            }
        }
    }
}
