package org.red5.resource;

/**
 * Exception thrown when server directory cannot be resolved.
 * @author Andy Shaules
 */
public class RootResolutionException extends Exception {

    private static final long serialVersionUID = 2412009315006073537L;

    /**
     * Creates a new exception with the specified detail message.
     *
     * @param message the detail message describing why the server directory could not be resolved
     */
    public RootResolutionException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the specified detail message and cause.
     *
     * @param message the detail message describing why the server directory could not be resolved
     * @param cause the underlying cause of the resolution failure
     */
    public RootResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
