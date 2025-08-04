/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations.mutable;

import dev.iq.common.version.Locator;
import dev.iq.common.version.NanoId;
import dev.iq.graph.model.Data;
import dev.iq.graph.model.Node;
import dev.iq.graph.model.Type;
import java.time.Instant;
import java.util.Set;

/**
 * Mutable operations for managing nodes in a graph during construction or bulk operations.
 * These operations are separate from the immutable NodeOperations interface as they serve
 * a different purpose - graph construction rather than graph querying.
 */
public interface MutableNodeOperations {

    /**
     * Adds a new node to the graph with a specified type and components.
     */
    Node add(Type type, Data data, Set<Locator> components, Instant timestamp);

    /**
     * Updates an existing node with new data.
     * This will expire the current version and create a new version,
     * maintaining referential integrity by updating all connected edges.
     */
    Node update(NanoId id, Data data, Instant timestamp);

    /**
     * Updates an existing node with new type and data.
     * This will expire the current version and create a new version,
     * maintaining referential integrity by updating all connected edges.
     */
    Node update(NanoId id, Type type, Data data, Instant timestamp);

    /**
     * Updates an existing node with new components.
     * This will expire the current version and create a new version.
     */
    Node updateComponents(NanoId id, Set<Locator> components, Instant timestamp);

    /**
     * Expires a node at the given timestamp.
     * This will also expire all connected edges to maintain referential integrity.
     */
    Node expire(NanoId id, Instant timestamp);
}
