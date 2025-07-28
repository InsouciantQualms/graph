package dev.iq.graph.access;

/**
 * Encapsulates an operation that can be requested for authorization.
 */
public enum Operation {
    READ,
    WRITE,
    EXECUTE,
    GRANT
}
