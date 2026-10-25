/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.amf3;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.mina.core.buffer.IoBuffer;
import org.red5.io.amf.AMF;
import org.red5.io.object.DataTypes;
import org.red5.io.object.Deserializer;
import org.red5.io.utils.ArrayUtils;
import org.red5.io.utils.ConversionUtils;
import org.red5.io.utils.ObjectMap;
import org.red5.io.utils.XMLUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

/**
 * Input for Red5 data (AMF3) types.
 * <p>
 * An instance starts in an AMF0 context, where values follow the AMF0 type table and the AMF0 avmplus-object marker (0x11) switches to AMF3
 * for the next value only. {@link #enforceAMF3()} puts the instance into an AMF3 context, where 0x11 is the AMF3 dictionary marker. AMF3
 * string, object and traits references are kept in a {@link RefStorage} that may be shared by several instances decoding the same message
 * body; AMF0 references are kept per instance.
 * </p>
 *
 * @author The Red5 Project
 * @author Luke Hubbard, Codegent Ltd (luke@codegent.com)
 * @author Joachim Bauch (jojo@struktur.de)
 */
public class Input extends org.red5.io.amf.Input {

    /**
     * Holds informations about already deserialized classes (AMF3 traits).
     */
    protected static class ClassReference {

        private static Logger log = LoggerFactory.getLogger(ClassReference.class);

        /** Name of the deserialized class. */
        protected String className;

        /** Type of the class, one of the AMF3.TYPE_OBJECT_* constants. */
        protected int type;

        /** Names of the sealed attributes of the class. */
        protected List<String> attributeNames;

        /**
         * Create new information about a class.
         *
         * @param className
         *            class name
         * @param type
         *            type
         * @param attributeNames
         *            attributes
         */
        public ClassReference(String className, int type, List<String> attributeNames) {
            if (log.isDebugEnabled()) {
                log.debug("Class reference - className: {} type: {} attributeNames: {}", new Object[] { className, type, attributeNames });
            }
            this.className = className;
            this.type = type;
            this.attributeNames = attributeNames;
        }
    }

    /**
     * Class used to collect AMF3 references. In AMF3 references should be collected through the whole "body" (across several Input
     * objects).
     */
    public static class RefStorage {
        // traits of previously deserialized objects; only inline traits are added
        private List<ClassReference> classReferences = new ArrayList<>();

        // list of string values found in the input stream; the empty string is never added
        private List<String> stringReferences = new ArrayList<>();

        // complex values (objects, arrays, dates, xml, byte arrays, vectors, dictionaries) in the order they were found
        private List<Object> objectReferences = new ArrayList<>();
    }

    /**
     * Set to a value above 0 to decode AMF3; 0 means the AMF0 context.
     */
    private int amf3_mode;

    /**
     * Nesting depth of the value being decoded, mirrors the depth tracked by the base class.
     */
    private int nesting;

    /**
     * Nesting depth at which an AMF0 avmplus-object marker switched to AMF3, or -1 when no switch is active.
     */
    private int switchDepth = -1;

    /**
     * Stores references declared in this input of previous ones in the same message body
     */
    private RefStorage refStorage;

    /**
     * Creates Input object for AMF3 from byte buffer
     *
     * @param buf
     *            Byte buffer
     */
    public Input(IoBuffer buf) {
        this(buf, new RefStorage());
    }

    /**
     * Creates Input object for AMF3 from byte buffer and initializes references from passed RefStorage
     *
     * @param buf
     *            buffer
     * @param refStorage
     *            ref storage
     */
    public Input(IoBuffer buf, RefStorage refStorage) {
        super(buf);
        this.refStorage = refStorage;
    }

    /**
     * Force using AMF3 everywhere
     */
    public void enforceAMF3() {
        amf3_mode++;
    }

    /**
     * Returns whether values are currently decoded as AMF3.
     *
     * @return true in an AMF3 context, false in an AMF0 context
     */
    protected boolean isAMF3() {
        return amf3_mode > 0;
    }

    /**
     * Provide access to raw data.
     *
     * @return IoBuffer
     */
    protected IoBuffer getBuffer() {
        return buf;
    }

    /** {@inheritDoc} */
    @Override
    protected void enterNested() {
        super.enterNested();
        nesting++;
    }

    /** {@inheritDoc} */
    @Override
    protected void exitNested() {
        if (switchDepth == nesting) {
            // the value that followed the avmplus-object marker is complete
            endSwitch();
        }
        nesting--;
        super.exitNested();
    }

    private void endSwitch() {
        switchDepth = -1;
        amf3_mode--;
        log.trace("Switched back to AMF0");
    }

    /**
     * {@inheritDoc}
     *
     * Reads the data type. In the AMF0 context the AMF0 type table applies and the avmplus-object marker switches to AMF3 for the next
     * value only; in the AMF3 context the AMF3 type table applies.
     */
    @Override
    public byte readDataType() {
        log.trace("readDataType - amf3_mode: {}", amf3_mode);
        if (switchDepth >= 0 && switchDepth >= nesting) {
            // a switched value read without the deserializer is complete once the next value at the same depth starts
            endSwitch();
        }
        if (amf3_mode == 0) {
            byte coreType = super.readDataType();
            if (coreType != DataTypes.CORE_SWITCH) {
                return coreType;
            }
            log.trace("Got AMF3 object indicator");
            amf3_mode++;
            switchDepth = nesting;
        }
        return readAMF3DataType();
    }

    private byte readAMF3DataType() {
        currentDataType = buf.get();
        log.debug("Current data type: {}", currentDataType);
        byte coreType;
        switch (currentDataType) {
            case AMF3.TYPE_UNDEFINED:
            case AMF3.TYPE_NULL:
                coreType = DataTypes.CORE_NULL;
                break;
            case AMF3.TYPE_INTEGER:
            case AMF3.TYPE_NUMBER:
                coreType = DataTypes.CORE_NUMBER;
                break;
            case AMF3.TYPE_BOOLEAN_TRUE:
            case AMF3.TYPE_BOOLEAN_FALSE:
                coreType = DataTypes.CORE_BOOLEAN;
                break;
            case AMF3.TYPE_STRING:
                coreType = DataTypes.CORE_STRING;
                break;
            case AMF3.TYPE_XML:
            case AMF3.TYPE_XML_DOCUMENT:
                coreType = DataTypes.CORE_XML;
                break;
            case AMF3.TYPE_OBJECT:
                coreType = DataTypes.CORE_OBJECT;
                break;
            case AMF3.TYPE_ARRAY:
                coreType = DataTypes.CORE_ARRAY;
                break;
            case AMF3.TYPE_DATE:
                coreType = DataTypes.CORE_DATE;
                break;
            case AMF3.TYPE_BYTEARRAY:
                coreType = DataTypes.CORE_BYTEARRAY;
                break;
            case AMF3.TYPE_VECTOR_INT:
                coreType = DataTypes.CORE_VECTOR_INT;
                break;
            case AMF3.TYPE_VECTOR_UINT:
                coreType = DataTypes.CORE_VECTOR_UINT;
                break;
            case AMF3.TYPE_VECTOR_NUMBER:
                coreType = DataTypes.CORE_VECTOR_NUMBER;
                break;
            case AMF3.TYPE_VECTOR_OBJECT:
                coreType = DataTypes.CORE_VECTOR_OBJECT;
                break;
            case AMF3.TYPE_DICTIONARY:
                // decoded by readMap
                coreType = DataTypes.CORE_MAP;
                break;
            default:
                log.info("Unknown datatype: {}", currentDataType);
                // End of object, and anything else lets just skip
                coreType = DataTypes.CORE_SKIP;
                break;
        }
        log.debug("Core type: {}", coreType);
        return coreType;
    }

    /**
     * {@inheritDoc}
     *
     * Reads a null (value)
     */
    @Override
    public Object readNull() {
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * Reads a boolean
     */
    @Override
    public Boolean readBoolean() {
        if (!isAMF3()) {
            return super.readBoolean();
        }
        return (currentDataType == AMF3.TYPE_BOOLEAN_TRUE) ? Boolean.TRUE : Boolean.FALSE;
    }

    /**
     * {@inheritDoc}
     *
     * Reads a Number
     */
    @Override
    public Number readNumber() {
        if (!isAMF3()) {
            return super.readNumber();
        }
        if (!buf.hasRemaining()) {
            log.warn("No remaining bytes for buffer readNumber");
            return null;
        }
        Number v;
        if (currentDataType == AMF3.TYPE_NUMBER) {
            v = buf.getDouble();
        } else {
            // integer type: U29 holding a signed 29-bit value
            v = readI29();
        }
        if (log.isTraceEnabled()) {
            log.trace("readNumber - value: {}", v);
        }
        return v;
    }

    /**
     * {@inheritDoc}
     *
     * Reads a string
     */
    @Override
    public String readString() {
        log.trace("readString - amf3_mode: {}", amf3_mode);
        if (!isAMF3()) {
            // read the string as AMF0
            return super.readString();
        }
        return readAMF3String();
    }

    /**
     * Reads an AMF3 UTF-8-vr string, using the string reference table.
     *
     * @return string
     */
    private String readAMF3String() {
        int ref = readU29();
        log.debug("readString - header: {}", ref);
        if ((ref & 1) == 0) {
            // reference
            return getStringReference(ref >> 1);
        }
        int len = ref >> 1;
        if (len == 0) {
            // the empty string is never sent by reference
            return "";
        }
        String string = decodeUTF8(len);
        log.debug("String: {}", string);
        refStorage.stringReferences.add(string);
        return string;
    }

    private String decodeUTF8(int len) {
        checkCount(len, 1, buf.remaining());
        int limit = buf.limit();
        final ByteBuffer strBuf = buf.buf();
        strBuf.limit(strBuf.position() + len);
        final String string = AMF.CHARSET.decode(strBuf).toString();
        buf.limit(limit); // reset the limit
        return string;
    }

    /**
     * Reads a string of a set length. This does not use the string reference table.
     *
     * @param length the length of the string
     * @return String
     */
    public String readString(int length) {
        log.debug("readString - length: {}", length);
        int limit = buf.limit();
        final ByteBuffer strBuf = buf.buf();
        strBuf.limit(strBuf.position() + length);
        final String string = AMF.CHARSET.decode(strBuf).toString();
        log.debug("String: {}", string);
        buf.limit(limit);
        // check for null termination
        byte b = buf.get();
        if (b != 0) {
            buf.position(buf.position() - 1);
        }
        return string;
    }

    /**
     * <p>Getter for the field <code>refStorage</code>.</p>
     *
     * @return a {@link org.red5.io.amf3.Input.RefStorage} object
     */
    public RefStorage getRefStorage() {
        return refStorage;
    }

    /**
     * {@inheritDoc}
     *
     * Reads a string without a type marker: an AMF3 UTF-8-vr in the AMF3 context, an AMF0 UTF-8 (U16 length) string otherwise.
     */
    @Override
    public String getString() {
        if (!isAMF3()) {
            return super.getString();
        }
        return readAMF3String();
    }

    /**
     * {@inheritDoc}
     *
     * Returns a date
     */
    @Override
    public Date readDate() {
        if (!isAMF3()) {
            return super.readDate();
        }
        int ref = readU29();
        if ((ref & 1) == 0) {
            // reference to previously found date
            return (Date) getObjectReference(ref >> 1);
        }
        long ms = (long) buf.getDouble();
        Date date = new Date(ms);
        storeObjectReference(date);
        return date;
    }

    // Array

    /**
     * {@inheritDoc}
     *
     * Returns an array
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public Object readArray(Type target) {
        if (!isAMF3()) {
            return super.readArray(target);
        }
        int count = readU29();
        log.debug("Count: {} and {} ref {}", new Object[] { count, (count & 1), (count >> 1) });
        if ((count & 1) == 0) {
            // reference
            return getObjectReference(count >> 1);
        }
        count = (count >> 1);
        String key = readAMF3String();
        // every dense element takes at least one marker byte
        checkCount(count, 1, buf.remaining());
        Object result;
        if (key.equals("")) {
            Class<?> nested = Object.class;
            Class<?> collection = Collection.class;
            Collection resultCollection;
            if (target instanceof ParameterizedType) {
                ParameterizedType t = (ParameterizedType) target;
                Type[] actualTypeArguments = t.getActualTypeArguments();
                if (actualTypeArguments.length == 1 && actualTypeArguments[0] instanceof Class) {
                    nested = (Class<?>) actualTypeArguments[0];
                }
                target = t.getRawType();
            }
            if (target instanceof Class) {
                collection = (Class) target;
            }
            if (collection.isArray()) {
                log.debug("Using an array for key: {} count: {}", key, count);
                nested = ArrayUtils.getGenericType(collection.getComponentType());
                result = Array.newInstance(nested, count);
                storeObjectReference(result);
                for (int i = 0; i < count; i++) {
                    final Object value = Deserializer.deserialize(this, nested);
                    Array.set(result, i, value);
                }
            } else {
                if (SortedSet.class.isAssignableFrom(collection)) {
                    resultCollection = new TreeSet();
                } else if (Set.class.isAssignableFrom(collection)) {
                    resultCollection = new HashSet(count);
                } else {
                    log.debug("Using an ArrayList for key: {} count: {}", key, count);
                    resultCollection = new ArrayList(count);
                }
                result = resultCollection;
                storeObjectReference(result);
                for (int i = 0; i < count; i++) {
                    final Object value = Deserializer.deserialize(this, nested);
                    resultCollection.add(value);
                }
            }
        } else {
            Class<?> v = Object.class;
            Class<?> collection = Collection.class;
            if (target instanceof ParameterizedType) {
                ParameterizedType t = (ParameterizedType) target;
                Type[] actualTypeArguments = t.getActualTypeArguments();
                if (actualTypeArguments.length == 2 && actualTypeArguments[1] instanceof Class) {
                    v = (Class<?>) actualTypeArguments[1];
                }
                target = t.getRawType();
            }
            if (target instanceof Class) {
                collection = (Class) target;
            }
            if (SortedMap.class.isAssignableFrom(collection)) {
                collection = TreeMap.class;
            } else {
                collection = HashMap.class;
            }
            Map resultMap;
            try {
                resultMap = (Map) collection.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                resultMap = new HashMap(count);
            }
            // associative array
            storeObjectReference(resultMap);
            while (!key.equals("")) {
                final Object value = Deserializer.deserialize(this, v);
                resultMap.put(key, value);
                key = readAMF3String();
            }
            for (int i = 0; i < count; i++) {
                final Object value = Deserializer.deserialize(this, v);
                resultMap.put(i, value);
            }
            result = resultMap;
        }
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * Reads an AMF0 ECMA array in the AMF0 context or an AMF3 dictionary in the AMF3 context.
     */
    @Override
    public Object readMap() {
        if (!isAMF3()) {
            return super.readMap();
        }
        if (currentDataType == AMF3.TYPE_DICTIONARY) {
            return readDictionary();
        }
        throw new RuntimeException(String.format("AMF3 type 0x%02X is not a map", currentDataType));
    }

    /**
     * Reads an AMF3 dictionary: U29Dict (count or reference), U8 weak-keys flag, then count key / value pairs of any value type.
     *
     * @return map of the dictionary entries in wire order
     */
    private Map<Object, Object> readDictionary() {
        int ref = readU29();
        if ((ref & 1) == 0) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) getObjectReference(ref >> 1);
            return map;
        }
        int count = ref >> 1;
        // weak-keys flag, has no meaning outside of the AVM
        byte weakKeys = buf.get();
        log.debug("Dictionary count: {} weak keys: {}", count, weakKeys);
        // every key and value take at least one marker byte
        checkCount(count, 2, buf.remaining());
        Map<Object, Object> result = new LinkedHashMap<>();
        storeObjectReference(result);
        for (int i = 0; i < count; i++) {
            Object key = Deserializer.deserialize(this, Object.class);
            Object value = Deserializer.deserialize(this, Object.class);
            result.put(key, value);
        }
        return result;
    }

    /** {@inheritDoc} */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public Object readObject() {
        log.trace("readObject - amf3_mode: {}", amf3_mode);
        if (!isAMF3()) {
            return super.readObject();
        }
        int type = readU29();
        log.debug("Type: {} and {} ref {}", new Object[] { type, (type & 1), (type >> 1) });
        if ((type & 1) == 0) {
            // object reference
            return getObjectReference(type >> 1);
        }
        type >>= 1;
        ClassReference traits;
        if ((type & 1) == 0) {
            // traits reference
            traits = getTraitsReference(type >> 1);
        } else {
            // inline traits
            type >>= 1;
            if ((type & 1) == 1) {
                // externalizable, any further bits are not significant
                traits = new ClassReference(readAMF3String(), AMF3.TYPE_OBJECT_EXTERNALIZABLE, Collections.emptyList());
            } else {
                boolean dynamic = ((type >> 1) & 1) == 1;
                int count = type >> 2;
                String className = readAMF3String();
                // every sealed member name takes at least one byte
                checkCount(count, 1, buf.remaining());
                List<String> attributes = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    attributes.add(readAMF3String());
                }
                traits = new ClassReference(className, dynamic ? AMF3.TYPE_OBJECT_VALUE : AMF3.TYPE_OBJECT_PROPERTY, attributes);
            }
            // only inline traits enter the traits reference table
            refStorage.classReferences.add(traits);
        }
        String className = traits.className;
        log.debug("Traits - class: {} type: {} sealed: {}", className, traits.type, traits.attributeNames);
        if (traits.type == AMF3.TYPE_OBJECT_EXTERNALIZABLE) {
            return readExternalizable(className);
        }
        if ("RecordSet".equals(className)) {
            // TODO: how are RecordSet objects encoded?
            throw new RuntimeException("Objects of type RecordSet not supported yet");
        } else if ("RecordSetPage".equals(className)) {
            // TODO: how are RecordSetPage objects encoded?
            throw new RuntimeException("Objects of type RecordSetPage not supported yet");
        }
        Object instance = newInstance(className);
        Map<String, Object> properties = new ObjectMap<>();
        // the object enters the reference table before its members are read, so members may refer to it; anonymous objects and classes
        // that could not be instantiated are returned as a map
        Object result = (instance != null) ? instance : properties;
        storeObjectReference(result);
        // sealed members: the names come from the traits, the values follow in the same order
        for (String name : traits.attributeNames) {
            properties.put(name, Deserializer.deserialize(this, getPropertyType(instance, name)));
        }
        if (traits.type == AMF3.TYPE_OBJECT_VALUE) {
            // dynamic members are name / value pairs ending with the empty string, they are not part of the traits; some encoders omit
            // the final empty string of the outermost object at the end of the message, so the end of the input also ends the members
            while (buf.hasRemaining()) {
                String key = readAMF3String();
                if ("".equals(key)) {
                    break;
                }
                log.debug("Looking for property: {}", key);
                properties.put(key, Deserializer.deserialize(this, getPropertyType(instance, key)));
            }
        }
        if (instance != null) {
            applyProperties(instance, properties);
        }
        return result;
    }

    private Object readExternalizable(String className) {
        log.debug("Externalizable class: {}", className);
        if ("".equals(className)) {
            throw new RuntimeException("Classname is required to load an Externalizable object");
        }
        Object result = newInstance(className);
        if (result == null) {
            throw new RuntimeException(String.format("Could not instantiate class: %s", className));
        }
        if (!(result instanceof IExternalizable)) {
            throw new RuntimeException(String.format("Class must implement the IExternalizable interface: %s", className));
        }
        storeObjectReference(result);
        ((IExternalizable) result).readExternal(new DataInput(this));
        return result;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void applyProperties(Object result, Map<String, Object> properties) {
        Class resultClass = result.getClass();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            final String key = entry.getKey();
            Object value = entry.getValue();
            if (value != null) {
                try {
                    final Field field = resultClass.getField(key);
                    final Class fieldType = field.getType();
                    if (!fieldType.isAssignableFrom(value.getClass())) {
                        value = ConversionUtils.convert(value, fieldType);
                    } else if (value instanceof Enum) {
                        value = Enum.valueOf(fieldType, value.toString());
                    }
                    field.set(result, value);
                } catch (Exception e) {
                    try {
                        BeanUtils.setProperty(result, key, value);
                    } catch (IllegalAccessException ex) {
                        log.warn("Error mapping key: {} value: {}", key, value);
                    } catch (InvocationTargetException ex) {
                        log.warn("Error mapping key: {} value: {}", key, value);
                    }
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Skipping null property: {}", key);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * Read ByteArray object.
     */
    @Override
    public ByteArray readByteArray() {
        if (!isAMF3()) {
            return super.readByteArray();
        }
        int type = readU29();
        if ((type & 1) == 0) {
            return (ByteArray) getObjectReference(type >> 1);
        }
        type >>= 1;
        checkCount(type, 1, buf.remaining());
        ByteArray result = new ByteArray(buf, type);
        storeObjectReference(result);
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * Read Vector&lt;Integer&gt; object.
     */
    @SuppressWarnings("unchecked")
    @Override
    public Vector<Integer> readVectorInt() {
        if (!isAMF3()) {
            return super.readVectorInt();
        }
        log.debug("readVectorInt");
        int type = readU29();
        if ((type & 1) == 0) {
            return (Vector<Integer>) getObjectReference(type >> 1);
        }
        int len = type >> 1;
        readVectorFixed();
        checkCount(len, 4, buf.remaining());
        Vector<Integer> array = new Vector<Integer>(len);
        storeObjectReference(array);
        for (int j = 0; j < len; ++j) {
            array.add(buf.getInt());
        }
        return array;
    }

    /**
     * {@inheritDoc}
     *
     * Read Vector&lt;uint&gt; object.
     */
    @SuppressWarnings("unchecked")
    @Override
    public Vector<Long> readVectorUInt() {
        if (!isAMF3()) {
            return super.readVectorUInt();
        }
        log.debug("readVectorUInt");
        int type = readU29();
        if ((type & 1) == 0) {
            return (Vector<Long>) getObjectReference(type >> 1);
        }
        int len = type >> 1;
        readVectorFixed();
        checkCount(len, 4, buf.remaining());
        Vector<Long> array = new Vector<Long>(len);
        storeObjectReference(array);
        for (int j = 0; j < len; ++j) {
            // U32
            array.add(buf.getUnsignedInt());
        }
        return array;
    }

    /**
     * {@inheritDoc}
     *
     * Read Vector&lt;Number&gt; object.
     */
    @SuppressWarnings("unchecked")
    @Override
    public Vector<Double> readVectorNumber() {
        if (!isAMF3()) {
            return super.readVectorNumber();
        }
        log.debug("readVectorNumber");
        int type = readU29();
        log.debug("Type: {}", type);
        if ((type & 1) == 0) {
            return (Vector<Double>) getObjectReference(type >> 1);
        }
        int len = type >> 1;
        log.debug("Length: {}", len);
        readVectorFixed();
        checkCount(len, 8, buf.remaining());
        Vector<Double> array = new Vector<Double>(len);
        storeObjectReference(array);
        for (int j = 0; j < len; ++j) {
            array.add(buf.getDouble());
        }
        return array;
    }

    /**
     * {@inheritDoc}
     *
     * Read Vector&lt;Object&gt; object: U29V, U8 fixed flag, the UTF-8-vr object type name, then each element as a full AMF3 value.
     */
    @SuppressWarnings("unchecked")
    @Override
    public Vector<Object> readVectorObject() {
        if (!isAMF3()) {
            return super.readVectorObject();
        }
        log.debug("readVectorObject");
        int type = readU29();
        log.debug("Type: {}", type);
        if ((type & 1) == 0) {
            return (Vector<Object>) getObjectReference(type >> 1);
        }
        int len = type >> 1;
        log.debug("Length: {}", len);
        readVectorFixed();
        String typeName = readAMF3String();
        log.debug("Object type name: {}", typeName);
        // every element takes at least one marker byte
        checkCount(len, 1, buf.remaining());
        Vector<Object> array = new Vector<Object>(len);
        storeObjectReference(array);
        for (int j = 0; j < len; ++j) {
            array.add(Deserializer.deserialize(this, Object.class));
        }
        log.debug("Vector: {}", array);
        return array;
    }

    private void readVectorFixed() {
        // U8 fixed-length flag, has no meaning for the decoded vector
        byte fixed = buf.get();
        log.debug("Fixed length: {}", fixed);
    }

    /**
     * {@inheritDoc}
     *
     * Reads Custom
     */
    @Override
    public Object readCustom() {
        if (!isAMF3()) {
            return super.readCustom();
        }
        // Return null for now
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public Object readReference() {
        if (!isAMF3()) {
            return super.readReference();
        }
        throw new RuntimeException("AMF3 doesn't support direct references");
    }

    /**
     * Reads an unsigned 29-bit integer (U29) as used by AMF3 headers, lengths and references.
     *
     * @return value in the range 0 to 2^29 - 1
     */
    private int readU29() {
        int b = buf.get() & 0xFF;
        if (b < 0x80) {
            return b;
        }
        int value = (b & 0x7F) << 7;
        b = buf.get() & 0xFF;
        if (b < 0x80) {
            return value | b;
        }
        value = (value | (b & 0x7F)) << 7;
        b = buf.get() & 0xFF;
        if (b < 0x80) {
            return value | b;
        }
        // the fourth byte contributes all 8 bits
        value = (value | (b & 0x7F)) << 8;
        return value | (buf.get() & 0xFF);
    }

    /**
     * Reads the value of the AMF3 integer type: a U29 holding a signed 29-bit integer.
     *
     * @return value in the range -2^28 to 2^28 - 1
     */
    private int readI29() {
        int value = readU29();
        // extend the sign bit of the 29-bit value
        return (value << 3) >> 3;
    }

    private String getStringReference(int index) {
        List<String> strings = refStorage.stringReferences;
        if (index >= strings.size()) {
            throw new RuntimeException(String.format("AMF3 string reference %d out of range, %d strings known", index, strings.size()));
        }
        return strings.get(index);
    }

    private ClassReference getTraitsReference(int index) {
        List<ClassReference> traits = refStorage.classReferences;
        if (index >= traits.size()) {
            throw new RuntimeException(String.format("AMF3 traits reference %d out of range, %d traits known", index, traits.size()));
        }
        return traits.get(index);
    }

    private Object getObjectReference(int index) {
        List<Object> objects = refStorage.objectReferences;
        if (index >= objects.size()) {
            throw new RuntimeException(String.format("AMF3 object reference %d out of range, %d objects known", index, objects.size()));
        }
        return objects.get(index);
    }

    private void storeObjectReference(Object obj) {
        refStorage.objectReferences.add(obj);
    }

    /** {@inheritDoc} */
    @Override
    public Document readXML() {
        if (!isAMF3()) {
            return super.readXML();
        }
        int len = readU29();
        if ((len & 1) == 0) {
            // reference
            return (Document) getObjectReference(len >> 1);
        }
        len >>= 1;
        Document doc = null;
        if (len > 0) {
            final String xmlString = decodeUTF8(len);
            try {
                doc = XMLUtils.stringToDoc(xmlString);
            } catch (IOException ioex) {
                log.error("IOException converting xml to dom", ioex);
            }
        }
        // an inline xml value takes a reference index even when empty or unparseable
        storeObjectReference(doc);
        return doc;
    }

    /**
     * {@inheritDoc}
     *
     * Clears the AMF0 references. The AMF3 string, object and traits references are scoped to the message body and are kept, so values that
     * follow can still refer to those read before the reset.
     */
    @Override
    public void reset() {
        super.reset();
    }

}
