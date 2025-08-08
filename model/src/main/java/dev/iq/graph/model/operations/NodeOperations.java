/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations;

import dev.iq.common.annotation.Stable;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Type;
import java.time.Instant;

/**
 * Mutable operations for managing nodes in a graph during construction or bulk operations.
 * These operations are separate from the immutable ComponentSpace interface as they serve
 * a different purpose - graph construction rather than graph querying.
 */
@Stable
public interface NodeOperations {

    /** Adds a new node to the graph. */
    Node add(NanoId id, Type type, Data data, Instant timestamp);

    /** Updates an existing node in the graph.  This will expire the current version and create a new version. */
    Node update(NanoId id, Type type, Data data, Instant timestamp);

    /** Expires a node at the given timestamp. */
    Node expire(NanoId id, Instant timestamp);
}
