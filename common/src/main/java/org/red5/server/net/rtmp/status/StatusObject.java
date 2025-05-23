/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.rtmp.status;

import java.util.HashMap;
import java.util.Map;

import org.red5.annotations.Anonymous;
import org.red5.io.amf3.IDataInput;
import org.red5.io.amf3.IDataOutput;
import org.red5.io.amf3.IExternalizable;

/**
 * Status object that is sent to client with every status event
 *
 * @author The Red5 Project
 * @author Luke Hubbard, Codegent Ltd (luke@codegent.com)
 */
@Anonymous
public class StatusObject implements IExternalizable {

    /** Constant <code>ERROR="error"</code> */
    /** Constant <code>STATUS="status"</code> */
    /** Constant <code>WARNING="warning"</code> */
    public static final String ERROR = "error", STATUS = "status", WARNING = "warning";

    protected String code;

    protected String level;

    protected String description = "";

    protected Object application;

    protected Map<String, Object> additional;

    /**
     * Constructs a new StatusObject.
     */
    public StatusObject() {
    }

    /**
     * <p>Constructor for StatusObject.</p>
     *
     * @param code a {@link java.lang.String} object
     * @param level a {@link java.lang.String} object
     */
    public StatusObject(String code, String level) {
        this.code = code;
        this.level = level;
    }

    /**
     * <p>Constructor for StatusObject.</p>
     *
     * @param code a {@link java.lang.String} object
     * @param level a {@link java.lang.String} object
     * @param description a {@link java.lang.String} object
     */
    public StatusObject(String code, String level, String description) {
        this.code = code;
        this.level = level;
        this.description = description;
    }

    /**
     * Getter for property 'code'.
     *
     * @return Value for property 'code'.
     */
    public String getCode() {
        return code;
    }

    /**
     * Setter for property 'code'.
     *
     * @param code
     *            Value to set for property 'code'.
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * Getter for property 'description'.
     *
     * @return Value for property 'description'.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Setter for property 'description'.
     *
     * @param description
     *            Value to set for property 'description'.
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Getter for property 'level'.
     *
     * @return Value for property 'level'.
     */
    public String getLevel() {
        return level;
    }

    /**
     * Setter for property 'level'.
     *
     * @param level
     *            Value to set for property 'level'.
     */
    public void setLevel(String level) {
        this.level = level;
    }

    /**
     * Setter for property 'application'.
     *
     * @param application
     *            Value to set for property 'application'.
     */
    public void setApplication(Object application) {
        this.application = application;
    }

    /**
     * Getter for property 'application'.
     *
     * @return Value for property 'application'.
     */
    public Object getApplication() {
        return application;
    }

    /**
     * <p>Setter for the field <code>additional</code>.</p>
     *
     * @param name a {@link java.lang.String} object
     * @param value a {@link java.lang.Object} object
     */
    public void setAdditional(String name, Object value) {
        if (!"code,level,description,application".contains(name)) {
            if (additional == null) {
                additional = new HashMap<>();
            }
            additional.put(name, value);
        }
    }

    /**
     * Generate Status object that can be returned through a RTMP channel.
     *
     * @return status
     */
    public Status asStatus() {
        return new Status(getCode(), getLevel(), getDescription());
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        if (additional != null) {
            return String.format("Status code: %s level: %s description: %s additional: %s", code, level, description, additional);
        }
        return String.format("Status code: %s level: %s description: %s", code, level, description);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public void readExternal(IDataInput in) {
        code = (String) in.readUTF();
        description = (String) in.readUTF();
        level = (String) in.readUTF();
        application = in.readObject();
        additional = (Map<String, Object>) in.readObject();
    }

    /** {@inheritDoc} */
    @Override
    public void writeExternal(IDataOutput out) {
        out.writeUTF(code);
        out.writeUTF(description);
        out.writeUTF(level);
        if (application != null) {
            out.writeObject(application);
        } else {
            out.writeObject(null);
        }
        if (additional != null) {
            out.writeObject(additional);
        } else {
            out.writeObject(null);
        }
    }

}
