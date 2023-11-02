/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.io;

import java.util.Date;

/**
 * @author The Red5 Project
 * @author Luke Hubbard, Codegent Ltd (luke@codegent.com)
 */
public class TestJavaBean {

    private byte testByte = 65;

    private int testPrimitiveNumber = 3;

    private Integer testNumberObject = Integer.valueOf(33);

    private String testString = "red5 rocks!";

    private Date testDate = new Date();

    private Boolean testBooleanObject = Boolean.FALSE;

    private boolean testBoolean = true;

    /**
     * Getter for property 'testByte'.
     *
     * @return Value for property 'testByte'.
     */
    public byte getTestByte() {
        return testByte;
    }

    /**
     * Setter for property 'testByte'.
     *
     * @param testByte
     *            Value to set for property 'testByte'.
     */
    public void setTestByte(byte testByte) {
        this.testByte = testByte;
    }

    /**
     * Getter for property 'testBoolean'.
     *
     * @return Value for property 'testBoolean'.
     */
    public boolean isTestBoolean() {
        return testBoolean;
    }

    /**
     * Setter for property 'testBoolean'.
     *
     * @param testBoolean
     *            Value to set for property 'testBoolean'.
     */
    public void setTestBoolean(boolean testBoolean) {
        this.testBoolean = testBoolean;
    }

    /**
     * Getter for property 'testBooleanObject'.
     *
     * @return Value for property 'testBooleanObject'.
     */
    public Boolean getTestBooleanObject() {
        return testBooleanObject;
    }

    /**
     * Setter for property 'testBooleanObject'.
     *
     * @param testBooleanObject
     *            Value to set for property 'testBooleanObject'.
     */
    public void setTestBooleanObject(Boolean testBooleanObject) {
        this.testBooleanObject = testBooleanObject;
    }

    /**
     * Getter for property 'testDate'.
     *
     * @return Value for property 'testDate'.
     */
    public Date getTestDate() {
        return testDate;
    }

    /**
     * Setter for property 'testDate'.
     *
     * @param testDate
     *            Value to set for property 'testDate'.
     */
    public void setTestDate(Date testDate) {
        this.testDate = testDate;
    }

    /**
     * Getter for property 'testNumberObject'.
     *
     * @return Value for property 'testNumberObject'.
     */
    public Integer getTestNumberObject() {
        return testNumberObject;
    }

    /**
     * Setter for property 'testNumberObject'.
     *
     * @param testNumberObject
     *            Value to set for property 'testNumberObject'.
     */
    public void setTestNumberObject(Integer testNumberObject) {
        this.testNumberObject = testNumberObject;
    }

    /**
     * Getter for property 'testPrimitiveNumber'.
     *
     * @return Value for property 'testPrimitiveNumber'.
     */
    public int getTestPrimitiveNumber() {
        return testPrimitiveNumber;
    }

    /**
     * Setter for property 'testPrimitiveNumber'.
     *
     * @param testPrimitiveNumber
     *            Value to set for property 'testPrimitiveNumber'.
     */
    public void setTestPrimitiveNumber(int testPrimitiveNumber) {
        this.testPrimitiveNumber = testPrimitiveNumber;
    }

    /**
     * Getter for property 'testString'.
     *
     * @return Value for property 'testString'.
     */
    public String getTestString() {
        return testString;
    }

    /**
     * Setter for property 'testString'.
     *
     * @param testString
     *            Value to set for property 'testString'.
     */
    public void setTestString(String testString) {
        this.testString = testString;
    }

}
