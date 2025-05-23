/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.server.net.rtmp.status;

import org.red5.annotations.Anonymous;
import org.red5.io.amf3.IDataInput;
import org.red5.io.amf3.IDataOutput;
import org.red5.io.amf3.IExternalizable;

/**
 * Represents status object that are transferred between server and client
 *
 * @author mondain
 */
@Anonymous
public class Status implements StatusCodes, IExternalizable {

    /**
     * Error constant
     */
    public static final String ERROR = "error";

    /**
     * Status constant
     */
    public static final String STATUS = "status";

    /**
     * Warning constant
     */
    public static final String WARNING = "warning";

    /**
     * Status code
     */
    protected String code;

    /**
     * Status level
     */
    protected String level;

    /**
     * Status event description
     */
    protected String description = "";

    /**
     * Status event details
     */
    protected String details = "";

    /**
     * Id of client
     */
    protected Number clientid = -1d;

    /**
     * Constructs a new Status.
     */
    public Status() {
    }

    /**
     * Creates status object with given status code
     *
     * @param code
     *            Status code
     */
    public Status(String code) {
        this.code = code;
        this.level = STATUS;
    }

    /**
     * Creates status object with given level, description and status code
     *
     * @param code
     *            Status code
     * @param level
     *            Level
     * @param description
     *            Description
     */
    public Status(String code, String level, String description) {
        this.code = code;
        this.level = level;
        this.description = description;
    }

    /**
     * Getter for status code.
     *
     * @return Status code
     */
    public String getCode() {
        return code;
    }

    /**
     * Setter for code
     *
     * @param code
     *            Status code
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * Getter for description.
     *
     * @return Status event description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Setter for description.
     *
     * @param description
     *            Status event description.
     */
    public void setDesciption(String description) {
        this.description = description;
    }

    /**
     * Getter for level.
     *
     * @return Level
     */
    public String getLevel() {
        return level;
    }

    /**
     * Setter for level
     *
     * @param level
     *            Level
     */
    public void setLevel(String level) {
        this.level = level;
    }

    /**
     * Getter for client id
     *
     * @return Client id
     */
    public Number getClientid() {
        return clientid;
    }

    /**
     * Setter for client id
     *
     * @param clientid
     *            Client id
     */
    public void setClientid(Number clientid) {
        this.clientid = clientid;
    }

    /**
     * Getter for details
     *
     * @return Status event details
     */
    public String getDetails() {
        return details;
    }

    /**
     * Setter for details.
     *
     * @param details
     *            Status event details
     */
    public void setDetails(String details) {
        this.details = details;
    }

    /**
     * Setter for description.
     *
     * @param description
     *            Status event description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return "Status: code: " + getCode() + " desc: " + getDescription() + " level: " + getLevel();
    }

    /** {@inheritDoc} */
    @Override
    public void readExternal(IDataInput in) {
        clientid = (Number) in.readDouble();
        code = (String) in.readUTF();
        description = (String) in.readUTF();
        details = (String) in.readUTF();
        level = (String) in.readUTF();
    }

    /** {@inheritDoc} */
    @Override
    public void writeExternal(IDataOutput out) {
        out.writeDouble(clientid.doubleValue());
        out.writeUTF(code);
        out.writeUTF(description);
        out.writeUTF(details);
        out.writeUTF(level);
    }

}
