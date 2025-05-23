package org.red5.compatibility.flex.messaging.messages;

import org.red5.io.amf3.IDataOutput;

/**
 * <p>Abstract RPCMessage class.</p>
 *
 * @author mondain
 */
public abstract class RPCMessage extends AbstractMessage {
    private static final long serialVersionUID = -1203255926746881424L;

    private String remoteUsername;

    private String remotePassword;

    /**
     * <p>Getter for the field <code>remoteUsername</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getRemoteUsername() {
        return this.remoteUsername;
    }

    /**
     * <p>Setter for the field <code>remoteUsername</code>.</p>
     *
     * @param s a {@link java.lang.String} object
     */
    public void setRemoteUsername(String s) {
        this.remoteUsername = s;
    }

    /**
     * <p>Getter for the field <code>remotePassword</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getRemotePassword() {
        return this.remotePassword;
    }

    /**
     * <p>Setter for the field <code>remotePassword</code>.</p>
     *
     * @param s a {@link java.lang.String} object
     */
    public void setRemotePassword(String s) {
        this.remotePassword = s;
    }

    /** {@inheritDoc} */
    @Override
    public void writeExternal(IDataOutput output) {
        super.writeExternal(output);
        output.writeObject(remoteUsername);
        output.writeObject(remotePassword);
    }

    /** {@inheritDoc} */
    @Override
    protected void addParameters(StringBuilder result) {
        super.addParameters(result);
        result.append("remoteUsername=" + remoteUsername);
    }
}
