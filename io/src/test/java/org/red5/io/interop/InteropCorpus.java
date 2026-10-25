/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.interop;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.red5.compatibility.flex.messaging.io.ArrayCollection;
import org.red5.io.amf3.ByteArray;
import org.red5.io.utils.ObjectMap;
import org.red5.io.utils.XMLUtils;
import org.w3c.dom.Document;

/**
 * Java counterparts of the interop corpus: the values of each named case as Red5 applications hold them. The oracle script builds the same
 * values in Python under the same names.
 */
public final class InteropCorpus {

    public static final long MS = 1700000000123L;

    public static final String MULTI = "h\u00e9llo w\u00f6rld \u2713 \u65e5\u672c \uD83D\uDE00";

    public static final String XML = "<root a=\"1\" b=\"two\"><child>text</child><empty/></root>";

    private InteropCorpus() {
    }

    private static ObjectMap<String, Object> object(Object... pairs) {
        ObjectMap<String, Object> map = new ObjectMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static List<Object> list(Object... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private static Document xml() {
        try {
            return XMLUtils.stringToDoc(XML);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String repeat(String s, int count) {
        return s.repeat(count);
    }

    private static Object nestedRepeated(Object one) {
        ObjectMap<String, Object> shared = object("k", one);
        return object("first", shared, "second", shared);
    }

    private static Object selfRef() {
        ObjectMap<String, Object> d = object();
        d.put("self", d);
        return d;
    }

    private static Object parentRef() {
        ObjectMap<String, Object> parent = object();
        ObjectMap<String, Object> child = object("name", "c", "parent", parent);
        parent.put("child", child);
        parent.put("name", "p");
        return parent;
    }

    private static Object listRepeated(Object a, Object b) {
        List<Object> inner = list(a, b);
        return list(inner, inner);
    }

    private static Map<Object, Object> mixedArray() {
        Map<Object, Object> m = new LinkedHashMap<>();
        m.put(0, "zero");
        m.put(1, "one");
        m.put("name", "mixed");
        return m;
    }

    private static Map<Object, Object> assocArray(Object one) {
        Map<Object, Object> m = new LinkedHashMap<>();
        m.put("a", one);
        m.put("b", "x");
        return m;
    }

    private static Object points() {
        InteropPoint p = new InteropPoint(1, 2.5);
        return list(p, new InteropPoint(-3, 0.5), p);
    }

    private static Object tree() {
        InteropNode root = new InteropNode("root");
        for (String name : new String[] { "a", "b" }) {
            InteropNode c = new InteropNode(name);
            c.parent = root;
            root.children.add(c);
        }
        return root;
    }

    /**
     * Returns a list of count distinct objects followed by the objects at the repeat indices again, so the encoder has to write references
     * to them.
     *
     * @param count
     *            number of distinct objects
     * @param amf3
     *            true for integer members, false for AMF0 numbers
     * @param repeat
     *            indices of the objects to repeat
     * @return list
     */
    public static List<Object> manyRefs(int count, boolean amf3, int... repeat) {
        List<Object> objects = new ArrayList<>(count + repeat.length);
        for (int i = 0; i < count; i++) {
            objects.add(object("i", amf3 ? (Object) i : (Object) (double) i));
        }
        for (int i : repeat) {
            objects.add(objects.get(i));
        }
        return objects;
    }

    private static ByteArray bytes() {
        ByteArray b = new ByteArray();
        b.writeBytes(new byte[] { 0x00, 0x01, 0x02, 0x7F, (byte) 0x80, (byte) 0xFF });
        return b;
    }

    /**
     * Returns the top level values of a case, or null when Red5 has no Java value for it (undefined, an unknown class alias, a body that
     * mixes AMF0 and AMF3 values).
     *
     * @param codec
     *            amf0, amf3 or amf0+amf3
     * @param name
     *            case name
     * @return values or null
     */
    public static List<Object> values(String codec, String name) {
        boolean amf3 = !"amf0".equals(codec);
        // AMF0 has a single number type, the oracle writes Python floats there
        Object one = amf3 ? (Object) 1 : (Object) 1.0;
        switch (name) {
            case "null":
                return list((Object) null);
            case "true":
                return list(true);
            case "false":
                return list(false);
            case "zero":
                return list(0.0);
            case "negzero":
                return list(-0.0);
            case "nan":
                return list(Double.NaN);
            case "posinf":
                return list(Double.POSITIVE_INFINITY);
            case "neginf":
                return list(Double.NEGATIVE_INFINITY);
            case "onehalf":
                return list(1.5);
            case "large":
                return list(1e300);
            case "pow53":
                return list(9007199254740992.0);
            case "fraction":
                return list(-123456789.25);
            case "tenth":
                return list(0.1);
            case "int_0":
                return list(0);
            case "int_1":
                return list(1);
            case "int_m1":
                return list(-1);
            case "int_127":
                return list(127);
            case "int_128":
                return list(128);
            case "int_16383":
                return list(16383);
            case "int_16384":
                return list(16384);
            case "int_2097151":
                return list(2097151);
            case "int_2097152":
                return list(2097152);
            case "int_max":
                return list(268435455);
            case "int_min":
                return list(-268435456);
            case "int_over":
                return list(268435456);
            case "int_under":
                return list(-268435457);
            case "int_2p31":
                return list(2147483648L);
            case "str_empty":
                return list("");
            case "str_ascii":
                return list("hello");
            case "str_multibyte":
                return list(MULTI);
            case "str_65535":
                return list(repeat("a", 65535));
            case "str_65536":
                return list(repeat("a", 65536));
            case "str_65535_mb":
                return list(repeat("\u00e9", 32767) + "a");
            case "str_refs":
                return list("abc", "abc", MULTI, "abc", MULTI, "");
            case "date":
                return list(new Date(MS));
            case "date_repeated": {
                Date d = new Date(MS);
                return list(list(d, d));
            }
            case "object":
                return list(object("a", one, "b", "x"));
            case "object_empty":
                return list(object());
            case "object_nested_repeated":
                return list(nestedRepeated(one));
            case "object_self_ref":
                return list(selfRef());
            case "object_parent_ref":
                return list(parentRef());
            case "objects_traits_ref":
                return list(list(object("a", 1, "b", "x"), object("a", 2, "b", "y"), object("a", 3, "b", "x")));
            case "strict_array":
                return list(list(1.0, "two", null, true));
            case "dense_array":
                return list(list(1, "two", null, true, 1.5));
            case "strict_array_empty":
            case "dense_array_empty":
                return list(list());
            case "strict_array_repeated":
                return list(listRepeated(1.0, 2.0));
            case "dense_array_repeated":
                return list(listRepeated(1, 2));
            case "ecma_array":
            case "mixed_array":
                return list(mixedArray());
            case "ecma_array_assoc":
            case "assoc_array":
                return list(assocArray(one));
            case "typed_points":
                return list(points());
            case "typed_tree":
                return list(tree());
            case "xml":
                return list(xml());
            case "xml_then_strings":
                return list(xml(), "a", "a");
            case "date_obj_ref": {
                ObjectMap<String, Object> o = object("k", 1.0);
                return list(list(new Date(MS), o, o));
            }
            case "body_command":
                return list("connect", 1.0, object("app", "live", "objectEncoding", 0.0), null);
            case "body_shared_refs": {
                ObjectMap<String, Object> o = object("k", 1.0);
                return list("call", 2.0, o, o);
            }
            case "bytearray":
                return list(bytes());
            case "bytearray_repeated": {
                ByteArray b = bytes();
                return list(list(b, b));
            }
            case "array_collection":
                return list(new ArrayCollection<Object>(new Object[] { "a", 1 }));
            case "switched_values":
                return list("abc", 1, 1.5, object("a", 1), list(1, "abc"), null);
            case "switched_shared_refs": {
                ObjectMap<String, Object> o = object("k", 1);
                return list("abc", o, "abc", o);
            }
            case "switched_xml_bytes_date":
                return list(xml(), bytes(), new Date(MS));
            case "list_self_ref": {
                List<Object> l = list();
                l.add(l);
                return list(l);
            }
            case "ecma_self_ref":
            case "mixed_self_ref": {
                Map<Object, Object> m = new LinkedHashMap<>();
                m.put(0, "zero");
                m.put("me", m);
                return list(m);
            }
            case "ecma_sparse":
            case "sparse_array": {
                Map<Object, Object> m = new LinkedHashMap<>();
                m.put(0, "a");
                m.put(2, "c");
                return list(m);
            }
            case "nested_switch": {
                ObjectMap<String, Object> c = object("d", "x");
                return list(object("a", object("k", 1), "b", 1.0, "c", c, "e", c));
            }
            case "unicode_keys":
                return list(object("cl\u00e9", one, "\u65e5\u672c", "x"));
            case "key_value_strings":
                return list("abc", object("abc", "abc"), "abc");
            case "bytearray_empty":
                return list(new ByteArray());
            case "dates_in_object": {
                Date d = new Date(MS);
                return list(object("d1", d, "d2", d));
            }
            case "int_types":
                return list(5L, (short) -7, 100000);
            case "str_2m":
                return list(repeat("a", 2097152));
            case "dense_array_large": {
                List<Object> l = new ArrayList<>();
                for (int i = 0; i < 20000; i++) {
                    l.add(i);
                }
                return list(l);
            }
            case "str_refs_many": {
                List<Object> l = new ArrayList<>();
                for (int r = 0; r < 2; r++) {
                    for (int i = 0; i < 300; i++) {
                        l.add("s" + i);
                    }
                }
                return list(l);
            }
            case "objects_many_refs":
                return list(amf3 ? manyRefs(40000, true, 0, 35000) : manyRefs(66000, false, 0, 40000, 65535, 65535));
            case "arrays_many_refs": {
                List<Object> arrays = new ArrayList<>();
                for (int i = 0; i < 40000; i++) {
                    arrays.add(list(i));
                }
                arrays.add(arrays.get(0));
                arrays.add(arrays.get(35000));
                return list(arrays);
            }
            case "ecma_numeric_keys": {
                Map<Object, Object> m = new LinkedHashMap<>();
                m.put("-1", "d");
                m.put("01", "a");
                m.put("1", "b");
                m.put("1.5", "e");
                m.put("12345678901", "c");
                return list(m);
            }
            case "object_empty_key":
                return list(object("", 1.0, "a", 2.0));
            case "vec_int":
                return list(new Vector<>(List.of(1, -2, Integer.MAX_VALUE, Integer.MIN_VALUE)));
            case "vec_int_empty":
                return list(new Vector<Integer>());
            case "vec_uint":
                return list(new Vector<>(List.of(0L, 1L, 4294967295L)));
            case "vec_number":
                return list(new Vector<>(List.of(1.5, Double.NaN, -0.0, Double.POSITIVE_INFINITY)));
            case "vec_object":
                return list(new Vector<>(Arrays.asList("a", 1, null)));
            case "vec_string":
                return list(new Vector<>(List.of("x", "y", "x")));
            case "vec_repeated": {
                Vector<Integer> v = new Vector<>(List.of(7, 8));
                return list(object("a", v, "b", v));
            }
            default:
                return null;
        }
    }

}
