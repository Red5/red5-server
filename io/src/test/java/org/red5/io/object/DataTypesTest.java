package org.red5.io.object;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;
import org.red5.io.amf.AMF;
import org.red5.io.amf.Input;

/**
 * Verifies the data type classification helpers and that core types do not overlap the custom type ranges (PR #415 follow-up).
 */
public class DataTypesTest {

    private static final Set<Byte> BASIC = Set.of(DataTypes.CORE_SKIP, DataTypes.CORE_NULL, DataTypes.CORE_BOOLEAN, DataTypes.CORE_NUMBER, DataTypes.CORE_STRING, DataTypes.CORE_DATE);

    private static final Set<Byte> COMPLEX = Set.of(DataTypes.CORE_ARRAY, DataTypes.CORE_MAP, DataTypes.CORE_XML, DataTypes.CORE_OBJECT, DataTypes.CORE_BYTEARRAY, DataTypes.CORE_VECTOR_INT, DataTypes.CORE_VECTOR_UINT, DataTypes.CORE_VECTOR_NUMBER, DataTypes.CORE_VECTOR_OBJECT);

    @Test
    public void testClassificationOfEveryByte() {
        for (int i = Byte.MIN_VALUE; i <= Byte.MAX_VALUE; i++) {
            byte type = (byte) i;
            String name = String.format("0x%02X (%s)", type & 0xff, DataTypes.toStringValue(type));
            assertEquals("isBasicType " + name, BASIC.contains(type), DataTypes.isBasicType(type));
            assertEquals("isComplexType " + name, COMPLEX.contains(type), DataTypes.isComplexType(type));
            assertFalse("a type cannot be both basic and complex: " + name, DataTypes.isBasicType(type) && DataTypes.isComplexType(type));
        }
    }

    @Test
    public void testMarkersAreNeitherBasicNorComplex() {
        // signed bytes: these used to fall below CORE_DATE and count as basic, and the original complex check accepted every value
        for (byte marker : new byte[] { DataTypes.CORE_END_OBJECT, DataTypes.CORE_SWITCH, DataTypes.OPT_REFERENCE }) {
            assertFalse(DataTypes.isBasicType(marker));
            assertFalse(DataTypes.isComplexType(marker));
        }
    }

    @Test
    public void testToStringValue() {
        assertEquals("Vector<int>", DataTypes.toStringValue(DataTypes.CORE_VECTOR_INT));
        assertEquals("End of object", DataTypes.toStringValue(DataTypes.CORE_END_OBJECT));
        assertEquals("AMF3 switch", DataTypes.toStringValue(DataTypes.CORE_SWITCH));
        assertEquals("AMF[13]", DataTypes.toStringValue((byte) (AMF.TYPE_UNSUPPORTED + DataTypes.CUSTOM_AMF_MASK)));
        // values outside the known ranges are not reported as custom XML types
        assertEquals("Unknown[0x80]", DataTypes.toStringValue((byte) 0x80));
        assertEquals("Unknown[0x1F]", DataTypes.toStringValue((byte) 0x1F));
    }

    @Test
    public void testCoreTypesDoNotOverlapCustomTypes() {
        byte[] core = { DataTypes.CORE_SKIP, DataTypes.CORE_NULL, DataTypes.CORE_BOOLEAN, DataTypes.CORE_NUMBER, DataTypes.CORE_STRING, DataTypes.CORE_DATE, DataTypes.CORE_ARRAY, DataTypes.CORE_MAP, DataTypes.CORE_XML, DataTypes.CORE_OBJECT, DataTypes.CORE_BYTEARRAY, DataTypes.OPT_REFERENCE, DataTypes.CORE_VECTOR_INT, DataTypes.CORE_VECTOR_UINT, DataTypes.CORE_VECTOR_NUMBER, DataTypes.CORE_VECTOR_OBJECT };
        for (byte type : core) {
            assertTrue(String.format("core type 0x%02X must stay below the custom ranges", type), type >= 0 && type < DataTypes.CUSTOM_MOCK_MASK);
            assertFalse(DataTypes.isCustomType(type));
        }
        // the AMF0 decoder reports markers it has no core type for as the marker plus the AMF mask
        for (byte marker : new byte[] { AMF.TYPE_MOVIECLIP, AMF.TYPE_UNSUPPORTED, AMF.TYPE_RECORDSET }) {
            byte custom = (byte) (marker + DataTypes.CUSTOM_AMF_MASK);
            assertTrue(DataTypes.isCustomType(custom));
            assertFalse(DataTypes.isComplexType(custom));
        }
    }

    /**
     * AMF0 2.15: the unsupported marker carries no further data; the movieclip and recordset markers are reserved. Each is decoded as
     * null and the following value is read intact.
     */
    @Test
    public void testAmf0ReservedAndUnsupportedMarkersDecodeAsNull() {
        for (byte marker : new byte[] { AMF.TYPE_MOVIECLIP, AMF.TYPE_UNSUPPORTED, AMF.TYPE_RECORDSET }) {
            // the marker, then the AMF0 number 1.0
            IoBuffer buf = IoBuffer.wrap(new byte[] { marker, AMF.TYPE_NUMBER, 0x3F, (byte) 0xF0, 0, 0, 0, 0, 0, 0 });
            Input in = new Input(buf);
            assertNull(String.format("marker 0x%02X", marker), Deserializer.deserialize(in, Object.class));
            assertEquals(1.0, (Double) Deserializer.deserialize(in, Object.class), 0d);
            assertFalse(buf.hasRemaining());
        }
    }

}
