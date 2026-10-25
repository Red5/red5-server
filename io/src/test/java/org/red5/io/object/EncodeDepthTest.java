package org.red5.io.object;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.mina.core.buffer.IoBuffer;
import org.junit.Test;
import org.red5.io.amf.Input;
import org.red5.io.amf.Output;

/**
 * Verifies the encoder's nesting limit, which matches the decoder's and stops a cycle that AMF0 cannot write as a reference.
 */
public class EncodeDepthTest {

    @Test
    public void testNestingWithinLimitEncodesAndDecodes() {
        // the outermost value counts as one level, as it does for the decoder, so this is the deepest value both accept
        List<Object> value = nested(BaseInput.MAX_DEPTH);
        IoBuffer buf = IoBuffer.allocate(4096).setAutoExpand(true);
        Serializer.serialize(new Output(buf), value);
        buf.flip();
        Object decoded = Deserializer.deserialize(new Input(buf), Object.class);
        int depth = 0;
        while (decoded instanceof List<?> list && !list.isEmpty()) {
            decoded = list.get(0);
            depth++;
        }
        // MAX_DEPTH lists, the innermost empty
        assertEquals(BaseInput.MAX_DEPTH - 1, depth);
    }

    @Test
    public void testNestingBeyondLimitFails() {
        try {
            Serializer.serialize(new Output(IoBuffer.allocate(4096).setAutoExpand(true)), nested(BaseInput.MAX_DEPTH + 1));
            fail("nesting beyond the decoder's limit must not be encoded");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("nesting depth"));
        }
    }

    /**
     * A vector the AMF0 encoder switches to AMF3 continues the depth of the AMF0 values around it, as the decoder counts it: AMF0 lists with
     * vectors inside encode and decode up to the limit in total and fail beyond it.
     */
    @Test
    public void testSwitchedVectorsContinueTheAmf0Depth() {
        int amf0Levels = BaseInput.MAX_DEPTH / 2;
        int vectorLevels = BaseInput.MAX_DEPTH - amf0Levels;
        IoBuffer buf = IoBuffer.allocate(4096).setAutoExpand(true);
        Serializer.serialize(new Output(buf), nestedWithVectors(amf0Levels, vectorLevels));
        buf.flip();
        Object decoded = Deserializer.deserialize(new Input(buf), Object.class);
        int depth = 0;
        while (decoded instanceof List<?> list && !list.isEmpty()) {
            decoded = list.get(0);
            depth++;
        }
        assertEquals(BaseInput.MAX_DEPTH - 1, depth);
        try {
            Serializer.serialize(new Output(IoBuffer.allocate(4096).setAutoExpand(true)), nestedWithVectors(amf0Levels, vectorLevels + 1));
            fail("nesting beyond the decoder's limit must not be encoded");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("nesting depth"));
        }
    }

    private static List<Object> nestedWithVectors(int amf0Levels, int vectorLevels) {
        Vector<Object> vector = new Vector<>();
        for (int i = 1; i < vectorLevels; i++) {
            Vector<Object> outer = new Vector<>();
            outer.add(vector);
            vector = outer;
        }
        List<Object> list = new ArrayList<>();
        list.add(vector);
        for (int i = 1; i < amf0Levels; i++) {
            List<Object> outer = new ArrayList<>();
            outer.add(list);
            list = outer;
        }
        return list;
    }

    /**
     * AMF0 references hold a U16 index, so an object stored at a higher index is written inline when it repeats. When it repeats inside
     * itself that never ends; the encoder must fail cleanly instead of overflowing the stack.
     */
    @Test
    public void testAmf0CycleBeyondReferenceRangeFails() {
        List<Object> values = new ArrayList<>();
        for (int i = 0; i < 0x10000; i++) {
            values.add(new HashMap<String, Object>());
        }
        Map<String, Object> cyclic = new HashMap<>();
        cyclic.put("self", cyclic);
        values.add(cyclic);
        try {
            Serializer.serialize(new Output(IoBuffer.allocate(1 << 20).setAutoExpand(true)), values);
            fail("a cycle beyond the AMF0 reference range cannot be encoded");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("cycle"));
        } catch (StackOverflowError e) {
            fail("the encoder overflowed the stack instead of failing cleanly");
        }
    }

    private static List<Object> nested(int levels) {
        List<Object> outer = new ArrayList<>();
        List<Object> current = outer;
        for (int i = 1; i < levels; i++) {
            List<Object> inner = new ArrayList<>();
            current.add(inner);
            current = inner;
        }
        return outer;
    }

}
