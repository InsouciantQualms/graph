/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.model;

import dev.iq.common.version.Locator;
import java.time.Instant;
import java.util.Collection;

/**
 * Builder for constructing graphs in a mutable fashion before creating an immutable Graph instance.
 * This pattern is particularly useful for:
 * - Loading graphs from persistence without triggering persistence events
 * - Bulk operations and batch construction
 * - Creating graphs in a controlled, validated manner
 */
public interface GraphBuilder {

    /**
     * Adds a node to the graph being built.
     */
    GraphBuilder addNode(Locator locator, Type type, Data data, Instant created);

    /**
     * Adds an edge to the graph being built.
     */
    GraphBuilder addEdge(Locator locator, Type type, Locator sourceId, Locator targetId, Data data, Instant created);

    /**
     * Adds a component to the graph being built.
     */
    GraphBuilder addComponent(Locator locator, Collection<Locator> elementIds, Data data, Instant created);

    /**
     * Validates the graph structure before building.
     * @throws IllegalStateException if the graph is invalid
     */
    GraphBuilder validate();

    /**
     * Builds an immutable Graph instance from the current builder state.
     * After calling build(), this builder should not be used further.
     */
    Graph build();
}
