package dev.iq.graph.model.simple;

import dev.iq.graph.model.Data;

/**
 * Simple implementation of Data interface.
 */
public record SimpleData(Class<?> type, Object value) implements Data {}