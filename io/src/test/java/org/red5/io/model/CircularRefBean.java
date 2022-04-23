/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2016 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io.model;

/**
 * @author The Red5 Project
 * @author Luke Hubbard, Codegent Ltd (luke@codegent.com)
 */
public class CircularRefBean extends SimpleJavaBean {

    private CircularRefBean refToSelf;

    /**
     * Constructs a new CircularRefBean.
     */
    public CircularRefBean() {
        super();
    }

    /**
     * Getter for property 'refToSelf'.
     *
     * @return Value for property 'refToSelf'.
     */
    public CircularRefBean getRefToSelf() {
        return refToSelf;
    }

    /**
     * Setter for property 'refToSelf'.
     *
     * @param refToSelf
     *            Value to set for property 'refToSelf'.
     */
    public void setRefToSelf(CircularRefBean refToSelf) {
        this.refToSelf = refToSelf;
    }

}
