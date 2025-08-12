/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */
package dev.iq.graph.model;

import dev.iq.common.annotation.Stable;
import dev.iq.common.version.Locator;
import java.time.Instant;
import java.util.Set;

/**
 * Represents a directed edge in the graph connecting two nodes.
 * Implementations must be immutable and thread-safe.
 */
@Stable
public interface Edge extends Element {

    /** Returns the source node of this edge. */
    Node source();

    /** Returns the target node of this edge. */
    Node target();

    /** Returns the components associated with this edge. */
    Set<Locator> components();

    /** Returns an expired version of this edge. */
    Edge expire(Instant timestamp);
}
