package org.red5.server.net;

import java.util.Map;

import org.apache.mina.core.buffer.IoBuffer;
import org.red5.server.api.service.IServiceCall;

/**
 * Represents a "command" sent to or received from an end-point.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public interface ICommand {

    /**
     * <p>getTransactionId.</p>
     *
     * @return a int
     */
    int getTransactionId();

    /**
     * <p>getCall.</p>
     *
     * @return a {@link org.red5.server.api.service.IServiceCall} object
     */
    IServiceCall getCall();

    /**
     * <p>getConnectionParams.</p>
     *
     * @return a {@link java.util.Map} object
     */
    Map<String, Object> getConnectionParams();

    /**
     * <p>getData.</p>
     *
     * @return a {@link org.apache.mina.core.buffer.IoBuffer} object
     */
    IoBuffer getData();

}
