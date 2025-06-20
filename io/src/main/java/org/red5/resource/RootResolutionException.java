package org.red5.resource;

/**
 * Exception thrown when server directory cannot be resolved.
 * @author Andy Shaules
 */
public class RootResolutionException extends Exception {

    private static final long serialVersionUID = 2412009315006073537L;

    public RootResolutionException(String message) {
        super(message);
    }

    public RootResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
