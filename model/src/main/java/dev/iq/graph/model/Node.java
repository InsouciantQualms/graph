/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */
package dev.iq.graph.model;

import dev.iq.common.annotation.Stable;
import java.time.Instant;

/**
 * Represents a vertex in the graph that has zero or more edges (which can be incoming or outgoing from the node).
 * Implementations must be immutable and thread-safe.
 */
@Stable
public interface Node extends Element {

    /** Return an expired instance of this node. */
    Node expire(final Instant timestamp);
}
