/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.interop;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

import org.red5.compatibility.flex.messaging.io.ArrayCollection;
import org.red5.io.amf3.ByteArray;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Writes a decoded AMF value graph as a canonical text, the same text the interop oracle scripts produce for the values they encode, so that
 * the two sides can be compared as strings. Complex values (lists, maps, typed objects, dates, byte arrays, XML) are numbered in the order
 * they are first met; a repeated instance is written as a back reference, so the text also shows object identity.
 * <ul>
 * <li>null, true, false</li>
 * <li>numbers: in AMF0 mode {@code n:<IEEE-754 hex>}; in AMF3 mode {@code i:<value>} for integral values that fit the 29-bit AMF3 integer,
 * {@code d:<IEEE-754 hex>} otherwise; NaN is written as {@code NaN}</li>
 * <li>strings: {@code s'text'} when short printable ASCII, otherwise {@code s#<utf-8 length>:<sha-256 prefix>}</li>
 * <li>{@code #k[...]} list, {@code #k{key=value,...}} map with sorted keys, {@code #k<alias>{...}} typed object, {@code #kdate:<ms>},
 * {@code #kbytes:<hex>}, {@code #kxml:<normalized>}, {@code #kvec[...]} vector and {@code &k} for a repeated instance</li>
 * </ul>
 */
public final class AmfDescriber {

    private static final long MIN_INT29 = -268435456L;

    private static final long MAX_INT29 = 268435455L;

    private final boolean amf0;

    private final Map<Object, Integer> seen = new IdentityHashMap<>();

    private AmfDescriber(boolean amf0) {
        this.amf0 = amf0;
    }

    /**
     * Describes the top level values of one message body.
     *
     * @param values
     *            decoded values
     * @param amf0
     *            true when all numbers are AMF0 numbers
     * @return canonical text
     */
    public static String describe(List<?> values, boolean amf0) {
        AmfDescriber d = new AmfDescriber(amf0);
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(d.value(values.get(i)));
        }
        return sb.append(')').toString();
    }

    private static String dbl(double v) {
        if (Double.isNaN(v)) {
            return "NaN";
        }
        return String.format("%016x", Double.doubleToRawLongBits(v));
    }

    private static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    private static String string(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        boolean plain = b.length <= 64;
        for (int i = 0; plain && i < b.length; i++) {
            int c = b[i] & 0xFF;
            plain = c >= 0x20 && c < 0x7F && c != '\'' && c != '\\';
        }
        if (plain) {
            return "s'" + s + "'";
        }
        try {
            String digest = hex(MessageDigest.getInstance("SHA-256").digest(b));
            return "s#" + b.length + ":" + digest.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String number(Number n) {
        if (amf0) {
            return "n:" + dbl(n.doubleValue());
        }
        if (n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte) {
            long v = n.longValue();
            if (v >= MIN_INT29 && v <= MAX_INT29) {
                return "i:" + v;
            }
        }
        return "d:" + dbl(n.doubleValue());
    }

    /** Returns the back reference of a value seen before, or null after numbering a new one. */
    private String track(Object v) {
        Integer k = seen.get(v);
        if (k != null) {
            return "&" + k;
        }
        seen.put(v, seen.size());
        return null;
    }

    private String value(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof Boolean) {
            return v.toString();
        }
        if (v instanceof Number) {
            return number((Number) v);
        }
        if (v instanceof String) {
            return string((String) v);
        }
        String ref = track(v);
        if (ref != null) {
            return ref;
        }
        int k = seen.get(v);
        if (v instanceof Date) {
            return "#" + k + "date:" + ((Date) v).getTime();
        }
        if (v instanceof ByteArray) {
            ByteArray ba = (ByteArray) v;
            byte[] data = new byte[ba.length()];
            int pos = ba.position();
            ba.position(0);
            ba.readBytes(data);
            ba.position(pos);
            return "#" + k + "bytes:" + hex(data);
        }
        if (v instanceof byte[]) {
            return "#" + k + "bytes:" + hex((byte[]) v);
        }
        if (v instanceof Document) {
            return "#" + k + "xml:" + xml(((Document) v).getDocumentElement());
        }
        if (v instanceof ArrayCollection) {
            // the collection and the list holding its elements are two values on the wire
            List<Object> source = new ArrayList<>((ArrayCollection<?>) v);
            seen.put(source, seen.size());
            return "#" + k + "<flex.messaging.io.ArrayCollection>#" + seen.get(source) + list(source);
        }
        if (v instanceof Vector) {
            return "#" + k + "vec" + list((List<?>) v);
        }
        if (v instanceof List) {
            return "#" + k + list((List<?>) v);
        }
        if (v.getClass().isArray()) {
            List<Object> items = new ArrayList<>();
            for (int i = 0; i < Array.getLength(v); i++) {
                items.add(Array.get(v, i));
            }
            return "#" + k + list(items);
        }
        if (v instanceof Map) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                sorted.put(String.valueOf(e.getKey()), e.getValue());
            }
            return "#" + k + map(sorted);
        }
        if (v.getClass().getPackage().getName().equals(AmfDescriber.class.getPackage().getName())) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Field f : v.getClass().getFields()) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    try {
                        sorted.put(f.getName(), f.get(v));
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
            return "#" + k + "<" + v.getClass().getName() + ">" + map(sorted);
        }
        return "#" + k + "?" + v.getClass().getName();
    }

    private String list(List<?> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(value(items.get(i)));
        }
        return sb.append(']').toString();
    }

    private String map(Map<String, Object> sorted) {
        StringBuilder sb = new StringBuilder("{");
        for (Map.Entry<String, Object> e : sorted.entrySet()) {
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append(e.getKey()).append('=').append(value(e.getValue()));
        }
        return sb.append('}').toString();
    }

    private static String xml(Element e) {
        StringBuilder sb = new StringBuilder("<").append(e.getTagName());
        NamedNodeMap attributes = e.getAttributes();
        Map<String, String> sorted = new TreeMap<>();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node a = attributes.item(i);
            sorted.put(a.getNodeName(), a.getNodeValue());
        }
        sorted.forEach((name, value) -> sb.append(' ').append(name).append('=').append(value));
        sb.append('>');
        NodeList children = e.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node c = children.item(i);
            if (c instanceof Element) {
                sb.append(xml((Element) c));
            } else if (c.getNodeType() == Node.TEXT_NODE || c.getNodeType() == Node.CDATA_SECTION_NODE) {
                sb.append(c.getNodeValue().trim());
            }
        }
        return sb.append("</").append(e.getTagName()).append('>').toString();
    }

}
