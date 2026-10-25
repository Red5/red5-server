/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.object;

import java.util.HashMap;
import java.util.Map;

/**
 * BaseOutput represents a way to map input to a HashMap. This class is meant to be extended.
 *
 * @author The Red5 Project
 * @author Luke Hubbard, Codegent Ltd (luke@codegent.com)
 */
public class BaseOutput {

    static class IdentityWrapper {
        /**
         * Wrapped object
         */
        final Object object;

        /**
         * Creates wrapper for object
         *
         * @param object
         *            Object to wrap
         */
        public IdentityWrapper(Object object) {
            this.object = object;
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            return System.identityHashCode(object);
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(Object object) {
            if (object instanceof IdentityWrapper) {
                return ((IdentityWrapper) object).object == this.object;
            }
            return false;
        }

    }

    /**
     * References map
     */
    protected Map<IdentityWrapper, Integer> refMap;

    /**
     * Reference id, the index the next stored object takes
     */
    protected int refId;

    /**
     * BaseOutput Constructor
     */
    protected BaseOutput() {
        refMap = new HashMap<>();
    }

    /**
     * Store an object into a map
     *
     * @param obj
     *            Object to store
     */
    protected void storeReference(Object obj) {
        refMap.put(new IdentityWrapper(obj), Integer.valueOf(refId++));
    }

    /**
     * Returns a boolean stating whether the map contains an object with that key
     *
     * @param obj
     *            Object
     * @return boolean <code>true</code> if it does contain it, <code>false</code> otherwise
     */
    protected boolean hasReference(Object obj) {
        return refMap.containsKey(new IdentityWrapper(obj));
    }

    /**
     * Clears the map
     */
    public void clearReferences() {
        refMap.clear();
        refId = 0;
    }

    /**
     * Returns the reference id based on the parameter obj
     *
     * @param obj
     *            Object
     * @return int Reference id
     */
    protected int getReferenceId(Object obj) {
        return refMap.get(new IdentityWrapper(obj)).intValue();
    }

    /**
     * Current nesting depth of the value being written
     */
    private int depth;

    /**
     * Enters a nested value, failing when the nesting depth would exceed {@link BaseInput#MAX_DEPTH}, the depth a decoder accepts. A cycle
     * that cannot be written as a reference, such as one back to an AMF0 object whose index does not fit the U16 of a reference, would
     * otherwise recurse until the stack overflows. Every call must be paired with {@link #exitNested()}.
     */
    protected void enterNested() {
        if (++depth > BaseInput.MAX_DEPTH) {
            depth--;
            throw new IllegalStateException("AMF nesting depth exceeds " + BaseInput.MAX_DEPTH + ", the value is nested too deeply or contains a cycle that cannot be written as a reference");
        }
    }

    /**
     * Leaves a nested value entered with {@link #enterNested()}.
     */
    protected void exitNested() {
        depth--;
    }

    /**
     * Makes this output continue the nesting depth of another output whose current value it writes, such as the AMF3 value the AMF0 encoder
     * writes after an avmplus-object marker, so the depth limit applies as if one output wrote both.
     *
     * @param outer
     *            output that entered the level of the value
     */
    protected void continueNesting(BaseOutput outer) {
        depth = outer.depth;
    }

}
