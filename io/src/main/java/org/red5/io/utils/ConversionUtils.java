/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.utils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.beanutils.BeanMap;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.ConversionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Misc utils for conversions
 *
 * @author The Red5 Project
 * @author Luke Hubbard, Codegent Ltd (luke@codegent.com)
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class ConversionUtils {

    private static final Logger log = LoggerFactory.getLogger(ConversionUtils.class);

    private static final Class<?>[] PRIMITIVES = { boolean.class, byte.class, char.class, short.class, int.class, long.class, float.class, double.class };

    private static final Class<?>[] WRAPPERS = { Boolean.class, Byte.class, Character.class, Short.class, Integer.class, Long.class, Float.class, Double.class };

    private static final String NUMERIC_TYPE = "[-]?\\b\\d+\\b|[-]?\\b[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?\\b";

    /**
     * Parameter chains
     */
    private static final Class<?>[][] PARAMETER_CHAINS = { { boolean.class, null }, { byte.class, Short.class }, { char.class, Integer.class }, { short.class, Integer.class }, { int.class, Long.class }, { long.class, Float.class }, { float.class, Double.class }, { double.class, null } };

    /** Mapping of primitives to wrappers */
    private static Map<Class<?>, Class<?>> primitiveMap = new HashMap<Class<?>, Class<?>>();

    /** Mapping of wrappers to primitives */
    private static Map<Class<?>, Class<?>> wrapperMap = new HashMap<Class<?>, Class<?>>();

    /**
     * Mapping from wrapper class to appropriate parameter types (in order) Each entry is an array of Classes, the last of which is either null (for no chaining) or the next class to try
     */
    private static Map<Class<?>, Class<?>[]> parameterMap = new HashMap<Class<?>, Class<?>[]>();

    static {
        for (int i = 0; i < PRIMITIVES.length; i++) {
            primitiveMap.put(PRIMITIVES[i], WRAPPERS[i]);
            wrapperMap.put(WRAPPERS[i], PRIMITIVES[i]);
            parameterMap.put(WRAPPERS[i], PARAMETER_CHAINS[i]);
        }
    }

    /**
     * Returns true for base types or arrays of base type.
     *
     * @param obj Object
     * @return true if base-type or array and false otherwise
     */
    public static boolean isBaseTypeOrArray(Object obj) {
        final Class<?> c = obj.getClass();
        if (c.isPrimitive() || c.equals(String.class) || c.isArray()) {
            return true;
        } else if (wrapperMap.containsKey(c)) {
            return true;
        }
        return false;
    }

    /**
     * Convert source to given class
     *
     * @param source
     *            Source object
     * @param target
     *            Target class
     * @return Converted object
     * @throws ConversionException
     *             If object can't be converted
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static Object convert(Object source, Class<?> target) throws ConversionException {
        if (target == null) {
            throw new ConversionException("Unable to perform conversion, target was null");
        }
        if (source == null) {
            if (target.isPrimitive()) {
                throw new ConversionException(String.format("Unable to convert null to primitive value of %s", target));
            }
            return source;
        } else if ((source instanceof Float && ((Float) source).isNaN()) || (source instanceof Double && ((Double) source).isNaN())) {
            // Don't convert NaN values
            return source;
        }
        final Class<?> sourceClass = source.getClass();
        log.trace("Source: {} target: {}", sourceClass, target);
        if (target.isInstance(source) || target.isAssignableFrom(sourceClass)) {
            log.trace("Source: {} is already an instance of: {}", source, target);
            return source;
        }
        if (target.isArray()) {
            log.trace("Source: {} to target array: {}", source, target);
            return convertToArray(source, target);
        }
        if (target.equals(String.class)) {
            return source.toString();
        }
        if (target.isPrimitive()) {
            return convertToWrappedPrimitive(source, primitiveMap.get(target));
        }
        if (wrapperMap.containsKey(target)) {
            return convertToWrappedPrimitive(source, target);
        }
        if (target.equals(Map.class)) {
            return convertBeanToMap(source);
        }
        if (sourceClass.equals(LinkedHashMap.class)) {
            return convertMapToList((LinkedHashMap<?, ?>) source);
        } else if (sourceClass.isArray()) {
            if (List.class.isAssignableFrom(target)) {
                log.trace("Source: {} to target list: {}", source, target);
                return Arrays.stream((Object[]) source).collect(Collectors.toCollection(ArrayList::new));
            } else if (Set.class.isAssignableFrom(target)) {
                log.trace("Source: {} to target set: {}", source, target);
                // special handling for sets when the source is a list
                if (source instanceof List) {
                    return ((List<?>) source).stream().collect(Collectors.toCollection(HashSet::new));
                }
                return Arrays.stream((Object[]) source).collect(Collectors.toCollection(HashSet::new));
            }
        }
        if (Map.class.isAssignableFrom(sourceClass)) {
            return convertMapToBean((Map) source, target);
        }
        // handle immutable collections
        final String sourceClassName = sourceClass.getName();
        if (sourceClassName.equals("java.util.ImmutableCollections$ListN")) {
            if (Set.class.isAssignableFrom(target)) {
                return ((List<?>) source).stream().collect(Collectors.toCollection(HashSet::new));
            }
            return ((List<?>) source).stream().collect(Collectors.toCollection(ArrayList::new));
        } else if (sourceClassName.equals("java.util.ImmutableCollections$SetN")) {
            if (Set.class.isAssignableFrom(target)) {
                return ((Set<?>) source).stream().collect(Collectors.toCollection(HashSet::new));
            }
            return ((Set<?>) source).stream().collect(Collectors.toCollection(ArrayList::new));
        } else if (sourceClassName.equals("java.util.ImmutableCollections$MapN")) {
            return convertMapToBean((Map) source, target);
        }
        throw new ConversionException(String.format("Unable to preform conversion from %s to %s", source, target));
    }

    /**
     * Convert to array
     *
     * @param source
     *            Source object
     * @param target
     *            Target class
     * @return Converted object
     * @throws ConversionException
     *             If object can't be converted
     */
    public static Object convertToArray(Object source, Class<?> target) throws ConversionException {
        try {
            Class<?> targetType = target.getComponentType();
            if (source.getClass().isArray()) {
                Object targetInstance = Array.newInstance(targetType, Array.getLength(source));
                for (int i = 0; i < Array.getLength(source); i++) {
                    Array.set(targetInstance, i, convert(Array.get(source, i), targetType));
                }
                return targetInstance;
            }
            if (source instanceof Collection<?>) {
                Collection<?> sourceCollection = (Collection<?>) source;
                Object targetInstance = Array.newInstance(target.getComponentType(), sourceCollection.size());
                Iterator<?> it = sourceCollection.iterator();
                int i = 0;
                while (it.hasNext()) {
                    Array.set(targetInstance, i++, convert(it.next(), targetType));
                }
                return targetInstance;
            }
            throw new ConversionException("Unable to convert to array");
        } catch (Exception ex) {
            throw new ConversionException("Error converting to array", ex);
        }
    }

    public static List<Object> convertMapToList(Map<?, ?> map) {
        return List.of(map.values());
    }

    /**
     * Convert to wrapped primitive
     *
     * @param source
     *            Source object
     * @param wrapper
     *            Primitive wrapper type
     * @return Converted object
     */
    public static Object convertToWrappedPrimitive(Object source, Class<?> wrapper) {
        if (source == null || wrapper == null) {
            return null;
        }
        if (wrapper.isInstance(source)) {
            return source;
        }
        if (wrapper.isAssignableFrom(source.getClass())) {
            return source;
        }
        if (source instanceof Number) {
            return convertNumberToWrapper((Number) source, wrapper);
        } else {
            //ensure we dont try to convert text to a number, prevent NumberFormatException
            if (Number.class.isAssignableFrom(wrapper)) {
                //test for int or fp number
                if (!source.toString().matches(NUMERIC_TYPE)) {
                    throw new ConversionException(String.format("Unable to convert string %s its not a number type: %s", source, wrapper));
                }
            }
            return convertStringToWrapper(source.toString(), wrapper);
        }
    }

    /**
     * Convert string to primitive wrapper like Boolean or Float
     *
     * @param str
     *            String to convert
     * @param wrapper
     *            Primitive wrapper type
     * @return Converted object
     */
    public static Object convertStringToWrapper(String str, Class<?> wrapper) {
        log.trace("String: {} to wrapper: {}", str, wrapper);
        if (wrapper.equals(String.class)) {
            return str;
        } else if (wrapper.equals(Boolean.class)) {
            return Boolean.valueOf(str);
        } else if (wrapper.equals(Double.class)) {
            return Double.valueOf(str);
        } else if (wrapper.equals(Long.class)) {
            return Long.valueOf(str);
        } else if (wrapper.equals(Float.class)) {
            return Float.valueOf(str);
        } else if (wrapper.equals(Integer.class)) {
            return Integer.valueOf(str);
        } else if (wrapper.equals(Short.class)) {
            return Short.valueOf(str);
        } else if (wrapper.equals(Byte.class)) {
            return Byte.valueOf(str);
        }
        throw new ConversionException(String.format("Unable to convert string to: %s", wrapper));
    }

    /**
     * Convert number to primitive wrapper like Boolean or Float
     *
     * @param num
     *            Number to conver
     * @param wrapper
     *            Primitive wrapper type
     * @return Converted object
     */
    public static Object convertNumberToWrapper(Number num, Class<?> wrapper) {
        //XXX Paul: Using valueOf will reduce object creation
        if (wrapper.equals(String.class)) {
            return num.toString();
        } else if (wrapper.equals(Boolean.class)) {
            return Boolean.valueOf(num.intValue() == 1);
        } else if (wrapper.equals(Double.class)) {
            return Double.valueOf(num.doubleValue());
        } else if (wrapper.equals(Long.class)) {
            return Long.valueOf(num.longValue());
        } else if (wrapper.equals(Float.class)) {
            return Float.valueOf(num.floatValue());
        } else if (wrapper.equals(Integer.class)) {
            return Integer.valueOf(num.intValue());
        } else if (wrapper.equals(Short.class)) {
            return Short.valueOf(num.shortValue());
        } else if (wrapper.equals(Byte.class)) {
            return Byte.valueOf(num.byteValue());
        }
        throw new ConversionException(String.format("Unable to convert number to: %s", wrapper));
    }

    /**
     * Convert parameters using methods of this utility class
     *
     * @param source
     *            Array of source object
     * @param target
     *            Array of target classes
     * @return Array of converted objects
     * @throws ConversionException
     *             If object can't be converted
     */
    public static Object[] convertParams(Object[] source, Class<?>[] target) throws ConversionException {
        Object[] converted = new Object[target.length];
        for (int i = 0; i < target.length; i++) {
            converted[i] = convert(source[i], target[i]);
        }
        return converted;
    }

    /**
     * Convert parameters using methods of this utility class. Special handling is afforded to classes that implement IConnection.
     *
     * @param source
     *            Array of source object
     * @return Array of converted objects
     */
    public static Class<?>[] convertParams(Object[] source) {
        Class<?>[] converted = source != null ? new Class<?>[source.length] : new Class<?>[0];
        if (source != null) {
            for (int i = 0; i < source.length; i++) {
                converted[i] = source[i] != null ? source[i].getClass() : null;
            }
            if (log.isTraceEnabled()) {
                log.trace("Converted parameters: {}", Arrays.toString(converted));
            }
        }
        return converted;
    }

    /**
     *
     * @param source
     *            source arra
     * @return list
     * @throws ConversionException
     *             on failure
     */
    public static List<?> convertArrayToList(Object[] source) throws ConversionException {
        return Arrays.stream(source).collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Convert map to bean
     *
     * @param source
     *            Source map
     * @param target
     *            Target class
     * @return Bean of that class
     * @throws ConversionException
     *             on failure
     */
    public static Object convertMapToBean(Map<String, ? extends Object> source, Class<?> target) throws ConversionException {
        Object bean = newInstance(target);
        if (bean == null) {
            //try with just the target name as specified in Trac #352
            bean = newInstance(target.getName());
            if (bean == null) {
                throw new ConversionException("Unable to create bean using empty constructor");
            }
        }
        try {
            BeanUtils.populate(bean, source);
        } catch (Exception e) {
            throw new ConversionException("Error populating bean", e);
        }
        return bean;
    }

    /**
     * Convert bean to map
     *
     * @param source
     *            Source bean
     * @return Converted map
     */
    public static Map<?, ?> convertBeanToMap(Object source) {
        return new BeanMap(source);
    }

    /**
     * Convert array to set, removing duplicates
     *
     * @param source
     *            Source array
     * @return Set
     */
    public static Set<?> convertArrayToSet(Object[] source) {
        return Arrays.stream(source).collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * Create new class instance
     *
     * @param className
     *            Class name; may not be loaded by JVM yet
     * @return Instance of given class
     */
    protected static Object newInstance(String className) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        log.debug("Conversion utils classloader: {}", cl);
        Object instance = null;
        try {
            Class<?> clazz = cl.loadClass(className);
            instance = clazz.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            log.error("Error loading class: {}", className, ex);
        }
        return instance;
    }

    /**
     * Create new class instance
     *
     * @param clazz
     *            Class; may not be loaded by JVM yet
     * @return Instance of given class
     */
    private static Object newInstance(Class<?> clazz) {
        Object instance = null;
        try {
            instance = clazz.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            log.error("Error loading class: {}", clazz.getName(), ex);
        }
        return instance;
    }

}
