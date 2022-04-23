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
public class SimpleJavaBean {

    private String nameOfBean = "jeff";

    /**
     * Getter for property 'nameOfBean'.
     *
     * @return Value for property 'nameOfBean'.
     */
    public String getNameOfBean() {
        return nameOfBean;
    }

    /**
     * Setter for property 'nameOfBean'.
     *
     * @param nameOfBean
     *            Value to set for property 'nameOfBean'.
     */
    public void setNameOfBean(String nameOfBean) {
        this.nameOfBean = nameOfBean;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SimpleJavaBean) {
            SimpleJavaBean sjb = (SimpleJavaBean) obj;
            return sjb.getNameOfBean().equals(sjb.getNameOfBean());
        }
        return false;
    }

}