package dev.iq.graph.model.simple;

import dev.iq.graph.model.Type;

/**
 * Describes a node or edge's type.
 */
public record SimpleType(String code) implements Type {}
