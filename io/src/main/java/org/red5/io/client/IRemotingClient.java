package org.red5.io.client;

/**
 * <p>IRemotingClient interface.</p>
 *
 * @author mondain
 */
public interface IRemotingClient {

    /**
     * <p>invokeMethod.</p>
     *
     * @param method a {@link java.lang.String} object
     * @param params an array of {@link java.lang.Object} objects
     * @return a {@link java.lang.Object} object
     */
    Object invokeMethod(String method, Object[] params);

}
