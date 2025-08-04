/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model.operations.mutable;

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
public interface MutableEdgeOperations {

    /**
     * Adds a new edge between two nodes with a specified type and components.
     */
    Edge add(Type type, Node source, Node target, Data data, Set<Locator> components, Instant timestamp);

    /**
     * Updates an existing edge with new data.
     * This will expire the current version and create a new version.
     */
    Edge update(NanoId id, Data data, Instant timestamp);

    /**
     * Updates an existing edge with new type and data.
     * This will expire the current version and create a new version.
     */
    Edge update(NanoId id, Type type, Data data, Instant timestamp);

    /**
     * Updates an existing edge with new components.
     * This will expire the current version and create a new version.
     */
    Edge updateComponents(NanoId id, Set<Locator> components, Instant timestamp);

    /**
     * Expires an edge at the given timestamp.
     */
    Edge expire(NanoId id, Instant timestamp);
}
