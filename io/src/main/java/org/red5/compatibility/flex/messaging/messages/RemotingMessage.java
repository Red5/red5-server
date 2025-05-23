/*
 * RED5 Open Source Media Server - https://github.com/Red5/ Copyright 2006-2023 by respective authors (see below). All rights reserved. Licensed under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package org.red5.compatibility.flex.messaging.messages;

/**
 * Flex compatibility message that is sent by the <code>mx:RemoteObject</code> mxml tag.
 *
 * @see <a href="http://en.wikipedia.org/wiki/Action_Message_Format">Action Message Format</a>
 * @see <a href="http://flex.apache.org/asdoc/mx/messaging/messages/RemotingMessage.html">Apache Flex</a>
 * @author The Red5 Project
 * @author Joachim Bauch (jojo@struktur.de)
 */
public class RemotingMessage extends RPCMessage {

    private static final long serialVersionUID = 1491092800943415719L;

    /** Method to execute. */
    public String operation;

    /** Value of the <code>source</code> attribute of mx:RemoteObject that sent the message. */
    public String source;

    private Object[] parameters;

    /**
     * <p>Getter for the field <code>operation</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getOperation() {
        return operation;
    }

    /**
     * <p>Setter for the field <code>operation</code>.</p>
     *
     * @param operation a {@link java.lang.String} object
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    /**
     * <p>Getter for the field <code>source</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getSource() {
        return source;
    }

    /**
     * <p>Setter for the field <code>source</code>.</p>
     *
     * @param source a {@link java.lang.String} object
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * <p>Getter for the field <code>parameters</code>.</p>
     *
     * @return an array of {@link java.lang.Object} objects
     */
    public Object[] getParameters() {
        return parameters;
    }

    /**
     * <p>Setter for the field <code>parameters</code>.</p>
     *
     * @param params a {@link java.lang.Object} object
     */
    public void setParameters(Object... params) {
        this.parameters = params;
    }

    /** {@inheritDoc} */
    @Override
    protected void addParameters(StringBuilder result) {
        super.addParameters(result);
        result.append(",source=");
        result.append(source);
        result.append(",operation=");
        result.append(operation);
        result.append(",parameters=");
        result.append(parameters);
    }

}
