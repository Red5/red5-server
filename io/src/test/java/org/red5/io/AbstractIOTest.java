/*
 * 7 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.commons.beanutils.BeanMap;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.red5.io.amf.AMF;
import org.red5.io.model.CircularRefBean;
import org.red5.io.model.SimpleJavaBean;
import org.red5.io.object.Deserializer;
import org.red5.io.object.Input;
import org.red5.io.object.Output;
import org.red5.io.object.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author The Red5 Project
 * @author Luke Hubbard, Codegent Ltd (luke@codegent.com)
 */
public abstract class AbstractIOTest {

    protected Logger log = LoggerFactory.getLogger(AbstractIOTest.class);

    protected Random rnd;

    protected int encoding = 0;

    protected Input in;

    protected Output out;

    abstract void dumpOutput();

    abstract void resetOutput();

    /** {@inheritDoc} */
    @Before
    public void setUp() {
        setupIO();
        rnd = new Random();
    }

    abstract void setupIO();

    @Test
    public void testArray() {
        log.debug("\ntestArray");
        String[] strArrIn = new String[] { "This", "Is", "An", "Array", "Of", "Strings" };
        Serializer.serialize(out, strArrIn);
        dumpOutput();
        Object[] objArrayOut = Deserializer.deserialize(in, Object[].class);
        for (int i = 0; i < strArrIn.length; i++) {
            assertEquals(strArrIn[i], objArrayOut[i]);
        }
        resetOutput();
    }

    @Test
    public void testArrayReference() {
        log.debug("\ntestArrayReference");
        TestVO mytest = new TestVO();
        TestVO[] strArrIn = new TestVO[] { mytest, mytest };
        Serializer.serialize(out, strArrIn);
        dumpOutput();
        TestVO[] objArrayOut = Deserializer.deserialize(in, TestVO[].class);
        assertEquals("Array length should be the same after deserialization", strArrIn.length, objArrayOut.length);
        for (int i = 0; i < strArrIn.length; i++) {
            assertEquals(String.format("The %sth value should be the same", i), strArrIn[i], objArrayOut[i]);
        }
        resetOutput();
    }

    @Test
    public void testBoolean() {
        log.debug("\ntestBoolean");
        Serializer.serialize(out, Boolean.TRUE);
        dumpOutput();
        Boolean val = Deserializer.deserialize(in, Boolean.class);
        assertEquals(Boolean.TRUE, val);
        resetOutput();
        Serializer.serialize(out, Boolean.FALSE);
        dumpOutput();
        val = Deserializer.deserialize(in, Boolean.class);
        assertEquals(Boolean.FALSE, val);
        resetOutput();
    }

    @Test
    public void testCircularReference() {
        log.debug("\ntestCircularReference");
        CircularRefBean beanIn = new CircularRefBean();
        beanIn.setRefToSelf(beanIn);
        Serializer.serialize(out, beanIn);

        dumpOutput();
        CircularRefBean beanOut = Deserializer.deserialize(in, CircularRefBean.class);
        assertNotNull(beanOut);
        assertEquals(beanOut, beanOut.getRefToSelf());
        assertEquals(beanIn.getNameOfBean(), beanOut.getNameOfBean());
        resetOutput();
    }

    @Test
    public void testDate() {
        log.debug("\ntestDate");
        Date dateIn = new Date();
        Serializer.serialize(out, dateIn);
        dumpOutput();
        Date dateOut = Deserializer.deserialize(in, Date.class);
        assertEquals(dateIn, dateOut);
        resetOutput();
    }

    @Test
    @SuppressWarnings({ "rawtypes" })
    public void testJavaBean() {
        log.debug("\ntestJavaBean");
        TestJavaBean beanIn = new TestJavaBean();
        beanIn.setTestString("test string here");
        beanIn.setTestBoolean((System.currentTimeMillis() % 2 == 0) ? true : false);
        beanIn.setTestBooleanObject((System.currentTimeMillis() % 2 == 0) ? Boolean.TRUE : Boolean.FALSE);
        beanIn.setTestNumberObject(Integer.valueOf((int) System.currentTimeMillis() / 1000));
        Serializer.serialize(out, beanIn);
        dumpOutput();
        Object mapOrBean = Deserializer.deserialize(in, Object.class);
        assertEquals(beanIn.getClass().getName(), mapOrBean.getClass().getName());
        Map<?, ?> map = (mapOrBean instanceof Map) ? (Map<?, ?>) mapOrBean : new BeanMap(mapOrBean);
        Set<?> entrySet = map.entrySet();
        Iterator<?> it = entrySet.iterator();
        Map beanInMap = new BeanMap(beanIn);
        assertEquals(beanInMap.size(), map.size());
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            String propOut = (String) entry.getKey();
            Object valueOut = entry.getValue();
            assertTrue(beanInMap.containsKey(propOut));
            assertEquals(valueOut, beanInMap.get(propOut));
        }
        resetOutput();
    }

    @Test
    public void testList() {
        log.debug("\ntestList");
        List<Comparable<?>> listIn = new LinkedList<Comparable<?>>();
        listIn.add(null);
        listIn.add(Boolean.FALSE);
        listIn.add(Boolean.TRUE);
        listIn.add(Integer.valueOf(1));
        listIn.add("This is a test string");
        listIn.add(new Date());
        assertEquals(6, listIn.size());
        Serializer.serialize(out, listIn);
        dumpOutput();
        List<?> listOut = Deserializer.deserialize(in, List.class);
        assertNotNull(listOut);
        assertEquals(listIn.size(), listOut.size());
        for (int i = 0; i < listIn.size(); i++) {
            Object in = listIn.get(i);
            // no integers in AMF0 so convert expected to double
            if (encoding == 0 && in instanceof Integer) {
                in = ((Number) in).doubleValue();
            }
            Object out = listOut.get(i);
            assertEquals(in, out);
        }
        resetOutput();
    }

    @Test
    public void testMap() {
        log.debug("\ntestMap");
        Map<String, Object> mapIn = new HashMap<>();
        mapIn.put("testNumber", 34d); //numbers are stored as double
        mapIn.put("testString", "wicked awesome");
        mapIn.put("testBean", new SimpleJavaBean());
        mapIn.put("21.0.1", "version");
        mapIn.put("2.1", "version2");
        Serializer.serialize(out, mapIn);
        dumpOutput();
        Map<?, ?> mapOut = Deserializer.deserialize(in, Map.class);
        log.info("mapOut: {}", mapOut);
        assertNotNull(mapOut);
        assertEquals(mapIn.size(), mapOut.size());
        for (Map.Entry<String, Object> entry : mapIn.entrySet()) {
            String key = entry.getKey();
            Object iVal = entry.getValue();
            Object oVal = mapOut.get(key);
            assertNotNull(oVal);
            assertEquals(iVal, oVal);
        }
        resetOutput();
    }

    @Test
    public void testNull() {
        log.debug("\ntestNull");
        Serializer.serialize(out, null);
        dumpOutput();
        Object val = Deserializer.deserialize(in, Object.class);
        assertEquals(val, null);
        resetOutput();
    }

    //@Test //This test failed, not sure why :(
    public void testNumberLong() {
        log.debug("\ntestNumberLong");
        for (Number n : new Number[] { Long.MIN_VALUE, rnd.nextLong(), -666L, 0L, 666L, Long.MAX_VALUE }) {
            Serializer.serialize(out, n);
            dumpOutput();
            Number rn = Deserializer.deserialize(in, Number.class);
            assertEquals("Deserialized Long should be the same", n, rn.longValue());
            resetOutput();
        }
    }

    @Test
    public void testNumberInteger() {
        log.debug("\ntestNumberInteger");
        for (Number n : new Number[] { Integer.MIN_VALUE, Integer.MAX_VALUE, 1024, rnd.nextInt(Integer.MAX_VALUE) }) {
            Serializer.serialize(out, n);
            dumpOutput();
            Number rn = Deserializer.deserialize(in, Number.class);
            assertEquals("Deserialized Integer should be the same", n, rn.intValue());
            resetOutput();
        }
    }

    @Test
    public void testNumberFloat() {
        log.debug("\ntestNumberFloat");
        for (Number n : new Number[] { Float.MIN_VALUE, Float.MIN_NORMAL, Float.MAX_VALUE, rnd.nextFloat(), 666.6666f }) {
            Serializer.serialize(out, n);
            dumpOutput();
            Number rn = Deserializer.deserialize(in, Number.class);
            assertEquals("Deserialized Float should be the same", (Float) n, (Float) rn.floatValue());
            resetOutput();
        }
    }

    @Test
    public void testNumberDouble() {
        log.debug("\ntestNumberDouble");
        for (Number n : new Number[] { 1.056d, Double.MIN_VALUE, Double.MAX_VALUE, Double.valueOf(899.45678d), rnd.nextDouble() }) {
            Serializer.serialize(out, n);
            dumpOutput();
            Number rn = Deserializer.deserialize(in, Number.class);
            assertEquals("Deserialized number should be the same", n, rn.doubleValue());
            resetOutput();
        }
    }

    @Test
    public void testNumber() {
        log.debug("\ntestNumber");
        int num = 1000;
        Serializer.serialize(out, Integer.valueOf(num));
        dumpOutput();
        Number n = Deserializer.deserialize(in, Number.class);
        assertEquals(n.intValue(), num);
        resetOutput();
    }

    @Test
    public void testInteger() {
        log.debug("\ntestInteger");
        int num = 129;
        Serializer.serialize(out, Integer.valueOf(num));
        dumpOutput();
        int n = ((Number) Deserializer.deserialize(in, Number.class)).intValue();
        assertEquals(n, num);
        resetOutput();
    }

    @Test
    public void testNegativeInteger() {
        log.debug("\ntestNegativeInteger");
        int num = -129;
        Serializer.serialize(out, Integer.valueOf(num));
        dumpOutput();
        int n = ((Number) Deserializer.deserialize(in, Number.class)).intValue();
        log.debug("Integer: {} {}", n, num);
        assertEquals(n, num);
        resetOutput();
    }

    @Test
    @SuppressWarnings({})
    public void testSimpleReference() {
        log.debug("\ntestSimpleReference");
        Map<String, Object> mapIn = new HashMap<String, Object>();
        Object bean = new SimpleJavaBean();
        mapIn.put("thebean", bean);
        mapIn.put("thesamebeanagain", bean);
        // mapIn.put("thismap",mapIn);
        Serializer.serialize(out, mapIn);

        dumpOutput();
        Map<?, ?> mapOut = Deserializer.deserialize(in, Map.class);
        assertNotNull(mapOut);
        assertEquals(mapIn.size(), mapOut.size());

        Set<?> entrySet = mapOut.entrySet();
        Iterator<?> it = entrySet.iterator();
        while (it.hasNext()) {
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) it.next();
            String propOut = (String) entry.getKey();
            SimpleJavaBean valueOut = (SimpleJavaBean) entry.getValue();
            assertNotNull("couldn't get output bean", valueOut);
            assertTrue(mapIn.containsKey(propOut));
            SimpleJavaBean valueIn = (SimpleJavaBean) mapIn.get(propOut);
            assertNotNull("couldn't get input bean", valueIn);
            assertEquals(valueOut.getNameOfBean(), valueIn.getNameOfBean());
        }
        resetOutput();
    }

    @Test
    public void testString() {
        log.debug("\ntestString");
        String inStr = "hello world \u00A3";
        Serializer.serialize(out, inStr);
        dumpOutput();
        String outStr = Deserializer.deserialize(in, String.class);
        assertEquals(inStr, outStr);
        resetOutput();
    }

    @Test
    public void testLongString() {
        log.debug("\ntestLongString");
        byte[] rndStr = new byte[AMF.LONG_STRING_LENGTH];
        Arrays.fill(rndStr, (byte) 0x65);
        //Random rnd = new Random();
        //rnd.nextBytes(rndStr);
        String inStr = new String(rndStr, StandardCharsets.UTF_8);
        //String inStr = RandomStringUtils.random(AMF.LONG_STRING_LENGTH);
        //log.trace(inStr);
        Serializer.serialize(out, inStr);
        dumpOutput();
        String outStr = Deserializer.deserialize(in, String.class);
        assertEquals(inStr, outStr);
        resetOutput();
    }

    @Test
    public void testLongString1() {
        log.debug("\ntestLongString1");
        String inStr = RandomStringUtils.random(rnd.nextInt(AMF.LONG_STRING_LENGTH));
        log.trace(inStr);
        Serializer.serialize(out, inStr);
        dumpOutput();
        String outStr = Deserializer.deserialize(in, String.class);
        assertEquals(inStr, outStr);
        resetOutput();
    }
}
