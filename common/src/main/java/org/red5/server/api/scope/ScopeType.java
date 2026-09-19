package org.red5.server.api.scope;

/**
 * Represents all the supported scope types.
 *
 * @author Paul Gregoire (mondain@gmail.com)
 */
public enum ScopeType {

    /** Scope type has not been set. */
    UNDEFINED,
    /** The top level scope for the entire server. */
    GLOBAL,
    /** A scope representing a deployed application. */
    APPLICATION,
    /** A scope representing a room within an application. */
    ROOM,
    /** A scope used for broadcasting a stream. */
    BROADCAST,
    /** A scope backing a shared object. */
    SHARED_OBJECT;

}
