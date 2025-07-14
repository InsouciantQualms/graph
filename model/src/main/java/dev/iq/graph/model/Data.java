package dev.iq.graph.model;

/**
 * Container for typed data that can be stored with graph elements.
 */
public interface Data {

    /**
     * Returns the class type of the contained value.
     */
    Class<?> type();

    /**
     * Returns the actual data value.
     */
    Object value();
}
