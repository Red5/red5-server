/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.object;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Deserializer class reads data input and handles the data according to the core data types
 *
 * @author The Red5 Project
 * @author Luke Hubbard, Codegent Ltd (luke@codegent.com)
 */
public class Deserializer {

    private static final Logger log = LoggerFactory.getLogger(Deserializer.class);

    /**
     * System property holding a comma-separated list of class name prefixes; when set, only matching classes may be instantiated from a
     * wire class name. Denied prefixes still apply.
     */
    public static final String ALLOW_LIST_PROPERTY = "red5.amf.class.allowlist";

    /**
     * System property holding a comma-separated list of class name prefixes to deny in addition to the bundled black-list.
     */
    public static final String DENY_LIST_PROPERTY = "red5.amf.class.denylist";

    private static volatile Set<String> BLACK_LIST = Collections.emptySet();

    private static volatile Set<String> ALLOW_LIST = Collections.emptySet();

    static {
        try {
            loadBlackList();
        } catch (Exception e) {
            // fail closed: without the bundled list only the allow-list, if any, can admit classes
            log.error("Failed to load the AMF class black-list", e);
            BLACK_LIST = Collections.singleton("");
        }
        ALLOW_LIST = parsePrefixes(System.getProperty(ALLOW_LIST_PROPERTY));
    }

    private Deserializer() {
    }

    /**
     * <p>loadBlackList.</p>
     *
     * @throws java.io.IOException if any.
     */
    public synchronized static void loadBlackList() throws IOException {
        try (InputStream is = Deserializer.class.getClassLoader().getResourceAsStream("org/red5/io/object/black-list.properties")) {
            if (is == null) {
                throw new IOException("black-list.properties not found");
            }
            Properties bl = new Properties();
            bl.load(is);
            Set<String> set = new HashSet<>();
            for (Entry<?, ?> e : bl.entrySet()) {
                set.add((String) e.getKey());
            }
            set.addAll(parsePrefixes(System.getProperty(DENY_LIST_PROPERTY)));
            BLACK_LIST = Collections.unmodifiableSet(set);
        }
    }

    /**
     * Restricts class instantiation from wire class names to the given prefixes. An empty or null value removes the restriction, leaving
     * only the black-list in effect.
     *
     * @param prefixes class name or package prefixes
     */
    public static void setAllowList(Set<String> prefixes) {
        ALLOW_LIST = prefixes == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(prefixes));
    }

    /**
     * Returns the class name prefixes currently allowed; empty means no allow-list restriction.
     *
     * @return allowed prefixes
     */
    public static Set<String> getAllowList() {
        return ALLOW_LIST;
    }

    private static Set<String> parsePrefixes(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(p -> !p.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Deserializes the input parameter and returns an Object which must then be cast to a core data type
     *
     * @param <T>
     *            type
     * @param in
     *            input
     * @param target
     *            target
     * @return Object object
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> T deserialize(Input in, Type target) {
        if (in instanceof BaseInput) {
            BaseInput input = (BaseInput) in;
            input.enterNested();
            try {
                return deserializeValue(in, target);
            } finally {
                input.exitNested();
            }
        }
        return deserializeValue(in, target);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static <T> T deserializeValue(Input in, Type target) {
        byte type = in.readDataType();
        if (log.isTraceEnabled()) {
            log.trace("Type {}: {} target: {}", type, DataTypes.toStringValue(type), (target != null ? target.toString() : "Target not specified"));
        }
        Object result = null;
        switch (type) {
            case DataTypes.CORE_NULL:
                result = in.readNull();
                break;
            case DataTypes.CORE_BOOLEAN:
                result = in.readBoolean();
                break;
            case DataTypes.CORE_NUMBER:
                result = in.readNumber();
                break;
            case DataTypes.CORE_STRING:
                try {
                    if (target != null && ((Class) target).isEnum()) {
                        log.warn("Enum target specified");
                        String name = in.readString();
                        result = Enum.valueOf((Class) target, name);
                    } else {
                        result = in.readString();
                    }
                } catch (RuntimeException e) {
                    log.error("failed to deserialize {}", target, e);
                    throw e;
                }
                break;
            case DataTypes.CORE_DATE:
                result = in.readDate();
                break;
            case DataTypes.CORE_ARRAY:
                result = in.readArray(target);
                break;
            case DataTypes.CORE_MAP:
                result = in.readMap();
                break;
            case DataTypes.CORE_XML:
                result = in.readXML();
                break;
            case DataTypes.CORE_OBJECT:
                result = in.readObject();
                break;
            case DataTypes.CORE_BYTEARRAY:
                result = in.readByteArray();
                break;
            case DataTypes.CORE_VECTOR_INT:
                result = in.readVectorInt();
                break;
            case DataTypes.CORE_VECTOR_UINT:
                result = in.readVectorUInt();
                break;
            case DataTypes.CORE_VECTOR_NUMBER:
                result = in.readVectorNumber();
                break;
            case DataTypes.CORE_VECTOR_OBJECT:
                result = in.readVectorObject();
                break;
            case DataTypes.OPT_REFERENCE:
                result = in.readReference();
                break;
            case DataTypes.CORE_END_OBJECT:
                // end-of-object returned, not sure that we should ever get here
                log.debug("End-of-object detected");
                break;
            default:
                result = in.readCustom();
                break;
        }
        return (T) result;
    }

    /**
     * Checks whether a class may be instantiated from a wire class name: it must not match a black-list prefix and, when an allow-list is
     * configured, it must match an allow-list prefix.
     *
     * @param className class name/package
     * @return true if instantiation is allowed and false otherwise
     */
    public static boolean classAllowed(String className) {
        if (className == null) {
            return false;
        }
        for (String name : BLACK_LIST) {
            if (className.startsWith(name)) {
                return false;
            }
        }
        Set<String> allowed = ALLOW_LIST;
        if (allowed.isEmpty()) {
            return true;
        }
        for (String name : allowed) {
            if (className.startsWith(name)) {
                return true;
            }
        }
        return false;
    }

}
