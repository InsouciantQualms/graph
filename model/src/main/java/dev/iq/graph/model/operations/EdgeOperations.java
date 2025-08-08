/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

import dev.iq.common.annotation.Stable;
import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Edge;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Type;
import java.time.Instant;
import java.util.Set;

/**
 * Mutable operations for managing edges in a graph during construction or bulk operations.
 * These operations are separate from the immutable EdgeOperations interface as they serve
 * a different purpose - graph construction rather than graph querying.
 */
@Stable
public interface EdgeOperations {

    /** Adds a new edge to the graph. */
    Edge add(Type type, Node source, Node target, Data data, Set<Locator> components, Instant timestamp);

    /** Updates an existing edge.  This will expire the current version and create a new version. */
    Edge update(NanoId id, Type type, Data data, Set<Locator> components, Instant timestamp);

    /** Expires an edge at the given timestamp. */
    Edge expire(NanoId id, Instant timestamp);
}
